package io.github.sandking.fastmcp.safe.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SafeMcpServerConfiguration {
    private final String name;
    private final String transport;
    private final String command;
    private final List<String> arguments;
    private final Map<String, String> environment;
    private final Map<String, SafeMcpToolConfiguration> tools;

    private SafeMcpServerConfiguration(Builder builder) {
        this.name = SafeMcpConfigException.requireText(builder.name, "name", "INVALID_SERVER_NAME");
        this.transport = builder.transport == null ? "" : builder.transport;
        this.command = builder.command == null ? "" : builder.command;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(builder.arguments));
        this.environment = Collections.unmodifiableMap(new LinkedHashMap<>(builder.environment));
        this.tools = Collections.unmodifiableMap(new LinkedHashMap<>(builder.tools));
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String name() {
        return name;
    }

    public String transport() {
        return transport;
    }

    public String command() {
        return command;
    }

    public List<String> arguments() {
        return arguments;
    }

    public Map<String, String> environment() {
        return environment;
    }

    public Map<String, SafeMcpToolConfiguration> tools() {
        return tools;
    }

    public SafeMcpToolConfiguration tool(String rawName) {
        return tools.get(SafeMcpConfigException.requireText(rawName, "rawName", "INVALID_RAW_TOOL_NAME"));
    }

    public static final class Builder {
        private final String name;
        private String transport;
        private String command;
        private final List<String> arguments = new ArrayList<>();
        private final Map<String, String> environment = new LinkedHashMap<>();
        private final Map<String, SafeMcpToolConfiguration> tools = new LinkedHashMap<>();

        private Builder(String name) {
            this.name = SafeMcpConfigException.requireText(name, "name", "INVALID_SERVER_NAME");
        }

        public Builder transport(String transport) {
            this.transport = SafeMcpConfigException.requireText(transport, "transport", "INVALID_TRANSPORT");
            return this;
        }

        public Builder command(String command) {
            this.command = SafeMcpConfigException.requireText(command, "command", "INVALID_COMMAND");
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
    }
}
