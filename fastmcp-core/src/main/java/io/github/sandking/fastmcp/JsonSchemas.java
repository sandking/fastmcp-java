package io.github.sandking.fastmcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class JsonSchemas {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private JsonSchemas() {
    }

    public static ObjectNode object() {
        ObjectNode schema = JSON.objectNode();
        schema.put("type", "object");
        schema.set("properties", JSON.objectNode());
        return schema;
    }

    public static ObjectNode string() {
        return typed("string");
    }

    public static ObjectNode integer() {
        return typed("integer");
    }

    public static ObjectNode number() {
        return typed("number");
    }

    public static ObjectNode bool() {
        return typed("boolean");
    }

    public static ObjectNode array(JsonNode items) {
        ObjectNode schema = typed("array");
        schema.set("items", items.deepCopy());
        return schema;
    }

    public static ObjectNode addProperty(ObjectNode objectSchema, String name, JsonNode propertySchema) {
        requireObjectSchema(objectSchema);
        ObjectNode properties = objectChild(objectSchema, "properties");
        properties.set(FastMcpServer.requireText(name, "name"), propertySchema.deepCopy());
        return objectSchema;
    }

    public static ObjectNode require(ObjectNode objectSchema, String... names) {
        requireObjectSchema(objectSchema);
        ArrayNode required = arrayChild(objectSchema, "required");
        for (String name : names) {
            required.add(FastMcpServer.requireText(name, "name"));
        }
        return objectSchema;
    }

    public static ObjectNode description(ObjectNode schema, String description) {
        schema.put("description", FastMcpServer.requireText(description, "description"));
        return schema;
    }

    private static ObjectNode typed(String type) {
        ObjectNode schema = JSON.objectNode();
        schema.put("type", type);
        return schema;
    }

    private static void requireObjectSchema(ObjectNode schema) {
        if (schema == null || !schema.path("type").asText().equals("object")) {
            throw new IllegalArgumentException("schema must be a JSON object schema");
        }
    }

    private static ObjectNode objectChild(ObjectNode parent, String fieldName) {
        JsonNode child = parent.get(fieldName);
        if (child instanceof ObjectNode) {
            return (ObjectNode) child;
        }
        ObjectNode created = JSON.objectNode();
        parent.set(fieldName, created);
        return created;
    }

    private static ArrayNode arrayChild(ObjectNode parent, String fieldName) {
        JsonNode child = parent.get(fieldName);
        if (child instanceof ArrayNode) {
            return (ArrayNode) child;
        }
        ArrayNode created = JSON.arrayNode();
        parent.set(fieldName, created);
        return created;
    }
}
