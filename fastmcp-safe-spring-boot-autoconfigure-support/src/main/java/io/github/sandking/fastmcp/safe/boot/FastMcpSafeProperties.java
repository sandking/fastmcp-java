package io.github.sandking.fastmcp.safe.boot;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fastmcp.safe")
public class FastMcpSafeProperties {
    private boolean enabled = true;
    private Diagnostics diagnostics = new Diagnostics();
    private Map<String, Server> servers = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Diagnostics getDiagnostics() {
        return diagnostics;
    }

    public void setDiagnostics(Diagnostics diagnostics) {
        this.diagnostics = diagnostics == null ? new Diagnostics() : diagnostics;
    }

    public Map<String, Server> getServers() {
        return servers;
    }

    public void setServers(Map<String, Server> servers) {
        this.servers = servers == null ? new LinkedHashMap<>() : servers;
    }

    public static class Server {
        private boolean enabled = true;
        private String transport;
        private String command;
        private String endpoint;
        private String sseEndpoint = "/sse";
        private Duration requestTimeout;
        private Duration initializationTimeout;
        private String clientName;
        private String clientVersion;
        private List<String> arguments = new ArrayList<>();
        private Map<String, String> environment = new LinkedHashMap<>();
        private Http http = new Http();
        private Map<String, Tool> tools = new LinkedHashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTransport() {
            return transport;
        }

        public void setTransport(String transport) {
            this.transport = transport;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getSseEndpoint() {
            return sseEndpoint;
        }

        public void setSseEndpoint(String sseEndpoint) {
            this.sseEndpoint = hasText(sseEndpoint) ? sseEndpoint : "/sse";
        }

        public Duration getRequestTimeout() {
            return requestTimeout;
        }

        public void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
        }

        public Duration getInitializationTimeout() {
            return initializationTimeout;
        }

        public void setInitializationTimeout(Duration initializationTimeout) {
            this.initializationTimeout = initializationTimeout;
        }

        public String getClientName() {
            return clientName;
        }

        public void setClientName(String clientName) {
            this.clientName = clientName;
        }

        public String getClientVersion() {
            return clientVersion;
        }

        public void setClientVersion(String clientVersion) {
            this.clientVersion = clientVersion;
        }

        public List<String> getArguments() {
            return arguments;
        }

        public void setArguments(List<String> arguments) {
            this.arguments = arguments == null ? new ArrayList<>() : arguments;
        }

        public Map<String, String> getEnvironment() {
            return environment;
        }

        public void setEnvironment(Map<String, String> environment) {
            this.environment = environment == null ? new LinkedHashMap<>() : environment;
        }

        public Http getHttp() {
            return http;
        }

        public void setHttp(Http http) {
            this.http = http == null ? new Http() : http;
        }

        public Map<String, Tool> getTools() {
            return tools;
        }

        public void setTools(Map<String, Tool> tools) {
            this.tools = tools == null ? new LinkedHashMap<>() : tools;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static class Diagnostics {
        private String externalRawProvider = "fail";

        public String getExternalRawProvider() {
            return externalRawProvider;
        }

        public void setExternalRawProvider(String externalRawProvider) {
            this.externalRawProvider = externalRawProvider == null ? "fail" : externalRawProvider;
        }
    }

    public static class Http {
        private Map<String, String> headers = new LinkedHashMap<>();
        private Map<String, String> queryParams = new LinkedHashMap<>();
        private Cookies cookies = new Cookies();

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers == null ? new LinkedHashMap<>() : headers;
        }

        public Map<String, String> getQueryParams() {
            return queryParams;
        }

        public void setQueryParams(Map<String, String> queryParams) {
            this.queryParams = queryParams == null ? new LinkedHashMap<>() : queryParams;
        }

        public Cookies getCookies() {
            return cookies;
        }

        public void setCookies(Cookies cookies) {
            this.cookies = cookies == null ? new Cookies() : cookies;
        }
    }

    public static class Cookies {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Tool {
        private String rawName;
        private String name;
        private String description;
        private Map<String, Object> inputSchema = new LinkedHashMap<>();
        private Map<String, String> argumentMappings = new LinkedHashMap<>();
        private Map<String, String> injectedArguments = new LinkedHashMap<>();
        private boolean readOnly;
        private boolean concurrencySafe;

        public String getRawName() {
            return rawName;
        }

        public void setRawName(String rawName) {
            this.rawName = rawName;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, Object> getInputSchema() {
            return inputSchema;
        }

        public void setInputSchema(Map<String, Object> inputSchema) {
            this.inputSchema = inputSchema == null ? new LinkedHashMap<>() : inputSchema;
        }

        public Map<String, String> getArgumentMappings() {
            return argumentMappings;
        }

        public void setArgumentMappings(Map<String, String> argumentMappings) {
            this.argumentMappings = argumentMappings == null ? new LinkedHashMap<>() : argumentMappings;
        }

        public Map<String, String> getInjectedArguments() {
            return injectedArguments;
        }

        public void setInjectedArguments(Map<String, String> injectedArguments) {
            this.injectedArguments = injectedArguments == null ? new LinkedHashMap<>() : injectedArguments;
        }

        public boolean isReadOnly() {
            return readOnly;
        }

        public void setReadOnly(boolean readOnly) {
            this.readOnly = readOnly;
        }

        public boolean isConcurrencySafe() {
            return concurrencySafe;
        }

        public void setConcurrencySafe(boolean concurrencySafe) {
            this.concurrencySafe = concurrencySafe;
        }
    }
}
