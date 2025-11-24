package dev.dimitra.bot.llm;

public class LlmRouter {

    public static LlmClient fromEnv() {
        // Default to gemini for now
        String provider = env("LLM_PROVIDER", "gemini").trim().toLowerCase();

        switch (provider) {
            case "gemini":
            default: {
                // Allow overriding the model with GEMINI_MODEL, otherwise use a good default
                String model = env("GEMINI_MODEL", "gemini-2.5-flash-lite");
                return new GeminiClient(
                        env("GEMINI_API_KEY", null),  // required
                        model
                );
            }

            /*

            case "openai": {
                String model = env("OPENAI_MODEL", "gpt-4o-mini");
                return new OpenAIClient(
                        env("OPENAI_API_KEY", null),              // required
                        model,
                        env("OPENAI_BASE_URL", "https://api.openai.com/v1")
                );
            }
            */
        }
    }

    /**
     * Read environment variable k, with default def.
     * If def == null and the variable is missing/blank, throw an error.
     */
    private static String env(String k, String def) {
        String v = System.getenv(k);
        if (v == null || v.isBlank()) {
            if (def == null) {
                throw new IllegalArgumentException("Missing env: " + k);
            }
            return def;
        }
        return v;
    }
}
