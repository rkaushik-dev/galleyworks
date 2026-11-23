package io.github.rkaushikdev.galleyworks.match;

import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.canon.Profiles;
import io.github.rkaushikdev.galleyworks.fingerprint.Shingle;
import io.github.rkaushikdev.galleyworks.fingerprint.Shingles;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The clauses that close an act in nearly every legislature, and that say nothing about copying.
 *
 * <p>A severability clause, a general repealer, an emergency clause, a savings clause and the
 * uniformity clause of a uniform act are drafted from the same manuals everywhere, so two
 * unrelated bills from two states share them word for word. A corpus large enough teaches the
 * matcher to ignore them; a corpus of a dozen bills is not large enough, so these are known up
 * front. Each is canonicalized and shingled under the engine's own settings, and every shingle it
 * produces is suppressed, not just the winnowed selection, because the selection depends on the
 * text around the clause.
 */
public final class StandardClauses {

    /** Each standard clause by name, in its most common wording. */
    public static final Map<String, String> CLAUSES = clauses();

    private StandardClauses() {}

    private static Map<String, String> clauses() {
        Map<String, String> clauses = new LinkedHashMap<>();
        clauses.put(
                "severability",
                "If any provision of this act or the application thereof to any person or circumstance is held"
                        + " invalid, the invalidity does not affect other provisions or applications of the act which"
                        + " can be given effect without the invalid provision or application, and to this end the"
                        + " provisions of this act are severable.");
        clauses.put("repealer", "All laws and parts of laws in conflict with this act are hereby repealed.");
        clauses.put(
                "emergency",
                "An emergency is declared to exist, and this act takes effect on passage and approval by the"
                        + " governor.");
        clauses.put(
                "savings",
                "This act does not affect rights and duties that matured, penalties that were incurred, and"
                        + " proceedings that were begun before the effective date of this act.");
        clauses.put(
                "uniformity",
                "In applying and construing this uniform act, consideration must be given to the need to promote"
                        + " uniformity of the law with respect to its subject matter among states that enact it.");
        return Collections.unmodifiableMap(clauses);
    }

    /** Every shingle the standard clauses produce under {@code config}. */
    public static Set<Long> fingerprints(EngineConfig config) {
        Set<Long> values = new HashSet<>();
        for (String clause : CLAUSES.values()) {
            String canonical = Profiles.canonicalize(clause, config.canon()).text();
            for (Shingle shingle : Shingles.shinglesFor(canonical, config.shingle())) {
                values.add(shingle.value());
            }
        }
        return values;
    }

    /** A filter suppressing the standard clauses under {@code config}. */
    public static BoilerplateFilter filter(EngineConfig config) {
        return BoilerplateFilter.of(fingerprints(config));
    }
}
