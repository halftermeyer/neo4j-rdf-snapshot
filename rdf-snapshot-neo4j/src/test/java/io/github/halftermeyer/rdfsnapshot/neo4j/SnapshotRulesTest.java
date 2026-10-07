package io.github.halftermeyer.rdfsnapshot.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SnapshotRulesTest {

    @Test
    void defaultSnapshotId() {
        assertEquals("20261005T140311Z", SnapshotRules.defaultSnapshotId(Instant.parse("2026-10-05T14:03:11.999Z")));
    }

    @Test
    void exportInstantIsWholeSeconds() {
        assertEquals(0, SnapshotRules.exportInstant().getNano());
    }

    @Test
    void base() {
        assertEquals("http://acme.org/plm/", SnapshotRules.requireBase("http://acme.org/plm/"));
        assertThrows(IllegalArgumentException.class, () -> SnapshotRules.requireBase(null));
        assertThrows(IllegalArgumentException.class, () -> SnapshotRules.requireBase(" "));
        assertThrows(IllegalArgumentException.class, () -> SnapshotRules.requireBase(42));
    }

    @Test
    void snapshotIdAndScope() {
        assertEquals("s1", SnapshotRules.requireSnapshotId("s1"));
        assertThrows(IllegalArgumentException.class, () -> SnapshotRules.requireSnapshotId(""));
        assertThrows(IllegalArgumentException.class, () -> SnapshotRules.requireSnapshotId(7));
        assertThrows(IllegalArgumentException.class, () -> SnapshotRules.requireScopeQuery(null));
    }
}
