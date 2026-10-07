package io.github.halftermeyer.rdfsnapshot.neo4j;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.halftermeyer.rdfsnapshot.model.LpgDuration;
import io.github.halftermeyer.rdfsnapshot.model.LpgPoint;
import io.github.halftermeyer.rdfsnapshot.model.LpgVector;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.neo4j.graphdb.Vector;
import org.neo4j.graphdb.spatial.CRS;
import org.neo4j.graphdb.spatial.Coordinate;
import org.neo4j.graphdb.spatial.Point;

class Neo4jAdapterTest {

    @Test
    void arraysBecomeListsExceptBytes() {
        assertEquals(List.of(1L, 2L), Neo4jAdapter.value(new long[] {1, 2}));
        assertEquals(List.of("a", "b"), Neo4jAdapter.value(new String[] {"a", "b"}));
        assertEquals(List.of("x"), Neo4jAdapter.value(new char[] {'x'}));
        byte[] bytes = {1, 2};
        assertSame(bytes, Neo4jAdapter.value(bytes));
    }

    @Test
    void uuidsPassThrough() {
        UUID uuid = UUID.randomUUID();
        assertSame(uuid, Neo4jAdapter.value(uuid));
        assertEquals(List.of(uuid), Neo4jAdapter.value(new UUID[] {uuid}));
    }

    @Test
    void vectorsAreMarked() {
        Vector v = FakeGraph.proxy(Vector.class, (p, m, a) -> null);
        assertSame(LpgVector.INSTANCE, Neo4jAdapter.value(v));
    }

    @Test
    void durationsByUnit() {
        TemporalAmount d = new TemporalAmount() {
            @Override
            public long get(TemporalUnit unit) {
                return switch ((ChronoUnit) unit) {
                    case MONTHS -> 14;
                    case DAYS -> 3;
                    case SECONDS -> -1;
                    case NANOS -> 500_000_000;
                    default -> throw new IllegalArgumentException();
                };
            }

            @Override
            public List<TemporalUnit> getUnits() {
                return List.of(ChronoUnit.MONTHS, ChronoUnit.DAYS, ChronoUnit.SECONDS, ChronoUnit.NANOS);
            }

            @Override
            public Temporal addTo(Temporal temporal) {
                return temporal;
            }

            @Override
            public Temporal subtractFrom(Temporal temporal) {
                return temporal;
            }
        };
        assertEquals(new LpgDuration(14, 3, -1, 500_000_000), Neo4jAdapter.value(d));
    }

    @Test
    void points() {
        CRS crs = FakeGraph.proxy(CRS.class, (p, m, a) -> m.getName().equals("getCode") ? 4979 : null);
        Point point = FakeGraph.proxy(Point.class, (p, m, a) -> switch (m.getName()) {
            case "getCRS" -> crs;
            case "getCoordinate" -> new Coordinate(2.35, 48.85, 35);
            default -> null;
        });
        LpgPoint converted = (LpgPoint) Neo4jAdapter.value(point);
        assertEquals(LpgPoint.Crs.WGS84_3D, converted.crs());
        assertArrayEquals(new double[] {2.35, 48.85, 35}, converted.coordinates());
    }
}
