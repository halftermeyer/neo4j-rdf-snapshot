package io.github.halftermeyer.rdfsnapshot.sink;

/** Escaping shared by the N-Quads and TriG sinks. */
final class TextEscaping {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private TextEscaping() {}

    /**
     * The body of a double-quoted literal. Follows canonical N-Triples: {@code \" \\ \n \r \t \b \f}
     * as ECHAR, other control characters (U+0000-U+001F, U+007F) as {@code \}{@code uXXXX},
     * everything else verbatim.
     */
    static void appendString(StringBuilder out, String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20 || c == 0x7F) {
                        appendUchar(out, c);
                    } else {
                        out.append(c);
                    }
                }
            }
        }
    }

    /**
     * An IRIREF body. Minted IRIs never need escaping; this protects against a base IRI with
     * characters IRIREF forbids ({@code <>"{}|^`\} and U+0000-U+0020), written as UCHAR.
     */
    static void appendIri(StringBuilder out, String iri) {
        for (int i = 0; i < iri.length(); i++) {
            char c = iri.charAt(i);
            if (c <= 0x20 || "<>\"{}|^`\\".indexOf(c) >= 0) {
                appendUchar(out, c);
            } else {
                out.append(c);
            }
        }
    }

    private static void appendUchar(StringBuilder out, char c) {
        out.append("\\u")
                .append(HEX[(c >> 12) & 0xF]).append(HEX[(c >> 8) & 0xF])
                .append(HEX[(c >> 4) & 0xF]).append(HEX[c & 0xF]);
    }
}
