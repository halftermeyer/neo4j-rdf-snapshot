package io.github.halftermeyer.rdfsnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.halftermeyer.rdfsnapshot.model.LpgDuration;
import io.github.halftermeyer.rdfsnapshot.model.LpgPoint;
import io.github.halftermeyer.rdfsnapshot.model.LpgVector;
import io.github.halftermeyer.rdfsnapshot.term.Iri;
import io.github.halftermeyer.rdfsnapshot.term.Literal;
import io.github.halftermeyer.rdfsnapshot.term.Vocab;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValueMapperTest {

    private static Literal single(Object value) {
        return assertInstanceOf(ValueMapper.Single.class, ValueMapper.map(value)).literal();
    }

    private static void assertLiteral(String lexical, Iri datatype, Object value) {
        assertEquals(new Literal(lexical, datatype), single(value));
    }

    @Test
    void strings() {
        assertLiteral("P-001", Vocab.XSD_STRING, "P-001");
        assertLiteral("", Vocab.XSD_STRING, "");
    }

    @Test
    void integers() {
        assertLiteral("4", Vocab.XSD_INTEGER, 4L);
        assertLiteral("-9223372036854775808", Vocab.XSD_INTEGER, Long.MIN_VALUE);
        assertLiteral("7", Vocab.XSD_INTEGER, 7);
        assertLiteral("-3", Vocab.XSD_INTEGER, (short) -3);
        assertLiteral("8", Vocab.XSD_INTEGER, (byte) 8);
    }

    @Test
    void doublesUseCanonicalForm() {
        assertLiteral("1.2E0", Vocab.XSD_DOUBLE, 1.2);
        assertLiteral("4.0E0", Vocab.XSD_DOUBLE, 4.0);
        assertLiteral("1.0E2", Vocab.XSD_DOUBLE, 100.0);
        assertLiteral("-1.25E-3", Vocab.XSD_DOUBLE, -0.00125);
        assertLiteral("1.7976931348623157E308", Vocab.XSD_DOUBLE, Double.MAX_VALUE);
        assertLiteral("4.9E-324", Vocab.XSD_DOUBLE, Double.MIN_VALUE);
        assertLiteral("0.0E0", Vocab.XSD_DOUBLE, 0.0);
        assertLiteral("-0.0E0", Vocab.XSD_DOUBLE, -0.0);
        assertLiteral("NaN", Vocab.XSD_DOUBLE, Double.NaN);
        assertLiteral("INF", Vocab.XSD_DOUBLE, Double.POSITIVE_INFINITY);
        assertLiteral("-INF", Vocab.XSD_DOUBLE, Double.NEGATIVE_INFINITY);
        assertLiteral("1.1E0", Vocab.XSD_DOUBLE, 1.1f);
    }

    @Test
    void booleans() {
        assertLiteral("true", Vocab.XSD_BOOLEAN, true);
        assertLiteral("false", Vocab.XSD_BOOLEAN, false);
    }

    @Test
    void dates() {
        assertLiteral("2026-10-05", Vocab.XSD_DATE, LocalDate.of(2026, 10, 5));
        assertLiteral("0033-01-01", Vocab.XSD_DATE, LocalDate.of(33, 1, 1));
        assertLiteral("12345-01-01", Vocab.XSD_DATE, LocalDate.of(12345, 1, 1));
        assertLiteral("-0044-03-15", Vocab.XSD_DATE, LocalDate.of(-44, 3, 15));
    }

    @Test
    void localTimesHaveNoOffset() {
        assertLiteral("10:15:00", Vocab.XSD_TIME, LocalTime.of(10, 15));
        assertLiteral("10:15:30.5", Vocab.XSD_TIME, LocalTime.of(10, 15, 30, 500_000_000));
        assertLiteral("00:00:00.000000001", Vocab.XSD_TIME, LocalTime.of(0, 0, 0, 1));
    }

    @Test
    void zonedTimesKeepOffset() {
        assertLiteral("10:15:00+02:00", Vocab.XSD_TIME, OffsetTime.of(10, 15, 0, 0, ZoneOffset.ofHours(2)));
        assertLiteral("10:15:00Z", Vocab.XSD_TIME, OffsetTime.of(10, 15, 0, 0, ZoneOffset.UTC));
        assertLiteral("10:15:00-05:30", Vocab.XSD_TIME,
                OffsetTime.of(10, 15, 0, 0, ZoneOffset.ofHoursMinutes(-5, -30)));
    }

    @Test
    void offsetSecondsAreDropped() {
        assertLiteral("10:15:00+01:00", Vocab.XSD_TIME,
                OffsetTime.of(10, 15, 0, 0, ZoneOffset.ofHoursMinutesSeconds(1, 0, 30)));
    }

    @Test
    void localDateTimes() {
        assertLiteral("2026-10-05T14:03:11", Vocab.XSD_DATE_TIME, LocalDateTime.of(2026, 10, 5, 14, 3, 11));
        assertLiteral("2026-10-05T14:03:00.123", Vocab.XSD_DATE_TIME,
                LocalDateTime.of(2026, 10, 5, 14, 3, 0, 123_000_000));
    }

    @Test
    void zonedDateTimesKeepOffsetAndDropZoneName() {
        assertLiteral("2026-10-05T14:03:11+02:00", Vocab.XSD_DATE_TIME,
                ZonedDateTime.of(2026, 10, 5, 14, 3, 11, 0, ZoneId.of("Europe/Paris")));
        assertLiteral("2026-01-05T14:03:11+01:00", Vocab.XSD_DATE_TIME,
                ZonedDateTime.of(2026, 1, 5, 14, 3, 11, 0, ZoneId.of("Europe/Paris")));
        assertLiteral("2026-10-05T14:03:11Z", Vocab.XSD_DATE_TIME,
                OffsetDateTime.of(2026, 10, 5, 14, 3, 11, 0, ZoneOffset.UTC));
    }

    @Test
    void durations() {
        assertLiteral("P1Y2M3DT4H5M6.5S", Vocab.XSD_DURATION, new LpgDuration(14, 3, 4 * 3600 + 5 * 60 + 6, 500_000_000));
        assertLiteral("PT0S", Vocab.XSD_DURATION, new LpgDuration(0, 0, 0, 0));
        assertLiteral("P1M", Vocab.XSD_DURATION, new LpgDuration(1, 0, 0, 0));
        assertLiteral("PT1M", Vocab.XSD_DURATION, new LpgDuration(0, 0, 60, 0));
        assertLiteral("PT0.000000001S", Vocab.XSD_DURATION, new LpgDuration(0, 0, 0, 1));
        assertLiteral("-P2DT1H", Vocab.XSD_DURATION, new LpgDuration(0, -2, -3600, 0));
        // Neo4j keeps nanos in [0, 1e9): -0.5 s is seconds = -1, nanos = 500 000 000
        assertLiteral("-PT0.5S", Vocab.XSD_DURATION, new LpgDuration(0, 0, -1, 500_000_000));
        assertLiteral("P1DT25H", Vocab.XSD_DURATION, new LpgDuration(0, 1, 25 * 3600, 0));
    }

    @Test
    void mixedSignDurationsAreSkipped() {
        assertSame(ValueMapper.Skipped.INSTANCE, ValueMapper.map(new LpgDuration(1, -1, 0, 0)));
        assertSame(ValueMapper.Skipped.INSTANCE, ValueMapper.map(new LpgDuration(0, 1, -1, 0)));
    }

    @Test
    void points() {
        assertLiteral("<http://www.opengis.net/def/crs/OGC/1.3/CRS84> POINT(2.35 48.85)", Vocab.GEO_WKT_LITERAL,
                new LpgPoint(LpgPoint.Crs.WGS84_2D, new double[] {2.35, 48.85}));
        assertLiteral("<http://www.opengis.net/def/crs/OGC/0/CRS84h> POINT Z(2.35 48.85 35)", Vocab.GEO_WKT_LITERAL,
                new LpgPoint(LpgPoint.Crs.WGS84_3D, new double[] {2.35, 48.85, 35.0}));
        assertLiteral("POINT(1 -2.5)", Vocab.GEO_WKT_LITERAL,
                new LpgPoint(LpgPoint.Crs.CARTESIAN_2D, new double[] {1.0, -2.5}));
        assertLiteral("POINT Z(0 0.0000001 10000000)", Vocab.GEO_WKT_LITERAL,
                new LpgPoint(LpgPoint.Crs.CARTESIAN_3D, new double[] {-0.0, 1e-7, 1e7}));
    }

    @Test
    void byteArrays() {
        assertLiteral("AAEC/w==", Vocab.XSD_BASE64_BINARY, new byte[] {0, 1, 2, (byte) 255});
        assertLiteral("", Vocab.XSD_BASE64_BINARY, new byte[0]);
    }

    @Test
    void listsKeepOrderAndDuplicates() {
        var list = assertInstanceOf(ValueMapper.Collection.class, ValueMapper.map(List.of("steel", "EU", "steel")));
        assertEquals(List.of(Literal.string("steel"), Literal.string("EU"), Literal.string("steel")), list.elements());
    }

    @Test
    void emptyListIsAnEmptyCollection() {
        var list = assertInstanceOf(ValueMapper.Collection.class, ValueMapper.map(List.of()));
        assertEquals(List.of(), list.elements());
    }

    @Test
    void vectorsAreSkipped() {
        assertSame(ValueMapper.Skipped.INSTANCE, ValueMapper.map(LpgVector.INSTANCE));
        assertEquals(false, ValueMapper.isSerialized(LpgVector.INSTANCE));
    }

    @Test
    void listContainingSkippedValueIsSkipped() {
        assertSame(ValueMapper.Skipped.INSTANCE,
                ValueMapper.map(List.of(new LpgDuration(0, 0, 1, 0), new LpgDuration(1, -1, 0, 0))));
    }

    @Test
    void rejectsUnsupportedValues() {
        assertThrows(IllegalArgumentException.class, () -> ValueMapper.map(Map.of()));
        assertThrows(IllegalArgumentException.class, () -> ValueMapper.map(List.of(List.of(1L))));
        assertThrows(IllegalArgumentException.class, () -> ValueMapper.map(null));
    }
}
