package io.github.halftermeyer.rdfsnapshot;

import io.github.halftermeyer.rdfsnapshot.model.LpgDuration;
import io.github.halftermeyer.rdfsnapshot.model.LpgPoint;
import io.github.halftermeyer.rdfsnapshot.model.LpgVector;
import io.github.halftermeyer.rdfsnapshot.term.Iri;
import io.github.halftermeyer.rdfsnapshot.term.Literal;
import io.github.halftermeyer.rdfsnapshot.term.Vocab;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Maps property values to RDF literals (SPEC §7.4).
 *
 * <p>Accepted Java types:
 * <table>
 *   <tr><td>{@code STRING}</td><td>{@link String}</td></tr>
 *   <tr><td>{@code INTEGER}</td><td>{@link Long}, {@link Integer}, {@link Short}, {@link Byte}</td></tr>
 *   <tr><td>{@code FLOAT}</td><td>{@link Double}, {@link Float}</td></tr>
 *   <tr><td>{@code BOOLEAN}</td><td>{@link Boolean}</td></tr>
 *   <tr><td>{@code DATE}</td><td>{@link LocalDate}</td></tr>
 *   <tr><td>{@code LOCAL TIME}</td><td>{@link LocalTime}</td></tr>
 *   <tr><td>{@code ZONED TIME}</td><td>{@link OffsetTime}</td></tr>
 *   <tr><td>{@code LOCAL DATETIME}</td><td>{@link LocalDateTime}</td></tr>
 *   <tr><td>{@code ZONED DATETIME}</td><td>{@link ZonedDateTime}, {@link OffsetDateTime}</td></tr>
 *   <tr><td>{@code DURATION}</td><td>{@link LpgDuration}</td></tr>
 *   <tr><td>{@code POINT}</td><td>{@link LpgPoint}</td></tr>
 *   <tr><td>{@code UUID}</td><td>{@link UUID}</td></tr>
 *   <tr><td>byte array</td><td>{@code byte[]}</td></tr>
 *   <tr><td>{@code LIST<T>}</td><td>{@link List} of any of the scalar types above</td></tr>
 *   <tr><td>{@code VECTOR}</td><td>{@link LpgVector} (not serialized)</td></tr>
 * </table>
 */
public final class ValueMapper {
    static final String CRS84 = "<http://www.opengis.net/def/crs/OGC/1.3/CRS84> ";
    static final String CRS84H = "<http://www.opengis.net/def/crs/OGC/0/CRS84h> ";
    static final String CARTESIAN_2D = "<" + Vocab.LPG_CARTESIAN_2D.value() + "> ";
    static final String CARTESIAN_3D = "<" + Vocab.LPG_CARTESIAN_3D.value() + "> ";

    private ValueMapper() {}

    /** The RDF form of a property value. */
    public sealed interface Mapped permits Single, Collection, Skipped {}

    /** One literal. */
    public record Single(Literal literal) implements Mapped {}

    /** An RDF collection; {@code rdf:nil} when empty. */
    public record Collection(List<Literal> elements) implements Mapped {
        public Collection {
            elements = List.copyOf(elements);
        }
    }

    /** The property is not serialized: a {@code VECTOR}, or a value with no RDF form (see {@link #map}). */
    public enum Skipped implements Mapped {
        INSTANCE
    }

    /** Whether a property with this value appears in the snapshot. */
    public static boolean isSerialized(Object value) {
        return !(map(value) instanceof Skipped);
    }

    /**
     * Maps a property value.
     *
     * <p>Skipped: {@link LpgVector}, durations whose components have mixed signs (SPEC §11), and
     * lists containing such a value.
     *
     * @throws IllegalArgumentException for {@code null}, unsupported types and nested lists
     */
    public static Mapped map(Object value) {
        if (value instanceof List<?> list) {
            List<Literal> elements = new ArrayList<>(list.size());
            for (Object element : list) {
                if (element instanceof List<?>) {
                    throw new IllegalArgumentException("Nested lists are not property values");
                }
                Literal literal = scalar(element);
                if (literal == null) {
                    return Skipped.INSTANCE;
                }
                elements.add(literal);
            }
            return new Collection(elements);
        }
        Literal literal = scalar(value);
        return literal == null ? Skipped.INSTANCE : new Single(literal);
    }

    /** @return the literal, or {@code null} if the value is not serialized */
    private static Literal scalar(Object value) {
        return switch (value) {
            case null -> throw new IllegalArgumentException("null is not a property value");
            case String s -> Literal.string(s);
            case Long l -> lit(Long.toString(l), Vocab.XSD_INTEGER);
            case Integer i -> lit(Integer.toString(i), Vocab.XSD_INTEGER);
            case Short s -> lit(Short.toString(s), Vocab.XSD_INTEGER);
            case Byte b -> lit(Byte.toString(b), Vocab.XSD_INTEGER);
            case Double d -> lit(xsdDouble(d), Vocab.XSD_DOUBLE);
            // Float.toString keeps the shortest decimal form of the float, not of its widened double
            case Float f -> lit(xsdDouble(Double.parseDouble(Float.toString(f))), Vocab.XSD_DOUBLE);
            case Boolean b -> lit(b.toString(), Vocab.XSD_BOOLEAN);
            case LocalDate d -> lit(date(d), Vocab.XSD_DATE);
            case LocalTime t -> lit(time(t), Vocab.XSD_TIME);
            case OffsetTime t -> lit(time(t.toLocalTime()) + offset(t.getOffset()), Vocab.XSD_TIME);
            case LocalDateTime dt -> lit(dateTime(dt), Vocab.XSD_DATE_TIME);
            case OffsetDateTime dt ->
                lit(dateTime(dt.toLocalDateTime()) + offset(dt.getOffset()), Vocab.XSD_DATE_TIME);
            case ZonedDateTime dt ->
                lit(dateTime(dt.toLocalDateTime()) + offset(dt.getOffset()), Vocab.XSD_DATE_TIME);
            case LpgDuration d -> {
                String lexical = duration(d);
                yield lexical == null ? null : lit(lexical, Vocab.XSD_DURATION);
            }
            case LpgPoint p -> lit(wkt(p), Vocab.GEO_WKT_LITERAL);
            case byte[] bytes -> lit(Base64.getEncoder().encodeToString(bytes), Vocab.XSD_BASE64_BINARY);
            // UUID.toString() is the lower-case 8-4-4-4-12 form (SPEC §6)
            case UUID uuid -> lit(uuid.toString(), Vocab.LPG_UUID);
            case LpgVector v -> null;
            default -> throw new IllegalArgumentException(
                    "Unsupported property value type: " + value.getClass().getName());
        };
    }

    private static Literal lit(String lexical, Iri datatype) {
        return new Literal(lexical, datatype);
    }

    /** Canonical {@code xsd:double} lexical form: {@code 1.2E0}, {@code 1.0E2}, {@code INF}, {@code NaN}. */
    static String xsdDouble(double d) {
        if (Double.isNaN(d)) {
            return "NaN";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "INF" : "-INF";
        }
        if (d == 0) {
            return 1 / d < 0 ? "-0.0E0" : "0.0E0";
        }
        // Double.toString is the shortest decimal that round-trips
        BigDecimal bd = new BigDecimal(Double.toString(d)).stripTrailingZeros();
        String digits = bd.unscaledValue().abs().toString();
        int exponent = digits.length() - 1 - bd.scale();
        String fraction = digits.length() > 1 ? digits.substring(1) : "0";
        return (bd.signum() < 0 ? "-" : "") + digits.charAt(0) + "." + fraction + "E" + exponent;
    }

    /** {@code xsd:date}: at least four year digits, no {@code +} sign. */
    static String date(LocalDate d) {
        int year = d.getYear();
        String y = year < 0 ? "-" + pad(-(long) year, 4) : pad(year, 4);
        return y + "-" + pad(d.getMonthValue(), 2) + "-" + pad(d.getDayOfMonth(), 2);
    }

    /** {@code xsd:time} without offset: seconds always present, fraction without trailing zeros. */
    static String time(LocalTime t) {
        return pad(t.getHour(), 2) + ":" + pad(t.getMinute(), 2) + ":" + pad(t.getSecond(), 2)
                + fraction(t.getNano());
    }

    static String dateTime(LocalDateTime dt) {
        return date(dt.toLocalDate()) + "T" + time(dt.toLocalTime());
    }

    /** {@code Z} for UTC, otherwise {@code ±hh:mm}. XSD has no seconds in offsets: they are dropped. */
    static String offset(ZoneOffset offset) {
        int total = offset.getTotalSeconds();
        if (total == 0) {
            return "Z";
        }
        int abs = Math.abs(total);
        return (total < 0 ? "-" : "+") + pad(abs / 3600, 2) + ":" + pad((abs / 60) % 60, 2);
    }

    /**
     * {@code xsd:duration}. Seconds and nanoseconds are combined first; if months, days and
     * seconds then have mixed signs, there is no {@code xsd:duration} for the value: returns
     * {@code null}.
     */
    static String duration(LpgDuration d) {
        BigInteger nanos = BigInteger.valueOf(d.seconds())
                .multiply(BigInteger.valueOf(1_000_000_000L))
                .add(BigInteger.valueOf(d.nanos()));
        int sMonths = Long.signum(d.months());
        int sDays = Long.signum(d.days());
        int sNanos = nanos.signum();
        boolean negative = sMonths < 0 || sDays < 0 || sNanos < 0;
        boolean positive = sMonths > 0 || sDays > 0 || sNanos > 0;
        if (negative && positive) {
            return null;
        }
        BigInteger months = BigInteger.valueOf(d.months()).abs();
        BigInteger days = BigInteger.valueOf(d.days()).abs();
        nanos = nanos.abs();

        BigInteger[] yearsMonths = months.divideAndRemainder(BigInteger.valueOf(12));
        BigInteger[] secondsNanos = nanos.divideAndRemainder(BigInteger.valueOf(1_000_000_000L));
        BigInteger seconds = secondsNanos[0];
        BigInteger hours = seconds.divide(BigInteger.valueOf(3600));
        BigInteger minutes = seconds.mod(BigInteger.valueOf(3600)).divide(BigInteger.valueOf(60));
        BigInteger secs = seconds.mod(BigInteger.valueOf(60));
        int frac = secondsNanos[1].intValue();

        StringBuilder out = new StringBuilder();
        if (negative) {
            out.append('-');
        }
        out.append('P');
        appendIfNonZero(out, yearsMonths[0], 'Y');
        appendIfNonZero(out, yearsMonths[1], 'M');
        appendIfNonZero(out, days, 'D');
        if (hours.signum() != 0 || minutes.signum() != 0 || secs.signum() != 0 || frac != 0) {
            out.append('T');
            appendIfNonZero(out, hours, 'H');
            appendIfNonZero(out, minutes, 'M');
            if (secs.signum() != 0 || frac != 0) {
                out.append(secs).append(fraction(frac)).append('S');
            }
        }
        if (out.length() == (negative ? 2 : 1)) {
            out.append("T0S");
        }
        return out.toString();
    }

    /**
     * {@code geo:wktLiteral} with a CRS IRI (SPEC §7.5): CRS84 / CRS84h for WGS-84 2D / 3D,
     * {@code lpg:Cartesian2D} / {@code lpg:Cartesian3D} for Cartesian points. Coordinates in plain
     * decimal notation.
     */
    static String wkt(LpgPoint p) {
        double[] c = p.coordinates();
        StringBuilder coords = new StringBuilder();
        for (int i = 0; i < c.length; i++) {
            if (i > 0) {
                coords.append(' ');
            }
            coords.append(plainDecimal(c[i]));
        }
        String shape = (c.length == 3 ? "POINT Z(" : "POINT(") + coords + ")";
        return switch (p.crs()) {
            case WGS84_2D -> CRS84 + shape;
            case WGS84_3D -> CRS84H + shape;
            case CARTESIAN_2D -> CARTESIAN_2D + shape;
            case CARTESIAN_3D -> CARTESIAN_3D + shape;
        };
    }

    private static String plainDecimal(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("Point coordinates must be finite: " + d);
        }
        if (d == 0) {
            return "0";
        }
        return new BigDecimal(Double.toString(d)).stripTrailingZeros().toPlainString();
    }

    private static void appendIfNonZero(StringBuilder out, BigInteger n, char designator) {
        if (n.signum() != 0) {
            out.append(n).append(designator);
        }
    }

    private static String fraction(int nanos) {
        if (nanos == 0) {
            return "";
        }
        String digits = pad(nanos, 9);
        int end = digits.length();
        while (digits.charAt(end - 1) == '0') {
            end--;
        }
        return "." + digits.substring(0, end);
    }

    private static String pad(long n, int width) {
        String s = Long.toString(n);
        return s.length() >= width ? s : "0".repeat(width - s.length()) + s;
    }
}
