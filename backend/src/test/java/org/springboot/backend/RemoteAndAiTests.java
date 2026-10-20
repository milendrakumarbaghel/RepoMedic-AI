package org.springboot.backend;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.*;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class RemoteAndAiTests {
    Path directory;
    final JsonMapper json = JsonMapper.builder().build();
    @BeforeEach void setup() { directory = TestSupport.directory(); }
    @AfterEach void cleanup() throws Exception { SafeFiles.deleteTree(directory); }

    @Test void importsDefaultBranchFromFixedHostsAndNormalizesGithubArchiveRoot() throws Exception {
        List<URI> requests = new ArrayList<>();
        byte[] archive = TestSupport.zip(Map.of("repo-dev/package.json", "{}", "repo-dev/src/index.js", "debugger;\n"));
        RemoteHttp remote = new RemoteHttp() {
            @Override Response send(HttpRequest request, int limit) {
                requests.add(request.uri());
                return requests.size() == 1 ? new Response(200, "{\"default_branch\":\"feature/cart\",\"private\":false}".getBytes())
                        : new Response(200, archive);
            }
        };
        Path workspace = SafeFiles.privateDirectory(directory.resolve("workspace"));
        var repository = new GitHubImporter(remote, json, new ZipIngestor())
                .fetch("https://github.com/owner/repo.git", directory, workspace);
        assertThat(repository.branch()).isEqualTo("feature/cart");
        assertThat(repository.name()).isEqualTo("owner/repo");
        assertThat(requests).extracting(URI::getHost).containsExactly("api.github.com", "codeload.github.com");
        assertThat(requests.get(1).getRawPath()).endsWith("/zip/refs/heads/feature%2Fcart");
        assertThat(workspace.resolve("src/index.js")).exists();
        assertThat(directory.resolve("input.zip")).doesNotExist();
    }

    @Test void remoteClientDoesNotFollowRedirectsAndBoundsResponseBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        AtomicInteger destinationCalls = new AtomicInteger();
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "/destination");
            exchange.sendResponseHeaders(302, -1); exchange.close();
        });
        server.createContext("/destination", exchange -> {
            destinationCalls.incrementAndGet();
            exchange.sendResponseHeaders(200, 0); exchange.close();
        });
        server.createContext("/large", exchange -> {
            byte[] content = new byte[10000];
            exchange.sendResponseHeaders(200, content.length);
            try (var out = exchange.getResponseBody()) { out.write(content); }
        });
        server.start();
        try {
            String base = "http://localhost:" + server.getAddress().getPort();
            RemoteHttp remote = new RemoteHttp();
            RemoteHttp.Response redirect = remote.send(HttpRequest.newBuilder(URI.create(base + "/redirect"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), 100);
            assertThat(redirect.status()).isEqualTo(302);
            assertThat(destinationCalls).hasValue(0);
            assertThatThrownBy(() -> remote.send(HttpRequest.newBuilder(URI.create(base + "/large")).GET().build(), 100))
                    .isInstanceOf(ApiException.class).satisfies(e -> assertThat(((ApiException) e).status()).isEqualTo(413));
        } finally { server.stop(0); }
    }

    @Test void configuredAiReceivesBoundedSnippetsAndReturnsSourceOnlySuggestion() throws Exception {
        var settings = new RepoMedicConfig.Settings(directory.toString(), false,
                "https://provider.example/chat/completions", "test-key-do-not-log", "test-model");
        Path workspace = SafeFiles.privateDirectory(directory.resolve("workspace"));
        Files.writeString(workspace.resolve("app.js"), "const count = 2;\nif (count == '2') console.log(count);\n");
        Analysis analysis = new Analysis(); analysis.source = "upload";
        new StaticAnalyzer().scan(analysis, workspace);
        List<HttpRequest> requests = new ArrayList<>();
        RemoteHttp remote = new RemoteHttp() {
            @Override Response send(HttpRequest request, int limit) {
                requests.add(request);
                String content = json.writeValueAsString(Map.of("file", "app.js", "before", "count == '2'",
                        "after", "count === 2", "explanation", "Compare numbers without coercion."));
                return new Response(200, json.writeValueAsString(Map.of("choices", List.of(Map.of("message",
                        Map.of("content", content))))).getBytes(StandardCharsets.UTF_8));
            }
        };
        new AiAdvisor(settings, remote, json, new DemoFixture()).advise(analysis, workspace);
        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().uri().toString()).isEqualTo(settings.aiApiUrl());
        assertThat(requests.getFirst().bodyPublisher().orElseThrow().contentLength()).isLessThan(14000);
        assertThat(analysis.patches).singleElement().satisfies(p -> {
            assertThat(p.provider()).isEqualTo("ai"); assertThat(p.file()).isEqualTo("app.js"); assertThat(p.line()).isEqualTo(2);
        });
        assertThat(settings.toString()).doesNotContain("test-key-do-not-log");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside.js", "cart.test.js", "package.json", "missing.js"})
    void unsafeAiTargetsAreRejectedWithVisibleFallbackWarning(String target) throws Exception {
        Path workspace = SafeFiles.privateDirectory(directory.resolve("workspace"));
        Files.writeString(workspace.resolve("cart.js"), StaticAnalyzer.BROKEN + "\nreturn subtotal - discount;\n");
        Files.writeString(workspace.resolve("cart.test.js"), "debugger;");
        Files.writeString(workspace.resolve("package.json"), "{}");
        Analysis analysis = new Analysis(); analysis.source = "upload";
        new StaticAnalyzer().scan(analysis, workspace);
        var settings = new RepoMedicConfig.Settings(directory.toString(), false, "https://provider.example/api", "key", "model");
        RemoteHttp remote = new RemoteHttp() {
            @Override Response send(HttpRequest request, int limit) {
                String content = json.writeValueAsString(Map.of("file", target, "before", "debugger;", "after", "",
                        "explanation", "Must be rejected"));
                return new Response(200, json.writeValueAsString(Map.of("choices", List.of(Map.of("message",
                        Map.of("content", content))))).getBytes(StandardCharsets.UTF_8));
            }
        };
        new AiAdvisor(settings, remote, json, new DemoFixture()).advise(analysis, workspace);
        assertThat(analysis.events).anyMatch(e -> e.label().equals("AI warning") && e.status().equals("blocked"));
        assertThat(analysis.patches).singleElement().satisfies(p -> assertThat(p.provider()).isEqualTo("rule-based"));
        assertThat(Files.readString(workspace.resolve("cart.test.js"))).isEqualTo("debugger;");
    }

    @Test void providerFailureDoesNotHideDeterministicSuggestions() throws Exception {
        Path workspace = SafeFiles.privateDirectory(directory.resolve("workspace"));
        Files.writeString(workspace.resolve("cart.js"), StaticAnalyzer.BROKEN + "\nreturn subtotal - discount;\n");
        Analysis analysis = new Analysis(); analysis.source = "upload";
        new StaticAnalyzer().scan(analysis, workspace);
        var settings = new RepoMedicConfig.Settings(directory.toString(), false, "https://provider.example/api", "key", "model");
        RemoteHttp remote = new RemoteHttp() { @Override Response send(HttpRequest request, int limit) {
            return new Response(503, "provider failure with sensitive detail".getBytes());
        } };
        new AiAdvisor(settings, remote, json, new DemoFixture()).advise(analysis, workspace);
        assertThat(analysis.events).singleElement().satisfies(e -> {
            assertThat(e.label()).isEqualTo("AI warning"); assertThat(e.detail()).contains("fallback").doesNotContain("sensitive");
        });
        assertThat(analysis.patches).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"demo", "upload"})
    void trustedFixtureMakesNoExternalCallsEvenWithAiExplicitlyConfigured(String source) throws IOException {
        var settings = new RepoMedicConfig.Settings(directory.toString(), false, "https://provider.example/api", "key", "model");
        Analysis analysis = new Analysis(); analysis.source = source;
        DemoFixture fixture = new DemoFixture();
        fixture.copyTo(directory);
        RemoteHttp forbidden = new RemoteHttp() { @Override Response send(HttpRequest request, int limit) {
            throw new AssertionError("Content-verified fixture must not make external calls");
        } };
        new AiAdvisor(settings, forbidden, json, fixture).advise(analysis, directory);
        assertThat(analysis.events).singleElement().satisfies(e -> assertThat(e.detail()).contains("no external AI request"));
        assertThat(analysis.patches).isEmpty();
    }

    @Test void exactGithubFixtureAndForgedExecutionModeNeverAuthorizeHostExecution() throws IOException {
        DemoFixture fixture = new DemoFixture();
        fixture.copyTo(directory);
        Analysis analysis = new Analysis();
        analysis.source = "github";
        analysis.executionMode = "trusted-demo-node";
        analysis.repository = "shopping-cart-demo";
        var result = new TestRunner(TestSupport.settings(directory), fixture).run(analysis, directory);
        assertThat(result.status()).isEqualTo("blocked");
        assertThat(result.total()).isZero();
        assertThat(analysis.executionMode).isEqualTo("static-only");
        assertThat(analysis.events).noneMatch(e -> e.label().equals("Trusted audited fixture recognition"));
    }

    @Test void nodeSummaryCannotClaimPassingTestsForEmptySkippedOrMissingDependencies() {
        for (String output : List.of("TAP version 13\n# tests 0\n# pass 0\n# fail 0\n# skipped 0\n# todo 0\n",
                "ERR_MODULE_NOT_FOUND", "SyntaxError: unexpected token")) {
            assertThat(TestRunner.node(new TestRunner.ProcessResult(0, output, 1, false, false), TestRunner.NODE_COMMAND).status())
                    .isEqualTo("blocked");
        }
        var skipped = TestRunner.node(new TestRunner.ProcessResult(0,
                "# tests 6\n# pass 0\n# fail 0\n# skipped 6\n# todo 0\n", 1, false, false), TestRunner.NODE_COMMAND);
        assertThat(skipped.status()).isEqualTo("failed");
    }
}
