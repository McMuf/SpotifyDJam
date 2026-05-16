package com.spotifydjams.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class BackendClient {
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    public static CompletableFuture<JsonObject> get(String endpoint) {
        String url = BackendConfig.BASE_URL + endpoint;
        System.out.println("[SpotifyDJams] Making request to: " + url);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                System.out.println("[SpotifyDJams] Response code: " + response.statusCode());
                System.out.println("[SpotifyDJams] Response body: " + response.body());
                return GSON.fromJson(response.body(), JsonObject.class);
            })
            .exceptionally(e -> {
                System.out.println("[SpotifyDJams] Request FAILED: " + e.getMessage());
                e.printStackTrace();
                return null;
            });
    }

    public static CompletableFuture<JsonObject> post(String endpoint, JsonObject body) {
        String url = BackendConfig.BASE_URL + endpoint;
        System.out.println("[SpotifyDJams] POST request to: " + url);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
            .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> {
                System.out.println("[SpotifyDJams] Response code: " + response.statusCode());
                return GSON.fromJson(response.body(), JsonObject.class);
            })
            .exceptionally(e -> {
                System.out.println("[SpotifyDJams] Request FAILED: " + e.getMessage());
                return null;
            });
    }
}