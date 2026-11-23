package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.ReportConfig;
import io.github.rkaushikdev.galleyworks.match.Confidence;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which findings a printed report keeps.
 *
 * <p>An analyst reads a report with a question in mind: what does this bill take from anywhere, what
 * does it take from outside its own state, what does it take from Wyoming in particular. Each
 * question is one setting here, and they combine: a finding is kept only when every setting keeps
 * it, and the limit then applies to what is left, strongest first.
 *
 * @param minConfidence the weakest confidence kept, when there is a floor
 * @param crossingOnly whether findings within the report's own jurisdiction are dropped
 * @param from the jurisdictions a finding has to point at, or empty for any
 * @param limit how many findings are kept at most
 */
public record ReportFilter(
        Optional<Confidence> minConfidence, boolean crossingOnly, Set<String> from, Optional<Integer> limit) {

    /** A filter that keeps everything. */
    public static final ReportFilter NONE = new ReportFilter(Optional.empty(), false, Set.of(), Optional.empty());

    /** Copies the jurisdictions and rejects a negative limit. */
    public ReportFilter {
        from = Set.copyOf(from);
        if (limit.isPresent() && limit.get() < 0) {
            throw new ConfigError("limit cannot be negative", Context.of("limit", limit.get()));
        }
    }

    /** The filter a report configuration asks for; a floor of weak is no floor. */
    public static ReportFilter of(ReportConfig settings) {
        Confidence floor = Confidence.fromValue(settings.minConfidence());
        return new ReportFilter(
                floor == Confidence.WEAK ? Optional.empty() : Optional.of(floor),
                settings.crossingOnly(),
                Set.of(),
                settings.top());
    }

    /** This filter with a confidence floor. */
    public ReportFilter withMinConfidence(Confidence value) {
        return new ReportFilter(Optional.of(value), crossingOnly, from, limit);
    }

    /** This filter dropping, or keeping, findings within the report's own jurisdiction. */
    public ReportFilter withCrossingOnly(boolean value) {
        return new ReportFilter(minConfidence, value, from, limit);
    }

    /** This filter keeping only findings against {@code jurisdictions}. */
    public ReportFilter withFrom(Set<String> jurisdictions) {
        return new ReportFilter(minConfidence, crossingOnly, jurisdictions, limit);
    }

    /** This filter keeping at most {@code value} findings. */
    public ReportFilter withLimit(int value) {
        return new ReportFilter(minConfidence, crossingOnly, from, Optional.of(value));
    }

    /** True when the filter keeps everything. */
    public boolean isEmpty() {
        return equals(NONE);
    }

    /** True when {@code match}, found in {@code report}, passes every setting but the limit. */
    public boolean keeps(InstrumentReport report, ReportedMatch match) {
        if (minConfidence.isPresent() && match.confidence().compareTo(minConfidence.get()) < 0) {
            return false;
        }
        String other = match.otherKey().jurisdiction();
        if (crossingOnly && other.equals(report.key().jurisdiction())) {
            return false;
        }
        return from.isEmpty() || from.contains(other);
    }

    @Override
    public String toString() {
        return "ReportFilter[minConfidence=" + minConfidence + ", crossingOnly=" + crossingOnly + ", from="
                + new TreeSet<>(from) + ", limit=" + limit + "]";
    }
}
