package dev.dimitra.bot.model;

import java.util.List;

public class PRDiffSummary {
    public String repository;       // "owner/repo"
    public int prNumber;
    public int totalFiles;
    public int javaFiles;
    public int javaFilesWithPatch;
    public int totalAdditions;
    public int totalDeletions;
    public List<ChangedFile> javaChangedFiles;
}
