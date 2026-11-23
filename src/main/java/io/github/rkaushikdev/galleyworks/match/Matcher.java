package io.github.rkaushikdev.galleyworks.match;

import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.MatchConfig;
import io.github.rkaushikdev.galleyworks.fingerprint.Fingerprinter;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import io.github.rkaushikdev.galleyworks.index.Index;
import io.github.rkaushikdev.galleyworks.index.SegmentRef;
import io.github.rkaushikdev.galleyworks.match.Scoring.PairScore;
import io.github.rkaushikdev.galleyworks.util.Log;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;

/**
 * Finds the reuse in one instrument against an index.
 *
 * <p>Each stage narrows the previous one: the index proposes candidates that share an LSH band;
 * boilerplate fingerprints are removed from both sides, for the alignment as much as for the
 * scores, so no passage rests on a standard clause; the surviving sets are scored against a
 * containment floor that is higher for two instruments from the same jurisdiction; what is left is
 * aligned into passages; and a passage that is too short, or rests on too few distinct fingerprints,
 * is dropped, because a score without a passage a person can check is not a finding. Alignment is
 * the expensive step and runs last, on a candidate set the earlier stages have already cut down.
 */
public final class Matcher {

    private static final Logger LOG = Log.get("match.engine");

    private static final Comparator<SegmentMatch> BY_SCORE = Comparator.comparingDouble(
                    (SegmentMatch match) -> match.score().bestContainment())
            .reversed()
            .thenComparing(match -> match.other().slug());
    private static final Comparator<SegmentMatch> BY_LENGTH = Comparator.comparingInt(SegmentMatch::longestPassage)
            .reversed()
            .thenComparing(match -> match.other().slug());

    private final Index index;
    private final EngineConfig config;
    private final BoilerplateFilter boilerplate;
    private final Fingerprinter fingerprinter;

    /** A matcher over {@code index} under the index's own configuration, with no boilerplate filter. */
    public Matcher(Index index) {
        this(index, index.config());
    }

    /** A matcher over {@code index} under {@code config}, with no boilerplate filter. */
    public Matcher(Index index, EngineConfig config) {
        this(index, config, new BoilerplateFilter());
    }

    /** A matcher over {@code index} under {@code config}, ignoring {@code boilerplate}. */
    public Matcher(Index index, EngineConfig config, BoilerplateFilter boilerplate) {
        this.index = index;
        this.config = config;
        this.boilerplate = boilerplate;
        this.fingerprinter = new Fingerprinter(config);
    }

    /** A matcher whose boilerplate filter is learned from the index with the default thresholds. */
    public static Matcher over(Index index) {
        return over(index, index.config(), 0.2, 5);
    }

    /** A matcher whose boilerplate filter is learned from the index. */
    public static Matcher over(Index index, EngineConfig config, double maxRatio, int minSegments) {
        BoilerplateFilter learned = BoilerplateFilter.learn(index, maxRatio, minSegments);
        LOG.atDebug().addKeyValue("suppressed", learned.size()).log("boilerplate learned");
        return new Matcher(index, config, learned);
    }

    /** The index matched against. */
    public Index index() {
        return index;
    }

    /** The configuration matched under. */
    public EngineConfig config() {
        return config;
    }

    /** The fingerprints ignored on both sides of every comparison. */
    public BoilerplateFilter boilerplate() {
        return boilerplate;
    }

    /**
     * The containment a pair has to clear to be reported.
     *
     * <p>Two instruments from one jurisdiction share text as a matter of routine: a bill amends a
     * chapter and re-prints the surrounding subsections. The floor is raised for that case rather
     * than the finding being dropped, because a wholesale copy inside one state is still worth seeing.
     */
    public static double containmentFloor(SegmentRef query, SegmentRef other, MatchConfig settings) {
        if (query.key().jurisdiction().equals(other.key().jurisdiction())) {
            return Math.max(settings.minContainment(), settings.sameJurisdictionContainment());
        }
        return settings.minContainment();
    }

    /**
     * True when a passage clears both floors on its own. The distinct-anchor floor matters: a
     * repeated phrase inside one long subsection produces many anchors from one shared fingerprint,
     * which would otherwise read as a long passage resting on a single coincidence.
     */
    public static boolean passageIsReportable(Passage passage, MatchConfig settings) {
        if (passage.length() < settings.minPassageChars()) {
            return false;
        }
        long distinct =
                passage.anchors().stream().mapToLong(Anchor::value).distinct().count();
        return distinct >= settings.minDistinctAnchors();
    }

    /** Match one fingerprinted segment against the index. */
    public List<SegmentMatch> matchRecord(SegmentFingerprint record) {
        MatchConfig settings = config.match();
        if (record.contentRatio() < settings.minContentRatio()) {
            return List.of();
        }
        List<Long> values = boilerplate.keeps(record);
        if (values.isEmpty()) {
            return List.of();
        }
        List<SegmentMatch> matches = new ArrayList<>();
        List<SegmentRef> candidates = index.candidates(record);
        for (SegmentRef ref : candidates.subList(0, Math.min(candidates.size(), settings.maxCandidates()))) {
            compare(record, values, ref).ifPresent(matches::add);
        }
        return ordered(matches);
    }

    private Optional<SegmentMatch> compare(SegmentFingerprint record, List<Long> values, SegmentRef ref) {
        MatchConfig settings = config.match();
        SegmentFingerprint other = index.record(ref);
        if (other.contentRatio() < settings.minContentRatio()) {
            return Optional.empty();
        }
        List<Long> otherValues = boilerplate.keeps(other);
        if (otherValues.isEmpty()) {
            return Optional.empty();
        }
        PairScore score = Scoring.scorePair(new HashSet<>(values), new HashSet<>(otherValues));
        if (score.bestContainment() < containmentFloor(SegmentRef.of(record), ref, settings)) {
            return Optional.empty();
        }
        List<Passage> passages =
                Align.align(boilerplate.strip(record), boilerplate.strip(other), settings.maxAnchorGap(), 2).stream()
                        .filter(passage -> passageIsReportable(passage, settings))
                        .toList();
        if (passages.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SegmentMatch(SegmentRef.of(record), record.path(), ref, other.path(), score, passages));
    }

    /** Match every body segment of {@code instrument} against the index. */
    public List<SegmentMatch> matchInstrument(Instrument instrument) {
        List<SegmentMatch> found = matchRecords(fingerprinter.fingerprint(instrument));
        LOG.atDebug()
                .addKeyValue("key", instrument.key().slug())
                .addKeyValue("matches", found.size())
                .log("instrument matched");
        return found;
    }

    /** Match every record against the index, ordered as configured. */
    public List<SegmentMatch> matchRecords(Iterable<SegmentFingerprint> records) {
        List<SegmentMatch> found = new ArrayList<>();
        for (SegmentFingerprint record : records) {
            found.addAll(matchRecord(record));
        }
        return ordered(found);
    }

    private List<SegmentMatch> ordered(List<SegmentMatch> matches) {
        List<SegmentMatch> sorted = new ArrayList<>(matches);
        sorted.sort(config.match().order().equals("length") ? BY_LENGTH : BY_SCORE);
        return sorted;
    }
}
