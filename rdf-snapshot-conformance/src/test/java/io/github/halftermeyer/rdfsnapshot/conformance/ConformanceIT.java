package io.github.halftermeyer.rdfsnapshot.conformance;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

/** Runs every case of {@code conformance/} and compares the output to {@code expected.trig}. */
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
            save(c, "nq", nquads);
            DatasetGraph expected = parse(Files.readString(c.expected(), StandardCharsets.UTF_8), Lang.TRIG);
            assertIsomorphic(c, "N-Quads output", expected, parse(nquads, Lang.NQUADS));
        }));
    }

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
