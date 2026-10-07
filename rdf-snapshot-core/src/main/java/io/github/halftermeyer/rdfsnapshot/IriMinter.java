package io.github.halftermeyer.rdfsnapshot;

import io.github.halftermeyer.rdfsnapshot.term.Iri;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Mints the IRIs of SPEC §5 from a base IRI. */
public final class IriMinter {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private final String base;

    /** @param base the project base IRI; one trailing {@code /} is ignored */
    public IriMinter(String base) {
        Objects.requireNonNull(base, "base");
        if (base.isEmpty()) {
            throw new IllegalArgumentException("base must not be empty");
        }
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public String base() {
        return base;
    }

    public String elementNamespace() {
        return base + "/e/";
    }

    public String labelNamespace() {
        return base + "/label/";
    }

    public String typeNamespace() {
        return base + "/type/";
    }

    public String propertyKeyNamespace() {
        return base + "/prop/";
    }

    public String snapshotNamespace() {
        return base + "/snapshot/";
    }

    public Iri element(String elementId) {
        return new Iri(elementNamespace() + encodeSegment(elementId));
    }

    public Iri label(String name) {
        return new Iri(labelNamespace() + encodeSegment(name));
    }

    public Iri type(String name) {
        return new Iri(typeNamespace() + encodeSegment(name));
    }

    public Iri propertyKey(String name) {
        return new Iri(propertyKeyNamespace() + encodeSegment(name));
    }

    public Iri snapshot(String snapshotId) {
        return new Iri(snapshotNamespace() + encodeSegment(snapshotId));
    }

    public Iri schemaGraph(String snapshotId) {
        return new Iri(snapshotNamespace() + encodeSegment(snapshotId) + "/schema");
    }

    /**
     * Percent-encodes a string as one RFC 3986 path segment (SPEC §5): UTF-8 bytes, every byte
     * outside the unreserved set ({@code A-Z a-z 0-9 - . _ ~}) and {@code :} becomes {@code %XX}
     * with upper-case hex digits.
     */
    public static String encodeSegment(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder(bytes.length);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if (isUnreserved(c) || c == ':') {
                out.append((char) c);
            } else {
                out.append('%').append(HEX[c >> 4]).append(HEX[c & 0xF]);
            }
        }
        return out.toString();
    }

    private static boolean isUnreserved(int c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '-' || c == '.' || c == '_' || c == '~';
    }
}
