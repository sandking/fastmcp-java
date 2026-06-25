package io.github.sandking.fastmcp.safe.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SafeMcpConfiguration {
    private final Map<String, SafeMcpServerConfiguration> servers;

    private SafeMcpConfiguration(Builder builder) {
        this.servers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.servers));
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, SafeMcpServerConfiguration> servers() {
        return servers;
    }

    public SafeMcpServerConfiguration server(String name) {
        return servers.get(SafeMcpConfigException.requireText(name, "name", "INVALID_SERVER_NAME"));
    }

    public static final class Builder {
        private final Map<String, SafeMcpServerConfiguration> servers = new LinkedHashMap<>();

        public Builder server(SafeMcpServerConfiguration server) {
            if (server == null) {
                throw new SafeMcpConfigException("INVALID_SERVER", "server must not be null");
            }
            if (servers.containsKey(server.name())) {
                throw new SafeMcpConfigException("DUPLICATE_SERVER", "Duplicate server: " + server.name());
            }
            servers.put(server.name(), server);
            return this;
        }

        public SafeMcpConfiguration build() {
            return new SafeMcpConfiguration(this);
        }
    }
}
