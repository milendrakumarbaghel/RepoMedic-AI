package org.springboot.backend;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Component
class DemoFixture {
    private final Map<String, String> contents = new LinkedHashMap<>();

    DemoFixture() throws IOException {
        for (String name : List.of("package.json", "cart.js", "cart.test.js", "app.jsx")) {
            try (InputStream input = getClass().getResourceAsStream("/demo/" + name)) {
                if (input == null) throw new IOException("Missing bundled demo resource");
                contents.put(name, new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    void copyTo(Path workspace) throws IOException {
        for (var file : contents.entrySet()) Files.writeString(workspace.resolve(file.getKey()), file.getValue(),
                StandardOpenOption.CREATE_NEW);
    }

    /** Source is only an eligibility filter; neither names nor persisted executionMode grant trust. */
    boolean trustedForHost(String source, Path workspace) {
        if (!"demo".equals(source) && !"upload".equals(source)) return false;
        try { return trusted(workspace); }
        catch (IOException | SecurityException e) { return false; }
    }

    /** Host execution accepts only the byte-for-byte bundle or its one audited arithmetic fix. */
    boolean trusted(Path workspace) throws IOException {
        try (var paths = Files.walk(workspace)) {
            List<Path> all = paths.filter(p -> !p.equals(workspace)).toList();
            if (all.size() != contents.size()) return false;
            for (Path path : all) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return false;
                String name = SafeFiles.relative(workspace, path);
                String expected = contents.get(name);
                if (expected == null || Files.size(path) > SafeFiles.MAX_SOURCE_BYTES) return false;
                byte[] actual = Files.readAllBytes(path);
                if (!Arrays.equals(actual, expected.getBytes(StandardCharsets.UTF_8)) && !(name.equals("cart.js")
                        && Arrays.equals(actual, expected.replace(StaticAnalyzer.BROKEN, StaticAnalyzer.FIXED)
                        .getBytes(StandardCharsets.UTF_8)))) return false;
            }
        }
        return true;
    }
}
