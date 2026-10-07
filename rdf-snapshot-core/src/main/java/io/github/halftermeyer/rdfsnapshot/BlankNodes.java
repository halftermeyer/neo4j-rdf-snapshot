package io.github.halftermeyer.rdfsnapshot;

import io.github.halftermeyer.rdfsnapshot.term.BlankNode;
import io.github.halftermeyer.rdfsnapshot.term.Term;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Deterministic blank node labels. A label is {@code b} followed by the first 128 bits, in hex,
 * of SHA-256 over the context {@code graph, owner, key, position} (joined with U+0000).
 * See docs/DECISIONS.md.
 */
public final class BlankNodes {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private BlankNodes() {}

    /**
     * @param graph    IRI of the graph the blank node lives in
     * @param owner    the term the blank node hangs from (a subject IRI, or another blank node)
     * @param key      what it hangs from it by (a predicate, or a predicate and a property key)
     * @param position the position among siblings with the same owner and key
     */
    public static BlankNode of(String graph, Term owner, String key, int position) {
        String context = graph + '\u0000' + owner + '\u0000' + key + '\u0000' + position;
        byte[] digest = sha256().digest(context.getBytes(StandardCharsets.UTF_8));
        StringBuilder label = new StringBuilder(33).append('b');
        for (int i = 0; i < 16; i++) {
            label.append(HEX[(digest[i] >> 4) & 0xF]).append(HEX[digest[i] & 0xF]);
        }
        return new BlankNode(label.toString());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
