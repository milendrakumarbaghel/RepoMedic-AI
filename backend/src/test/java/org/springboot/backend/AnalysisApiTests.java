package org.springboot.backend;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"repomedic.ai-api-url=", "repomedic.ai-api-key=", "repomedic.ai-model=",
        "repomedic.sandbox-enabled=false"})
@AutoConfigureMockMvc
class AnalysisApiTests {
    static final Path DATA = TestSupport.directory();
    @DynamicPropertySource static void config(DynamicPropertyRegistry properties) {
        properties.add("repomedic.data-dir", DATA::toString);
    }
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired AnalysisStore store;
    @Autowired AnalysisService service;

    @AfterAll static void cleanup() throws Exception { SafeFiles.deleteTree(DATA); }

    @Test void demoFailsThenValidatesWithRealNodeAndDownloadableArtifacts() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok")).andExpect(jsonPath("$.aiEnabled").value(false));
        Analysis baseline = json.readValue(mvc.perform(post("/api/analyses/demo"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), Analysis.class);
        assertThat(baseline.source).isEqualTo("demo");
        assertThat(baseline.executionMode).isEqualTo("trusted-demo-node");
        assertThat(baseline.stack).contains("React", "JavaScript", "Node.js test runner");
        assertThat(baseline.status).isEqualTo("issues_found");
        assertThat(baseline.baselineTests.total()).isEqualTo(6);
        assertThat(baseline.baselineTests.failed()).isEqualTo(3);
        assertThat(baseline.baselineTests.passed()).isEqualTo(3);
        assertThat(baseline.baselineTests.output()).contains("TAP version 13", "ERR_ASSERTION", "# tests 6");
        assertThat(baseline.findings).singleElement().satisfies(f -> {
            assertThat(f.file()).isEqualTo("cart.js");
            assertThat(f.line()).isEqualTo(13);
            assertThat(f.patchable()).isTrue();
        });
        assertThat(baseline.patches).singleElement().satisfies(p -> assertThat(p.provider()).isEqualTo("rule-based"));
        String initialDiff = mvc.perform(get("/api/analyses/{id}/patch", baseline.id)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/plain"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsString();
        assertThat(initialDiff).contains("--- a/cart.js", "+++ b/cart.js", "@@ -1,", "+  " + StaticAnalyzer.FIXED);
        Analysis validated = json.readValue(mvc.perform(post("/api/analyses/{id}/validate", baseline.id))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), Analysis.class);
        assertThat(validated.status).isEqualTo("validated");
        assertThat(validated.tests.total()).isEqualTo(6);
        assertThat(validated.tests.passed()).isEqualTo(6);
        assertThat(validated.tests.failed()).isZero();
        assertThat(validated.tests.output()).contains("# pass 6", "# fail 0");
        assertThat(validated.baselineTests).isEqualTo(baseline.baselineTests);
        assertThat(validated.patches).allMatch(p -> p.status().equals("validated"));
        mvc.perform(get("/api/analyses/{id}/report", baseline.id)).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/markdown"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("# fail 3")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("# pass 6")));
        mvc.perform(get("/api/analyses/{id}/patch", baseline.id)).andExpect(content().string(initialDiff));
        mvc.perform(get("/api/analyses/{id}", baseline.id)).andExpect(jsonPath("$.status").value("validated"));
        mvc.perform(get("/api/analyses")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(baseline.id)));
        // Snapshot is actually durable and readable by a freshly created store.
        AnalysisStore reopened = new AnalysisStore(TestSupport.settings(DATA), json);
        assertThat(reopened.read(baseline.id).tests.passed()).isEqualTo(6);
    }

    @Test void exactFixtureUploadFailsThenValidatesWithoutDockerAndKeepsUploadIdentity() throws Exception {
        Analysis baseline = upload(TestSupport.demoFiles(), "customer-cart.zip");
        assertThat(baseline.source).isEqualTo("upload");
        assertThat(baseline.repository).isEqualTo("customer-cart");
        assertThat(baseline.executionMode).isEqualTo("trusted-demo-node");
        assertThat(baseline.status).isEqualTo("issues_found");
        assertThat(baseline.tests.total()).isEqualTo(6);
        assertThat(baseline.tests.passed()).isEqualTo(3);
        assertThat(baseline.tests.failed()).isEqualTo(3);
        assertThat(baseline.tests.output()).contains("TAP version 13", "# fail 3");
        assertThat(baseline.events).anySatisfy(e -> {
            assertThat(e.label()).isEqualTo("Trusted audited fixture recognition");
            assertThat(e.detail()).contains("four", "byte-for-byte", "upload");
            assertThat(e.status()).isEqualTo("complete");
        });
        assertThat(baseline.patches).singleElement().satisfies(p -> assertThat(p.provider()).isEqualTo("rule-based"));
        Analysis validated = json.readValue(mvc.perform(post("/api/analyses/{id}/validate", baseline.id))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), Analysis.class);
        assertThat(validated.source).isEqualTo("upload");
        assertThat(validated.repository).isEqualTo("customer-cart");
        assertThat(validated.executionMode).isEqualTo("trusted-demo-node");
        assertThat(validated.status).isEqualTo("validated");
        assertThat(validated.tests.total()).isEqualTo(6);
        assertThat(validated.tests.passed()).isEqualTo(6);
        assertThat(validated.tests.failed()).isZero();
        assertThat(validated.tests.output()).contains("# pass 6", "# fail 0");
        assertThat(validated.baselineTests).isEqualTo(baseline.baselineTests);
        assertThat(validated.patches).allMatch(p -> p.status().equals("validated"));
        assertThat(validated.events.stream().filter(e -> e.label().equals("Trusted audited fixture recognition"))).hasSize(2);
    }

    @Test void exactAlreadyFixedFixtureUploadRunsRealTestsAndIsHealthy() throws Exception {
        Map<String, String> files = TestSupport.demoFiles();
        files.compute("cart.js", (name, content) -> content.replace(StaticAnalyzer.BROKEN, StaticAnalyzer.FIXED));
        Analysis uploaded = upload(files);
        assertThat(uploaded.source).isEqualTo("upload");
        assertThat(uploaded.executionMode).isEqualTo("trusted-demo-node");
        assertThat(uploaded.status).isEqualTo("healthy");
        assertThat(uploaded.tests.total()).isEqualTo(6);
        assertThat(uploaded.tests.passed()).isEqualTo(6);
        assertThat(uploaded.findings).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"extra-file", "modified-test"})
    void maliciousFixtureLookalikeUploadIsBlockedRegardlessOfFilename(String tampering) throws Exception {
        Map<String, String> files = TestSupport.demoFiles();
        String malicious = "import fs from 'node:fs'; fs.writeFileSync('HOST_EXECUTED', 'unsafe');\n";
        if (tampering.equals("extra-file")) files.put("evil.test.js", malicious);
        else files.compute("cart.test.js", (name, content) -> content + malicious);
        Analysis uploaded = upload(files, "shopping-cart-demo.zip");
        assertThat(uploaded.source).isEqualTo("upload");
        assertThat(uploaded.executionMode).isEqualTo("static-only");
        assertThat(uploaded.tests.status()).isEqualTo("blocked");
        assertThat(uploaded.tests.total()).isZero();
        assertThat(uploaded.events).noneMatch(e -> e.label().equals("Trusted audited fixture recognition"));
        assertThat(service.validate(uploaded.id).tests.status()).isEqualTo("blocked");
        assertThat(DATA.resolve("analyses").resolve(uploaded.id).resolve("workspace/HOST_EXECUTED")).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"extra-file", "modified-test"})
    void validationRechecksPreviouslyTrustedUploadRatherThanPersistedMode(String tampering) throws Exception {
        Analysis uploaded = upload(TestSupport.demoFiles());
        assertThat(uploaded.executionMode).isEqualTo("trusted-demo-node");
        Path workspace = DATA.resolve("analyses").resolve(uploaded.id).resolve("workspace");
        String malicious = "\nimport fs from 'node:fs'; fs.writeFileSync('HOST_EXECUTED', 'unsafe');\n";
        if (tampering.equals("extra-file")) Files.writeString(workspace.resolve("evil.test.js"), malicious);
        else Files.writeString(workspace.resolve("cart.test.js"), malicious, StandardOpenOption.APPEND);
        Analysis attempt = json.readValue(mvc.perform(post("/api/analyses/{id}/validate", uploaded.id))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), Analysis.class);
        assertThat(attempt.executionMode).isEqualTo("static-only");
        assertThat(attempt.tests.status()).isEqualTo("blocked");
        assertThat(attempt.tests.total()).isZero();
        assertThat(attempt.baselineTests).isEqualTo(uploaded.baselineTests);
        assertThat(attempt.patches).allMatch(p -> p.status().equals("suggested"));
        assertThat(Files.readString(workspace.resolve("cart.js"))).contains(StaticAnalyzer.BROKEN);
        assertThat(workspace.resolve("HOST_EXECUTED")).doesNotExist();
    }

    @Test void unverifiedUploadWithoutFindingsIsNotHealthy() throws Exception {
        Analysis uploaded = upload(Map.of("cart.js", "export const total = 42;\n"));
        assertThat(uploaded.findings).isEmpty();
        assertThat(uploaded.tests.status()).isEqualTo("blocked");
        assertThat(uploaded.status).isEqualTo("issues_found");
        assertThat(uploaded.summary).startsWith("Unverified static-only.");
    }

    @Test void untrustedUploadedCodeIsNeverExecutedAndBlockedValidationDoesNotModifySources() throws Exception {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("package.json", "{\"scripts\":{\"test\":\"touch HOST_EXECUTED\"},\"type\":\"module\"}");
        input.put("cart.js", "export function calculate(subtotal, discountPercent) {\n  " + StaticAnalyzer.BROKEN
                + "\n  return subtotal - discount;\n}\n");
        input.put("evil.test.js", "import fs from 'node:fs'; fs.writeFileSync('HOST_EXECUTED', 'unsafe');\n");
        input.put(".git/config", "[credential]\nhelper = unsafe");
        input.put(".env.production", "TOKEN=private");
        input.put("nested/credentials.json", "private");
        input.put(".npmrc", "token=private");
        input.put("node_modules/dependency/index.js", "debugger;");
        input.put("dist/bundle.js", "debugger;");
        input.put("target/output.txt", "generated");
        Analysis uploaded = upload(input);
        assertThat(uploaded.source).isEqualTo("upload");
        assertThat(uploaded.tests.status()).isEqualTo("blocked");
        assertThat(uploaded.tests.total()).isZero();
        assertThat(uploaded.tests.output()).contains("never executed on the host");
        assertThat(uploaded.fileTree).containsExactly("cart.js", "evil.test.js", "package.json");
        Path workspace = DATA.resolve("analyses").resolve(uploaded.id).resolve("workspace");
        assertThat(workspace.resolve("HOST_EXECUTED")).doesNotExist();
        assertThat(workspace.resolve(".env.production")).doesNotExist();
        assertThat(workspace.resolve(".git/config")).doesNotExist();
        String original = Files.readString(workspace.resolve("cart.js"));
        Analysis validated = service.validate(uploaded.id);
        assertThat(validated.status).isEqualTo("issues_found");
        assertThat(validated.patches).allMatch(p -> p.status().equals("suggested"));
        assertThat(Files.readString(workspace.resolve("cart.js"))).isEqualTo(original);
        assertThat(workspace.resolve("HOST_EXECUTED")).doesNotExist();
    }

    @Test void sourceFindingsUseRealLinesAndDoNotFlagStringOrCommentText() throws Exception {
        Analysis a = upload(Map.of("package.json", "{}", "src/app.jsx", "// debugger; ==\nconst s = 'debugger; ==';\n"
                + "debugger;\nif (value == 2) render();\nconst view = <div dangerouslySetInnerHTML={{__html: value}} />;\n",
                "Main.java", "class Main { void run() { System.out.println(42); } }\n"));
        assertThat(a.findings).hasSize(4);
        assertThat(a.findings).anySatisfy(f -> {
            assertThat(f.file()).isEqualTo("src/app.jsx"); assertThat(f.line()).isEqualTo(3);
            assertThat(f.category()).isEqualTo("debugging");
        });
        assertThat(a.findings).anyMatch(f -> f.line() == 4 && f.category().equals("correctness"));
        assertThat(a.findings).anyMatch(f -> f.line() == 5 && f.category().equals("security"));
        assertThat(a.findings).anyMatch(f -> f.file().equals("Main.java") && f.line() == 1 && f.category().equals("logging"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo", "upload"})
    void unsafePatchCannotExecuteOnHostAndAllChangesRollBack(String source) throws Exception {
        Analysis demo = source.equals("demo") ? service.demo() : upload(TestSupport.demoFiles());
        store.locked(demo.id, entry -> {
            Analysis.Patch p = entry.analysis.patches.getFirst();
            entry.analysis.patches.set(0, new Analysis.Patch(p.id(), p.file(), p.line(), p.before(),
                    "throw new Error('not the audited fix');", p.explanation(), "ai", "suggested"));
            store.save(entry);
            return null;
        });
        Analysis attempt = service.validate(demo.id);
        assertThat(attempt.tests.status()).isEqualTo("blocked");
        assertThat(attempt.tests.output()).containsIgnoringCase("host");
        assertThat(attempt.tests.total()).isZero();
        assertThat(attempt.baselineTests).isEqualTo(demo.baselineTests);
        assertThat(Files.readString(DATA.resolve("analyses").resolve(demo.id).resolve("workspace/cart.js")))
                .contains(StaticAnalyzer.BROKEN).doesNotContain(StaticAnalyzer.FIXED);
    }

    @Test void sameAnalysisValidationIsAtomicAndIdempotentUnderConcurrency() throws Exception {
        Analysis demo = service.demo();
        try (ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Analysis> validate = () -> { start.await(); return service.validate(demo.id); };
            Future<Analysis> first = threads.submit(validate), second = threads.submit(validate);
            start.countDown();
            for (Future<Analysis> result : List.of(first, second)) {
                Analysis a = result.get(30, TimeUnit.SECONDS);
                assertThat(a.status).isEqualTo("validated");
                assertThat(a.tests.passed()).isEqualTo(6);
                assertThat(a.events.stream().filter(e -> e.label().equals("Patched tests"))).hasSize(1);
            }
        }
    }

    @Test void structuredErrorsForBadImportEmptyZipAndUnknownAnalysis() throws Exception {
        for (String url : List.of("http://localhost:9000/private", "https://github.com.evil/owner/repo",
                "https://github.com/owner/repo/tree/main", "https://github.com/owner/repo?url=evil",
                "https://github.com/owner/..", "https://github.com@localhost/owner/repo")) {
            mvc.perform(post("/api/analyses/import").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("url", url))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_input"));
        }
        mvc.perform(post("/api/analyses/import").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/analyses/upload").file(new MockMultipartFile("file", "empty.zip", "application/zip", TestSupport.zip(Map.of()))))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/analyses/upload")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/analyses/not-an-id")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("not_found"));
        mvc.perform(get("/api/missing")).andExpect(status().isNotFound());
    }

    private Analysis upload(Map<String, String> files) throws Exception {
        return upload(files, "project.zip");
    }

    private Analysis upload(Map<String, String> files, String filename) throws Exception {
        return json.readValue(mvc.perform(multipart("/api/analyses/upload").file(
                        new MockMultipartFile("file", filename, "application/zip", TestSupport.zip(files))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), Analysis.class);
    }
}
