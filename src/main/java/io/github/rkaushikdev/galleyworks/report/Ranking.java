package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.ReportConfig;
import io.github.rkaushikdev.galleyworks.match.Confidence;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Ordering and filtering findings before they are printed.
 *
 * <p>A report with forty findings is not read; the first five are. The order is confidence, then
 * reused characters, then the slug: confidence first because a strong short finding is worth more
 * attention than a weak long one, and the slug last so two runs over the same corpus print the same
 * document.
 */
public final class Ranking {

    /** Where each confidence sorts, strongest first. */
    public static final Map<Confidence, Integer> CONFIDENCE_ORDER =
            Map.of(Confidence.STRONG, 0, Confidence.MODERATE, 1, Confidence.WEAK, 2);

    private static final Comparator<ReportedMatch> ORDER = Comparator.comparingInt(
                    (ReportedMatch match) -> CONFIDENCE_ORDER.get(match.confidence()))
            .thenComparing(Comparator.comparingInt(ReportedMatch::characters).reversed())
            .thenComparing(match -> match.otherKey().slug() + "#" + match.otherPath());

    private Ranking() {}

    /** Sort findings so the ones worth reading come first. */
    public static List<ReportedMatch> rank(List<ReportedMatch> matches) {
        List<ReportedMatch> ranked = new ArrayList<>(matches);
        ranked.sort(ORDER);
        return ranked;
    }

    /** {@code report} with its findings ranked. */
    public static InstrumentReport rankReport(InstrumentReport report) {
        return report.withMatches(rank(report.matches()));
    }

    /**
     * Rank and narrow a report without recomputing anything.
     *
     * <p>The summary is deliberately left alone. It describes the document, not the subset that
     * survived a display filter, and recomputing it here would make two reports of the same run
     * disagree about how much text was reused.
     */
    public static InstrumentReport filterReport(InstrumentReport report, ReportFilter filter) {
        List<ReportedMatch> kept = rank(report.matches()).stream()
                .filter(match -> filter.keeps(report, match))
                .toList();
        if (filter.limit().isPresent() && kept.size() > filter.limit().get()) {
            kept = kept.subList(0, filter.limit().get());
        }
        return report.withMatches(kept);
    }

    /** Rank a report and apply no filter. */
    public static InstrumentReport filterReport(InstrumentReport report) {
        return filterReport(report, ReportFilter.NONE);
    }

    /** Narrow a report the way the configuration says to. */
    public static InstrumentReport applyReportConfig(InstrumentReport report, ReportConfig settings) {
        return filterReport(report, ReportFilter.of(settings));
    }
}
