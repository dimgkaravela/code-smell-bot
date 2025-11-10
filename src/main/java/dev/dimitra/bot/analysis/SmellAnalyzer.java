package dev.dimitra.bot.analysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dimitra.bot.llm.*;
import dev.dimitra.bot.model.ChangedFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class SmellAnalyzer {
    private final LlmClient llm;
    private final ObjectMapper mapper = new ObjectMapper();
    private final int maxFilesPerChunk;
    private final int maxPatchChars;

    public SmellAnalyzer(LlmClient llm, int maxFilesPerChunk, int maxPatchChars) {
        this.llm = llm;
        this.maxFilesPerChunk = maxFilesPerChunk <= 0 ? 5 : maxFilesPerChunk;
        this.maxPatchChars = maxPatchChars <= 0 ? 12000 : maxPatchChars;
    }

    public List<LlmFinding> analyze(String repository, int prNumber, List<ChangedFile> files) throws IOException, InterruptedException {
        List<LlmFinding> all = new ArrayList<>();
        List<List<ChangedFile>> chunks = chunkFiles(files);

        String system = """
            You are a precise static-analysis assistant for Java (and general code). 
            Task: review only the diff hunks and report code smells that are *visible in the diff*. 
            For each finding, return strict JSON (UTF-8) in the schema:
            [{"file":"<path>","line":<int>,"rule":"<name>","severity":"Blocker|Major|Minor","why":"<1-3 sentences>","suggestionPatch":"<optional GitHub suggestion or patch>"}]
            - "line": prefer an added/modified line number from the patch; if unknown, use 0.
            - Only include issues justified by the shown diff; avoid speculative project-wide claims.
            - If fix is clear and small, include a GitHub *suggestion* block.
            - Keep explanations concise and actionable.
            Respond with JSON only—no prose.
        """;

        for (int i = 0; i < chunks.size(); i++) {
            var c = chunks.get(i);
            String diffText = renderChunk(repository, prNumber, c);
            var messages = List.of(
                    new LlmClient.Message("user", diffText)
            );
            var res = llm.chat(system, messages, Map.of("temperature", 0.2, "max_tokens", 1200));

            // try strict JSON parse; if model wrapped in backticks, strip them
            String t = res.text().trim();
            if (t.startsWith("```")) t = t.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
            List<LlmFinding> findings;
            try {
                findings = mapper.readValue(t, new TypeReference<List<LlmFinding>>() {});
            } catch (Exception e) {
                // fallback: return empty for this chunk
                findings = List.of();
            }
            all.addAll(findings);
        }
        return mergeDuplicates(all);
    }

    private List<LlmFinding> mergeDuplicates(List<LlmFinding> list) {
        // de-dup simple (file,line,rule)
        Map<String, LlmFinding> map = new LinkedHashMap<>();
        for (var f : list) {
            String key = f.file()+"#"+f.line()+"#"+f.rule();
            map.putIfAbsent(key, f);
        }
        return new ArrayList<>(map.values());
    }

    private List<List<ChangedFile>> chunkFiles(List<ChangedFile> files) {
        List<ChangedFile> javaOnly = files.stream()
                .filter(f -> f.filename().endsWith(".java"))
                .collect(Collectors.toList());
        List<List<ChangedFile>> chunks = new ArrayList<>();
        List<ChangedFile> current = new ArrayList<>();
        int size = 0;
        for (ChangedFile f : javaOnly) {
            int patchLen = (f.patch() == null) ? 0 : f.patch().length();
            if (!current.isEmpty() && (current.size() >= maxFilesPerChunk || size + patchLen > maxPatchChars)) {
                chunks.add(current);
                current = new ArrayList<>();
                size = 0;
            }
            current.add(f);
            size += patchLen;
        }
        if (!current.isEmpty()) chunks.add(current);
        return chunks;
    }

    private String renderChunk(String repository, int prNumber, List<ChangedFile> c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Repository: ").append(repository).append("\n");
        sb.append("PR: ").append(prNumber).append("\n");
        sb.append("Analyze ONLY these patches:\n\n");
        for (var f : c) {
            sb.append("=== FILE: ").append(f.filename()).append(" (").append(f.status()).append(") ===\n");
            if (f.patch() != null && !f.patch().isBlank()) {
                sb.append(f.patch()).append("\n\n");
            } else {
                sb.append("(no patch available)\n\n");
            }
        }
        sb.append("""
        Output JSON array only. Example:
        [
          {"file":"src/Foo.java","line":42,"rule":"Long Method","severity":"Major","why":"Method exceeds 50 lines","suggestionPatch":"(optional)"},
          {"file":"src/Bar.java","line":0,"rule":"Dead Code","severity":"Minor","why":"Unused import in diff","suggestionPatch":""}
        ]
        """);
        return sb.toString();
    }
}
