package dev.dimitra.bot.model;

public class ChangedFile {
    public String filename;
    public String status; // added | modified | removed | renamed
    public int additions;
    public int deletions;
    public int changes;
    public String previousFilename; // may be null
    public String patch; // may be null for binary / large files

    // convenience
    public boolean isJavaFile() {
        return filename != null && filename.endsWith(".java");
    }

    public boolean isTextual() {
        return patch != null && !patch.isBlank();
    }
}
