package io.github.sandking.fastmcp.safe.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SafeMcpServerConfiguration {
    private final String name;
    private final boolean enabled;
    private final String transport;
    private final String command;
    private final String endpoint;
    private final String sseEndpoint;
    private final Duration requestTimeout;
    private final Duration initializationTimeout;
    private final String clientName;
    private final String clientVersion;
    private final List<String> arguments;
    private final Map<String, String> environment;
    private final Map<String, String> httpHeaders;
    private final Map<String, String> httpQueryParams;
    private final boolean httpCookiesEnabled;
    private final Map<String, SafeMcpToolConfiguration> tools;

    private SafeMcpServerConfiguration(Builder builder) {
        this.name = SafeMcpConfigException.requireText(builder.name, "name", "INVALID_SERVER_NAME");
        this.enabled = builder.enabled;
        this.transport = builder.transport == null ? "" : builder.transport;
        this.command = builder.command == null ? "" : builder.command;
        this.endpoint = builder.endpoint == null ? "" : builder.endpoint;
        this.sseEndpoint = builder.sseEndpoint;
        this.requestTimeout = builder.requestTimeout;
        this.initializationTimeout = builder.initializationTimeout;
        this.clientName = builder.clientName;
        this.clientVersion = builder.clientVersion;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(builder.arguments));
        this.environment = Collections.unmodifiableMap(new LinkedHashMap<>(builder.environment));
        this.httpHeaders = Collections.unmodifiableMap(new LinkedHashMap<>(builder.httpHeaders));
        this.httpQueryParams = Collections.unmodifiableMap(new LinkedHashMap<>(builder.httpQueryParams));
        this.httpCookiesEnabled = builder.httpCookiesEnabled;
        this.tools = Collections.unmodifiableMap(new LinkedHashMap<>(builder.tools));
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String name() {
        return name;
    }

    public boolean enabled() {
        return enabled;
    }

    public String transport() {
        return transport;
    }

    public String command() {
        return command;
    }

    public String endpoint() {
        return endpoint;
    }

    public String sseEndpoint() {
        return sseEndpoint;
    }

    public Optional<Duration> requestTimeout() {
        return Optional.ofNullable(requestTimeout);
    }

    public Optional<Duration> initializationTimeout() {
        return Optional.ofNullable(initializationTimeout);
    }

    public String clientName() {
        return clientName;
    }

    public String clientVersion() {
        return clientVersion;
    }

    public List<String> arguments() {
        return arguments;
    }

    public Map<String, String> environment() {
        return environment;
    }

    public Map<String, String> httpHeaders() {
        return httpHeaders;
    }

    public Map<String, String> httpQueryParams() {
        return httpQueryParams;
    }

    public boolean httpCookiesEnabled() {
        return httpCookiesEnabled;
    }

    public Map<String, SafeMcpToolConfiguration> tools() {
        return tools;
    }

    public SafeMcpToolConfiguration tool(String rawName) {
        return tools.get(SafeMcpConfigException.requireText(rawName, "rawName", "INVALID_RAW_TOOL_NAME"));
    }

    public static final class Builder {
        private final String name;
        private boolean enabled = true;
        private String transport;
        private String command;
        private String endpoint;
        private String sseEndpoint = "/sse";
        private Duration requestTimeout;
        private Duration initializationTimeout;
        private String clientName = "fastmcp-java";
        private String clientVersion = "0.1.0";
        private final List<String> arguments = new ArrayList<>();
        private final Map<String, String> environment = new LinkedHashMap<>();
        private final Map<String, String> httpHeaders = new LinkedHashMap<>();
        private final Map<String, String> httpQueryParams = new LinkedHashMap<>();
        private boolean httpCookiesEnabled = true;
        private final Map<String, SafeMcpToolConfiguration> tools = new LinkedHashMap<>();

        private Builder(String name) {
            this.name = SafeMcpConfigException.requireText(name, "name", "INVALID_SERVER_NAME");
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder transport(String transport) {
            this.transport = SafeMcpConfigException.requireText(transport, "transport", "INVALID_TRANSPORT");
            return this;
        }

        public Builder command(String command) {
            this.command = SafeMcpConfigException.requireText(command, "command", "INVALID_COMMAND");
            return this;
        }

        public Builder endpoint(String endpoint) {
            this.endpoint = SafeMcpConfigException.requireText(endpoint, "endpoint", "INVALID_ENDPOINT");
            return this;
        }

        public Builder sseEndpoint(String sseEndpoint) {
            this.sseEndpoint = SafeMcpConfigException.requireText(sseEndpoint, "sseEndpoint",
                    "INVALID_SSE_ENDPOINT");
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requirePositiveDuration(requestTimeout, "requestTimeout",
                    "INVALID_REQUEST_TIMEOUT");
            return this;
        }

        public Builder initializationTimeout(Duration initializationTimeout) {
            this.initializationTimeout = requirePositiveDuration(initializationTimeout, "initializationTimeout",
                    "INVALID_INITIALIZATION_TIMEOUT");
            return this;
        }

        public Builder clientName(String clientName) {
            this.clientName = SafeMcpConfigException.requireText(clientName, "clientName", "INVALID_CLIENT_NAME");
            return this;
        }

        public Builder clientVersion(String clientVersion) {
            this.clientVersion = SafeMcpConfigException.requireText(clientVersion, "clientVersion",
                    "INVALID_CLIENT_VERSION");
            return this;
        }

        public Builder argument(String argument) {
            arguments.add(SafeMcpConfigException.requireText(argument, "argument", "INVALID_ARGUMENT"));
            return this;
        }

        public Builder environment(String name, String value) {
            String key = SafeMcpConfigException.requireText(name, "name", "INVALID_ENVIRONMENT");
            String envValue = value == null ? "" : value;
            environment.put(key, envValue);
            return this;
        }

        public Builder httpHeader(String name, String value) {
            String key = SafeMcpConfigException.requireText(name, "name", "INVALID_HTTP_HEADER");
            String headerValue = value == null ? "" : value;
            httpHeaders.put(key, headerValue);
            return this;
        }

        public Builder httpQueryParam(String name, String value) {
            String key = SafeMcpConfigException.requireText(name, "name", "INVALID_HTTP_QUERY_PARAM");
            String queryParamValue = value == null ? "" : value;
            httpQueryParams.put(key, queryParamValue);
            return this;
        }

        public Builder httpCookiesEnabled(boolean httpCookiesEnabled) {
            this.httpCookiesEnabled = httpCookiesEnabled;
            return this;
        }

        public Builder tool(SafeMcpToolConfiguration tool) {
            if (tool == null) {
                throw new SafeMcpConfigException("INVALID_TOOL", "tool must not be null");
            }
            if (tools.containsKey(tool.rawName())) {
                throw new SafeMcpConfigException("DUPLICATE_TOOL", "Duplicate tool: " + tool.rawName());
            }
            tools.put(tool.rawName(), tool);
            return this;
        }

        public SafeMcpServerConfiguration build() {
            return new SafeMcpServerConfiguration(this);
        }

        private static Duration requirePositiveDuration(Duration duration, String fieldName, String code) {
            if (duration == null || duration.isZero() || duration.isNegative()) {
                throw new SafeMcpConfigException(code, fieldName + " must be a positive duration");
            }
            return duration;
        }
    }
}
