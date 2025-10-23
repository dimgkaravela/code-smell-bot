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

        // Optional: emit a short listing
        summary.javaChangedFiles.stream().limit(20).forEach(f ->
                System.out.printf(" - [%s] %s (Δ+%d/-%d)%n", f.status, f.filename, f.additions, f.deletions)
        );

        System.out.println("Wrote: " + out.getPath());
    }

    private static String env(String key) throws IOException {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            throw new IOException("Missing required env var: " + key);
        }
        return v;
    }
}
