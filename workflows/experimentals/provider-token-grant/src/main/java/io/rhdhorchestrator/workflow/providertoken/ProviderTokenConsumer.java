package io.rhdhorchestrator.workflow.providertoken;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Experimental call-time adapter for a provider token grant.
 *
 * The access token is deliberately kept in local variables only. It is not
 * returned from this operation, copied into workflow state, logged, or
 * included in an exception message.
 */
@ApplicationScoped
public class ProviderTokenConsumer {
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @ConfigProperty(name = "secure-token-storage.url")
    String tokenBrokerUrl;

    @ConfigProperty(name = "secure-token-storage.service-token")
    String serviceToken;

    @ConfigProperty(name = "github.profile-url")
    String githubProfileUrl;

    private final ObjectMapper objectMapper;

    public ProviderTokenConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Retrieves a short-lived GitHub access token and immediately uses it for
     * the provider call. Only a sanitized provider response crosses the
     * workflow boundary.
     */
    public Map<String, Object> getGithubProfile(String grantId, String provider) {
        requireInput(grantId, provider);
        if (!"github".equals(provider)) {
            throw new IllegalArgumentException("This prototype supports provider github only");
        }

        String accessToken = requestAccessToken(grantId, provider);
        return requestGithubProfile(accessToken);
    }

    private String requestAccessToken(String grantId, String provider) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "grantId", grantId,
                    "provider", provider));

            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(tokenBrokerUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));
            addServiceAuthorization(request);

            HttpResponse<String> response = httpClient.send(
                    request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Token broker rejected the grant request");
            }

            JsonNode body = objectMapper.readTree(response.body());
            JsonNode accessToken = body.get("accessToken");
            if (accessToken == null || !accessToken.isTextual() || accessToken.textValue().isBlank()) {
                throw new IllegalStateException("Token broker returned no access token");
            }
            return accessToken.textValue();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Token broker request failed", error);
        } catch (IOException error) {
            throw new IllegalStateException("Token broker request failed", error);
        }
    }

    private Map<String, Object> requestGithubProfile(String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(githubProfileUrl))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Provider request failed");
            }

            JsonNode body = objectMapper.readTree(response.body());
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("id", body.path("id").asLong());
            profile.put("login", body.path("login").asText());
            profile.put("name", body.path("name").isNull() ? null : body.path("name").asText());
            return profile;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Provider request failed", error);
        } catch (IOException error) {
            throw new IllegalStateException("Provider request failed", error);
        }
    }

    private void addServiceAuthorization(HttpRequest.Builder request) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException("SECURE_TOKEN_STORAGE_SERVICE_TOKEN is required");
        }
        request.header("Authorization", "Bearer " + serviceToken);
    }

    private static void requireInput(String grantId, String provider) {
        if (grantId == null || grantId.isBlank() || provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("grantId and provider are required");
        }
    }
}
