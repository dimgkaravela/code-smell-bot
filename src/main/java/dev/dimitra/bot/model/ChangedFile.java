package dev.dimitra.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)   // <— key line
public class ChangedFile {
    public String filename;
    public String status; // added | modified | removed | renamed
    public int additions;
    public int deletions;
    public int changes;

    @JsonProperty("previous_filename")        // <— map snake_case → camelCase
    public String previousFilename;

     @JsonProperty("raw_url")
    public String rawUrl;

    @JsonProperty("contents_url")
    public String contentsUrl;

    public String patch; // may be null for binary / large files

    // convenience
    public boolean isJavaFile() {
        return filename != null && filename.endsWith(".java");
    }

    public boolean isTextual() {
        return patch != null && !patch.isBlank();
    }
}
