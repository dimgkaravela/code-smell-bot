package dev.dimitra.bot.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public class DeepSeekClient implements LlmClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public DeepSeekClient(String apiKey, String model, String baseUrl) {
        this.apiKey = Objects.requireNonNull(apiKey, "DEEPSEEK_API_KEY missing");
        this.model = Objects.requireNonNull(model, "LLM_MODEL missing");
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://api.deepseek.com" : baseUrl;
    }

    @Override
    public Result chat(String systemPrompt, List<Message> messages, Map<String, Object> options) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);

        var msgs = mapper.createArrayNode();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            var sys = mapper.createObjectNode();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            msgs.add(sys);
        }
        for (Message m : messages) {
            var n = mapper.createObjectNode();
            n.put("role", m.role());
            n.put("content", m.content());
            msgs.add(n);
        }
        body.set("messages", msgs);
        if (options != null) {
            if (options.get("temperature") != null) body.put("temperature", ((Number) options.get("temperature")).doubleValue());
            if (options.get("max_tokens") != null) body.put("max_tokens", ((Number) options.get("max_tokens")).intValue());
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) throw new IOException("DeepSeek error " + resp.statusCode() + ": " + resp.body());

        var root = mapper.readTree(resp.body());
        String text = root.path("choices").path(0).path("message").path("content").asText("");
        // usage may not always be present
        int promptT = root.path("usage").path("prompt_tokens").asInt(0);
        int completionT = root.path("usage").path("completion_tokens").asInt(0);
        return new Result(text, new Usage(promptT, completionT));
    }
}
