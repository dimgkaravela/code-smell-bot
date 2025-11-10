package dev.dimitra.bot.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Matches GitHub /pulls/{number}/files JSON.
 * Using a record so SmellAnalyzer can call filename(), status(), patch(), etc.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChangedFile(
        String filename,
        String status,
        Integer additions,
        Integer deletions,
        Integer changes,
        String patch
) {}
