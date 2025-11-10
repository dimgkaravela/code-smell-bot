// ... imports
import dev.dimitra.bot.llm.*;
import dev.dimitra.bot.analysis.SmellAnalyzer;
import dev.dimitra.bot.model.ChangedFile;

public class Main {
    public static void main(String[] args) throws Exception {
        var token = System.getenv("GITHUB_TOKEN");
        var repo = System.getenv("REPOSITORY");              // "owner/name"
        var prNum = Integer.parseInt(System.getenv("PR_NUMBER"));

        var postComment = Boolean.parseBoolean(System.getenv().getOrDefault("POST_COMMENT","true"));
        int maxFiles = Integer.parseInt(System.getenv().getOrDefault("MAX_FILES","10"));

        // 1) fetch diff (you already have this)
        var gh = new GitHubClient(token, repo);
        var files = gh.listChangedFiles(prNum, maxFiles, /*fetchContents*/ true); // make sure patch is included

        // 2) set up LLM
        var llm = LlmRouter.fromEnv();
        var analyzer = new SmellAnalyzer(llm,
                Integer.parseInt(System.getenv().getOrDefault("LLM_MAX_FILES_PER_CHUNK","5")),
                Integer.parseInt(System.getenv().getOrDefault("LLM_MAX_PATCH_CHARS","12000"))
        );

        var findings = analyzer.analyze(repo, prNum, files);

        // 3) render a nice markdown summary
        StringBuilder md = new StringBuilder();
        md.append("## 🤖 Code Smell Report (LLM)\n");
        if (findings.isEmpty()) {
            md.append("No diff-scoped smells found in the analyzed files. ✅\n");
        } else {
            md.append("| File | Line | Rule | Severity | Why |\n");
            md.append("|---|---:|---|---|---|\n");
            for (var f : findings) {
                md.append("| ").append(f.file()).append(" | ")
                        .append(f.line()).append(" | ")
                        .append(escapeMd(f.rule())).append(" | ")
                        .append(f.severity()).append(" | ")
                        .append(escapeMd(f.why())).append(" |\n");
                if (f.suggestionPatch() != null && !f.suggestionPatch().isBlank()) {
                    md.append("\n<details><summary>Suggested fix</summary>\n\n")
                      .append("```suggestion\n")
                      .append(f.suggestionPatch().trim())
                      .append("\n```\n</details>\n\n");
                }
            }
        }

        // 4) post to PR as a comment
        if (postComment) {
            gh.postIssueComment(prNum, md.toString());
        } else {
            System.out.println(md);
        }
    }

    private static String escapeMd(String s) {
        return s == null ? "" : s.replace("|", "\\|");
    }
}
