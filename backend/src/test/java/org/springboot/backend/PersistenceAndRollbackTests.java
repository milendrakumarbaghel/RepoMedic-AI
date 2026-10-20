package org.springboot.backend;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class PersistenceAndRollbackTests {
    Path directory;
    JsonMapper json = JsonMapper.builder().build();
    @BeforeEach void setup() { directory = TestSupport.directory(); }
    @AfterEach void cleanup() throws Exception { SafeFiles.deleteTree(directory); }

    @Test void keepsOnlyFiftyNewestAnalysesAndDeletesOldPrivateWorkspaces() throws Exception {
        AnalysisStore store = new AnalysisStore(TestSupport.settings(directory), json);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            final int index = i;
            ids.add(store.create(entry -> {
                entry.analysis.repository = "project";
                entry.analysis.createdAt = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(index).toString();
                try { Files.writeString(entry.workspace().resolve("private.js"), "private"); }
                catch (Exception e) { throw new RuntimeException(e); }
                return entry.analysis;
            }).id);
        }
        assertThat(store.list()).hasSize(50);
        assertThat(directory.resolve("analyses").resolve(ids.getFirst())).doesNotExist();
        assertThatThrownBy(() -> store.read(ids.getFirst())).isInstanceOf(ApiException.class);
        assertThat(new AnalysisStore(TestSupport.settings(directory), json).list()).hasSize(50);
    }

    @ParameterizedTest
    @ValueSource(strings = {"failed", "zero", "all-skipped", "blocked"})
    void unsuccessfulTestResultRollsBackAndNeverMarksPatchesValidated(String outcome) throws Exception {
        var settings = TestSupport.settings(directory);
        DemoFixture demo = new DemoFixture();
        TestRunner runner = new TestRunner(settings, demo) {
            @Override Analysis.TestResult run(Analysis a, Path workspace) {
                try {
                    if (Files.readString(workspace.resolve("cart.js")).contains(StaticAnalyzer.BROKEN)) return super.run(a, workspace);
                } catch (Exception e) { throw new RuntimeException(e); }
                return switch (outcome) {
                    case "failed" -> new Analysis.TestResult(6, 2, 4, 0, "failed", NODE_COMMAND, "controlled failing runner output", 1);
                    case "zero" -> new Analysis.TestResult(0, 0, 0, 0, "passed", NODE_COMMAND, "no tests", 1);
                    case "all-skipped" -> new Analysis.TestResult(6, 0, 0, 6, "passed", NODE_COMMAND, "all skipped", 1);
                    default -> Analysis.TestResult.blocked(NODE_COMMAND, "sandbox unavailable");
                };
            }
        };
        AnalysisStore store = new AnalysisStore(settings, json);
        var remote = new RemoteHttp();
        var zip = new ZipIngestor();
        AnalysisService service = new AnalysisService(store, settings, demo, zip,
                new GitHubImporter(remote, json, zip), new StaticAnalyzer(), runner, new AiAdvisor(settings, remote, json, demo));
        Analysis initial = service.demo();
        Analysis failed = service.validate(initial.id);
        assertThat(failed.status).isEqualTo("issues_found");
        assertThat(failed.baselineTests).isEqualTo(initial.baselineTests);
        assertThat(failed.tests.output()).contains(outcome.equals("failed") ? "controlled failing" : outcome.equals("zero") ? "no tests"
                : outcome.equals("all-skipped") ? "all skipped" : "sandbox unavailable");
        assertThat(failed.patches).noneMatch(p -> p.status().equals("validated"));
        assertThat(Files.readString(directory.resolve("analyses").resolve(initial.id).resolve("workspace/cart.js")))
                .contains(StaticAnalyzer.BROKEN).doesNotContain(StaticAnalyzer.FIXED);
        assertThat(new AnalysisStore(settings, json).read(initial.id).status).isEqualTo("issues_found");
    }

    @Test void arbitraryFilesCannotBeAddedToTrustedDemo() throws Exception {
        DemoFixture fixture = new DemoFixture();
        Path workspace = SafeFiles.privateDirectory(directory.resolve("workspace"));
        fixture.copyTo(workspace);
        assertThat(fixture.trusted(workspace)).isTrue();
        Files.writeString(workspace.resolve("evil.test.js"), "throw Error('must not execute')");
        assertThat(fixture.trusted(workspace)).isFalse();
    }
}
