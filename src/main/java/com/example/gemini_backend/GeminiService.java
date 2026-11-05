package com.example.gemini_backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    // Main model first, backup after. Change these to models that exist for your key.
    @Value("${gemini.models:gemini-2.5-flash,gemini-2.5-flash-lite}")
    private String[] models;

    private static final String FALLBACK_MESSAGE =
            "The planner is very busy right now. Please try again in a few seconds.";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public String askGemini(String prompt) {
        String jsonBody = buildBody(prompt);

        for (String model : models) {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + model.trim() + ":generateContent";

            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Content-Type", "application/json")
                            .header("x-goog-api-key", apiKey)
                            .timeout(Duration.ofSeconds(30))
                            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                            .build();

                    HttpResponse<String> response =
                            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    int status = response.statusCode();

                    if (status == 200) {
                        return extractText(response.body());
                    }

                    boolean retryable = status == 503 || status == 429
                            || status == 500 || status == 504;
                    if (!retryable) {
                        // e.g. 400/403/404: wrong key, wrong model name, bad request
                        System.err.println("Gemini error " + status + " on " + model
                                + ": " + response.body());
                        break; // skip to the backup model
                    }

                    System.err.println("Gemini " + status + " on " + model
                            + ", attempt " + (attempt + 1));
                    if (attempt < 2) {
                        Thread.sleep(1000L << attempt); // waits 1s, then 2s
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return FALLBACK_MESSAGE;
                } catch (Exception e) {
                    System.err.println("Request failed on " + model + ": " + e.getMessage());
                }
            }
        }
        return FALLBACK_MESSAGE;
    }

    private String buildBody(String prompt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("contents")
            .addObject()
            .putArray("parts")
            .addObject()
            .put("text", prompt);
        return root.toString();
    }

    private String extractText(String body) throws Exception {
        JsonNode rootNode = objectMapper.readTree(body);
        return rootNode.path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("text").asText("No text found in response.");
    }
}