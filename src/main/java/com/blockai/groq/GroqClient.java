package com.blockai.groq;

import com.blockai.config.APIKeyManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.blockai.ai.planner.Roadmap;

public class GroqClient {
    private static final HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Gson gson = new Gson();
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    public static final String DEFAULT_GROQ_MODEL = "openai/gpt-oss-20b";

    private static int apiCalls = 0;

    public static int getApiCallCount() {
        return apiCalls;
    }

    public static void sendMessageAsync(String message, Consumer<Roadmap> successCallback, Consumer<String> errorCallback) {
        if (!APIKeyManager.hasKey()) {
            errorCallback.accept("Error: API Key not registered.");
            return;
        }

        apiCalls++;

        JsonObject payload = new JsonObject();
        payload.addProperty("model", DEFAULT_GROQ_MODEL);
        
        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        payload.add("response_format", responseFormat);
        
        JsonArray messages = new JsonArray();
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", "You are BlockAI, an autonomous Minecraft survival agent. You must respond ONLY with a valid JSON object containing a complete roadmap to achieve the user's goal. Format:\n{\"goal\": \"<goal>\",\"summary\": \"<summary>\",\"tasks\": [{\"id\": 1, \"type\": \"observe|clear_area|resource_gather|craft|build|combat|verify|excavate\", \"description\": \"<desc>\", \"radius\": 8, \"allowsExcavation\": false, \"protectGround\": true, \"resourceCategory\": \"WOOD_LOG\", \"specificItem\": null, \"quantity\": 64}]}\n\nIMPORTANT RULES:\n- DO NOT generate 'clear_area' tasks when the primary goal is resource gathering (e.g., gathering wood, stone). The 'resource_gather' task handles access internally.\n- For 'clear_area' tasks: set allowsExcavation=false and protectGround=true. ONLY remove surface obstacles (trees, grass).\n- For 'excavate' tasks: set allowsExcavation=true.\n- For 'resource_gather' tasks: set resourceCategory (e.g. WOOD_LOG, STONE, FOOD, IRON_ORE). Set quantity. Set specificItem ONLY if the user asks for an exact item (e.g. \"minecraft:oak_log\"). Otherwise leave specificItem as null so the AI can choose the best alternative.\n- 'clear_area' and 'excavate' are DIFFERENT task types.");
        messages.add(systemMessage);
        
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", message);
        messages.add(userMessage);
        
        payload.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + APIKeyManager.getKey())
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        try {
                            JsonObject jsonResponse = JsonParser.parseString(response.body()).getAsJsonObject();
                            String reply = jsonResponse.getAsJsonArray("choices")
                                    .get(0).getAsJsonObject()
                                    .getAsJsonObject("message")
                                    .get("content").getAsString();
                            
                            Roadmap roadmap = gson.fromJson(reply, Roadmap.class);
                            successCallback.accept(roadmap);
                        } catch (Exception e) {
                            errorCallback.accept("Error parsing response: " + e.getMessage());
                        }
                    } else {
                        com.blockai.BlockAI.LOGGER.error("Groq HTTP {}: {}", response.statusCode(), response.body());
                        System.out.println("[BlockAI] Groq HTTP " + response.statusCode());
                        System.out.println("[BlockAI] Response:\n" + response.body());
                        
                        String errorMessage = "API request failed with status: " + response.statusCode();
                        try {
                            JsonObject errorJson = JsonParser.parseString(response.body()).getAsJsonObject();
                            if (errorJson.has("error")) {
                                JsonObject errObj = errorJson.getAsJsonObject("error");
                                if (errObj.has("message")) {
                                    errorMessage = errObj.get("message").getAsString();
                                }
                            }
                        } catch (Exception e) {
                            // Ignored
                        }
                        
                        errorCallback.accept("API Error (" + response.statusCode() + "): " + errorMessage);
                    }
                })
                .exceptionally(ex -> {
                    errorCallback.accept("Unable to reach Groq API: " + ex.getMessage());
                    return null;
                });
    }
}
