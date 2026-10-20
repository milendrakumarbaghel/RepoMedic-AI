package org.springboot.backend;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

@Service
class AnalysisService {
    private final AnalysisStore store;
    private final RepoMedicConfig.Settings settings;
    private final DemoFixture fixture;
    private final ZipIngestor zip;
    private final GitHubImporter github;
    private final StaticAnalyzer scanner;
    private final TestRunner runner;
    private final AiAdvisor ai;

    AnalysisService(AnalysisStore store, RepoMedicConfig.Settings settings, DemoFixture fixture,
                    ZipIngestor zip, GitHubImporter github, StaticAnalyzer scanner, TestRunner runner, AiAdvisor ai) {
        this.store = store; this.settings = settings; this.fixture = fixture; this.zip = zip;
        this.github = github; this.scanner = scanner; this.runner = runner; this.ai = ai;
    }

    List<Analysis> list() { return store.list(); }
    Analysis get(String id) { return store.read(id); }

    Analysis demo() {
        return store.create(entry -> {
            long start = System.nanoTime();
            initialize(entry.analysis, "shopping-cart-demo", "demo", "main");
            try { fixture.copyTo(entry.workspace()); }
            catch (IOException e) { throw AnalysisStore.storageFailure(); }
            entry.analysis.events.add(new Analysis.Event("Load demo", "Copied the bundled React cart and six dependency-free node:test cases.", "complete", TestRunner.elapsed(start)));
            return analyze(entry, start);
        });
    }

    Analysis upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.invalid("Provide a nonempty ZIP in multipart field 'file'");
        if (file.getSize() > ZipIngestor.MAX_COMPRESSED) throw ZipIngestor.tooLarge("ZIP exceeds 20 MB compressed");
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("uploaded-project.zip")
                .replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
        final String repository = name.isBlank() ? "uploaded-project" : name.substring(0, Math.min(150, name.length())).replaceFirst("(?i)\\.zip$", "");
        return store.create(entry -> {
            long start = System.nanoTime();
            initialize(entry.analysis, repository, "upload", "uploaded");
            try (var input = file.getInputStream()) { zip.extract(input, entry.directory, entry.workspace()); }
            catch (IOException e) { throw ApiException.invalid("Unable to read the ZIP upload"); }
            entry.analysis.events.add(new Analysis.Event("Inspect ZIP", "Extracted bounded ZIP; ignored credentials and generated/dependency directories. Host execution requires a fresh exact audited fixture content match.", "complete", TestRunner.elapsed(start)));
            return analyze(entry, start);
        });
    }

    Analysis importGitHub(String url) {
        GitHubImporter.repository(url); // Reject invalid URLs before reserving storage or making a request.
        return store.create(entry -> {
            long start = System.nanoTime();
            initialize(entry.analysis, GitHubImporter.repository(url), "github", "");
            var repository = github.fetch(url, entry.directory, entry.workspace());
            entry.analysis.branch = repository.branch();
            entry.analysis.events.add(new Analysis.Event("Import GitHub", "Fetched the public default branch through fixed GitHub API/codeload hosts without redirects.", "complete", TestRunner.elapsed(start)));
            return analyze(entry, start);
        });
    }

    private void initialize(Analysis a, String repository, String source, String branch) {
        a.repository = repository;
        a.source = source;
        a.branch = branch;
        a.createdAt = Instant.now().toString();
        a.aiEnabled = settings.aiEnabled();
        a.executionMode = settings.sandboxEnabled() ? "docker-sandbox" : "static-only";
    }

    private Analysis analyze(AnalysisStore.Entry entry, long start) {
        Analysis a = entry.analysis;
        long scanStart = System.nanoTime();
        try { scanner.scan(a, entry.workspace()); }
        catch (IOException e) { throw AnalysisStore.storageFailure(); }
        a.events.add(new Analysis.Event("Static analysis", "Read " + a.filesScanned + " bounded text files; detected "
                + a.findings.size() + " source findings. Findings are heuristics for review, not proof of exploitability.", "complete", TestRunner.elapsed(scanStart)));
        a.tests = runner.run(a, entry.workspace());
        a.baselineTests = a.tests;
        testEvent(a, "Baseline tests", a.tests);
        ai.advise(a, entry.workspace());
        try {
            for (String file : a.patches.stream().map(Analysis.Patch::file).distinct().toList()) {
                Path original = SafeFiles.resolve(entry.directory.resolve("originals"), file);
                Files.createDirectories(original.getParent());
                Files.copy(SafeFiles.resolve(entry.workspace(), file), original);
            }
        } catch (IOException e) { throw AnalysisStore.storageFailure(); }
        a.status = a.findings.isEmpty() && a.tests.status().equals("passed") ? "healthy" : "issues_found";
        a.summary = summary(a);
        a.durationMs = TestRunner.elapsed(start);
        return a;
    }

    Analysis validate(String id) {
        return store.locked(id, entry -> {
            Analysis a = entry.analysis;
            if (a.status.equals("error")) throw new ApiException(409, "workspace_error", "This workspace cannot be validated after a storage error");
            if (a.status.equals("validated")) return store.copy(a);
            long start = System.nanoTime();
            List<Analysis.Patch> suggestions = a.patches.stream().filter(p -> p.status().equals("suggested")).toList();
            if (suggestions.isEmpty()) {
                a.events.add(new Analysis.Event("Validate patches", "No suggested patches are available to validate.", "blocked", 0));
                store.save(entry);
                return store.copy(a);
            }
            // Fresh bytes, never a persisted mode or prior recognition, authorize the trusted path.
            // The runner independently re-verifies the patched workspace immediately before execution.
            if (!fixture.trustedForHost(a.source, entry.workspace()) && !settings.sandboxEnabled()) {
                a.tests = runner.run(a, entry.workspace());
                testEvent(a, "Validation blocked", a.tests);
                a.status = "issues_found";
                a.summary = summary(a);
                store.save(entry);
                return store.copy(a);
            }
            Map<Path, String> originals = new LinkedHashMap<>();
            Map<Path, String> modified = new LinkedHashMap<>();
            boolean accepted = false;
            List<Analysis.Patch> previousPatches = new ArrayList<>(a.patches);
            try {
                for (Analysis.Patch patch : suggestions) {
                    Path file = SafeFiles.resolve(entry.workspace(), patch.file());
                    if (!SafeFiles.patchable(patch.file()) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                            || patch.before().equals(patch.after()) || patch.after().length() > 8000)
                        throw ApiException.invalid("Patch targets a forbidden source or contains no change");
                    String original = originals.get(file);
                    if (original == null) {
                        original = SafeFiles.source(file);
                        if (original == null) throw ApiException.invalid("Patch source is binary or too large");
                        originals.put(file, original);
                    }
                    String current = modified.getOrDefault(file, original);
                    if (!StaticAnalyzer.unique(current, patch.before())) throw ApiException.invalid("Patch does not match one unique exact source location");
                    modified.put(file, current.replace(patch.before(), patch.after()));
                }
                for (var file : modified.entrySet()) Files.writeString(file.getKey(), file.getValue());
                a.tests = runner.run(a, entry.workspace());
                testEvent(a, "Patched tests", a.tests);
                accepted = a.tests.status().equals("passed") && a.tests.total() > 0 && a.tests.passed() > 0 && a.tests.failed() == 0;
                final String next = accepted ? "validated" : a.tests.status().equals("blocked") ? "suggested" : "rejected";
                a.patches = a.patches.stream().map(p -> p.status().equals("suggested") ? p.withStatus(next) : p).toList();
                a.status = accepted ? "validated" : "issues_found";
                a.events.add(new Analysis.Event("Validate patches", accepted
                        ? "Accepted exact source-only patches after a real nonzero passing test run. Baseline results are preserved."
                        : "Patch validation did not pass; all source changes were rolled back. Baseline results and attempted test logs are preserved.",
                        accepted ? "complete" : a.tests.status().equals("blocked") ? "blocked" : "failed", TestRunner.elapsed(start)));
                a.summary = accepted ? "Validated " + suggestions.size() + " patch(es): " + a.tests.passed()
                        + "/" + a.tests.total() + " tests passed; baseline had " + a.baselineTests.failed() + " failures. Findings describe the original source."
                        : "Validation did not pass; workspace restored. " + summary(a);
                a.durationMs += TestRunner.elapsed(start);
                store.save(entry);
            } catch (IOException | RuntimeException e) {
                accepted = false;
                a.patches = previousPatches.stream().map(p -> p.status().equals("suggested") ? p.withStatus("rejected") : p).toList();
                a.status = "issues_found";
                a.events.add(new Analysis.Event("Validate patches", "Patch was rejected because it could not be safely applied or persisted; workspace restored.", "failed", TestRunner.elapsed(start)));
                a.summary = "Patch rejected; the original workspace was restored.";
            } finally {
                if (!accepted) {
                    try { for (var file : originals.entrySet()) Files.writeString(file.getKey(), file.getValue()); }
                    catch (IOException e) {
                        a.status = "error";
                        a.events.add(new Analysis.Event("Rollback", "Unable to restore a source file; this workspace must not be executed again.", "failed", 0));
                        store.save(entry);
                        throw AnalysisStore.storageFailure();
                    }
                }
            }
            store.save(entry);
            return store.copy(a);
        });
    }

    private static String summary(Analysis a) {
        String prefix = a.tests.status().equals("blocked")
                ? ("static-only".equals(a.executionMode) ? "Unverified static-only. " : "Unverified. ") : "";
        prefix += a.findings.size() + " static finding(s), " + a.patches.size() + " suggested patch(es). ";
        return prefix + (a.tests.status().equals("blocked")
                ? "Tests are blocked; no passing test counts or validation are claimed."
                : a.tests.passed() + "/" + a.tests.total() + " tests passed, " + a.tests.failed() + " failed.");
    }

    private static void testEvent(Analysis a, String label, Analysis.TestResult result) {
        String status = switch (result.status()) { case "passed" -> "complete"; case "failed" -> "failed"; default -> "blocked"; };
        String detail = result.status().equals("blocked") ? result.output().lines().findFirst().orElse("Execution blocked")
                : result.command() + ": " + result.passed() + " passed, " + result.failed() + " failed, " + result.skipped() + " skipped.";
        a.events.add(new Analysis.Event(label, detail, status, result.durationMs()));
        if (result.output().matches("(?s).*(SyntaxError|COMPILATION ERROR|error TS[0-9]+|ESLint|Checkstyle).*"))
            a.events.add(new Analysis.Event("Compiler/linter diagnostics", "Execution emitted compiler/linter diagnostics. See the preserved test output for actual file locations and messages.", "failed", 0));
    }
}
