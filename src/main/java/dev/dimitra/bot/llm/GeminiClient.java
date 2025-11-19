package dev.dimitra.bot.llm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * LLM client for Google's Gemini API.
 */
public class GeminiClient implements LlmClient {

    // Gemini REST endpoint (v1beta)
    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiClient(String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Missing GEMINI_API_KEY");
        }
        // Recommended cheap+good default (Gemini 2.5 family)
        // 1.0 / 1.5 models are retired now. 
        if (model == null || model.isBlank()) {
            this.model = "gemini-2.5-flash-lite";
        } else {
            this.model = model;
        }

        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String chat(String prompt) throws Exception {
        // Body must follow Gemini "generateContent" format 
        String bodyJson = """
        {
          "contents": [
            {
              "parts": [
                { "text": %s }
              ]
            }
          ]
        }
        """.formatted(objectMapper.writeValueAsString(prompt));

        String url = BASE_URL + model + ":generateContent?key=" + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini error " + response.statusCode() +
                    ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new RuntimeException("Gemini returned no candidates: " + response.body());
        }

        JsonNode textNode = candidates.get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text");

        if (textNode.isMissingNode() || textNode.isNull()) {
            throw new RuntimeException("Gemini response missing text: " + response.body());
        }

        return textNode.asText();
    }
}
