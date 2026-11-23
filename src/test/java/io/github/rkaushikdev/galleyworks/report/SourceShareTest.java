package io.github.rkaushikdev.galleyworks.report;

import static io.github.rkaushikdev.galleyworks.support.GoldenCorpus.goldenInstruments;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.match.Confidence;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SourceShareTest {

    private static ReportedMatch finding(String jurisdiction, String number, int start, int end) {
        return new ReportedMatch(
                "1(a)",
                new InstrumentKey(jurisdiction, "2024", Chamber.LOWER, number),
                "1(a)",
                0.8,
                0.6,
                List.of(new ReportedPassage(new TextSpan(start, end), new TextSpan(0, end - start), "q", "o", true)),
                Confidence.MODERATE,
                "h",
                List.of("r"));
    }

    private static InstrumentReport report(ReportedMatch... matches) {
        return new InstrumentReport(
                new InstrumentKey("nd", "2025", Chamber.LOWER, "HB0044"),
                "t",
                ReportFixtures.FIXED,
                new ReportSummary(1, 3, 300, 1000, 2),
                List.of(matches));
    }

    private static InstrumentReport golden() {
        return new Engine()
                .runOver(goldenInstruments(), Optional.of(ReportFixtures.FIXED))
                .reportFor("nd/2025/lower/HB0044")
                .orElseThrow();
    }

    @Test
    void overlappingFindingsInOneJurisdictionCountTheirCharactersOnce() {
        List<SourceShare> shares = SourceShare.of(report(
                finding("wy", "HB0142", 0, 200), finding("wy", "HB0207", 100, 300), finding("mt", "HB0311", 0, 150)));
        assertThat(shares).containsExactly(new SourceShare("wy", 300, 2, 2), new SourceShare("mt", 150, 1, 1));
    }

    @Test
    void equalSharesAreOrderedByJurisdiction() {
        List<SourceShare> shares =
                SourceShare.of(report(finding("wy", "HB0142", 0, 100), finding("co", "SB0007", 0, 100)));
        assertThat(shares).extracting(SourceShare::jurisdiction).containsExactly("co", "wy");
        assertThat(SourceShare.of(report())).isEmpty();
    }

    @Test
    void aShareDescribesItselfInWords() {
        assertThat(new SourceShare("wy", 663, 1, 1).describe())
                .isEqualTo("wy: 663 characters in 1 finding against 1 instrument");
        assertThat(new SourceShare("mt", 40, 3, 2).describe())
                .isEqualTo("mt: 40 characters in 3 findings against 2 instruments");
    }

    @Test
    void theTextReportListsEverySourceUnderTheTotals() {
        InstrumentReport report = golden();
        List<String> lines = TextRenderer.renderText(report).lines().toList();
        List<String> from =
                lines.stream().filter(line -> line.startsWith("  from ")).toList();
        assertThat(from).hasSize(SourceShare.of(report).size()).anyMatch(line -> line.startsWith("  from wy: "));
        assertThat(lines.indexOf(from.getFirst())).isEqualTo(5);
    }

    @Test
    void theMarkdownReportHasASourcesTable() {
        String markdown = MarkdownWriter.toMarkdown(golden());
        assertThat(markdown)
                .contains(
                        "## Sources\n\n| jurisdiction | characters | findings | instruments |\n| --- | --- | --- | --- |\n")
                .contains("| wy | ");
        assertThat(markdown.indexOf("## Sources")).isLessThan(markdown.indexOf("## Findings"));
    }

    @Test
    void anEmptyReportNamesNoSources() {
        InstrumentReport empty = report();
        assertThat(TextRenderer.renderText(empty)).doesNotContain("  from ");
        assertThat(MarkdownWriter.toMarkdown(empty)).doesNotContain("## Sources");
    }
}
