package io.github.rkaushikdev.galleyworks.match;

import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The fingerprints a corpus has taught the matcher to ignore.
 *
 * <p>"Notwithstanding any other provision of law", "is hereby amended to read as follows",
 * severability and effective-date clauses: a corpus of statutes is full of runs that appear in
 * hundreds of segments and mean nothing about copying. A fingerprint seen in more than a set
 * fraction of segments is removed from both sides of every comparison. The floor on absolute count
 * matters as much as the ratio: in a corpus of four documents, "more than twenty percent of
 * segments" is one segment.
 *
 * @param suppressed the fingerprints treated as boilerplate
 * @param segmentsSeen the segments the filter was learned from
 * @param ratio the share of segments above which a fingerprint is boilerplate
 * @param floor the segment count a fingerprint had to reach
 */
public record BoilerplateFilter(Set<Long> suppressed, int segmentsSeen, double ratio, int floor) {

    /** Copies the suppressed set. */
    public BoilerplateFilter {
        suppressed = Set.copyOf(suppressed);
    }

    /** A filter that keeps everything. */
    public BoilerplateFilter() {
        this(Set.of(), 0, 0.0, 0);
    }

    /** A filter suppressing exactly {@code suppressed}. */
    public static BoilerplateFilter of(Set<Long> suppressed) {
        return new BoilerplateFilter(suppressed, 0, 0.0, 0);
    }

    /** This filter also suppressing everything {@code other} does; the learned counts are this one's. */
    public BoilerplateFilter union(BoilerplateFilter other) {
        Set<Long> combined = new HashSet<>(suppressed);
        combined.addAll(other.suppressed());
        return new BoilerplateFilter(combined, segmentsSeen, ratio, floor);
    }

    /** Number of suppressed fingerprints. */
    public int size() {
        return suppressed.size();
    }

    /** True when {@code value} is suppressed. */
    public boolean contains(long value) {
        return suppressed.contains(value);
    }

    /** {@code values} without the suppressed fingerprints, in order. */
    public List<Long> apply(Collection<Long> values) {
        return values.stream().filter(value -> !suppressed.contains(value)).toList();
    }

    /** The fingerprints of {@code record} the filter keeps. */
    public List<Long> keeps(SegmentFingerprint record) {
        return apply(record.fingerprintValues());
    }

    /**
     * {@code record} with the suppressed shingles taken out of its selection, so an alignment built
     * from it cannot rest a passage on boilerplate.
     */
    public SegmentFingerprint strip(SegmentFingerprint record) {
        if (suppressed.isEmpty()) {
            return record;
        }
        return new SegmentFingerprint(
                record.key(),
                record.segmentIndex(),
                record.path(),
                record.tokenCount(),
                record.signature(),
                record.simhash(),
                record.selected().stream()
                        .filter(shingle -> !suppressed.contains(shingle.value()))
                        .toList(),
                record.canonical(),
                record.contentRatio());
    }

    /** Learn the boilerplate of {@code records} with the default ratio of 0.2 and floor of 5. */
    public static BoilerplateFilter learn(Iterable<SegmentFingerprint> records) {
        return learn(records, 0.2, 5);
    }

    /** Learn which fingerprints are too common in {@code records} to be evidence. */
    public static BoilerplateFilter learn(Iterable<SegmentFingerprint> records, double maxRatio, int minSegments) {
        if (!(maxRatio > 0.0 && maxRatio <= 1.0)) {
            throw new ConfigError("max_ratio must lie in (0, 1]", Context.of("max_ratio", maxRatio));
        }
        if (minSegments < 1) {
            throw new ConfigError("min_segments must be at least one", Context.of("min_segments", minSegments));
        }
        Map<Long, Integer> counts = new HashMap<>();
        int seen = 0;
        for (SegmentFingerprint record : records) {
            seen++;
            new HashSet<>(record.fingerprintValues()).forEach(value -> counts.merge(value, 1, Integer::sum));
        }
        if (seen == 0) {
            return new BoilerplateFilter(Set.of(), 0, maxRatio, minSegments);
        }
        int floor = Math.max(minSegments, (int) (seen * maxRatio) + 1);
        Set<Long> suppressed = new HashSet<>();
        counts.forEach((value, count) -> {
            if (count >= floor) {
                suppressed.add(value);
            }
        });
        return new BoilerplateFilter(suppressed, seen, maxRatio, floor);
    }
}
