package dev.dimitra.bot.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/**
 * ClaudeClient – connects to Anthropic’s Claude models via the /v1/messages API.
 * Works for claude-3-opus, claude-3-sonnet, claude-3-5-sonnet, etc.
 */
public class ClaudeClient implements LlmClient {
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final String apiVersion;

    public ClaudeClient(String apiKey, String model, String baseUrl, String apiVersion) {
        this.apiKey = Objects.requireNonNull(apiKey, "CLAUDE_API_KEY missing");
        this.model = Objects.requireNonNull(model, "LLM_MODEL missing");
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://api.anthropic.com" : baseUrl;
        this.apiVersion = (apiVersion == null || apiVersion.isBlank()) ? "2023-06-01" : apiVersion;
    }

    @Override
    public Result chat(String systemPrompt, List<Message> messages, Map<String, Object> options)
            throws IOException, InterruptedException {

        // Claude uses a messages array: role=user|assistant, with "content":[{type:"text",text:"..."}]
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);

        var content = mapper.createArrayNode();
        StringBuilder userText = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            userText.append("[SYSTEM]\n").append(systemPrompt).append("\n\n");
        }
        for (Message m : messages) {
            userText.append("[").append(m.role().toUpperCase()).append("]\n").append(m.content()).append("\n\n");
        }

        content.add(mapper.createObjectNode()
                .put("type", "text")
                .put("text", userText.toString()));

        var msgArray = mapper.createArrayNode();
        msgArray.add(mapper.createObjectNode()
                .put("role", "user")
                .set("content", content));

        body.set("messages", msgArray);
        if (options != null) {
            if (options.get("temperature") != null)
                body.put("temperature", ((Number) options.get("temperature")).doubleValue());
            if (options.get("max_tokens") != null)
                body.put("max_tokens", ((Number) options.get("max_tokens")).intValue());
        } else {
            body.put("max_tokens", 2000);
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/messages"))
                .header("x-api-key", apiKey)
                .header("anthropic-version", apiVersion)
                .header("content-type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2)
            throw new IOException("Claude API error " + resp.statusCode() + ": " + resp.body());

        var root = mapper.readTree(resp.body());
        String text = root.path("content").path(0).path("text").asText("");
        int inputT = root.path("usage").path("input_tokens").asInt(0);
        int outputT = root.path("usage").path("output_tokens").asInt(0);

        return new Result(text, new Usage(inputT, outputT));
    }
}
