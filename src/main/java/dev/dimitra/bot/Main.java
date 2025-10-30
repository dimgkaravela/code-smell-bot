package dev.dimitra.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dimitra.bot.model.ChangedFile;
import dev.dimitra.bot.model.PRDiffSummary;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        String token = env("GITHUB_TOKEN");
        String repo = env("REPOSITORY"); // "owner/repo"
        String prNumStr = env("PR_NUMBER");
        int prNumber = Integer.parseInt(prNumStr);

        boolean postComment   = boolEnv("POST_COMMENT", true);
        boolean fetchContents = boolEnv("FETCH_CONTENTS", true);
        int maxFilesList      = intEnv("MAX_FILES", 10);

        GitHubClient gh = new GitHubClient(token, repo);
        List<ChangedFile> files = gh.listPullRequestFiles(prNumber);

        PRDiffSummary summary = new PullRequestDiffCollector().buildSummary(repo, prNumber, files);

        // ensure output dir
        File outDir = new File("out");
        if (!outDir.exists()) Files.createDirectories(outDir.toPath());
        File out = new File(outDir, "pr_diff.json");

        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(out, summary);

        // Log a simple summary for humans
        System.out.printf("PR #%d in %s: %d files total | %d Java files | %d Java files with patch | +%d -%d%n",
                summary.prNumber, summary.repository, summary.totalFiles, summary.javaFiles,
                summary.javaFilesWithPatch, summary.totalAdditions, summary.totalDeletions);

        // Day 3: fetch file contents
        if (fetchContents) {
            FileContentFetcher fetcher = new FileContentFetcher(gh);
            int saved = fetcher.fetchAndSaveJavaFiles(summary.javaChangedFiles);
            System.out.println("Saved head contents for Java files: " + saved);
        }

        // Day 3: comment on the PR
        if (postComment) {
            PRCommenter commenter = new PRCommenter();
            String md = commenter.buildSummaryMarkdown(repo, prNumber, summary.javaChangedFiles, maxFilesList);
            gh.postIssueComment(prNumber, md);
            System.out.println("Posted PR summary comment.");
        }

        System.out.println("Wrote: " + out.getPath());
    }

    private static String env(String key) throws IOException {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            throw new IOException("Missing required env var: " + key);
        }
        return v;
    }

     private static boolean boolEnv(String key, boolean def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v.equalsIgnoreCase("true");
    }
    private static int intEnv(String key, int def) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) return def;
        try { return Integer.parseInt(v); } catch (Exception e) { return def; }
    }



    //added a comment to trigger the test
}
