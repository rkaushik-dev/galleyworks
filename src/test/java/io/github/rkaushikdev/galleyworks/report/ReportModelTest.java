package io.github.rkaushikdev.galleyworks.report;

import static io.github.rkaushikdev.galleyworks.report.ReportFixtures.FIXED;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.LOCAL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.fingerprints;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.JurisdictionRegistry;
import io.github.rkaushikdev.galleyworks.MatchConfig;
import io.github.rkaushikdev.galleyworks.Segment;
import io.github.rkaushikdev.galleyworks.index.MemoryIndex;
import io.github.rkaushikdev.galleyworks.ingest.EnrolledReader;
import io.github.rkaushikdev.galleyworks.ingest.SourceDocument;
import io.github.rkaushikdev.galleyworks.match.Explanation;
import io.github.rkaushikdev.galleyworks.match.Matcher;
import io.github.rkaushikdev.galleyworks.match.SegmentMatch;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReportModelTest {

    private final Instrument sourceBill = makeBill("wy", "HB0142", MODEL_CLAUSE);
    private final Instrument probe = makeBill("mt", "HB0311", MODEL_CLAUSE);
    private final List<SegmentMatch> matches = matches();

    private List<SegmentMatch> matches() {
        MemoryIndex index = new MemoryIndex();
        index.addAll(fingerprints(sourceBill));
        return new Matcher(index).matchInstrument(probe);
    }

    private InstrumentReport resolved() {
        return Reports.buildReport(probe, matches, new MappingTextSource(List.of(sourceBill)), FIXED);
    }

    @Test
    void aMappingSourceSatisfiesTheProtocol() {
        assertThat(new MappingTextSource()).isInstanceOf(TextSource.class);
    }

    @Test
    void aMappingSourceFindsWhatItHolds() {
        MappingTextSource source = new MappingTextSource(List.of(sourceBill));
        assertThat(source.textOf(sourceBill.key())).contains(sourceBill.rawText());
        assertThat(source.textOf(makeBill("nd", "HB1", "x").key())).isEmpty();
    }

    @Test
    void aReportQuotesBothSides() {
        InstrumentReport report = resolved();
        assertThat(report.isEmpty()).isFalse();
        ReportedPassage passage = report.matches().getFirst().passages().getFirst();
        assertThat(passage.resolved()).isTrue();
        assertThat(passage.queryText()).isNotEmpty();
        assertThat(passage.otherText()).isNotEmpty();
        assertThat(passage.characters()).isPositive();
    }

    @Test
    void anUnresolvedOtherSideIsFlagged() {
        InstrumentReport report = Reports.buildReport(probe, matches, new MappingTextSource(), FIXED);
        assertThat(report.matches().getFirst().resolved()).isFalse();
        assertThat(report.matches().getFirst().passages().getFirst().otherText())
                .isEmpty();
        assertThat(report.matches().getFirst().passages().getFirst().queryText())
                .isNotEmpty();
    }

    @Test
    void theReportCarriesItsIdentity() {
        InstrumentReport report = resolved();
        assertThat(report.key()).isEqualTo(probe.key());
        assertThat(report.title()).startsWith("AN ACT");
        assertThat(report.generatedAt()).isEqualTo(FIXED);
    }

    @Test
    void theSummaryCountsSegmentsAndCharacters() {
        ReportSummary summary = resolved().summary();
        assertThat(summary.matchedSegments()).isGreaterThanOrEqualTo(1);
        assertThat(summary.totalSegments()).isGreaterThanOrEqualTo(summary.matchedSegments());
        assertThat(summary.reusedCharacters()).isPositive();
        assertThat(summary.otherInstruments()).isEqualTo(1);
        assertThat(summary.reuseRatio()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
    }

    @Test
    void overlappingPassagesAreCountedOnce() {
        InstrumentReport report = resolved();
        int naive = report.matches().stream()
                .flatMap(match -> match.passages().stream())
                .mapToInt(ReportedPassage::characters)
                .sum();
        assertThat(report.summary().reusedCharacters()).isLessThanOrEqualTo(naive);
    }

    @Test
    void anEmptyReport() {
        InstrumentReport report =
                Reports.buildReport(makeBill("co", "SB0007", LOCAL_CLAUSE), List.of(), new MappingTextSource(), FIXED);
        assertThat(report.isEmpty()).isTrue();
        assertThat(report.summary().reusedCharacters()).isZero();
        assertThat(report.summary().reuseRatio()).isEqualTo(0.0);
        assertThat(report.summary().otherInstruments()).isZero();
    }

    @Test
    void scoresAreCarriedThrough() {
        ReportedMatch reported = resolved().matches().getFirst();
        assertThat(reported.containment()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
        assertThat(reported.jaccard()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
        assertThat(reported.characters()).isPositive();
    }

    @Test
    void matchesGroupByTheOtherInstrument() {
        InstrumentReport report = resolved();
        Map<String, List<ReportedMatch>> grouped = Reports.groupByInstrument(report);
        assertThat(grouped.keySet()).containsExactly(sourceBill.key().slug());
        assertThat(grouped.get(sourceBill.key().slug()))
                .hasSize(report.matches().size());
    }

    @Test
    void aSourceCanBeExtendedAfterConstruction() {
        MappingTextSource source = new MappingTextSource();
        source.add(sourceBill);
        assertThat(Reports.buildReport(probe, matches, source, FIXED)
                        .matches()
                        .getFirst()
                        .resolved())
                .isTrue();
    }

    @Test
    void aMappingSourceHandsBackTheWholeInstrument() {
        MappingTextSource source = new MappingTextSource(List.of(sourceBill));
        assertThat(source.instrumentOf(sourceBill.key())).containsSame(sourceBill);
        assertThat(source.instrumentOf(probe.key())).isEmpty();
    }

    @Test
    void aSubsectionTakesTheCatchlineOfItsSection() {
        Segment subsection = probe.bodySegments().stream()
                .filter(segment -> segment.path().printed().equals("1(a)"))
                .findFirst()
                .orElseThrow();
        assertThat(Reports.catchlineOf(probe, subsection.index())).contains("Schedule of appropriations");
        assertThat(Reports.catchlineOf(probe, 999)).isEmpty();
    }

    @Test
    void aSharedCatchlineIsNamedAmongTheReasons() {
        ReportedMatch finding = resolved().matches().getFirst();
        assertThat(finding.reasons()).contains("both sit under the catchline \"Schedule of appropriations\"");
    }

    @Test
    void aDifferentCatchlineIsNotClaimedAsShared() {
        String text = sourceBill.rawText().replace("Schedule of appropriations.", "Appropriation records kept.");
        Instrument retitled = new EnrolledReader()
                .read(new SourceDocument(text, Map.of("jurisdiction", "wy", "session", "2025", "number", "HB0142")));
        InstrumentReport report = Reports.buildReport(probe, matches, new MappingTextSource(List.of(retitled)), FIXED);
        assertThat(report.matches().getFirst().reasons()).noneMatch(reason -> reason.contains("catchline"));
        assertThat(Reports.sharedCatchline(probe, matches.getFirst(), retitled)).isEmpty();
        assertThat(Reports.sharedCatchline(probe, matches.getFirst(), sourceBill))
                .contains("Schedule of appropriations");
    }

    @Test
    void aSharedCatchlineStrengthensTheCase() {
        SegmentMatch match = matches.getFirst();
        Explanation without = Explanation.explain(match, new MatchConfig(), new JurisdictionRegistry());
        Explanation with = Explanation.explain(
                match, new MatchConfig(), new JurisdictionRegistry(), Optional.of("Schedule of appropriations"));
        assertThat(with.confidence().compareTo(without.confidence())).isGreaterThanOrEqualTo(0);
        assertThat(with.reasons()).hasSize(without.reasons().size() + 1);
        assertThat(Reports.buildReport(probe, matches, new MappingTextSource(), FIXED)
                        .matches()
                        .getFirst()
                        .reasons())
                .noneMatch(reason -> reason.contains("catchline"));
    }
}
