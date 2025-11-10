package dev.dimitra.bot.llm;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface LlmClient {
    record Message(String role, String content) {}
    record Usage(int inputTokens, int outputTokens) {}
    record Result(String text, Usage usage) {}

    Result chat(String systemPrompt, List<Message> messages, Map<String, Object> options) throws IOException, InterruptedException;
}
