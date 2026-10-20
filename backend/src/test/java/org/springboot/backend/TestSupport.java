package org.springboot.backend;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

final class TestSupport {
    private TestSupport() { }

    static Path directory() {
        try {
            Files.createDirectories(Path.of("target"));
            return Files.createTempDirectory(Path.of("target").toAbsolutePath(), "repomedic-test-");
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    static byte[] zip(Map<String, String> files) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                for (var entry : files.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return bytes.toByteArray();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    static Map<String, String> demoFiles() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        for (String name : List.of("package.json", "cart.js", "cart.test.js", "app.jsx")) {
            try (InputStream input = TestSupport.class.getResourceAsStream("/demo/" + name)) {
                if (input == null) throw new IOException("Missing bundled demo resource");
                files.put(name, new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    static RepoMedicConfig.Settings settings(Path directory) {
        return new RepoMedicConfig.Settings(directory.toString(), false, "", "", "");
    }
}
