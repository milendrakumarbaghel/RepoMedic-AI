package org.springboot.backend;

import org.apache.commons.compress.archivers.zip.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

import static org.assertj.core.api.Assertions.*;

class IngestionSecurityTests {
    Path directory;
    Path workspace;
    final ZipIngestor zip = new ZipIngestor();

    @BeforeEach void setup() throws Exception {
        directory = TestSupport.directory();
        workspace = SafeFiles.privateDirectory(directory.resolve("workspace"));
    }
    @AfterEach void cleanup() throws Exception { SafeFiles.deleteTree(directory); }

    @ParameterizedTest
    @ValueSource(strings = {"../escape.txt", "project/../../escape.txt", "/absolute.txt", "C:/windows.txt",
            "project\\..\\escape.txt", ".git/../../escape.txt"})
    void zipTraversalIsRejectedBeforeWritingAnyFiles(String path) {
        assertThatThrownBy(() -> extract(TestSupport.zip(Map.of(path, "unsafe"))))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(400));
        assertThat(directory.resolve("escape.txt")).doesNotExist();
        assertThat(directory.resolve("input.zip")).doesNotExist();
    }

    @Test void symbolicLinkInZipIsRejectedEvenWhenItWouldBeIgnored() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream output = new ZipArchiveOutputStream(bytes)) {
            ZipArchiveEntry link = new ZipArchiveEntry("node_modules/link");
            link.setUnixMode(0120777);
            output.putArchiveEntry(link);
            output.write("../../outside".getBytes());
            output.closeArchiveEntry();
        }
        assertThatThrownBy(() -> extract(bytes.toByteArray())).isInstanceOf(ApiException.class)
                .hasMessageContaining("symbolic links");
    }

    @Test void symlinkInWorkspaceAndStoragePathIsRejected() throws Exception {
        Files.createSymbolicLink(workspace.resolve("link"), directory);
        assertThatThrownBy(() -> SafeFiles.resolve(workspace, "link/file.js"))
                .isInstanceOf(ApiException.class).hasMessageContaining("Symbolic");
        assertThatThrownBy(() -> SafeFiles.privateDirectory(workspace.resolve("link/child")))
                .isInstanceOf(IOException.class);
    }

    @Test void expansionOverFiftyMegabytesIsRejected() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes)) {
            output.putNextEntry(new ZipEntry("huge.txt"));
            byte[] block = new byte[1024 * 1024];
            for (int i = 0; i < 51; i++) output.write(block);
            output.closeEntry();
        }
        assertThat(bytes.size()).isLessThan(20 * 1024 * 1024);
        assertThatThrownBy(() -> extract(bytes.toByteArray())).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isIn(400, 413));
    }

    @Test void moreThanTwoThousandFilesIsRejected() {
        Map<String, String> files = new LinkedHashMap<>();
        for (int i = 0; i < 2001; i++) files.put("file" + i + ".js", "");
        assertThatThrownBy(() -> extract(TestSupport.zip(files))).isInstanceOf(ApiException.class)
                .hasMessageContaining("2000");
    }

    @Test void compressedInputIsBoundedWhileStreaming() {
        InputStream endless = new InputStream() {
            @Override public int read() { return 0; }
            @Override public int read(byte[] b, int off, int len) { Arrays.fill(b, off, off + len, (byte) 0); return len; }
        };
        assertThatThrownBy(() -> zip.extract(endless, directory, workspace)).isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(413));
        assertThat(directory.resolve("input.zip")).doesNotExist();
    }

    @Test void duplicateCaseInsensitivePathsAreRejected() {
        assertThatThrownBy(() -> extract(TestSupport.zip(Map.of("app.js", "1", "APP.js", "2"))))
                .isInstanceOf(ApiException.class).hasMessageContaining("Duplicate");
    }

    @Test void invalidEmptyAndCredentialsOnlyArchivesAreRejected() {
        for (byte[] bytes : List.of("not a zip".getBytes(), TestSupport.zip(Map.of()),
                TestSupport.zip(Map.of(".env", "private", ".git/config", "private")))) {
            assertThatThrownBy(() -> extract(bytes)).isInstanceOf(ApiException.class);
        }
    }

    @Test void testsAndManifestsCannotBePatched() {
        for (String name : List.of("cart.test.js", "src/__tests__/cart.js", "src/test/cart.js", "tests/cart.js",
                "src/CartTests.java", "package.json", "pom.xml", "build.gradle", ".env"))
            assertThat(SafeFiles.patchable(name)).as(name).isFalse();
        assertThat(SafeFiles.patchable("src/cart.js")).isTrue();
    }

    private void extract(byte[] bytes) { zip.extract(new ByteArrayInputStream(bytes), directory, workspace); }
}
