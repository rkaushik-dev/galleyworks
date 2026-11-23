package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.JurisdictionRegistry;
import io.github.rkaushikdev.galleyworks.MatchConfig;
import io.github.rkaushikdev.galleyworks.Segment;
import io.github.rkaushikdev.galleyworks.SegmentKind;
import io.github.rkaushikdev.galleyworks.match.Explanation;
import io.github.rkaushikdev.galleyworks.match.Passage;
import io.github.rkaushikdev.galleyworks.match.SegmentMatch;
import io.github.rkaushikdev.galleyworks.util.Spans;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import io.github.rkaushikdev.galleyworks.util.Timestamps;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Resolving the engine's matches into a readable report.
 *
 * <p>A match holds references and spans; a report resolves them into quoted text on both sides,
 * because a reuse finding that cannot be checked against the words is a number nobody should act
 * on. When the other document cannot be found the passage is still reported, with an empty
 * quotation and {@code resolved} false, so a missing corpus file shows up as a gap rather than as a
 * silently shorter report.
 */
public final class Reports {

    private Reports() {}

    /** A report built now, quoting other instruments from {@code source}, under default thresholds. */
    public static InstrumentReport buildReport(
            Instrument instrument, Iterable<SegmentMatch> matches, TextSource source) {
        return buildReport(instrument, matches, source, Timestamps.nowUtc(), new MatchConfig());
    }

    /** A report stamped {@code now}, quoting other instruments from {@code source}. */
    public static InstrumentReport buildReport(
            Instrument instrument, Iterable<SegmentMatch> matches, TextSource source, OffsetDateTime now) {
        return buildReport(instrument, matches, source, now, new MatchConfig());
    }

    /** A report about {@code instrument} explained without a jurisdiction table. */
    public static InstrumentReport buildReport(
            Instrument instrument,
            Iterable<SegmentMatch> matches,
            TextSource source,
            OffsetDateTime now,
            MatchConfig matchConfig) {
        return buildReport(instrument, matches, source, now, matchConfig, new JurisdictionRegistry());
    }

    /** Resolve {@code matches} into a readable report about {@code instrument}. */
    public static InstrumentReport buildReport(
            Instrument instrument,
            Iterable<SegmentMatch> matches,
            TextSource source,
            OffsetDateTime now,
            MatchConfig matchConfig,
            JurisdictionRegistry jurisdictions) {
        List<ReportedMatch> reported = new ArrayList<>();
        List<TextSpan> covered = new ArrayList<>();
        Set<String> matchedPaths = new HashSet<>();
        for (SegmentMatch match : matches) {
            Optional<Instrument> other = source.instrumentOf(match.other().key());
            Optional<String> shared = other.flatMap(found -> sharedCatchline(instrument, match, found));
            reported.add(
                    resolve(instrument, match, other, Explanation.explain(match, matchConfig, jurisdictions, shared)));
            match.passages().forEach(passage -> covered.add(passage.query()));
            matchedPaths.add(match.queryPath());
        }
        Set<String> others = new HashSet<>();
        reported.forEach(match -> others.add(match.otherKey().slug()));
        ReportSummary summary = new ReportSummary(
                matchedPaths.size(),
                instrument.bodySegments().size(),
                Spans.coverage(covered),
                instrument.rawText().length(),
                others.size());
        return new InstrumentReport(instrument.key(), instrument.title(), now, summary, reported);
    }

    /** One finding with both sides quoted; a missing other instrument leaves its quotations empty. */
    private static ReportedMatch resolve(
            Instrument instrument, SegmentMatch match, Optional<Instrument> other, Explanation reasoning) {
        List<ReportedPassage> passages = new ArrayList<>();
        for (Passage passage : match.passages()) {
            passages.add(new ReportedPassage(
                    passage.query(),
                    passage.other(),
                    passage.query().sliceOf(instrument.rawText()),
                    other.map(found -> passage.other().sliceOf(found.rawText())).orElse(""),
                    other.isPresent()));
        }
        return new ReportedMatch(
                match.queryPath(),
                match.other().key(),
                match.otherPath(),
                match.score().queryContainment(),
                match.score().jaccard(),
                passages,
                reasoning.confidence(),
                reasoning.headline(),
                reasoning.reasons());
    }

    /**
     * The statute catchline both matched segments sit under, as the query prints it, when they print
     * the same one. A subsection has no catchline of its own and takes its section's.
     */
    static Optional<String> sharedCatchline(Instrument query, SegmentMatch match, Instrument other) {
        Optional<String> mine = catchlineOf(query, match.query().segmentIndex());
        Optional<String> theirs = catchlineOf(other, match.other().segmentIndex());
        if (mine.isEmpty() || theirs.isEmpty() || !folded(mine.get()).equals(folded(theirs.get()))) {
            return Optional.empty();
        }
        return mine;
    }

    /** The catchline of the section holding segment {@code index} of {@code instrument}, if it has one. */
    static Optional<String> catchlineOf(Instrument instrument, int index) {
        Optional<Segment> cursor = segment(instrument, index);
        while (cursor.isPresent() && cursor.get().kind() != SegmentKind.SECTION) {
            OptionalInt parent = cursor.get().parent();
            cursor = parent.isPresent() ? segment(instrument, parent.getAsInt()) : Optional.empty();
        }
        return cursor.map(section -> Text.strip(section.heading()))
                .map(heading -> heading.endsWith(".") ? heading.substring(0, heading.length() - 1) : heading)
                .filter(heading -> !heading.isEmpty());
    }

    private static Optional<Segment> segment(Instrument instrument, int index) {
        return instrument.segments().stream()
                .filter(segment -> segment.index() == index)
                .findFirst();
    }

    private static String folded(String catchline) {
        return Text.squash(Text.lower(catchline));
    }

    /** A report's findings grouped by the instrument they point at, in first-seen order. */
    public static Map<String, List<ReportedMatch>> groupByInstrument(InstrumentReport report) {
        Map<String, List<ReportedMatch>> grouped = new LinkedHashMap<>();
        for (ReportedMatch match : report.matches()) {
            grouped.computeIfAbsent(match.otherKey().slug(), ignored -> new ArrayList<>())
                    .add(match);
        }
        return grouped;
    }
}
