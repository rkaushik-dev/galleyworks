package io.github.rkaushikdev.galleyworks;

import io.github.rkaushikdev.galleyworks.canon.Profiles;
import io.github.rkaushikdev.galleyworks.canon.Tokens;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.SequenceMatcher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Comparing two prints of the same instrument.
 *
 * <p>Reuse detection answers "who else has this text". This answers the narrower and more common
 * question: "what changed between the introduced bill and the enrolled one". Segments are paired by
 * their printed path, so {@code 1(a)(ii)} is compared with {@code 1(a)(ii)} and a renumbered
 * subsection shows up as one removal and one addition rather than as a rewrite. That is the honest
 * reading: a renumbering is a change to the instrument even when no word moved.
 *
 * <p>A reviewer following an amendment through committee usually wants the other reading as well:
 * when a new subsection (b) is inserted, every later subsection is relettered, and pairing by path
 * reports each of them as rewritten. Following renumbering pairs a segment that left one path with
 * an identical segment that arrived at another, and reports it once, as renumbered.
 */
public final class Diff {

    private static final Pattern LABEL =
            Regex.ignoringCase("^\\s*(?:\\([A-Za-z0-9]{1,6}\\)|(?:Section|Sec\\.|§)\\s*[A-Za-z0-9-]{1,8}\\.)\\s*");

    private Diff() {}

    /** What happened to one segment between two prints. */
    public enum ChangeKind {
        UNCHANGED("unchanged"),
        CHANGED("changed"),
        ADDED("added"),
        REMOVED("removed"),
        RENUMBERED("renumbered");

        private final String value;

        ChangeKind(String value) {
            this.value = value;
        }

        /** The lowercase name written in diff output. */
        public String value() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * What happened to one segment between two prints.
     *
     * @param path the printed path both prints share, or the later print's path for a renumbering
     * @param kind what happened
     * @param similarity how alike the two word sequences are
     * @param wordsAdded words in the later print not matched in the earlier
     * @param wordsRemoved words in the earlier print not matched in the later
     * @param previousPath the earlier print's path, for a renumbered segment
     */
    public record SegmentChange(
            String path,
            ChangeKind kind,
            double similarity,
            int wordsAdded,
            int wordsRemoved,
            Optional<String> previousPath) {

        /** A change to a segment that kept its path. */
        public SegmentChange(String path, ChangeKind kind, double similarity, int wordsAdded, int wordsRemoved) {
            this(path, kind, similarity, wordsAdded, wordsRemoved, Optional.empty());
        }

        /** A segment whose words did not change but whose path did. */
        public static SegmentChange renumbered(String from, String to) {
            return new SegmentChange(to, ChangeKind.RENUMBERED, 1.0, 0, 0, Optional.of(from));
        }

        /** True for anything but an unchanged segment. */
        public boolean isMaterial() {
            return kind != ChangeKind.UNCHANGED;
        }

        /** Words added plus words removed. */
        public int churn() {
            return wordsAdded + wordsRemoved;
        }
    }

    /**
     * The whole comparison.
     *
     * @param left the earlier print's slug
     * @param right the later print's slug
     * @param changes one entry per path, sorted by path
     */
    public record InstrumentDiff(String left, String right, List<SegmentChange> changes) {

        /** Copies the changes. */
        public InstrumentDiff {
            changes = List.copyOf(changes);
        }

        /** True when no segment changed. */
        public boolean isIdentical() {
            return changes.stream().noneMatch(SegmentChange::isMaterial);
        }

        /** The segments that changed, in path order. */
        public List<SegmentChange> changed() {
            return changes.stream().filter(SegmentChange::isMaterial).toList();
        }

        /** How many segments fell into each kind, every kind present. */
        public Map<String, Integer> counts() {
            Map<String, Integer> found = new LinkedHashMap<>();
            for (ChangeKind kind : ChangeKind.values()) {
                found.put(kind.value(), 0);
            }
            changes.forEach(change -> found.merge(change.kind().value(), 1, Integer::sum));
            return found;
        }

        /** Churn across every segment. */
        public int totalChurn() {
            return changes.stream().mapToInt(SegmentChange::churn).sum();
        }
    }

    /**
     * The canonical words of {@code text}, keeping case. Folding case here would override the
     * profile: the verbatim profile exists so that a comparison can notice a capitalisation change.
     */
    private static List<String> canonicalWords(String text, CanonConfig config) {
        String canon = Profiles.buildPipeline().run(text, config).text();
        return Tokens.wordsOnly(Tokens.tokenize(canon)).stream()
                .map(Tokens.Token::text)
                .toList();
    }

    private static Map<String, List<String>> segments(Instrument instrument, CanonConfig config) {
        return segments(instrument, config, false);
    }

    /**
     * The canonical words of every labelled body segment, by printed path. Without its label the
     * text is what a renumbering leaves untouched: {@code (b) A fee applies} and {@code (c) A fee
     * applies} are the same subsection under two letters.
     */
    private static Map<String, List<String>> segments(Instrument instrument, CanonConfig config, boolean unlabelled) {
        Map<String, List<String>> found = new LinkedHashMap<>();
        for (Segment segment : instrument.bodySegments()) {
            String path = segment.path().printed();
            if (!path.isEmpty()) {
                String text = segment.span().sliceOf(instrument.rawText());
                found.put(path, canonicalWords(unlabelled ? LABEL.matcher(text).replaceFirst("") : text, config));
            }
        }
        return found;
    }

    /** Compare two prints under the verbatim profile. */
    public static InstrumentDiff compare(Instrument left, Instrument right) {
        return compare(left, right, Profiles.profile("verbatim"));
    }

    /** Compare two prints of one instrument, segment by segment, pairing by path alone. */
    public static InstrumentDiff compare(Instrument left, Instrument right, CanonConfig config) {
        return compare(left, right, config, false);
    }

    /**
     * Compare two prints of one instrument, segment by segment. With {@code followRenumbering}, a
     * segment that is not identical at its own path in the later print, and is identical to one
     * that arrived at another path, is reported as renumbered there, first come first paired in path
     * order.
     */
    public static InstrumentDiff compare(
            Instrument left, Instrument right, CanonConfig config, boolean followRenumbering) {
        Map<String, List<String>> before = segments(left, config);
        Map<String, List<String>> after = segments(right, config);
        TreeSet<String> paths = new TreeSet<>(before.keySet());
        paths.addAll(after.keySet());
        Map<String, String> movedIn = followRenumbering
                ? moves(before, after, segments(left, config, true), segments(right, config, true))
                : Map.of();
        Set<String> movedAway = new HashSet<>(movedIn.values());
        List<SegmentChange> changes = new ArrayList<>();
        for (String path : paths) {
            List<String> old = before.get(path);
            List<String> fresh = after.get(path);
            if (old != null && old.equals(fresh)) {
                changes.add(new SegmentChange(path, ChangeKind.UNCHANGED, 1.0, 0, 0));
                continue;
            }
            boolean arrived = movedIn.containsKey(path);
            boolean departed = movedAway.contains(path);
            if (arrived) {
                changes.add(SegmentChange.renumbered(movedIn.get(path), path));
            }
            if (old != null && fresh != null && !arrived && !departed) {
                changes.add(changed(path, old, fresh));
                continue;
            }
            if (old != null && !departed) {
                changes.add(new SegmentChange(path, ChangeKind.REMOVED, 0.0, 0, old.size()));
            }
            if (fresh != null && !arrived) {
                changes.add(new SegmentChange(path, ChangeKind.ADDED, 0.0, fresh.size(), 0));
            }
        }
        return new InstrumentDiff(left.key().slug(), right.key().slug(), changes);
    }

    private static SegmentChange changed(String path, List<String> old, List<String> fresh) {
        SequenceMatcher<String> matcher = new SequenceMatcher<>(old, fresh);
        int matched = matcher.matched();
        return new SegmentChange(
                path, ChangeKind.CHANGED, matcher.ratio(), fresh.size() - matched, old.size() - matched);
    }

    /**
     * Each later path whose words came, unchanged but for the label, from a different earlier path
     * that did not keep them: later path to earlier path.
     */
    private static Map<String, String> moves(
            Map<String, List<String>> before,
            Map<String, List<String>> after,
            Map<String, List<String>> beforeBodies,
            Map<String, List<String>> afterBodies) {
        List<String> departed = new ArrayList<>(new TreeSet<>(before.keySet().stream()
                .filter(path -> !before.get(path).equals(after.get(path)))
                .toList()));
        Map<String, String> moved = new LinkedHashMap<>();
        for (String path : new TreeSet<>(after.keySet())) {
            List<String> body = afterBodies.get(path);
            if (after.get(path).equals(before.get(path)) || body.isEmpty()) {
                continue;
            }
            departed.stream()
                    .filter(from -> !from.equals(path) && beforeBodies.get(from).equals(body))
                    .findFirst()
                    .ifPresent(from -> {
                        moved.put(path, from);
                        departed.remove(from);
                    });
        }
        return moved;
    }
}
