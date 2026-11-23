package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.util.Floats;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextWrap;
import io.github.rkaushikdev.galleyworks.util.Timestamps;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Plain text rendering for the terminal.
 *
 * <p>The layout is a header, a summary line, then one block per finding: which subsection of the
 * reported instrument, which instrument and subsection it matches, the scores, the reasons, and the
 * passages quoted from both sides, wrapped and indented so the eye can find the boundary between
 * them. There is no colour and no box drawing: the output is read as often through {@code less} and
 * in a CI log as in a terminal that supports either.
 */
public final class TextRenderer {

    /** The default line width. */
    public static final int DEFAULT_WIDTH = 100;

    /** The default number of passages printed per finding. */
    public static final int DEFAULT_MAX_PASSAGES = 3;

    private static final String RULE = "-";
    private static final int RULE_WIDTH = 78;

    private TextRenderer() {}

    private static List<String> quote(String label, String text, int width, String indent) {
        if (text.isEmpty()) {
            return List.of(indent + label + ": (not available)");
        }
        return TextWrap.wrap(
                Shared.flow(text),
                Math.max(width - indent.length() - 2, 20),
                indent + label + ": ",
                indent + " ".repeat(label.length() + 2));
    }

    private static List<String> passageBlock(ReportedPassage passage, int width, int position) {
        List<String> lines = new ArrayList<>();
        lines.add("    passage " + position + ": " + passage.characters() + " characters, query "
                + passage.querySpan().start() + "-" + passage.querySpan().end() + ", other "
                + passage.otherSpan().start() + "-" + passage.otherSpan().end());
        lines.addAll(quote("query", passage.queryText(), width, "      "));
        lines.addAll(quote("other", passage.otherText(), width, "      "));
        return lines;
    }

    private static List<String> matchBlock(ReportedMatch match, int width, int limit) {
        List<String> lines = new ArrayList<>();
        lines.add("  " + match.queryPath() + " matches " + match.otherKey().slug() + " at " + match.otherPath());
        lines.add("    confidence " + match.confidence().value());
        lines.add("    containment " + Floats.fixed(match.containment(), 2) + "  jaccard "
                + Floats.fixed(match.jaccard(), 2) + "  " + match.characters() + " characters over "
                + match.passages().size() + " passages");
        for (String reason : match.reasons()) {
            lines.add("    why: " + reason);
        }
        Shared.Trimmed trimmed = Shared.trimPassages(match, Optional.of(limit));
        List<ReportedPassage> shown = trimmed.match().passages();
        for (int position = 0; position < shown.size(); position++) {
            lines.addAll(passageBlock(shown.get(position), width, position + 1));
        }
        if (trimmed.hidden() > 0) {
            lines.add("    ... " + trimmed.hidden() + " further passages");
        }
        return lines;
    }

    /**
     * Wrap any line that overruns {@code width}, keeping its indentation. Headers carry instrument
     * slugs and paths, so they overrun on a narrow terminal exactly as quotations do.
     */
    private static List<String> fit(List<String> lines, int width) {
        List<String> fitted = new ArrayList<>();
        for (String line : lines) {
            String stripped = Text.strip(line);
            if (line.length() <= width || stripped.isEmpty()) {
                fitted.add(line);
                continue;
            }
            int lead = 0;
            while (lead < line.length() && Text.isSpace(line.charAt(lead))) {
                lead++;
            }
            String indent = line.substring(0, lead);
            fitted.addAll(TextWrap.wrap(stripped, Math.max(width, 24), indent, indent + "  "));
        }
        return fitted;
    }

    /** Render a report at the default width, showing every finding and three passages each. */
    public static String renderText(InstrumentReport report) {
        return renderText(report, DEFAULT_WIDTH, Optional.empty(), DEFAULT_MAX_PASSAGES);
    }

    /** Render a report at {@code width} columns. */
    public static String renderText(InstrumentReport report, int width) {
        return renderText(report, width, Optional.empty(), DEFAULT_MAX_PASSAGES);
    }

    /**
     * The heading block: the instrument, its title, when the report was built, the totals, and one
     * line per jurisdiction the reused text was found in.
     */
    private static List<String> header(InstrumentReport report, int width) {
        ReportSummary summary = report.summary();
        String title = Shared.flow(report.title());
        List<String> lines = new ArrayList<>();
        lines.add("lexdrift report for " + report.key().slug());
        lines.add(title.substring(0, Math.min(title.length(), Math.max(0, width))));
        lines.add(RULE.repeat(Math.min(width, RULE_WIDTH)));
        lines.add("generated " + Timestamps.isoformatSeconds(report.generatedAt()));
        lines.add(summary.matchedSegments() + " of " + summary.totalSegments() + " segments matched, "
                + summary.reusedCharacters() + " characters reused (" + Floats.percent(summary.reuseRatio(), 1)
                + " of the document) across " + summary.otherInstruments() + " other instruments");
        SourceShare.of(report).forEach(share -> lines.add("  from " + share.describe()));
        return lines;
    }

    /** Render a report for a terminal. */
    public static String renderText(InstrumentReport report, int width, Optional<Integer> maxMatches, int maxPassages) {
        List<String> lines = header(report, width);
        if (report.isEmpty()) {
            lines.add("no reuse found");
            return String.join("\n", fit(lines, width)) + "\n";
        }
        List<ReportedMatch> shown = maxMatches
                .map(limit -> report.matches()
                        .subList(0, Math.min(limit, report.matches().size())))
                .orElse(report.matches());
        for (ReportedMatch match : shown) {
            lines.add("");
            lines.addAll(matchBlock(match, width, maxPassages));
        }
        int remaining = report.matches().size() - shown.size();
        if (remaining > 0) {
            lines.add("");
            lines.add("... " + remaining + " further matches");
        }
        return String.join("\n", fit(lines, width)) + "\n";
    }
}
