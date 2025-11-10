package dev.dimitra.bot.llm;

public class LlmRouter {
    public static LlmClient fromEnv() {
        String provider = env("LLM_PROVIDER", "openai").trim().toLowerCase();
        String model = env("LLM_MODEL", "gpt-4o-mini");
        switch (provider) {
            case "claude":
                return new ClaudeClient(
                        env("CLAUDE_API_KEY", null),
                        model,
                        env("CLAUDE_BASE_URL", null),
                        env("CLAUDE_API_VERSION", null)
                );
            case "deepseek":
                return new DeepSeekClient(
                        env("DEEPSEEK_API_KEY", null),
                        model,
                        env("DEEPSEEK_BASE_URL", null)
                );
            default:
                return new OpenAIClient(
                        env("OPENAI_API_KEY", null),
                        model,
                        env("OPENAI_BASE_URL", null)
                );
        }
    }

    private static String env(String k, String def) {
        String v = System.getenv(k);
        if (v == null || v.isBlank()) {
            if (def == null) throw new IllegalArgumentException("Missing env: " + k);
            return def;
        }
        return v;
    }
}
