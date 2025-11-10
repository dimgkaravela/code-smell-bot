package dev.dimitra.bot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;


//added this comment to trigger the bot
public class Main {
    // ---- ENTRY POINT ----
    public static void main(String[] args) throws Exception {
        String token = reqEnv("GITHUB_TOKEN");
        String repository = reqEnv("REPOSITORY");       // "owner/repo"
        int prNumber = intEnv("PR_NUMBER", -1);
        int maxFiles = intEnv("MAX_FILES", 50);
        if (prNumber <= 0) fail("PR_NUMBER must be > 0");

        String[] parts = repository.split("/");
        if (parts.length != 2) fail("REPOSITORY must be 'owner/repo'");
        String owner = parts[0], repo = parts[1];

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        ObjectMapper mapper = new ObjectMapper();

        // 1) Fetch changed files (1 σελίδα μέχρι maxFiles, απλό MVP)
        int perPage = Math.min(100, Math.max(1, maxFiles));
        String url = String.format(
                "https://api.github.com/repos/%s/%s/pulls/%d/files?per_page=%d",
                owner, repo, prNumber, perPage);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "token " + token)
                .GET()
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) {
            fail("GitHub API error: HTTP " + res.statusCode() + " -> " + res.body());
        }

        List<ChangedFile> files = mapper.readValue(res.body(), new TypeReference<>() {});
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
                        f.filename, nvl(f.status, "?"),
                        safeInt(f.additions), safeInt(f.deletions),
                        safeInt(f.changes),
                        // για MVP κόβουμε το patch σε μικρό preview για καθαρό stdout
                        previewPatch(f.patch, 400)
                ))
                .collect(Collectors.toList());

        // 4) Print JSON to stdout
        System.out.println(mapper.writeValueAsString(report));
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

    //added this comment ot trigger the bot

    // ensure output dir & write pr_diff.json
java.nio.file.Path outDir = java.nio.file.Paths.get("out");
java.nio.file.Files.createDirectories(outDir);
java.nio.file.Path outJson = outDir.resolve("pr_diff.json");

// pretty JSON
com.fasterxml.jackson.databind.ObjectMapper pretty = new com.fasterxml.jackson.databind.ObjectMapper();
pretty.writerWithDefaultPrettyPrinter().writeValue(outJson.toFile(), report);

// ensure out/files exists even if we didn't fetch contents in this simplified version
java.nio.file.Files.createDirectories(outDir.resolve("out/files".replace("out/","")));

// also keep printing to stdout (helpful in logs)
System.out.println(pretty.writeValueAsString(report));

}
