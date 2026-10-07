package io.github.halftermeyer.rdfsnapshot.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.util.IsoMatcher;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * Runs every case of {@code conformance/}. For each case: the N-Quads and the TriG output are each
 * isomorphic to {@code expected.trig} (after normalization), isomorphic to each other, and
 * byte-identical across two runs. The last two checks mask {@code prov:generatedAtTime}, the
 * export instant, which differs between exports.
 */
class ConformanceIT {
    private static final Path CASES = Path.of(System.getProperty("conformance.dir"));
    private static final Path OUTPUT = Path.of(System.getProperty("output.dir"));

    @TestFactory
    Stream<DynamicTest> conformance() {
        CaseRunner runner = new CaseRunner(Neo4jFixture.driver());
        List<ConformanceCase> cases = ConformanceCase.all(CASES);
        return cases.stream().map(c -> DynamicTest.dynamicTest(c.name(), () -> {
            runner.load(c);
            String nquads = runner.export(c, "nquads");
            String trig = runner.export(c, "trig");
            save(c, "nq", nquads);
            save(c, "trig", trig);
            DatasetGraph expected = parse(Files.readString(c.expected(), StandardCharsets.UTF_8), Lang.TRIG);
            DatasetGraph fromNQuads = parse(nquads, Lang.NQUADS);
            DatasetGraph fromTrig = parse(trig, Lang.TRIG);

            assertIsomorphic(c, "N-Quads output", expected, fromNQuads);
            assertIsomorphic(c, "TriG output", expected, fromTrig);
            if (!IsoMatcher.isomorphic(parse(maskTime(nquads), Lang.NQUADS), parse(maskTime(trig), Lang.TRIG))) {
                fail(c.name() + ": N-Quads and TriG outputs are not isomorphic");
            }
            assertEquals(maskTime(nquads), maskTime(runner.export(c, "nquads")), c.name() + ": N-Quads differs between runs");
            assertEquals(maskTime(trig), maskTime(runner.export(c, "trig")), c.name() + ": TriG differs between runs");
        }));
    }

    /** Replaces the {@code prov:generatedAtTime} value with a constant, keeping a valid literal. */
    static String maskTime(String text) {
        return GENERATED_AT.matcher(text).replaceAll("$1\"2000-01-01T00:00:00Z\"^^");
    }

    private static final Pattern GENERATED_AT =
            Pattern.compile("((?:<http://www\\.w3\\.org/ns/prov#generatedAtTime>|prov:generatedAtTime) )\"[^\"]*\"\\^\\^");

    static DatasetGraph parse(String text, Lang lang) {
        DatasetGraph dsg = DatasetGraphFactory.create();
        RDFParser.create().source(new StringReader(text)).lang(lang).strict(true).parse(dsg);
        return dsg;
    }

    static void assertIsomorphic(ConformanceCase c, String what, DatasetGraph expected, DatasetGraph actual) {
        DatasetGraph e = Normalizer.normalize(expected, CaseRunner.BASE);
        DatasetGraph a = Normalizer.normalize(actual, CaseRunner.BASE);
        if (!IsoMatcher.isomorphic(e, a)) {
            fail(c.name() + ": " + what + " is not isomorphic to expected.trig (after normalization)\n"
                    + "--- expected\n" + sortedNQuads(e) + "--- actual\n" + sortedNQuads(a));
        }
    }

    private static String sortedNQuads(DatasetGraph dsg) {
        StringWriter w = new StringWriter();
        RDFDataMgr.write(w, dsg, RDFFormat.NQUADS);
        return w.toString().lines().sorted().reduce("", (x, y) -> x + y + "\n");
    }

    static void save(ConformanceCase c, String extension, String text) throws IOException {
        Files.createDirectories(OUTPUT);
        Files.writeString(OUTPUT.resolve(c.name() + "." + extension), text, StandardCharsets.UTF_8);
    }
}
