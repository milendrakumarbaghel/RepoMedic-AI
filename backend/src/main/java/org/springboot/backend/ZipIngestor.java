package org.springboot.backend;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;

@Component
class ZipIngestor {
    static final long MAX_COMPRESSED = 20L * 1024 * 1024;
    static final long MAX_EXPANDED = 50L * 1024 * 1024;
    static final int MAX_FILES = 2000;

    void extract(InputStream input, Path directory, Path workspace) {
        extract(input, directory, workspace, false);
    }

    void extract(InputStream input, Path directory, Path workspace, boolean stripRoot) {
        Path archive = directory.resolve("input.zip");
        try {
            try (OutputStream output = Files.newOutputStream(archive, StandardOpenOption.CREATE_NEW)) {
                copyBounded(input, output, MAX_COMPRESSED);
            }
            try (ZipFile zip = ZipFile.builder().setPath(archive).get()) {
                List<ZipArchiveEntry> entries = Collections.list(zip.getEntries());
                if (entries.isEmpty()) throw ApiException.invalid("The ZIP archive is empty");
                if (entries.size() > MAX_FILES * 2) throw ApiException.invalid("Too many ZIP entries");
                int files = 0;
                long declaredSize = 0;
                for (ZipArchiveEntry entry : entries) {
                    SafeFiles.resolve(workspace, entry.getName());
                    int type = entry.getUnixMode() & 0170000;
                    if (entry.isUnixSymlink() || (type != 0 && type != 0100000 && type != 0040000))
                        throw ApiException.invalid("ZIP symbolic links and special files are not allowed");
                    if (!entry.isDirectory() && ++files > MAX_FILES) throw ApiException.invalid("ZIP exceeds 2000 files");
                    if (entry.getSize() < 0 || entry.getSize() > MAX_EXPANDED)
                        throw ApiException.invalid("Invalid or oversized ZIP entry");
                    declaredSize += entry.getSize();
                    if (declaredSize > MAX_EXPANDED) throw tooLarge("ZIP exceeds 50 MB unpacked");
                    if (!zip.canReadEntryData(entry)) throw ApiException.invalid("Encrypted or unsupported ZIP entry");
                }
                String prefix = stripRoot ? commonPrefix(entries) : "";
                long expanded = 0;
                int retained = 0;
                Set<String> names = new HashSet<>();
                for (ZipArchiveEntry entry : entries) {
                    if (entry.isDirectory()) continue;
                    String name = entry.getName().substring(prefix.length());
                    Path destination = SafeFiles.resolve(workspace, name);
                    if (!names.add(name.toLowerCase(Locale.ROOT))) throw ApiException.invalid("Duplicate archive path");
                    boolean ignored = SafeFiles.ignored(name);
                    if (!ignored) Files.createDirectories(destination.getParent());
                    CRC32 crc = new CRC32();
                    long count = 0;
                    try (InputStream source = zip.getInputStream(entry);
                         OutputStream target = ignored ? OutputStream.nullOutputStream()
                                 : Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
                        byte[] buffer = new byte[8192];
                        for (int n; (n = source.read(buffer)) != -1;) {
                            expanded += n;
                            count += n;
                            if (expanded > MAX_EXPANDED) throw tooLarge("ZIP exceeds 50 MB unpacked");
                            crc.update(buffer, 0, n);
                            target.write(buffer, 0, n);
                        }
                    }
                    if (count != entry.getSize() || crc.getValue() != entry.getCrc())
                        throw ApiException.invalid("ZIP entry integrity check failed");
                    if (!ignored) retained++;
                }
                if (retained == 0) throw ApiException.invalid("ZIP contains no usable project files");
            }
        } catch (ApiException e) { throw e; }
        catch (IOException | IllegalArgumentException e) { throw ApiException.invalid("Invalid or unreadable ZIP archive"); }
        finally { try { Files.deleteIfExists(archive); } catch (IOException ignored) { } }
    }

    private static String commonPrefix(List<ZipArchiveEntry> entries) {
        String prefix = null;
        for (ZipArchiveEntry entry : entries) {
            if (entry.isDirectory()) continue;
            String name = entry.getName();
            int slash = name.indexOf('/');
            if (slash < 0) return "";
            String candidate = name.substring(0, slash + 1);
            if (prefix == null) prefix = candidate;
            else if (!prefix.equals(candidate)) return "";
        }
        return prefix == null ? "" : prefix;
    }

    static long copyBounded(InputStream input, OutputStream output, long limit) throws IOException {
        long size = 0;
        byte[] buffer = new byte[8192];
        for (int n; (n = input.read(buffer)) != -1;) {
            size += n;
            if (size > limit) throw tooLarge("Download or upload exceeds its size limit");
            output.write(buffer, 0, n);
        }
        return size;
    }

    static ApiException tooLarge(String message) { return new ApiException(413, "size_limit", message); }
}
