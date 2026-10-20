package org.springboot.backend;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Component
class AnalysisExports {
    private final AnalysisStore store;
    AnalysisExports(AnalysisStore store) { this.store = store; }

    String report(String id) {
        Analysis a = store.read(id);
        StringBuilder md = new StringBuilder("# RepoMedic AI analysis\n\n");
        md.append("- Repository: ").append(inline(a.repository)).append("\n- ID: ").append(a.id)
                .append("\n- Source: ").append(a.source).append("\n- Branch: ").append(inline(a.branch))
                .append("\n- Created: ").append(a.createdAt).append("\n- Status: ").append(a.status)
                .append("\n- Execution mode: ").append(a.executionMode).append("\n- AI configured: ").append(a.aiEnabled)
                .append("\n- Files scanned: ").append(a.filesScanned).append("\n- Duration: ").append(a.durationMs).append(" ms\n\n")
                .append(inline(a.summary)).append("\n\n");
        tests(md, "Baseline tests", a.baselineTests);
        tests(md, "Latest tests / validation attempt", a.tests);
        md.append("## Original-source findings\n\n");
        for (Analysis.Finding f : a.findings) {
            md.append("### ").append(inline(f.title())).append(" (").append(f.severity()).append(")\n\n")
                    .append(inline(f.file())).append(":").append(f.line()).append(" — ").append(inline(f.category())).append("\n\n")
                    .append(inline(f.message())).append("\n\n").append(inline(f.explanation())).append("\n\n");
            fenced(md, "Before", f.before());
            if (f.patchable()) fenced(md, "Suggested replacement", f.after());
        }
        md.append("## Patches\n\n");
        for (Analysis.Patch p : a.patches) md.append("- ").append(inline(p.file())).append(":").append(p.line())
                .append(" — ").append(p.status()).append("; provider: ").append(inline(p.provider()))
                .append(" — ").append(inline(p.explanation())).append('\n');
        md.append("\n## Events\n\n");
        for (Analysis.Event e : a.events) md.append("- ").append(inline(e.label())).append(" [").append(e.status())
                .append("] ").append(inline(e.detail())).append(" (").append(e.durationMs()).append(" ms)\n");
        return md.toString();
    }

    String patch(String id) {
        return store.locked(id, entry -> {
            Analysis a = entry.analysis;
            StringBuilder diff = new StringBuilder();
            Map<String, List<Analysis.Patch>> byFile = new LinkedHashMap<>();
            for (Analysis.Patch p : a.patches) if (!p.status().equals("rejected"))
                byFile.computeIfAbsent(p.file(), key -> new ArrayList<>()).add(p);
            try {
                for (var group : byFile.entrySet()) {
                    String file = group.getKey();
                    Path path = SafeFiles.resolve(entry.directory.resolve("originals"), file);
                    String before = Files.readString(path), after = before;
                    for (Analysis.Patch p : group.getValue()) {
                        if (!StaticAnalyzer.unique(after, p.before())) throw new IllegalStateException("Patch source changed");
                        after = after.replace(p.before(), p.after());
                    }
                    appendDiff(diff, file, before, after);
                }
            } catch (IOException e) { throw AnalysisStore.storageFailure(); }
            catch (IllegalStateException e) { throw new ApiException(409, "patch_conflict", "Workspace no longer matches the exact patch source"); }
            return diff.toString();
        });
    }

    /** Full-file unified hunk preserves context and is accepted by git apply/patch. */
    private static void appendDiff(StringBuilder out, String file, String before, String after) {
        if (before.equals(after)) return;
        List<String> oldLines = lines(before), newLines = lines(after);
        String quoted = file.matches("[A-Za-z0-9_./-]+") ? "a/" + file : quote("a/" + file);
        String quotedNew = file.matches("[A-Za-z0-9_./-]+") ? "b/" + file : quote("b/" + file);
        out.append("diff --git ").append(quoted).append(' ').append(quotedNew).append("\n--- ").append(quoted)
                .append("\n+++ ").append(quotedNew).append("\n@@ -").append(oldLines.isEmpty() ? 0 : 1)
                .append(',').append(oldLines.size()).append(" +").append(newLines.isEmpty() ? 0 : 1).append(',')
                .append(newLines.size()).append(" @@\n");
        for (int i = 0; i < oldLines.size(); i++) {
            out.append('-').append(oldLines.get(i)).append('\n');
            if (i == oldLines.size() - 1 && !before.endsWith("\n")) out.append("\\ No newline at end of file\n");
        }
        for (int i = 0; i < newLines.size(); i++) {
            out.append('+').append(newLines.get(i)).append('\n');
            if (i == newLines.size() - 1 && !after.endsWith("\n")) out.append("\\ No newline at end of file\n");
        }
    }

    private static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    private static List<String> lines(String s) {
        if (s.isEmpty()) return List.of();
        String value = s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
        return Arrays.asList(value.split("\n", -1));
    }

    private static void tests(StringBuilder md, String title, Analysis.TestResult result) {
        md.append("## ").append(title).append("\n\nStatus: ").append(result.status())
                .append("; total: ").append(result.total()).append("; passed: ").append(result.passed())
                .append("; failed: ").append(result.failed()).append("; skipped: ").append(result.skipped())
                .append("; duration: ").append(result.durationMs()).append(" ms\n\nCommand: ").append(inline(result.command())).append("\n\n");
        fenced(md, "Output", result.output());
    }

    private static String inline(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", " ").replace("\r", " "); }
    private static void fenced(StringBuilder md, String label, String text) {
        String fence = "```";
        while (text.contains(fence)) fence += "`";
        md.append(label).append(":\n\n").append(fence).append("text\n").append(text)
                .append(text.endsWith("\n") ? "" : "\n").append(fence).append("\n\n");
    }
}
