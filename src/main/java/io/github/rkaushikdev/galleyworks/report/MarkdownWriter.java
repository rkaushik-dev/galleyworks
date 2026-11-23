package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.util.Floats;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.Timestamps;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Markdown output, for a report circulated in a ticket or a pull request before it is acted on.
 *
 * <p>Quotations are fenced rather than indented. Legislative text starts with {@code (a)} and a
 * four-space indent inside a list turns that into a nested list, which renumbers the subsection in
 * the rendered output; fencing is the only form that survives every renderer intact.
 */
public final class MarkdownWriter {

    private static final String FENCE = "```";

    private MarkdownWriter() {}

    private static String escapeCell(String text) {
        return Shared.flow(text).replace("|", "\\|");
    }

    private static List<String> matchSection(ReportedMatch match, int index) {
        List<String> lines = new ArrayList<>(List.of(
                "### " + index + ". " + match.queryPath() + " into "
                        + match.otherKey().slug() + " " + match.otherPath(),
                "",
                "| measure | value |",
                "| --- | --- |",
                "| containment | " + Floats.fixed(match.containment(), 2) + " |",
                "| jaccard | " + Floats.fixed(match.jaccard(), 2) + " |",
                "| characters | " + match.characters() + " |",
                "| passages | " + match.passages().size() + " |",
                "| other jurisdiction | " + escapeCell(match.otherKey().jurisdiction()) + " |",
                "| confidence | " + match.confidence().value() + " |",
                ""));
        if (!match.reasons().isEmpty()) {
            lines.add("Why this was reported:");
            lines.add("");
            match.reasons().forEach(reason -> lines.add("- " + Shared.flow(reason)));
            lines.add("");
        }
        for (int position = 0; position < match.passages().size(); position++) {
            ReportedPassage passage = match.passages().get(position);
            lines.add("**Passage " + (position + 1) + "** (" + passage.characters() + " characters)");
            lines.add("");
            lines.add(FENCE);
            lines.add(Shared.quote(passage.queryText(), Optional.empty(), "(empty)"));
            lines.add(FENCE);
            lines.add("");
            lines.add(FENCE);
            lines.add(Shared.quote(passage.otherText(), Optional.empty(), "(not available)"));
            lines.add(FENCE);
            lines.add("");
        }
        return lines;
    }

    /** The table of jurisdictions the reused text was found in, the largest share first. */
    private static List<String> sources(InstrumentReport report) {
        List<String> lines = new ArrayList<>(List.of(
                "## Sources",
                "",
                "| jurisdiction | characters | findings | instruments |",
                "| --- | --- | --- | --- |"));
        for (SourceShare share : SourceShare.of(report)) {
            lines.add("| " + escapeCell(share.jurisdiction()) + " | " + share.characters() + " | " + share.findings()
                    + " | " + share.instruments() + " |");
        }
        lines.add("");
        return lines;
    }

    /** Render a report as markdown with three passages per finding. */
    public static String toMarkdown(InstrumentReport report) {
        return toMarkdown(report, 3);
    }

    /** Render a report as markdown. */
    public static String toMarkdown(InstrumentReport report, int maxPassages) {
        ReportSummary summary = report.summary();
        List<String> lines = new ArrayList<>(List.of(
                "# Reuse report: " + report.key().slug(),
                "",
                Shared.flow(report.title()),
                "",
                "Generated " + Timestamps.isoformatSeconds(report.generatedAt()) + ".",
                "",
                "## Summary",
                "",
                "| measure | value |",
                "| --- | --- |",
                "| segments matched | " + summary.matchedSegments() + " of " + summary.totalSegments() + " |",
                "| characters reused | " + summary.reusedCharacters() + " |",
                "| document characters | " + summary.documentCharacters() + " |",
                "| reuse ratio | " + Floats.percent(summary.reuseRatio(), 1) + " |",
                "| other instruments | " + summary.otherInstruments() + " |",
                ""));
        if (report.isEmpty()) {
            lines.add("No reuse found.");
            return String.join("\n", lines) + "\n";
        }
        lines.addAll(sources(report));
        lines.add("## Findings");
        lines.add("");
        for (int index = 0; index < report.matches().size(); index++) {
            Shared.Trimmed trimmed = Shared.trimPassages(report.matches().get(index), Optional.of(maxPassages));
            lines.addAll(matchSection(trimmed.match(), index + 1));
            if (trimmed.hidden() > 0) {
                lines.add("_" + trimmed.hidden() + " further passages omitted._");
                lines.add("");
            }
        }
        return Text.rstrip(String.join("\n", lines)) + "\n";
    }

    /** Write a report as markdown. */
    public static void writeMarkdown(InstrumentReport report, Writer stream) {
        try {
            stream.write(toMarkdown(report));
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }
    }
}
