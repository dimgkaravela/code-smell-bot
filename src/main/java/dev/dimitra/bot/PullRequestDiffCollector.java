package dev.dimitra.bot;

import dev.dimitra.bot.model.ChangedFile;
import dev.dimitra.bot.model.PRDiffSummary;

import java.util.List;

public class PullRequestDiffCollector {

    public PRDiffSummary buildSummary(String repository, int prNumber, List<ChangedFile> allFiles) {
        PRDiffSummary sum = new PRDiffSummary();
        sum.repository = repository;
        sum.prNumber = prNumber;
        sum.totalFiles = allFiles.size();

        List<ChangedFile> javaFiles = allFiles.stream()
                .filter(ChangedFile::isJavaFile)
                .toList();

        sum.javaFiles = javaFiles.size();
        sum.javaFilesWithPatch = (int) javaFiles.stream().filter(ChangedFile::isTextual).count();
        sum.totalAdditions = javaFiles.stream().mapToInt(f -> f.additions).sum();
        sum.totalDeletions = javaFiles.stream().mapToInt(f -> f.deletions).sum();
        sum.javaChangedFiles = javaFiles;
        return sum;
    }
}
