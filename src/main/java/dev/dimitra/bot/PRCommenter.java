package dev.dimitra.bot;

import dev.dimitra.bot.model.ChangedFile;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class PRCommenter {

    public String buildSummaryMarkdown(String repository, int prNumber, List<ChangedFile> javaFiles, int maxList) {
        int javaFilesWithPatch = (int) javaFiles.stream().filter(ChangedFile::isTextual).count();
        int totalAdditions = javaFiles.stream().mapToInt(f -> f.additions).sum();
        int totalDeletions = javaFiles.stream().mapToInt(f -> f.deletions).sum();

        String header = String.format("**PR Diff Summary for `%s` / #%d**\n\n", repository, prNumber);
        String stats  = String.format("- Java files: **%d**  | with patch: **%d**  | `+%d / -%d`\n",
                javaFiles.size(), javaFilesWithPatch, totalAdditions, totalDeletions);

        String list = javaFiles.stream()
                .sorted(Comparator.comparingInt((ChangedFile f) -> f.additions + f.deletions).reversed())
                .limit(maxList)
                .map(f -> String.format("  - `%s` (%s, +%d/-%d)", f.filename, f.status, f.additions, f.deletions))
                .collect(Collectors.joining("\n"));

        if (!list.isEmpty()) {
            list = "\n**Top changed Java files:**\n" + list + "\n";
        }

        return header + stats + list + "\n> _Posted by the Day-3 diff collector._";
    }
}
