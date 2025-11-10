package dev.dimitra.bot.llm;

public record LlmFinding(
        String file,          // e.g., "src/main/java/.../Foo.java"
        int line,             // 1-based line number if known; 0 if not
        String rule,          // e.g., "Long Method"
        String severity,      // "Blocker" | "Major" | "Minor"
        String why,           // 1–3 sentence explanation
        String suggestionPatch // optional GitHub suggestion fenced block or git patch
) {}
