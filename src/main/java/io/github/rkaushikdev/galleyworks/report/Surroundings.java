package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.Segment;
import io.github.rkaushikdev.galleyworks.SegmentKind;
import io.github.rkaushikdev.galleyworks.ingest.AmendatoryClauses;
import io.github.rkaushikdev.galleyworks.match.SegmentMatch;
import io.github.rkaushikdev.galleyworks.match.SharedContext;
import io.github.rkaushikdev.galleyworks.util.Text;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Reading what surrounds a matched pair of segments in their two instruments.
 *
 * <p>The report is the first stage that holds both instruments, so it is where the structure around
 * a match can be read: the catchline of the section each segment sits in, and the directives at the
 * top of the bills. The result is a {@link SharedContext} for the explanation to weigh.
 */
public final class Surroundings {

    private Surroundings() {}

    /** What the segments of {@code match} share in {@code query} and {@code other}. */
    public static SharedContext between(Instrument query, SegmentMatch match, Instrument other) {
        return new SharedContext(sharedCatchline(query, match, other), companionProvisions(query, other));
    }

    /**
     * The statute catchline both matched segments sit under, as the query prints it, when they print
     * the same one. A subsection has no catchline of its own and takes its section's.
     */
    public static Optional<String> sharedCatchline(Instrument query, SegmentMatch match, Instrument other) {
        Optional<String> mine = catchlineOf(query, match.query().segmentIndex());
        Optional<String> theirs = catchlineOf(other, match.other().segmentIndex());
        if (mine.isEmpty() || theirs.isEmpty() || !folded(mine.get()).equals(folded(theirs.get()))) {
            return Optional.empty();
        }
        return mine;
    }

    /** The catchline of the section holding segment {@code index} of {@code instrument}, if it has one. */
    public static Optional<String> catchlineOf(Instrument instrument, int index) {
        Optional<Segment> cursor = segment(instrument, index);
        while (cursor.isPresent() && cursor.get().kind() != SegmentKind.SECTION) {
            OptionalInt parent = cursor.get().parent();
            cursor = parent.isPresent() ? segment(instrument, parent.getAsInt()) : Optional.empty();
        }
        return cursor.map(section -> Text.strip(section.heading()))
                .map(heading -> heading.endsWith(".") ? heading.substring(0, heading.length() - 1) : heading)
                .filter(heading -> !heading.isEmpty());
    }

    /**
     * The provisions both instruments amend, when they are bills of the same session in the same
     * jurisdiction. Two states amending sections that happen to share a number are not companions,
     * and neither are two sessions returning to one section.
     */
    public static List<String> companionProvisions(Instrument query, Instrument other) {
        if (!query.key().jurisdiction().equals(other.key().jurisdiction())
                || !query.key().session().equals(other.key().session())
                || query.key().equals(other.key())) {
            return List.of();
        }
        Set<String> theirs = provisions(other);
        return provisions(query).stream().filter(theirs::contains).toList();
    }

    private static Set<String> provisions(Instrument instrument) {
        Set<String> found = new LinkedHashSet<>();
        AmendatoryClauses.read(instrument).forEach(clause -> found.addAll(clause.provisions()));
        return found;
    }

    private static Optional<Segment> segment(Instrument instrument, int index) {
        return instrument.segments().stream()
                .filter(segment -> segment.index() == index)
                .findFirst();
    }

    private static String folded(String catchline) {
        return Text.squash(Text.lower(catchline));
    }
}
