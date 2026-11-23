package io.github.rkaushikdev.galleyworks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Thresholds applied after candidate generation.
 *
 * @param minContainment the min containment setting
 * @param sameJurisdictionContainment the same jurisdiction containment setting
 * @param minPassageChars the min passage chars setting
 * @param minContentRatio the min content ratio setting
 * @param minDistinctAnchors the min distinct anchors setting
 * @param maxAnchorGap the max anchor gap setting
 * @param maxCandidates the max candidates setting
 * @param order the order setting
 * @param ignoreStandardClauses whether severability, repealer and other standard clauses are
 *     ignored as boilerplate before any corpus has been learned
 */
public record MatchConfig(
        double minContainment,
        double sameJurisdictionContainment,
        int minPassageChars,
        double minContentRatio,
        int minDistinctAnchors,
        int maxAnchorGap,
        int maxCandidates,
        String order,
        boolean ignoreStandardClauses) {

    static final List<String> FIELDS = List.of(
            "min_containment",
            "same_jurisdiction_containment",
            "min_passage_chars",
            "min_content_ratio",
            "min_distinct_anchors",
            "max_anchor_gap",
            "max_candidates",
            "order",
            "ignore_standard_clauses");

    /** Validates every field. */
    public MatchConfig {
        Objects.requireNonNull(order, "order");
        ConfigChecks.atLeast("min_containment", minContainment, 0.0);
        ConfigChecks.atMost("min_containment", minContainment, 1.0);
        ConfigChecks.atLeast("same_jurisdiction_containment", sameJurisdictionContainment, 0.0);
        ConfigChecks.atMost("same_jurisdiction_containment", sameJurisdictionContainment, 1.0);
        ConfigChecks.atLeast("min_passage_chars", minPassageChars, 1);
        ConfigChecks.atLeast("min_content_ratio", minContentRatio, 0.0);
        ConfigChecks.atMost("min_content_ratio", minContentRatio, 1.0);
        ConfigChecks.atLeast("min_distinct_anchors", minDistinctAnchors, 1);
        ConfigChecks.atLeast("max_anchor_gap", maxAnchorGap, 0);
        ConfigChecks.atLeast("max_candidates", maxCandidates, 1);
        ConfigChecks.oneOf("order", order, List.of("score", "length"));
    }

    /** Every field at its default. */
    public MatchConfig() {
        this(0.35, 0.6, 90, 0.25, 2, 40, 200, "score", true);
    }

    /** This configuration with minContainment replaced. */
    public MatchConfig withMinContainment(double value) {
        return new MatchConfig(
                value,
                sameJurisdictionContainment,
                minPassageChars,
                minContentRatio,
                minDistinctAnchors,
                maxAnchorGap,
                maxCandidates,
                order,
                ignoreStandardClauses);
    }
    /** This configuration with sameJurisdictionContainment replaced. */
    public MatchConfig withSameJurisdictionContainment(double value) {
        return new MatchConfig(
                minContainment,
                value,
                minPassageChars,
                minContentRatio,
                minDistinctAnchors,
                maxAnchorGap,
                maxCandidates,
                order,
                ignoreStandardClauses);
    }
    /** This configuration with minPassageChars replaced. */
    public MatchConfig withMinPassageChars(int value) {
        return new MatchConfig(
                minContainment,
                sameJurisdictionContainment,
                value,
                minContentRatio,
                minDistinctAnchors,
                maxAnchorGap,
                maxCandidates,
                order,
                ignoreStandardClauses);
    }
    /** This configuration with minContentRatio replaced. */
    public MatchConfig withMinContentRatio(double value) {
        return new MatchConfig(
                minContainment,
                sameJurisdictionContainment,
                minPassageChars,
                value,
                minDistinctAnchors,
                maxAnchorGap,
                maxCandidates,
                order,
                ignoreStandardClauses);
    }
    /** This configuration with minDistinctAnchors replaced. */
    public MatchConfig withMinDistinctAnchors(int value) {
        return new MatchConfig(
                minContainment,
                sameJurisdictionContainment,
                minPassageChars,
                minContentRatio,
                value,
                maxAnchorGap,
                maxCandidates,
                order,
                ignoreStandardClauses);
    }
    /** This configuration with maxAnchorGap replaced. */
    public MatchConfig withMaxAnchorGap(int value) {
        return new MatchConfig(
                minContainment,
                sameJurisdictionContainment,
                minPassageChars,
                minContentRatio,
                minDistinctAnchors,
                value,
                maxCandidates,
                order,
                ignoreStandardClauses);
    }
    /** This configuration with maxCandidates replaced. */
    public MatchConfig withMaxCandidates(int value) {
        return new MatchConfig(
                minContainment,
                sameJurisdictionContainment,
                minPassageChars,
                minContentRatio,
                minDistinctAnchors,
                maxAnchorGap,
                value,
                order,
                ignoreStandardClauses);
    }
    /** This configuration with order replaced. */
    public MatchConfig withOrder(String value) {
        return new MatchConfig(
                minContainment,
                sameJurisdictionContainment,
                minPassageChars,
                minContentRatio,
                minDistinctAnchors,
                maxAnchorGap,
                maxCandidates,
                value,
                ignoreStandardClauses);
    }

    /** This configuration with ignoreStandardClauses replaced. */
    public MatchConfig withIgnoreStandardClauses(boolean value) {
        return new MatchConfig(
                minContainment,
                sameJurisdictionContainment,
                minPassageChars,
                minContentRatio,
                minDistinctAnchors,
                maxAnchorGap,
                maxCandidates,
                order,
                value);
    }

    /** The fields as a mapping, in declaration order, with plain values. */
    public Map<String, Object> toMapping() {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("min_containment", minContainment);
        mapping.put("same_jurisdiction_containment", sameJurisdictionContainment);
        mapping.put("min_passage_chars", minPassageChars);
        mapping.put("min_content_ratio", minContentRatio);
        mapping.put("min_distinct_anchors", minDistinctAnchors);
        mapping.put("max_anchor_gap", maxAnchorGap);
        mapping.put("max_candidates", maxCandidates);
        mapping.put("order", order);
        mapping.put("ignore_standard_clauses", ignoreStandardClauses);
        return mapping;
    }

    /** Read the {@code section} of a configuration mapping, recording problems. */
    static MatchConfig read(Object value, String section, List<String> problems) {
        MatchConfig defaults = new MatchConfig();
        ConfigFields fields = ConfigFields.of(value, section, java.util.Set.copyOf(FIELDS), problems);
        return fields.validated(
                "",
                defaults,
                () -> new MatchConfig(
                        fields.number("min_containment", defaults.minContainment()),
                        fields.number("same_jurisdiction_containment", defaults.sameJurisdictionContainment()),
                        fields.integer("min_passage_chars", defaults.minPassageChars()),
                        fields.number("min_content_ratio", defaults.minContentRatio()),
                        fields.integer("min_distinct_anchors", defaults.minDistinctAnchors()),
                        fields.integer("max_anchor_gap", defaults.maxAnchorGap()),
                        fields.integer("max_candidates", defaults.maxCandidates()),
                        fields.choice("order", defaults.order(), List.of("score", "length")),
                        fields.flag("ignore_standard_clauses", defaults.ignoreStandardClauses())));
    }
}
