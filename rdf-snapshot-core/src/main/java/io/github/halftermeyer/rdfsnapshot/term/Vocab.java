package io.github.halftermeyer.rdfsnapshot.term;

/** Namespaces and terms used by the snapshot format (SPEC §6). */
public final class Vocab {
    private Vocab() {}

    /** The {@code lpg:} meta-vocabulary namespace. Placeholder (SPEC §6, §12): change it here only. */
    public static final String LPG = "https://example.org/lpg#";

    public static final String RDF = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    public static final String XSD = "http://www.w3.org/2001/XMLSchema#";
    public static final String PROV = "http://www.w3.org/ns/prov#";
    public static final String GEO = "http://www.opengis.net/ont/geosparql#";

    // rdf:
    public static final Iri RDF_TYPE = new Iri(RDF + "type");
    public static final Iri RDF_FIRST = new Iri(RDF + "first");
    public static final Iri RDF_REST = new Iri(RDF + "rest");
    public static final Iri RDF_NIL = new Iri(RDF + "nil");

    // xsd:
    public static final Iri XSD_STRING = xsd("string");
    public static final Iri XSD_INTEGER = xsd("integer");
    public static final Iri XSD_DOUBLE = xsd("double");
    public static final Iri XSD_BOOLEAN = xsd("boolean");
    public static final Iri XSD_DATE = xsd("date");
    public static final Iri XSD_TIME = xsd("time");
    public static final Iri XSD_DATE_TIME = xsd("dateTime");
    public static final Iri XSD_DURATION = xsd("duration");
    public static final Iri XSD_BASE64_BINARY = xsd("base64Binary");

    // geo:, prov:
    public static final Iri GEO_WKT_LITERAL = new Iri(GEO + "wktLiteral");
    public static final Iri PROV_GENERATED_AT_TIME = new Iri(PROV + "generatedAtTime");

    // lpg: records
    public static final Iri LPG_NODE = lpg("Node");
    public static final Iri LPG_RELATIONSHIP = lpg("Relationship");
    public static final Iri LPG_ELEMENT_ID = lpg("elementId");
    public static final Iri LPG_LABEL = lpg("label");
    public static final Iri LPG_TYPE = lpg("type");
    public static final Iri LPG_SOURCE = lpg("source");
    public static final Iri LPG_TARGET = lpg("target");

    // lpg: graph vocabulary
    public static final Iri LPG_LABEL_CLASS = lpg("Label");
    public static final Iri LPG_RELATIONSHIP_TYPE = lpg("RelationshipType");
    public static final Iri LPG_PROPERTY_KEY = lpg("PropertyKey");
    public static final Iri LPG_NAME = lpg("name");

    // lpg: values
    public static final Iri LPG_UUID = lpg("uuid");
    public static final Iri LPG_CARTESIAN_2D = lpg("Cartesian2D");
    public static final Iri LPG_CARTESIAN_3D = lpg("Cartesian3D");

    // lpg: snapshot
    public static final Iri LPG_SNAPSHOT = lpg("Snapshot");
    public static final Iri LPG_DATABASE = lpg("database");
    public static final Iri LPG_SCOPE = lpg("scope");

    private static Iri xsd(String local) {
        return new Iri(XSD + local);
    }

    private static Iri lpg(String local) {
        return new Iri(LPG + local);
    }
}
