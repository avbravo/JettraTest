package io.jettra.test.jwt;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Utility client to perform JWT authentication and subsequent requests for JettraTest.
 */
public class JwtTestClient {

    private final HttpClient httpClient;
    private String token;

    public JwtTestClient() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Authenticates with the given endpoint and payload, and stores the token.
     * Extracts token from response body or headers depending on implementation.
     * We will assume the endpoint returns a raw token string or it is JSON and we grab the whole thing for simplicity
     * or we expect the user to parse it. Let's return the body.
     */
    public String authenticate(String url, String jsonPayload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            // Simplified: we assume the response body is the token, or the user can process it.
            this.token = response.body();
            return this.token;
        } else {
            throw new RuntimeException("Authentication failed with status " + response.statusCode() + ": " + response.body());
        }
    }

    /**
     * Sets the token manually.
     */
    public void setToken(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    /**
     * Performs a GET request using the stored JWT token.
     */
    public String getWithToken(String url) throws Exception {
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("JWT Token is not set. Authenticate first.");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        } else {
            throw new RuntimeException("GET request failed with status " + response.statusCode() + ": " + response.body());
        }
    }
}
