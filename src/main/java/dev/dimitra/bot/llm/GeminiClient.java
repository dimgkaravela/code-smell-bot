package dev.dimitra.bot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.dimitra.bot.llm.LlmClient.Message;
import dev.dimitra.bot.llm.LlmClient.Result;
import dev.dimitra.bot.llm.LlmClient.Usage;


import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * LLM client for Google's Gemini API using the generateContent endpoint.
 *
 * See: https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
 */
public class GeminiClient implements LlmClient {

    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String apiKey;
    private final String model;

    public GeminiClient(String apiKey, String model) {
        this.apiKey = Objects.requireNonNull(apiKey, "GEMINI_API_KEY missing");
        // Good default: fast & cheap text model
        this.model = (model == null || model.isBlank())
                ? "gemini-2.5-flash-lite"
                : model;
    }

    @Override
    public Result chat(String systemPrompt,
                       List<Message> messages,
                       Map<String, Object> options) throws IOException, InterruptedException {

        ObjectNode body = mapper.createObjectNode();

        // System prompt -> Gemini system_instruction
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode sys = mapper.createObjectNode();
            ArrayNode sysParts = mapper.createArrayNode();
            ObjectNode part = mapper.createObjectNode();
            part.put("text", systemPrompt);
            sysParts.add(part);
            sys.set("parts", sysParts);
            body.set("system_instruction", sys);
        }

        // Messages -> contents[]
        ArrayNode contents = mapper.createArrayNode();
        for (Message m : messages) {
            ObjectNode content = mapper.createObjectNode();

            // Gemini uses "user" / "model" roles
            String role = m.role();
            String gemRole;
            if ("assistant".equalsIgnoreCase(role)) {
                gemRole = "model";
            } else {
                // treat user/system/other as user-side content
                gemRole = "user";
            }
            content.put("role", gemRole);

            ArrayNode parts = mapper.createArrayNode();
            ObjectNode part = mapper.createObjectNode();
            part.put("text", m.content());
            parts.add(part);
            content.set("parts", parts);

            contents.add(content);
        }
        body.set("contents", contents);

        // Map options to Gemini-compatible fields
        if (options != null) {
            if (options.get("temperature") != null) {
                double temp = ((Number) options.get("temperature")).doubleValue();
                body.put("temperature", temp);
            }
            if (options.get("max_tokens") != null) {
                int max = ((Number) options.get("max_tokens")).intValue();
                // Gemini's field is maxOutputTokens
                body.put("maxOutputTokens", max);
            }
        }

        String url = BASE_URL + "/" + model + ":generateContent?key=" + apiKey;

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(120))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Gemini error " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode root = mapper.readTree(resp.body());

        // Extract text: candidates[0].content.parts[0].text
        JsonNode candidates = root.path("candidates");
        String text = "";
        if (candidates.isArray() && candidates.size() > 0) {
            JsonNode content = candidates.get(0).path("content");
            JsonNode parts = content.path("parts");
            if (parts.isArray() && parts.size() > 0) {
                text = parts.get(0).path("text").asText("");
            }
        }

        // Usage metadata (if present)
        JsonNode usageNode = root.path("usageMetadata");
        int promptTokens = usageNode.path("promptTokenCount").asInt(0);
        int completionTokens = usageNode.path("candidatesTokenCount").asInt(0);

        return new Result(text, new Usage(promptTokens, completionTokens));
    }
}
