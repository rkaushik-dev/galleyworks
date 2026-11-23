package io.github.rkaushikdev.galleyworks.report;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_FINDINGS;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static io.github.rkaushikdev.galleyworks.support.GoldenCorpus.goldenInstruments;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.ReportConfig;
import io.github.rkaushikdev.galleyworks.match.Confidence;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RankingTest {

    @TempDir
    Path tmp;

    private static ReportedMatch match(Confidence confidence) {
        return match(confidence, "wy", "1");
    }

    private static ReportedMatch match(Confidence confidence, String jurisdiction, String path) {
        return new ReportedMatch(
                "1(a)",
                new InstrumentKey(jurisdiction, "2025", Chamber.LOWER, "HB0142"),
                path,
                0.5,
                0.5,
                List.of(),
                confidence,
                "h",
                List.of("r"));
    }

    private static InstrumentReport synthetic(ReportedMatch... matches) {
        return new InstrumentReport(
                new InstrumentKey("mt", "2025", Chamber.LOWER, "HB0311"),
                "t",
                ReportFixtures.FIXED,
                new ReportSummary(1, 2, 100, 1000, 1),
                List.of(matches));
    }

    private static InstrumentReport filtered(
            InstrumentReport report, Optional<Confidence> floor, boolean crossingOnly, Optional<Integer> limit) {
        return Ranking.filterReport(report, new ReportFilter(floor, crossingOnly, Set.of(), limit));
    }

    @Test
    void strongFindingsComeFirst() {
        List<ReportedMatch> ordered =
                Ranking.rank(List.of(match(Confidence.WEAK), match(Confidence.STRONG, "wy", "2")));
        assertThat(ordered).extracting(ReportedMatch::confidence).containsExactly(Confidence.STRONG, Confidence.WEAK);
    }

    @Test
    void theOrderIsStable() {
        List<ReportedMatch> matches = IntStream.range(0, 5)
                .mapToObj(n -> match(Confidence.MODERATE, "wy", String.valueOf(n)))
                .toList();
        assertThat(Ranking.rank(matches)).isEqualTo(Ranking.rank(new ArrayList<>(matches.reversed())));
    }

    @Test
    void rankReportReordersInPlace() {
        InstrumentReport report = synthetic(match(Confidence.WEAK), match(Confidence.STRONG, "wy", "2"));
        assertThat(Ranking.rankReport(report).matches().getFirst().confidence()).isEqualTo(Confidence.STRONG);
        assertThat(Ranking.rankReport(report).summary()).isEqualTo(report.summary());
    }

    @Test
    void aConfidenceFloorDropsTheWeakOnes() {
        InstrumentReport report = synthetic(match(Confidence.WEAK), match(Confidence.STRONG, "wy", "2"));
        InstrumentReport narrowed = filtered(report, Optional.of(Confidence.STRONG), false, Optional.empty());
        assertThat(narrowed.matches()).hasSize(1);
        assertThat(narrowed.matches().getFirst().confidence()).isEqualTo(Confidence.STRONG);
    }

    @Test
    void moderateAdmitsStrong() {
        InstrumentReport report = synthetic(match(Confidence.STRONG), match(Confidence.MODERATE, "wy", "2"));
        assertThat(filtered(report, Optional.of(Confidence.MODERATE), false, Optional.empty())
                        .matches())
                .hasSize(2);
    }

    @Test
    void crossingOnlyDropsSameJurisdictionFindings() {
        InstrumentReport report = synthetic(match(Confidence.STRONG, "mt", "1"), match(Confidence.STRONG));
        InstrumentReport narrowed = filtered(report, Optional.empty(), true, Optional.empty());
        assertThat(narrowed.matches())
                .extracting(item -> item.otherKey().jurisdiction())
                .containsExactly("wy");
    }

    @Test
    void aLimitTruncates() {
        ReportedMatch[] six = IntStream.range(0, 6)
                .mapToObj(n -> match(Confidence.STRONG, "wy", String.valueOf(n)))
                .toArray(ReportedMatch[]::new);
        assertThat(filtered(synthetic(six), Optional.empty(), false, Optional.of(2))
                        .matches())
                .hasSize(2);
    }

    @Test
    void theSummaryIsNeverRecomputed() {
        InstrumentReport report = synthetic(match(Confidence.WEAK));
        assertThat(filtered(report, Optional.of(Confidence.STRONG), false, Optional.empty())
                        .summary())
                .isEqualTo(report.summary());
    }

    @Test
    void aNegativeLimitIsRejected() {
        assertThatThrownBy(() -> filtered(synthetic(), Optional.empty(), false, Optional.of(-1)))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("cannot be negative");
    }

    @Test
    void filteringAnEmptyReport() {
        assertThat(Ranking.filterReport(synthetic()).matches()).isEmpty();
    }

    @Test
    void aReplacedReportKeepsItsIdentity() {
        InstrumentReport report = synthetic(match(Confidence.STRONG));
        assertThat(report.withMatches(List.of()).key()).isEqualTo(report.key());
    }

    @Test
    void theReportCommandTakesAConfidenceFloor() {
        Cli.Result result = run(
                "report", "--min-confidence", "strong", Cli.goldenCorpus(tmp).toString());
        assertThat(result.code()).isIn(EXIT_OK, EXIT_FINDINGS);
        assertThat(result.output()).doesNotContain("weak");
    }

    @Test
    void theReportCommandTakesATopLimit() {
        String output = run(
                        "report",
                        "--top",
                        "1",
                        "--only-findings",
                        Cli.goldenCorpus(tmp).toString())
                .output();
        assertThat(Cli.count(output, " matches ")).isLessThanOrEqualTo(4);
    }

    @Test
    void theQueryCommandTakesTheSameFlags() {
        Path corpus = Cli.goldenCorpus(tmp);
        String index = tmp.resolve("index.sqlite").toString();
        run("index", "--index", index, corpus.toString());
        Cli.Result result = run(
                "query",
                "--index",
                index,
                "--jurisdiction",
                "mt",
                "--crossing-only",
                "--top",
                "1",
                corpus.resolve("mt_2024_HB0311.txt").toString());
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("lexdrift report for");
    }

    @Test
    void theEngineReportsAreRankedByDefault() {
        for (InstrumentReport report : new Engine()
                .runOver(goldenInstruments(), Optional.of(ReportFixtures.FIXED))
                .reports()) {
            assertThat(Ranking.rankReport(report).matches())
                    .extracting(ReportedMatch::otherPath)
                    .containsExactlyElementsOf(Ranking.rank(report.matches()).stream()
                            .map(ReportedMatch::otherPath)
                            .toList());
        }
    }

    @Test
    void aSourceFilterKeepsOnlyTheNamedJurisdictions() {
        InstrumentReport report = synthetic(
                match(Confidence.STRONG, "wy", "1"),
                match(Confidence.WEAK, "nd", "2"),
                match(Confidence.STRONG, "co", "3"));
        InstrumentReport narrowed = Ranking.filterReport(report, ReportFilter.NONE.withFrom(Set.of("wy", "nd")));
        assertThat(narrowed.matches())
                .extracting(item -> item.otherKey().jurisdiction())
                .containsExactly("wy", "nd");
        assertThat(narrowed.summary()).isEqualTo(report.summary());
    }

    @Test
    void everySettingHasToKeepAFindingAndTheLimitComesLast() {
        InstrumentReport report = synthetic(
                match(Confidence.WEAK, "wy", "1"),
                match(Confidence.STRONG, "wy", "2"),
                match(Confidence.STRONG, "mt", "3"),
                match(Confidence.MODERATE, "wy", "4"),
                match(Confidence.STRONG, "co", "5"));
        ReportFilter filter = ReportFilter.NONE
                .withFrom(Set.of("wy", "mt"))
                .withCrossingOnly(true)
                .withMinConfidence(Confidence.MODERATE)
                .withLimit(1);
        assertThat(Ranking.filterReport(report, filter).matches())
                .extracting(ReportedMatch::otherPath)
                .containsExactly("2");
        assertThat(Ranking.filterReport(report, filter.withLimit(5)).matches())
                .extracting(ReportedMatch::otherPath)
                .containsExactly("2", "4");
    }

    @Test
    void theConfiguredFilterTreatsAWeakFloorAsNone() {
        assertThat(ReportFilter.of(new ReportConfig())).isEqualTo(ReportFilter.NONE);
        assertThat(ReportFilter.NONE.isEmpty()).isTrue();
        ReportFilter configured = ReportFilter.of(new ReportConfig("strong", true, Optional.of(3), 3));
        assertThat(configured.minConfidence()).contains(Confidence.STRONG);
        assertThat(configured.crossingOnly()).isTrue();
        assertThat(configured.limit()).contains(3);
        assertThat(configured.isEmpty()).isFalse();
        assertThat(ReportFilter.NONE.withFrom(Set.of("wy", "co")).toString()).contains("from=[co, wy]");
    }

    @Test
    void theCommandsTakeASourceFilter() {
        // Discovery runs the corpus in file name order, so Wyoming comes last and reuses the others.
        Path corpus = Cli.goldenCorpus(tmp);
        String output = run("report", "--from", "mt", "--only-findings", corpus.toString())
                .output();
        assertThat(output)
                .contains("lexdrift report for wy/2023/lower/HB0142")
                .contains("matches mt/2024/lower/HB0311");
        assertThat(output).doesNotContain("matches nd/2025/lower/HB0044").doesNotContain("matches laramie");
        String both = run("report", "--from", "mt,nd", "--only-findings", corpus.toString())
                .output();
        assertThat(both).contains("matches nd/2025/lower/HB0044").contains("matches mt/2024/lower/HB0311");
    }
}
