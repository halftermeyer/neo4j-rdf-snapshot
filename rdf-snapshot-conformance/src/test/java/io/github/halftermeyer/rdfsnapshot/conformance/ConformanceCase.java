package io.github.halftermeyer.rdfsnapshot.conformance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.jena.atlas.json.JSON;
import org.apache.jena.atlas.json.JsonArray;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonValue;

/**
 * One directory of {@code conformance/}: {@code graphtype.cypher} (optional), {@code data.cypher},
 * {@code params.json} (optional), {@code scope.cypher}, {@code expected.trig}.
 */
record ConformanceCase(
        String name,
        List<String> graphType,
        List<String> data,
        Map<String, Object> params,
        String scope,
        Path expected) {

    static List<ConformanceCase> all(Path root) {
        try (Stream<Path> dirs = Files.list(root)) {
            return dirs.filter(Files::isDirectory).sorted().map(ConformanceCase::load).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static ConformanceCase load(Path dir) {
        Path graphType = dir.resolve("graphtype.cypher");
        Path params = dir.resolve("params.json");
        return new ConformanceCase(
                dir.getFileName().toString(),
                Files.exists(graphType) ? statements(read(graphType)) : List.of(),
                statements(read(dir.resolve("data.cypher"))),
                Files.exists(params) ? params(read(params)) : Map.of(),
                read(dir.resolve("scope.cypher")).strip(),
                dir.resolve("expected.trig"));
    }

    /** Statements end with {@code ;} at the end of a line. */
    static List<String> statements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : script.split("\n", -1)) {
            current.append(line).append('\n');
            if (line.stripTrailing().endsWith(";")) {
                String statement = current.toString().strip();
                statements.add(statement.substring(0, statement.length() - 1));
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) {
            statements.add(current.toString().strip());
        }
        return statements;
    }

    /** JSON values as driver parameters; {@code {"base64": "..."}} stands for a byte array. */
    private static Map<String, Object> params(String json) {
        Map<String, Object> params = new LinkedHashMap<>();
        JsonObject object = JSON.parse(json);
        object.keySet().forEach(key -> params.put(key, value(object.get(key))));
        return params;
    }

    private static Object value(JsonValue v) {
        if (v.isObject() && v.getAsObject().hasKey("base64")) {
            return Base64.getDecoder().decode(v.getAsObject().get("base64").getAsString().value());
        }
        if (v.isArray()) {
            JsonArray array = v.getAsArray();
            List<Object> list = new ArrayList<>();
            array.forEach(e -> list.add(value(e)));
            return list;
        }
        if (v.isString()) {
            return v.getAsString().value();
        }
        if (v.isBoolean()) {
            return v.getAsBoolean().value();
        }
        if (v.isNumber()) {
            Number n = v.getAsNumber().value();
            return (n instanceof java.math.BigDecimal d && d.scale() > 0) ? n.doubleValue() : n.longValue();
        }
        throw new IllegalArgumentException("Unsupported JSON value in params.json: " + v);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
