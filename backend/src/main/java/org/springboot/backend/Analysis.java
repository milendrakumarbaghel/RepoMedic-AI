package org.springboot.backend;

import java.util.ArrayList;
import java.util.List;

/** Public API DTO and on-disk snapshot. Mutations are guarded by AnalysisStore. */
public class Analysis {
    public String id;
    public String repository;
    public String source;
    public String branch;
    public List<String> stack = new ArrayList<>();
    public String status;
    public String createdAt;
    public long durationMs;
    public int filesScanned;
    public String summary;
    public String executionMode;
    public boolean aiEnabled;
    public List<String> fileTree = new ArrayList<>();
    public TestResult tests;
    public TestResult baselineTests;
    public List<Finding> findings = new ArrayList<>();
    public List<Patch> patches = new ArrayList<>();
    public List<Event> events = new ArrayList<>();

    public record TestResult(int total, int passed, int failed, int skipped, String status,
                             String command, String output, long durationMs) {
        public static TestResult blocked(String command, String reason) {
            return new TestResult(0, 0, 0, 0, "blocked", command, reason, 0);
        }
    }

    public record Finding(String id, String title, String severity, String category, String file,
                          int line, String message, String explanation, String before, String after,
                          boolean patchable) { }

    public record Patch(String id, String file, int line, String before, String after,
                        String explanation, String provider, String status) {
        public Patch withStatus(String next) {
            return new Patch(id, file, line, before, after, explanation, provider, next);
        }
    }

    public record Event(String label, String detail, String status, long durationMs) { }
}
