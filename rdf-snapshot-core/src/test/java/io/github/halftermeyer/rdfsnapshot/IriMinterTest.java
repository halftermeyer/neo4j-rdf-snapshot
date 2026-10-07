package io.github.halftermeyer.rdfsnapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class IriMinterTest {
    private final IriMinter iris = new IriMinter("http://acme.org/plm");

    @Test
    void patterns() {
        assertEquals("http://acme.org/plm/e/4:a1b2:12", iris.element("4:a1b2:12").value());
        assertEquals("http://acme.org/plm/label/Part", iris.label("Part").value());
        assertEquals("http://acme.org/plm/type/USES", iris.type("USES").value());
        assertEquals("http://acme.org/plm/prop/partNumber", iris.propertyKey("partNumber").value());
        assertEquals("http://acme.org/plm/snapshot/20261005T140311Z", iris.snapshot("20261005T140311Z").value());
    }

    @Test
    void trailingSlashOfBaseIsIgnored() {
        assertEquals("http://acme.org/plm/e/1", new IriMinter("http://acme.org/plm/").element("1").value());
    }

    @Test
    void separateNamespaces() {
        assertNotEquals(iris.label("Name"), iris.propertyKey("Name"));
        assertNotEquals(iris.label("Name"), iris.type("Name"));
    }

    @Test
    void colonAndUnreservedAreKept() {
        assertEquals("a:b-c.d_e~f09AZ", IriMinter.encodeSegment("a:b-c.d_e~f09AZ"));
    }

    @Test
    void reservedAndUnsafeAreEncoded() {
        assertEquals("has%20space", IriMinter.encodeSegment("has space"));
        assertEquals("a%2Fb", IriMinter.encodeSegment("a/b"));
        assertEquals("a%23b", IriMinter.encodeSegment("a#b"));
        assertEquals("a%3Fb", IriMinter.encodeSegment("a?b"));
        assertEquals("100%25", IriMinter.encodeSegment("100%"));
        assertEquals("a%40b%21%24%26%27%28%29%2A%2B%2C%3B%3D", IriMinter.encodeSegment("a@b!$&'()*+,;="));
        assertEquals("%3C%3E%22%7B%7D%7C%5E%60%5C", IriMinter.encodeSegment("<>\"{}|^`\\"));
        assertEquals("%0A%09", IriMinter.encodeSegment("\n\t"));
    }

    @Test
    void nonAsciiIsUtf8PercentEncoded() {
        assertEquals("Pi%C3%A8ce", IriMinter.encodeSegment("Pièce"));
        assertEquals("%E9%83%A8%E5%93%81", IriMinter.encodeSegment("部品"));
        assertEquals("%F0%9F%94%A9", IriMinter.encodeSegment("🔩"));
    }

    @Test
    void emptyName() {
        assertEquals("", IriMinter.encodeSegment(""));
    }
}
