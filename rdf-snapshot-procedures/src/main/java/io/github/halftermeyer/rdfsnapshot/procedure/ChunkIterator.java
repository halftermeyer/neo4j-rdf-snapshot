package io.github.halftermeyer.rdfsnapshot.procedure;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Runs serialization steps lazily and hands out the text they produce in chunks of roughly
 * {@code chunkSize} characters, cut at subject block boundaries.
 */
public final class ChunkIterator implements Iterator<String> {
    private final StringBuilder buffer;
    private final Iterator<Runnable> steps;
    private final int chunkSize;

    public ChunkIterator(StringBuilder buffer, Iterator<Runnable> steps, int chunkSize) {
        this.buffer = buffer;
        this.steps = steps;
        this.chunkSize = chunkSize;
    }

    @Override
    public boolean hasNext() {
        while (buffer.length() < chunkSize && steps.hasNext()) {
            steps.next().run();
        }
        return !buffer.isEmpty();
    }

    @Override
    public String next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        String chunk = buffer.toString();
        buffer.setLength(0);
        return chunk;
    }
}
