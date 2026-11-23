package io.github.rkaushikdev.galleyworks.match;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.LOCAL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.RunResult;
import io.github.rkaushikdev.galleyworks.fingerprint.Fingerprinter;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import io.github.rkaushikdev.galleyworks.index.MemoryIndex;
import io.github.rkaushikdev.galleyworks.ingest.EnrolledReader;
import io.github.rkaushikdev.galleyworks.ingest.SourceDocument;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StandardClausesTest {

    private static final String SEVERABILITY = StandardClauses.CLAUSES.get("severability");

    private static RunResult run(EngineConfig config, Instrument... instruments) {
        return new Engine(config).runOver(List.of(instruments));
    }

    private static Instrument wyoming(String body) {
        return makeBill("wy", "HB0142", body, LocalDate.of(2024, 7, 1));
    }

    private static Instrument montana(String body) {
        return makeBill("mt", "HB0311", body, LocalDate.of(2025, 7, 1));
    }

    private static EngineConfig counting() {
        EngineConfig defaults = new EngineConfig();
        return defaults.withMatch(defaults.match().withIgnoreStandardClauses(false));
    }

    /** A bill whose only section is {@code clause}, so nothing else can be shared. */
    private static Instrument closing(String jurisdiction, String number, String clause) {
        String text = "2025\n\nHOUSE BILL NO. " + number + "\n\nAN ACT relating to " + jurisdiction
                + " matters.\n\nSection 1.  " + clause + "\n";
        return new EnrolledReader()
                .read(new SourceDocument(
                        text, number, Map.of("jurisdiction", jurisdiction, "session", "2025", "number", number)));
    }

    @Test
    void aSharedSeverabilityClauseIsNotReuse() {
        RunResult result =
                run(new EngineConfig(), closing("wy", "HB0142", SEVERABILITY), closing("mt", "HB0311", SEVERABILITY));
        assertThat(result.reportFor("mt/2025/lower/HB0311").orElseThrow().matches())
                .isEmpty();
    }

    @Test
    void theSameClauseIsReportedWhenTheFilterIsSwitchedOff() {
        RunResult result =
                run(counting(), closing("wy", "HB0142", SEVERABILITY), closing("mt", "HB0311", SEVERABILITY));
        assertThat(result.reportFor("mt/2025/lower/HB0311").orElseThrow().matches())
                .singleElement()
                .satisfies(match -> assertThat(match.containment()).isGreaterThan(0.9));
    }

    @Test
    void realReuseBesideAStandardClauseIsStillFound() {
        String body = MODEL_CLAUSE + "\n\n(b)  " + SEVERABILITY;
        RunResult result = run(new EngineConfig(), wyoming(body), montana(body));
        assertThat(result.reportFor("mt/2025/lower/HB0311").orElseThrow().matches())
                .isNotEmpty()
                .allSatisfy(match -> assertThat(match.passages())
                        .allSatisfy(passage -> assertThat(passage.queryText()).doesNotContain("severable")));
    }

    @Test
    void everyClauseSuppressesShinglesUnderTheEngineSettings() {
        Set<Long> suppressed = StandardClauses.fingerprints(new EngineConfig());
        assertThat(StandardClauses.CLAUSES)
                .containsKeys("severability", "repealer", "emergency", "savings", "uniformity");
        assertThat(suppressed).hasSizeGreaterThan(StandardClauses.CLAUSES.size() * 5);
        Instrument bill = wyoming(StandardClauses.CLAUSES.get("repealer"));
        assertThat(new Fingerprinter()
                        .fingerprint(bill).stream()
                                .flatMap(record -> record.fingerprintValues().stream())
                                .filter(suppressed::contains))
                .isNotEmpty();
        assertThat(StandardClauses.filter(new EngineConfig()).size()).isEqualTo(suppressed.size());
    }

    @Test
    void ordinaryTextKeepsItsShingles() {
        Set<Long> suppressed = StandardClauses.fingerprints(new EngineConfig());
        assertThat(new Fingerprinter()
                        .fingerprint(wyoming(LOCAL_CLAUSE)).stream()
                                .flatMap(record -> record.fingerprintValues().stream())
                                .filter(suppressed::contains))
                .isEmpty();
    }

    @Test
    void learningKeepsTheStandardClausesSuppressed() {
        Engine engine = new Engine(new EngineConfig(), new MemoryIndex());
        int standard = engine.boilerplate().size();
        engine.add(wyoming(LOCAL_CLAUSE));
        engine.learnBoilerplate(0.5, 1);
        assertThat(engine.boilerplate().size()).isGreaterThan(standard);
        assertThat(engine.boilerplate().suppressed()).containsAll(StandardClauses.fingerprints(new EngineConfig()));
        assertThat(new Engine(counting()).boilerplate().size()).isZero();
    }

    @Test
    void aUnionKeepsTheLearnedCounts() {
        BoilerplateFilter learned = new BoilerplateFilter(Set.of(1L, 2L), 40, 0.2, 9);
        BoilerplateFilter union = learned.union(BoilerplateFilter.of(Set.of(2L, 3L)));
        assertThat(union.suppressed()).containsExactlyInAnyOrder(1L, 2L, 3L);
        assertThat(union.segmentsSeen()).isEqualTo(40);
        assertThat(union.floor()).isEqualTo(9);
    }

    @Test
    void strippingARecordDropsOnlyTheSuppressedShingles() {
        SegmentFingerprint record =
                new Fingerprinter().fingerprint(wyoming(MODEL_CLAUSE)).getLast();
        long first = record.fingerprintValues().getFirst();
        SegmentFingerprint stripped = BoilerplateFilter.of(Set.of(first)).strip(record);
        assertThat(stripped.fingerprintValues())
                .doesNotContain(first)
                .hasSizeLessThan(record.selected().size());
        assertThat(stripped.key()).isEqualTo(record.key());
        assertThat(stripped.signature()).isEqualTo(record.signature());
        assertThat(new BoilerplateFilter().strip(record)).isSameAs(record);
    }
}
