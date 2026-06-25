package io.github.sandking.fastmcp.springai.boot;

import io.github.sandking.fastmcp.safe.config.SafeMcpServerConfiguration;
import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class FastMcpSpringAiHttpTransportSupport {
    record StreamableHttpEndpoint(String baseUri, String endpoint) {
    }

    private FastMcpSpringAiHttpTransportSupport() {
    }

    static StreamableHttpEndpoint streamableHttpEndpoint(String endpoint) {
        URI uri = URI.create(endpoint);
        if (!uri.isAbsolute() || uri.getRawAuthority() == null || uri.getRawAuthority().isBlank()) {
            throw new IllegalArgumentException("streamable-http endpoint must be an absolute URL: " + endpoint);
        }
        if (uri.getRawFragment() != null) {
            throw new IllegalArgumentException("streamable-http endpoint must not contain a fragment: " + endpoint);
        }
        String baseUri = uri.getScheme() + "://" + uri.getRawAuthority();
        String path = uri.getRawPath();
        String endpointPath = path == null || path.isBlank() ? "/" : path;
        if (uri.getRawQuery() != null) {
            endpointPath = endpointPath + "?" + uri.getRawQuery();
        }
        return new StreamableHttpEndpoint(baseUri, endpointPath);
    }

    static HttpClient.Builder httpClientBuilder(SafeMcpServerConfiguration server) {
        HttpClient.Builder builder = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1);
        if (server.httpCookiesEnabled()) {
            builder.cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
        }
        return builder;
    }

    static McpSyncHttpClientRequestCustomizer httpRequestCustomizer(SafeMcpServerConfiguration server) {
        return (requestBuilder, method, uri, body, context) -> applyHttpRequestConfiguration(requestBuilder, uri,
                server.httpHeaders(), server.httpQueryParams());
    }

    static void applyHttpRequestConfiguration(HttpRequest.Builder requestBuilder, URI uri,
            Map<String, String> headers, Map<String, String> queryParams) {
        headers.forEach(requestBuilder::header);
        if (!queryParams.isEmpty()) {
            requestBuilder.uri(appendQueryParams(uri, queryParams));
        }
    }

    static URI appendQueryParams(URI uri, Map<String, String> queryParams) {
        if (queryParams.isEmpty()) {
            return uri;
        }
        StringBuilder value = new StringBuilder();
        value.append(uri.getScheme()).append("://").append(uri.getRawAuthority());
        if (uri.getRawPath() != null) {
            value.append(uri.getRawPath());
        }
        String rawQuery = uri.getRawQuery();
        if (rawQuery != null && !rawQuery.isBlank()) {
            value.append('?').append(rawQuery);
        }
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            value.append(value.indexOf("?") >= 0 ? '&' : '?')
                    .append(encodeQueryPart(entry.getKey()))
                    .append('=')
                    .append(encodeQueryPart(entry.getValue()));
        }
        if (uri.getRawFragment() != null) {
            value.append('#').append(uri.getRawFragment());
        }
        return URI.create(value.toString());
    }

    private static String encodeQueryPart(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
