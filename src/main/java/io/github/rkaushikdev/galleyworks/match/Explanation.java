package io.github.rkaushikdev.galleyworks.match;

import io.github.rkaushikdev.galleyworks.JurisdictionRegistry;
import io.github.rkaushikdev.galleyworks.JurisdictionRelation;
import io.github.rkaushikdev.galleyworks.MatchConfig;
import io.github.rkaushikdev.galleyworks.util.Floats;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Why one match was reported, in words.
 *
 * <p>A containment of 0.62 is not an argument. What persuades a reader is the shape of the
 * evidence: how many separate passages, how long the longest one is, whether they run in the same
 * order in both documents, and how the two enacting jurisdictions stand to each other: separate, the
 * same, or one inside the other. The explanation is derived, never stored, from the same
 * configuration the matcher ran under.
 *
 * @param confidence how strong the evidence is
 * @param headline the one-line verdict
 * @param reasons the observations behind it, in order
 */
public record Explanation(Confidence confidence, String headline, List<String> reasons) {

    private static final double WHOLESALE = 0.85;
    private static final double SUBSTANTIAL = 0.5;

    /** Copies the reasons. */
    public Explanation {
        reasons = List.copyOf(reasons);
    }

    /** True for strong evidence. */
    public boolean isStrong() {
        return confidence == Confidence.STRONG;
    }

    /** Describe the evidence behind {@code match} under the default thresholds. */
    public static Explanation explain(SegmentMatch match) {
        return explain(match, new MatchConfig());
    }

    /** Describe the evidence behind {@code match} under {@code settings}, knowing nothing of the hierarchy. */
    public static Explanation explain(SegmentMatch match, MatchConfig settings) {
        return explain(match, settings, new JurisdictionRegistry());
    }

    /**
     * Describe the evidence behind {@code match} under {@code settings}, reading from {@code
     * jurisdictions} how the two enacting bodies stand to each other.
     */
    public static Explanation explain(SegmentMatch match, MatchConfig settings, JurisdictionRegistry jurisdictions) {
        return explain(match, settings, jurisdictions, Optional.empty());
    }

    /**
     * Describe the evidence behind {@code match}, counting it in favour when both segments sit under
     * the same statute catchline, {@code sharedCatchline}. A catchline is drafted with its section
     * and travels with it, so two states printing the same one is a second, independent sign of the
     * same source.
     */
    public static Explanation explain(
            SegmentMatch match,
            MatchConfig settings,
            JurisdictionRegistry jurisdictions,
            Optional<String> sharedCatchline) {
        double containment = match.score().bestContainment();
        int longest = match.longestPassage();
        List<String> reasons = new ArrayList<>();
        reasons.add(Floats.percent(containment, 0) + " of the smaller fingerprint set is shared");
        reasons.add("the longest shared passage runs " + longest + " characters");
        int score = 0;
        if (containment >= WHOLESALE) {
            score += 2;
        } else if (containment >= Math.max(settings.minContainment(), SUBSTANTIAL)) {
            score += 1;
        }
        if (longest >= settings.minPassageChars() * 4) {
            score += 2;
            reasons.add("that passage is several times the reporting floor");
        } else if (longest >= settings.minPassageChars() * 2) {
            score += 1;
        }
        if (match.passageCount() >= 3) {
            score += 1;
            reasons.add("the text matches in " + match.passageCount() + " separate places");
        }
        if (orderedAlike(match)) {
            if (match.passageCount() >= 2) {
                reasons.add("the passages appear in the same order in both documents");
            }
        } else {
            score -= 1;
            reasons.add("the passages appear in a different order, which weakens the case");
        }
        String query = match.query().key().jurisdiction();
        String other = match.other().key().jurisdiction();
        JurisdictionRelation relation = jurisdictions.relation(query, other);
        reasons.add(
                switch (relation) {
                    case SAME -> "both instruments are from the same jurisdiction, where reuse is routine";
                    case ENCLOSING ->
                        query + " sits under " + other
                                + ", and repeating the enclosing jurisdiction's text is usually compliance";
                    case ENCLOSED ->
                        other + " sits under " + query + ", so the language moved up from a jurisdiction inside it";
                    case SEPARATE ->
                        "the two instruments are from different jurisdictions (" + query + " and " + other + ")";
                });
        if (relation.isRoutine()) {
            score -= 1;
        }
        if (sharedCatchline.isPresent()) {
            score += 1;
            reasons.add("both sit under the catchline \"" + sharedCatchline.get() + "\"");
        }
        Confidence confidence = score >= 4 ? Confidence.STRONG : score >= 2 ? Confidence.MODERATE : Confidence.WEAK;
        String headline = confidence.value() + " evidence that " + match.queryPath() + " reuses "
                + match.other().key().slug() + " " + match.otherPath();
        return new Explanation(confidence, headline, reasons);
    }

    /** True when the passages appear in the same order in both documents. */
    private static boolean orderedAlike(SegmentMatch match) {
        if (match.passages().size() < 2) {
            return true;
        }
        List<Passage> byQuery = new ArrayList<>(match.passages());
        byQuery.sort(Comparator.comparingInt(passage -> passage.query().start()));
        List<Integer> starts =
                byQuery.stream().map(passage -> passage.other().start()).toList();
        List<Integer> sorted = new ArrayList<>(starts);
        sorted.sort(null);
        return starts.equals(sorted);
    }
}
