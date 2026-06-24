package io.github.sandking.fastmcp;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class AnnotatedToolRegistrar {
    private AnnotatedToolRegistrar() {
    }

    public static FastMcpServer register(FastMcpServer server, Object target) {
        Objects.requireNonNull(server, "server must not be null");
        Objects.requireNonNull(target, "target must not be null");

        for (Method method : target.getClass().getDeclaredMethods()) {
            McpTool annotation = method.getAnnotation(McpTool.class);
            if (annotation != null) {
                registerMethod(server, target, method, annotation);
            }
        }
        return server;
    }

    private static void registerMethod(FastMcpServer server, Object target, Method method, McpTool annotation) {
        if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("@McpTool methods must not be static: " + method.getName());
        }
        if (method.getReturnType() == Void.TYPE) {
            throw new IllegalArgumentException("@McpTool methods must return a value: " + method.getName());
        }

        String toolName = annotation.name().trim().isEmpty() ? method.getName() : annotation.name().trim();
        String description = annotation.description().trim();
        ToolMethodBinding binding = ToolMethodBinding.from(target, method);

        server.tool(toolName, description, binding.inputSchema(), arguments -> binding.invoke(arguments.asMap()));
    }

    private static final class ToolMethodBinding {
        private final Object target;
        private final Method method;
        private final List<ToolParameterBinding> parameters;
        private final ObjectNode inputSchema;

        private ToolMethodBinding(Object target, Method method, List<ToolParameterBinding> parameters,
                ObjectNode inputSchema) {
            this.target = target;
            this.method = method;
            this.parameters = parameters;
            this.inputSchema = inputSchema;
        }

        static ToolMethodBinding from(Object target, Method method) {
            method.setAccessible(true);
            Parameter[] methodParameters = method.getParameters();
            List<ToolParameterBinding> bindings = new ArrayList<>();
            ObjectNode inputSchema = JsonSchemas.object();

            for (Parameter parameter : methodParameters) {
                ToolParameterBinding binding = ToolParameterBinding.from(parameter);
                bindings.add(binding);
                ObjectNode parameterSchema = schemaFor(parameter.getType());
                if (!binding.description().isEmpty()) {
                    JsonSchemas.description(parameterSchema, binding.description());
                }
                JsonSchemas.addProperty(inputSchema, binding.name(), parameterSchema);
                if (binding.required()) {
                    JsonSchemas.require(inputSchema, binding.name());
                }
            }

            return new ToolMethodBinding(target, method, bindings, inputSchema);
        }

        ObjectNode inputSchema() {
            return inputSchema.deepCopy();
        }

        ToolResult invoke(Map<String, Object> arguments) throws InvocationTargetException, IllegalAccessException {
            Object[] values = new Object[parameters.size()];
            for (int i = 0; i < parameters.size(); i++) {
                values[i] = parameters.get(i).read(arguments);
            }

            Object result;
            try {
                result = method.invoke(target, values);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw exception;
            }

            if (result instanceof ToolResult) {
                return (ToolResult) result;
            }
            return ToolResult.text(String.valueOf(result));
        }

        private static ObjectNode schemaFor(Class<?> type) {
            if (type == String.class) {
                return JsonSchemas.string();
            }
            if (type == Integer.class || type == Integer.TYPE || type == Long.class || type == Long.TYPE
                    || type == Short.class || type == Short.TYPE || type == Byte.class || type == Byte.TYPE) {
                return JsonSchemas.integer();
            }
            if (type == Double.class || type == Double.TYPE || type == Float.class || type == Float.TYPE) {
                return JsonSchemas.number();
            }
            if (type == Boolean.class || type == Boolean.TYPE) {
                return JsonSchemas.bool();
            }
            throw new IllegalArgumentException("Unsupported @McpTool parameter type: " + type.getName());
        }
    }

    private static final class ToolParameterBinding {
        private final String name;
        private final String description;
        private final boolean required;
        private final Class<?> type;

        private ToolParameterBinding(String name, String description, boolean required, Class<?> type) {
            this.name = name;
            this.description = description;
            this.required = required;
            this.type = type;
        }

        static ToolParameterBinding from(Parameter parameter) {
            ToolParam annotation = parameter.getAnnotation(ToolParam.class);
            String annotatedName = annotation == null ? "" : annotation.name().trim();
            String name = annotatedName.isEmpty() ? parameter.getName() : annotatedName;
            String description = annotation == null ? "" : annotation.description().trim();
            boolean required = annotation == null || annotation.required();

            if (name.matches("arg\\d+")) {
                throw new IllegalArgumentException("Parameter name is not available for " + parameter
                        + "; compile with -parameters or set @ToolParam(name = ...)");
            }
            return new ToolParameterBinding(name, description, required, parameter.getType());
        }

        String name() {
            return name;
        }

        String description() {
            return description;
        }

        boolean required() {
            return required;
        }

        Object read(Map<String, Object> arguments) {
            if (!arguments.containsKey(name)) {
                if (required) {
                    throw new IllegalArgumentException("Missing argument: " + name);
                }
                return null;
            }
            Object value = arguments.get(name);
            if (value == null) {
                if (type.isPrimitive()) {
                    throw new IllegalArgumentException("Argument " + name + " must not be null");
                }
                return null;
            }
            return convert(value);
        }

        private Object convert(Object value) {
            if (type.isInstance(value)) {
                return value;
            }
            if (type == String.class) {
                return String.valueOf(value);
            }
            if (type == Integer.class || type == Integer.TYPE) {
                return toNumber(value).intValue();
            }
            if (type == Long.class || type == Long.TYPE) {
                return toNumber(value).longValue();
            }
            if (type == Short.class || type == Short.TYPE) {
                return toNumber(value).shortValue();
            }
            if (type == Byte.class || type == Byte.TYPE) {
                return toNumber(value).byteValue();
            }
            if (type == Double.class || type == Double.TYPE) {
                return toNumber(value).doubleValue();
            }
            if (type == Float.class || type == Float.TYPE) {
                return toNumber(value).floatValue();
            }
            if (type == Boolean.class || type == Boolean.TYPE) {
                if (value instanceof Boolean) {
                    return value;
                }
                if (value instanceof String) {
                    return Boolean.parseBoolean((String) value);
                }
            }
            throw new IllegalArgumentException(
                    "Argument " + name + " cannot be converted to " + type.getSimpleName());
        }

        private Number toNumber(Object value) {
            if (value instanceof Number) {
                return (Number) value;
            }
            if (value instanceof String) {
                return Double.valueOf((String) value);
            }
            throw new IllegalArgumentException("Argument " + name + " must be numeric");
        }
    }
}
