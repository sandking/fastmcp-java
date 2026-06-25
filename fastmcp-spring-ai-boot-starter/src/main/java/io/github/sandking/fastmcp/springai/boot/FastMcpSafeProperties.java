package io.github.sandking.fastmcp.springai.boot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fastmcp.safe")
public class FastMcpSafeProperties {
    private boolean enabled = true;
    private Map<String, Server> servers = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Server> getServers() {
        return servers;
    }

    public void setServers(Map<String, Server> servers) {
        this.servers = servers == null ? new LinkedHashMap<>() : servers;
    }

    public static class Server {
        private String transport;
        private String command;
        private List<String> arguments = new ArrayList<>();
        private Map<String, String> environment = new LinkedHashMap<>();
        private Map<String, Tool> tools = new LinkedHashMap<>();

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

        public Map<String, Tool> getTools() {
            return tools;
        }

        public void setTools(Map<String, Tool> tools) {
            this.tools = tools == null ? new LinkedHashMap<>() : tools;
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
