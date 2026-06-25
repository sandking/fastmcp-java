package io.github.sandking.fastmcp.agentscope.boot;

import io.github.sandking.fastmcp.safe.config.SafeMcpServerConfiguration;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;

final class FastMcpAgentScopeHttpClientSupport {
    private FastMcpAgentScopeHttpClientSupport() {
    }

    static String sseUrl(SafeMcpServerConfiguration server) {
        URI endpoint = URI.create(requireAbsoluteUrl(server.endpoint(), "sse endpoint"));
        String sseEndpoint = requireText(server.sseEndpoint(), "sseEndpoint");
        URI resolved = URI.create(sseEndpoint).isAbsolute()
                ? URI.create(sseEndpoint)
                : endpoint.resolve(sseEndpoint);
        return resolved.toString();
    }

    static void configureHttpClient(HttpClient.Builder builder, SafeMcpServerConfiguration server) {
        builder.version(HttpClient.Version.HTTP_1_1);
        if (server.httpCookiesEnabled()) {
            builder.cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        }
    }

    static String requireAbsoluteUrl(String value, String fieldName) {
        String text = requireText(value, fieldName);
        URI uri = URI.create(text);
        if (!uri.isAbsolute() || uri.getRawAuthority() == null || uri.getRawAuthority().isBlank()) {
            throw new IllegalArgumentException(fieldName + " must be an absolute URL: " + value);
        }
        if (uri.getRawFragment() != null) {
            throw new IllegalArgumentException(fieldName + " must not contain a fragment: " + value);
        }
        return text;
    }

    static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
