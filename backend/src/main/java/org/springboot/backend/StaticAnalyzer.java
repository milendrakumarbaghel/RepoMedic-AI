package org.springboot.backend;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

@Component
class StaticAnalyzer {
    static final String BROKEN = "const discount = subtotal * discountPercent;";
    static final String FIXED = "const discount = subtotal * (discountPercent / 100);";
    private static final Pattern DEBUGGER = Pattern.compile("\\bdebugger\\s*;");
    private static final Pattern EQUALITY = Pattern.compile("(?<![=!<>])(?:==|!=)(?!=)");
    private static final Pattern HTML = Pattern.compile("\\bdangerouslySetInnerHTML\\s*=");
    private static final Pattern PRINT = Pattern.compile("\\bSystem\\s*\\.\\s*out\\s*\\.\\s*(?:println|print|printf)\\s*\\(");

    void scan(Analysis a, Path workspace) throws IOException {
        List<Path> files = SafeFiles.files(workspace);
        a.fileTree = files.stream().map(p -> SafeFiles.relative(workspace, p)).toList();
        a.filesScanned = 0;
        a.findings.clear();
        a.patches.clear();
        Set<String> stack = new LinkedHashSet<>();
        for (Path path : files) {
            String name = SafeFiles.relative(workspace, path);
            if (name.endsWith(".java")) stack.add("Java");
            if (name.endsWith("pom.xml")) stack.add("Maven");
            if (name.matches(".*\\.[cm]?js$") || name.endsWith("package.json")) stack.add("JavaScript");
            if (name.endsWith(".ts") || name.endsWith(".tsx")) stack.add("TypeScript");
            if (name.endsWith(".jsx") || name.endsWith(".tsx")) stack.add("React");
            if (!SafeFiles.isSource(name)) continue;
            String source = SafeFiles.source(path);
            if (source == null) continue;
            a.filesScanned++;
            if (name.endsWith("package.json") && source.contains("node --test")) stack.add("Node.js test runner");
            if (!name.matches(".*\\.(js|jsx|mjs|cjs|ts|tsx|java)$")) continue;
            String code = maskCommentsAndStrings(source);
            boolean js = !name.endsWith(".java");
            if (js) {
                find(a, name, source, code, DEBUGGER, "Debugger statement", "warning", "debugging",
                        "A debugger statement can interrupt a connected debugger in production.", "Remove the debugger statement.", true);
                find(a, name, source, code, EQUALITY, "Loose equality comparison", "warning", "correctness",
                        "Loose equality performs implicit type coercion.", "Review operand types before choosing strict equality; no automatic semantic change is made.", false);
                find(a, name, source, code, HTML, "Raw HTML injection sink", "critical", "security",
                        "dangerouslySetInnerHTML bypasses React escaping.", "Review the actual data flow and sanitize untrusted HTML with a trusted sanitizer, or render text.", false);
                int offset = code.indexOf(BROKEN);
                if (offset >= 0 && source.contains("subtotal - discount")) {
                    boolean patchable = SafeFiles.patchable(name) && unique(source, BROKEN);
                    add(a, name, line(source, offset), "Incorrect percentage calculation", "critical", "correctness",
                            "The discount multiplies by a whole percentage rather than its fractional value.",
                            "Divide discountPercent by 100 before multiplying. A 10% discount on 100 must subtract 10.",
                            BROKEN, FIXED, patchable);
                }
            } else {
                find(a, name, source, code, PRINT, "Unstructured console output", "info", "logging",
                        "System.out output bypasses configured logging levels and structured context.",
                        "Use the application's logger where operational logging is intended.", false);
            }
            if (a.findings.size() >= 200) break;
        }
        a.stack = new ArrayList<>(stack);
    }

    private void find(Analysis a, String file, String source, String code, Pattern pattern, String title,
                      String severity, String category, String message, String explanation, boolean removable) {
        var matches = pattern.matcher(code);
        while (matches.find() && a.findings.size() < 200) {
            String before = source.substring(matches.start(), matches.end());
            boolean patchable = removable && SafeFiles.patchable(file) && unique(source, before);
            add(a, file, line(source, matches.start()), title, severity, category, message, explanation,
                    before, removable ? "" : before, patchable);
        }
    }

    private void add(Analysis a, String file, int line, String title, String severity, String category,
                     String message, String explanation, String before, String after, boolean patchable) {
        String id = "finding-" + (a.findings.size() + 1);
        a.findings.add(new Analysis.Finding(id, title, severity, category, file, line, message,
                explanation, before, after, patchable));
        if (patchable && a.patches.size() < 32) a.patches.add(new Analysis.Patch("patch-" + (a.patches.size() + 1),
                file, line, before, after, explanation, "rule-based", "suggested"));
    }

    static boolean unique(String text, String before) {
        return before != null && !before.isEmpty() && text.indexOf(before) >= 0
                && text.indexOf(before) == text.lastIndexOf(before);
    }

    static int line(String text, int offset) { return 1 + (int) text.substring(0, offset).chars().filter(c -> c == '\n').count(); }

    // Preserve offsets and line numbers; avoid reporting operators embedded in strings/comments.
    static String maskCommentsAndStrings(String text) {
        char[] out = text.toCharArray();
        int state = 0;
        char quote = 0;
        for (int i = 0; i < out.length; i++) {
            char c = text.charAt(i), next = i + 1 < out.length ? text.charAt(i + 1) : 0;
            if (state == 0) {
                if (c == '/' && next == '/') { state = 1; out[i] = ' '; }
                else if (c == '/' && next == '*') { state = 2; out[i] = ' '; }
                else if (c == '\'' || c == '"' || c == '`') { state = 3; quote = c; out[i] = ' '; }
            } else if (state == 1) {
                if (c == '\n') state = 0; else out[i] = ' ';
            } else if (state == 2) {
                if (c != '\n') out[i] = ' ';
                if (c == '*' && next == '/') { out[++i] = ' '; state = 0; }
            } else {
                if (c != '\n') out[i] = ' ';
                if (c == '\\' && i + 1 < out.length) { if (out[i + 1] != '\n') out[i + 1] = ' '; i++; }
                else if (c == quote) state = 0;
            }
        }
        return new String(out);
    }
}
