package me.proxycracked.universalaccountmanager.auth;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/**
 * Shared JSON-over-HTTP plumbing for the Microsoft/Xbox/Minecraft endpoints.
 *
 * <p>Every auth step reports failures the same way here so the GUIs can show the real Microsoft
 * error instead of a bare {@code CompletionException}.</p>
 */
public final class AuthHttp {
    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
        .setConnectionRequestTimeout(15000)
        .setConnectTimeout(15000)
        .setSocketTimeout(30000)
        .build();

    private AuthHttp() {
    }

    /** Executes the request and returns the parsed body, failing on any non-2xx status. */
    public static JsonObject execute(HttpRequestBase request, String step) throws Exception {
        Response response = send(request, step);
        if (response.status == 429) {
            throw new Exception(step + " is rate limited by Microsoft, try again in a moment");
        }
        if (response.status < 200 || response.status >= 300) {
            throw new Exception(step + " failed (HTTP " + response.status + "): " + describe(response.json, response.body));
        }
        if (response.json == null) {
            throw new Exception(step + " returned a non-JSON response: " + snippet(response.body));
        }
        return response.json;
    }

    /** Executes the request and hands back the status too, for endpoints that answer with 400 by design. */
    public static Response send(HttpRequestBase request, String step) throws Exception {
        request.setConfig(REQUEST_CONFIG);
        if (request.getFirstHeader("Accept") == null) {
            request.setHeader("Accept", "application/json");
        }

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(request)) {
            int status = response.getStatusLine().getStatusCode();
            String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity(), "UTF-8");
            return new Response(status, body, parse(body));
        } catch (Exception error) {
            if (error instanceof java.io.IOException) {
                throw new Exception(step + " could not reach Microsoft: " + rootMessage(error));
            }
            throw error;
        }
    }

    public static JsonObject parse(String body) {
        try {
            JsonElement parsed = new JsonParser().parse(body);
            return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static String describe(JsonObject json, String body) {
        if (json == null) {
            return snippet(body);
        }
        List<String> parts = new ArrayList<String>();
        for (String field : new String[] {"error", "error_description", "errorMessage", "Message", "XErr"}) {
            String value = optionalString(json, field);
            if (!StringUtils.isBlank(value) && !parts.contains(value)) {
                parts.add(value);
            }
        }
        return parts.isEmpty() ? snippet(body) : StringUtils.join(parts, " - ");
    }

    public static String snippet(String body) {
        String value = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        if (value.isEmpty()) {
            return "empty response";
        }
        return value.length() > 140 ? value.substring(0, 140) + "..." : value;
    }

    public static String requiredString(JsonObject json, String field, String errorMessage) throws Exception {
        String value = optionalString(json, field);
        if (StringUtils.isBlank(value)) {
            throw new Exception(errorMessage);
        }
        return value;
    }

    public static String optionalString(JsonObject json, String field) {
        if (json == null) {
            return "";
        }
        JsonElement value = json.get(field);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return "";
        }
        return value.getAsString();
    }

    public static int optionalInt(JsonObject json, String field, int fallback) {
        String value = optionalString(json, field);
        try {
            return StringUtils.isBlank(value) ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return StringUtils.isBlank(message) ? current.getClass().getSimpleName() : message.trim();
    }

    public static final class Response {
        public final int status;
        public final String body;
        public final JsonObject json;

        Response(int status, String body, JsonObject json) {
            this.status = status;
            this.body = body;
            this.json = json;
        }
    }
}
