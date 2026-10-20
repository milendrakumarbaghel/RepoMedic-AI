package org.springboot.backend;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class SafeFiles {
    static final int MAX_SOURCE_BYTES = 256 * 1024;
    private static final Set<String> IGNORED = Set.of(".git", ".svn", ".hg", "node_modules", "target",
            "dist", "build", ".repomedic", ".ssh", ".aws", ".azure", ".kube", ".docker", "__macosx");
    private static final Set<String> EXTENSIONS = Set.of("js", "jsx", "mjs", "cjs", "ts", "tsx", "java",
            "json", "html", "css", "md", "xml", "properties", "yaml", "yml", "txt");

    private SafeFiles() { }

    static Path privateDirectory(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        for (Path component : absolute) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) throw new IOException("Symbolic link in storage path");
        }
        Files.createDirectories(absolute);
        try { Files.setPosixFilePermissions(absolute, PosixFilePermissions.fromString("rwx------")); }
        catch (UnsupportedOperationException ignored) { /* Non-POSIX deployment. */ }
        return absolute;
    }

    static boolean ignored(String name) {
        String normalized = name.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.equals("git/config") || normalized.endsWith("/git/config")) return true;
        for (String part : name.replace('\\', '/').split("/")) {
            String p = part.toLowerCase(Locale.ROOT);
            if (IGNORED.contains(p) || p.startsWith(".env") || p.equals(".npmrc") || p.equals(".netrc")
                    || p.equals(".gitconfig") || p.equals("gitconfig") || p.equals(".gitmodules")
                    || p.equals(".git-credentials") || p.equals("config.json") || p.equals(".dockercfg")
                    || p.equals(".yarnrc.yml") || p.equals(".pypirc") || p.equals("auth.json")
                    || p.contains("credential") || p.contains("secret") || p.equals("id_rsa")
                    || p.startsWith("id_ed25519") || p.endsWith(".pem") || p.endsWith(".key")
                    || p.endsWith(".p12") || p.endsWith(".pfx") || p.equals("settings.xml")) return true;
        }
        return false;
    }

    static Path resolve(Path root, String relative) {
        if (relative == null || relative.isBlank() || relative.length() > 500
                || relative.indexOf('\\') >= 0 || relative.startsWith("/")
                || relative.matches("^[A-Za-z]:.*") || relative.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw ApiException.invalid("Unsafe archive or source path");
        }
        for (String part : relative.split("/")) {
            if (part.equals("..") || part.equals(".")) throw ApiException.invalid("Archive traversal rejected");
        }
        Path base = root.toAbsolutePath().normalize();
        Path result = base.resolve(relative).normalize();
        if (!result.startsWith(base) || result.equals(base)) throw ApiException.invalid("Archive traversal rejected");
        for (Path p = result; p != null && p.startsWith(base); p = p.getParent()) {
            if (Files.isSymbolicLink(p)) throw ApiException.invalid("Symbolic links are not allowed");
        }
        return result;
    }

    static List<Path> files(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> !ignored(relative(root, p))).sorted().toList();
        }
    }

    static String relative(Path root, Path file) { return root.relativize(file).toString().replace('\\', '/'); }

    static String source(Path path) throws IOException {
        if (Files.size(path) > MAX_SOURCE_BYTES) return null;
        byte[] bytes = Files.readAllBytes(path);
        for (byte b : bytes) if (b == 0) return null;
        try { return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { return null; }
    }

    static boolean isSource(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 && EXTENSIONS.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    static boolean patchable(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        String name = Path.of(path).getFileName().toString().toLowerCase(Locale.ROOT);
        return isSource(path) && !ignored(path) && !lower.matches(".*(^|/)(__tests__|tests?|testdata|fixtures)(/.*)?")
                && !name.matches(".*(test|spec).*\\.(js|jsx|ts|tsx|mjs|cjs|java)$")
                && !Set.of("package.json", "package-lock.json", "pom.xml", "build.gradle", "build.gradle.kts",
                "manifest.json", "jsconfig.json", "tsconfig.json").contains(name)
                && !name.endsWith(".json") && !name.endsWith(".xml") && !name.endsWith(".yaml") && !name.endsWith(".yml");
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}
