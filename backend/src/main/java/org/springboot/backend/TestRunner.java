package org.springboot.backend;

import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
class TestRunner {
    static final String NODE_COMMAND = "node --test --test-reporter=tap";
    private static final int OUTPUT_LIMIT = 128 * 1024;
    private final RepoMedicConfig.Settings settings;
    private final DemoFixture fixture;

    TestRunner(RepoMedicConfig.Settings settings, DemoFixture fixture) {
        this.settings = settings; this.fixture = fixture;
    }

    Analysis.TestResult run(Analysis a, Path workspace) {
        // Recompute every time; persisted mode, repository name, and prior successful checks are not authority.
        a.executionMode = settings.sandboxEnabled() ? "docker-sandbox" : "static-only";
        long verificationStart = System.nanoTime();
        if (fixture.trustedForHost(a.source, workspace)) {
            a.executionMode = "trusted-demo-node";
            a.events.add(new Analysis.Event("Trusted audited fixture recognition",
                    "Reverified all four shipped fixture files byte-for-byte (original or exact audited arithmetic fix), with no extra files, directories, or symlinks. "
                            + "Content-verified " + a.source + " may run trusted Node tests on the host; source and repository identity are preserved.",
                    "complete", elapsed(verificationStart)));
            return node(execute(List.of("node", "--test", "--test-reporter=tap"), workspace, Duration.ofSeconds(20)), NODE_COMMAND);
        }
        if (a.source.equals("demo")) return Analysis.TestResult.blocked(NODE_COMMAND,
                "Host execution blocked: workspace is not the audited bundled demo or its exact rule-based fix.");
        if (!settings.sandboxEnabled()) return Analysis.TestResult.blocked("Not executed",
                "Untrusted uploaded/imported code is never executed on the host. Only byte-verified bundled fixture uploads qualify for trusted execution; GitHub imports do not. "
                        + "Docker sandbox execution is disabled (REPOMEDIC_SANDBOX_ENABLED=false). Static findings and suggested patches are available.");
        return sandbox(a, workspace);
    }

    private Analysis.TestResult sandbox(Analysis a, Path workspace) {
        boolean java = Files.isRegularFile(workspace.resolve("pom.xml"));
        boolean js = a.fileTree.stream().anyMatch(p -> p.matches(".*\\.(js|mjs|cjs)$"));
        if (!java && !js) return Analysis.TestResult.blocked("Not executed", "No supported root Maven project or JavaScript tests were detected.");
        String name = "repomedic-" + UUID.randomUUID();
        String image = java ? "maven:3.9.11-eclipse-temurin-25" : "node:23.10.0-bookworm-slim";
        String command = java ? "mvn -o -Dmaven.repo.local=/tmp/m2 test" : NODE_COMMAND;
        String user;
        try { user = Files.getAttribute(workspace, "unix:uid").toString(); }
        catch (IOException | UnsupportedOperationException e) {
            return Analysis.TestResult.blocked(command, "Cannot establish a non-root sandbox user on this host.");
        }
        if (user.equals("0")) user = "65534";
        List<String> args = new ArrayList<>(List.of("docker", "run", "--rm", "--pull=never", "--name", name,
                "--network=none", "--read-only", "--cap-drop=ALL", "--security-opt=no-new-privileges",
                "--memory=512m", "--memory-swap=512m", "--cpus=1", "--pids-limit=128", "--user", user,
                "--tmpfs", "/tmp:rw,noexec,nosuid,size=128m,mode=1777", "--env", "HOME=/tmp",
                "--mount", "type=bind,source=" + workspace.toAbsolutePath() + ",target=/workspace,readonly",
                "--workdir", "/workspace"));
        if (java) args.addAll(List.of("--tmpfs", "/workspace/target:rw,noexec,nosuid,size=128m,uid=" + user));
        args.add(image);
        args.addAll(java ? List.of("mvn", "-o", "-Dmaven.repo.local=/tmp/m2", "test")
                : List.of("node", "--test", "--test-reporter=tap"));
        try {
            ProcessResult result = execute(args, workspace, Duration.ofSeconds(45));
            if (result.exitCode == 125 || result.output.contains("Cannot connect to the Docker daemon")
                    || result.output.contains("permission denied while trying to connect"))
                return blocked(command, result, "Docker daemon/image unavailable; no host fallback is allowed.");
            return java ? maven(result, command) : node(result, command);
        } finally {
            // Also remove containers left alive by a killed/timeout CLI, without invoking a shell.
            execute(List.of("docker", "rm", "-f", name), workspace, Duration.ofSeconds(5));
        }
    }

    record ProcessResult(int exitCode, String output, long durationMs, boolean timedOut, boolean unavailable) { }

    private ProcessResult execute(List<String> command, Path cwd, Duration timeout) {
        long start = System.nanoTime();
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command).directory(cwd.toFile()).redirectErrorStream(true);
            // In particular, never inherit NODE_OPTIONS, JAVA_TOOL_OPTIONS, or provider credentials.
            builder.environment().clear();
            builder.environment().put("PATH", System.getenv().getOrDefault("PATH", "/usr/local/bin:/usr/bin:/bin"));
            builder.environment().put("HOME", cwd.toAbsolutePath().toString());
            builder.environment().put("LANG", "C.UTF-8");
            builder.environment().put("NO_COLOR", "1");
            process = builder.start();
            Process running = process;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            boolean[] truncated = {false};
            Thread reader = Thread.ofVirtual().start(() -> {
                try (InputStream in = running.getInputStream()) {
                    byte[] bytes = new byte[8192];
                    for (int n; (n = in.read(bytes)) >= 0;) {
                        int retain = Math.min(n, OUTPUT_LIMIT - output.size());
                        if (retain > 0) output.write(bytes, 0, retain);
                        if (retain < n) truncated[0] = true;
                    }
                } catch (IOException ignored) { }
            });
            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
            reader.join(2000);
            if (reader.isAlive()) { process.getInputStream().close(); reader.join(1000); }
            String text = output.toString(StandardCharsets.UTF_8)
                    .replace("file://" + cwd.toAbsolutePath() + "/", "")
                    .replace(cwd.toAbsolutePath() + "/", "")
                    + (truncated[0] ? "\n[Output truncated at 128 KiB]" : "")
                    + (!finished ? "\n[Execution timed out]" : "");
            return new ProcessResult(finished ? process.exitValue() : -1, text, elapsed(start), !finished, false);
        } catch (IOException e) {
            return new ProcessResult(-1, "Required execution tool is unavailable; no host fallback was attempted.", elapsed(start), false, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); }
            return new ProcessResult(-1, "Execution interrupted.", elapsed(start), true, false);
        }
    }

    static Analysis.TestResult node(ProcessResult result, String command) {
        if (result.unavailable || result.timedOut) return blocked(command, result, "Execution unavailable or timed out.");
        if (result.output.contains("ERR_MODULE_NOT_FOUND") || result.output.contains("Cannot find module"))
            return blocked(command, result, "Required Node dependencies are absent; dependencies are never installed for imported projects.");
        int total = metric(result.output, "tests"), passed = metric(result.output, "pass"),
                failed = metric(result.output, "fail"), skipped = metric(result.output, "skipped");
        int todo = Math.max(0, metric(result.output, "todo"));
        if (total < 1 || passed < 0 || failed < 0 || skipped < 0 || passed + failed + skipped + todo != total)
            return blocked(command, result, "No complete, nonzero Node test summary was produced. Compiler/test-runner output follows.");
        return new Analysis.TestResult(total, passed, failed, skipped + todo,
                result.exitCode == 0 && failed == 0 && passed > 0 ? "passed" : "failed", command, result.output, result.durationMs);
    }

    private static int metric(String output, String name) {
        var matcher = Pattern.compile("(?m)^# " + name + " (\\d+)\\s*$").matcher(output);
        int value = -1;
        while (matcher.find()) value = Integer.parseInt(matcher.group(1));
        return value;
    }

    private static Analysis.TestResult maven(ProcessResult result, String command) {
        if (result.unavailable || result.timedOut || result.output.contains("offline mode")
                || result.output.contains("has not been downloaded") || result.output.contains("Could not resolve"))
            return blocked(command, result, "Offline Maven dependencies/tooling are unavailable; no downloads or host execution are allowed.");
        var matcher = Pattern.compile("Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)").matcher(result.output);
        int total = 0, failed = 0, skipped = 0;
        while (matcher.find()) {
            total = Integer.parseInt(matcher.group(1));
            failed = Integer.parseInt(matcher.group(2)) + Integer.parseInt(matcher.group(3));
            skipped = Integer.parseInt(matcher.group(4));
        }
        if (total == 0) return blocked(command, result, "No nonzero Surefire test summary was produced. Compiler/test-runner output follows.");
        int passed = Math.max(0, total - failed - skipped);
        return new Analysis.TestResult(total, passed, failed, skipped,
                result.exitCode == 0 && failed == 0 && passed > 0 ? "passed" : "failed", command, result.output, result.durationMs);
    }

    private static Analysis.TestResult blocked(String command, ProcessResult r, String reason) {
        return new Analysis.TestResult(0, 0, 0, 0, "blocked", command, reason + "\n" + r.output, r.durationMs);
    }

    static long elapsed(long start) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start); }
}
