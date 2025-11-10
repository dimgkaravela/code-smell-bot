package dev.dimitra.bot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import dev.dimitra.bot.llm.LlmClient;
import dev.dimitra.bot.llm.LlmRouter;
import dev.dimitra.bot.llm.LlmFinding;


import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    // ---- ENTRY POINT ----
    public static void main(String[] args) throws Exception {
        String token = reqEnv("GITHUB_TOKEN");
        String repository = reqEnv("REPOSITORY");       // "owner/repo"
        int prNumber = intEnv("PR_NUMBER", -1);
        int maxFiles = intEnv("MAX_FILES", 50);
        boolean postComment = boolEnv("POST_COMMENT", true);
        if (prNumber <= 0) fail("PR_NUMBER must be > 0");

        String[] parts = repository.split("/");
        if (parts.length != 2) fail("REPOSITORY must be 'owner/repo'");
        String owner = parts[0], repo = parts[1];

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        ObjectMapper mapper = new ObjectMapper();

        // 1) Fetch changed files (1 σελίδα μέχρι maxFiles, απλό MVP)
        int perPage = Math.min(100, Math.max(1, maxFiles));
        String url = String.format(
                "https://api.github.com/repos/%s/%s/pulls/%d/files?per_page=%d",
                owner, repo, prNumber, perPage);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(40))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "token " + token) // μπορείς και "Bearer "
                .GET()
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            fail("GitHub API error: HTTP " + res.statusCode() + " -> " + res.body());
        }

        List<ChangedFile> files = mapper.readValue(res.body(), new TypeReference<List<ChangedFile>>() {});
        if (files.size() > maxFiles) {
            files = files.subList(0, maxFiles);
        }

        // 2) Compute simple metrics
        int totalFiles = files.size();
        int totalAdditions = files.stream().mapToInt(f -> safeInt(f.additions)).sum();
        int totalDeletions = files.stream().mapToInt(f -> safeInt(f.deletions)).sum();

        List<ChangedFile> javaFiles = files.stream()
                .filter(f -> f.filename != null && f.filename.endsWith(".java"))
                .collect(Collectors.toList());
        int javaFilesCount = javaFiles.size();
        int javaWithPatch = (int) javaFiles.stream().filter(f -> f.patch != null && !f.patch.isBlank()).count();

        // 3) Build tiny report
        Report report = new Report();
        report.repository = repository;
        report.prNumber = prNumber;
        report.totalFiles = totalFiles;
        report.totalAdditions = totalAdditions;
        report.totalDeletions = totalDeletions;
        report.javaFiles = javaFilesCount;
        report.javaFilesWithPatch = javaWithPatch;
        report.javaChangedFiles = javaFiles.stream()
                .map(f -> new JavaChanged(
                        nvl(f.filename, "?"), nvl(f.status, "?"),
                        safeInt(f.additions), safeInt(f.deletions),
                        safeInt(f.changes),
                        // για MVP κόβουμε το patch σε μικρό preview για καθαρό stdout
                        previewPatch(f.patch, 400)
                ))
                .toList();

        // 4) Γράψε artifacts ΠΡΙΝ το stdout (έτσι ικανοποιείται το workflow)
        Path outDir = Paths.get("out");
        Files.createDirectories(outDir);
        Path outJson = outDir.resolve("pr_diff.json");
        ObjectWriter pretty = mapper.writerWithDefaultPrettyPrinter();
        Files.writeString(outJson, pretty.writeValueAsString(report));

        // ensure out/files exists (ακόμη κι αν άδειο)
        Files.createDirectories(outDir.resolve("files"));

        // 5) Print JSON to stdout (χρήσιμο στα logs)
        System.out.println(pretty.writeValueAsString(report));
    }

     // 5) ----- LLM ANALYSIS -----
        // Build system + user content from the patches
        String systemPrompt = """
            You are a precise static-analysis assistant for Java and general code.
            Review ONLY the provided diff hunks and report code smells visible in the diff.
            Respond with strict JSON array (and nothing else) using this schema:
            [
              {"file":"<path>","line":<int>,"rule":"<name>","severity":"Blocker|Major|Minor","why":"<1-3 sentences>","suggestionPatch":"<optional suggestion block or replacement code>"}
            ]
            Rules:
            - "line": prefer the added/modified line number from the patch; if unknown, use 0.
            - Focus on issues justified by the diff; avoid project-wide speculation.
            - If a small, safe fix exists, include a GitHub-style suggestion block.
            - Keep explanations short and actionable.
        """;

    String diffText = renderDiffForModel(repository, prNumber, javaFiles);

        LlmClient llm = LlmRouter.fromEnv();
        LlmClient.Result llmResult = llm.chat(
                systemPrompt,
                List.of(new LlmClient.Message("user", diffText)),
                Map.of("temperature", 0.2, "max_tokens", 1400)
        );

        String raw = llmResult.text().trim();
        String json = stripBackticksIfAny(raw);

        List<LlmFinding> findings = new ArrayList<>();
        try {
            findings = mapper.readValue(json, new TypeReference<List<LlmFinding>>() {});
        } catch (Exception parseEx) {
            // If the model misbehaved, keep findings empty but still post a note
            findings = List.of();
        }

        // 6) Prepare markdown
        String md = renderMarkdown(findings);

        // 7) Post to PR (optional)
        if (postComment) {
            postIssueComment(http, token, owner, repo, prNumber, md);
        } else {
            System.out.println(md);
        }
    }


// ---- Helpers ----
    private static String renderDiffForModel(String repository, int prNumber, List<ChangedFile> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("Repository: ").append(repository).append("\n");
        sb.append("PR: ").append(prNumber).append("\n");
        sb.append("Analyze ONLY these patches:\n\n");
        for (var f : files) {
            if (f.patch == null || f.patch.isBlank()) continue;
            sb.append("=== FILE: ").append(f.filename).append(" (").append(nvl(f.status,"?")).append(") ===\n");
            sb.append(f.patch).append("\n\n");
        }
        sb.append("""
        Output JSON array only. Example:
        [
          {"file":"src/Foo.java","line":42,"rule":"Long Method","severity":"Major","why":"Method exceeds 50 lines","suggestionPatch":""},
          {"file":"src/Bar.java","line":0,"rule":"Dead Code","severity":"Minor","why":"Unused import","suggestionPatch":""}
        ]
        """);
        return sb.toString();
    }

    private static String renderMarkdown(List<LlmFinding> findings) {
        StringBuilder md = new StringBuilder();
        md.append("## 🤖 Code Smell Report (LLM)\n");
        if (findings == null || findings.isEmpty()) {
            md.append("No diff-scoped smells found in the analyzed Java files. ✅\n");
            return md.toString();
        }
        md.append("| File | Line | Rule | Severity | Why |\n");
        md.append("|---|---:|---|---|---|\n");
        for (var f : findings) {
            md.append("| ").append(nvl(f.file(), "?")).append(" | ")
              .append(f.line()).append(" | ")
              .append(escapeMd(nvl(f.rule(), ""))).append(" | ")
              .append(escapeMd(nvl(f.severity(), ""))).append(" | ")
              .append(escapeMd(nvl(f.why(), ""))).append(" |\n");
            if (f.suggestionPatch() != null && !f.suggestionPatch().isBlank()) {
                md.append("\n<details><summary>Suggested fix</summary>\n\n")
                  .append("```suggestion\n")
                  .append(f.suggestionPatch().trim())
                  .append("\n```\n</details>\n\n");
            }
        }
        return md.toString();
    }

    private static void postIssueComment(HttpClient http, String token, String owner, String repo, int prNumber, String body) throws Exception {
        String issuesUrl = String.format("https://api.github.com/repos/%s/%s/issues/%d/comments", owner, repo, prNumber);
        String payload = new ObjectMapper().writeValueAsString(Map.of("body", body));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(issuesUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "token " + token)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            System.err.println("[WARN] Failed to post PR comment: HTTP " + resp.statusCode() + " -> " + resp.body());
        }
    }

    private static String stripBackticksIfAny(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            // remove leading ``` or ```json and trailing ```
            t = t.replaceFirst("^```(?:json)?\\s*", "");
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3).trim();
            }
        }
        return t.trim();
    }

    private static boolean boolEnv(String key, boolean def) {
        String v = System.getenv(key);
        if (v == null) return def;
        return v.equalsIgnoreCase("true") || v.equalsIgnoreCase("1") || v.equalsIgnoreCase("yes");
    }

    // ---- Simple helpers (keep Main single-file & easy) ----
    private static String reqEnv(String key) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) fail("Missing ENV: " + key);
        return v;
    }

    private static int intEnv(String key, int def) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) return def;
        try { return Integer.parseInt(v.trim()); }
        catch (Exception e) { return def; }
    }

    private static void fail(String msg) {
        System.err.println("[ERROR] " + msg);
        System.exit(1);
    }

    private static int safeInt(Integer i) { return i == null ? 0 : i; }
    private static String nvl(String s, String d) { return (s == null || s.isBlank()) ? d : s; }

    private static String previewPatch(String patch, int maxChars) {
        if (patch == null) return null;
        return patch.length() <= maxChars ? patch : patch.substring(0, maxChars) + "\n... (truncated)";
    }

    //added comment to trigger bot

    // ---- Minimal DTOs (JSON mapping) ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ChangedFile {
        public String filename;
        public String status;
        public Integer additions;
        public Integer deletions;
        public Integer changes;
        public String patch; // μπορεί να είναι null (π.χ. binary)
    }

    static class JavaChanged {
        public String filename;
        public String status;
        public int additions;
        public int deletions;
        public int changes;
        public String patchPreview;

        public JavaChanged(String filename, String status, int additions, int deletions, int changes, String patchPreview) {
            this.filename = filename;
            this.status = status;
            this.additions = additions;
            this.deletions = deletions;
            this.changes = changes;
            this.patchPreview = patchPreview;
        }
    }

    static class Report {
        public String repository;
        public int prNumber;
        public int totalFiles;
        public int totalAdditions;
        public int totalDeletions;
        public int javaFiles;
        public int javaFilesWithPatch;
        public List<JavaChanged> javaChangedFiles;
    }
}
