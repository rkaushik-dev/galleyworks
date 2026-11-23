package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.util.Spans;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * How much of a reported instrument one other jurisdiction accounts for.
 *
 * <p>The question after "how much of this bill is reused" is "from where". The characters are
 * distinct characters of the reported instrument, so a subsection matched against two Wyoming acts
 * counts once for Wyoming, and the shares of two jurisdictions can overlap where both printed the
 * same language.
 *
 * @param jurisdiction the jurisdiction the findings point at
 * @param characters distinct characters of the reported instrument found there
 * @param findings findings against that jurisdiction
 * @param instruments distinct instruments of that jurisdiction matched
 */
public record SourceShare(String jurisdiction, int characters, int findings, int instruments) {

    private static final Comparator<SourceShare> LARGEST_FIRST =
            Comparator.comparingInt(SourceShare::characters).reversed().thenComparing(SourceShare::jurisdiction);

    /** One share per jurisdiction {@code report}'s findings point at, the largest first. */
    public static List<SourceShare> of(InstrumentReport report) {
        Map<String, List<ReportedMatch>> byJurisdiction = new TreeMap<>();
        for (ReportedMatch match : report.matches()) {
            byJurisdiction
                    .computeIfAbsent(match.otherKey().jurisdiction(), ignored -> new ArrayList<>())
                    .add(match);
        }
        List<SourceShare> shares = new ArrayList<>();
        byJurisdiction.forEach((jurisdiction, matches) -> {
            List<TextSpan> covered = new ArrayList<>();
            Set<String> instruments = new HashSet<>();
            for (ReportedMatch match : matches) {
                match.passages().forEach(passage -> covered.add(passage.querySpan()));
                instruments.add(match.otherKey().slug());
            }
            shares.add(new SourceShare(jurisdiction, Spans.coverage(covered), matches.size(), instruments.size()));
        });
        shares.sort(LARGEST_FIRST);
        return List.copyOf(shares);
    }

    /** {@code wy: 663 characters in 2 findings against 1 instrument}. */
    public String describe() {
        return jurisdiction + ": " + characters + " characters in " + findings + " finding" + (findings == 1 ? "" : "s")
                + " against " + instruments + " instrument" + (instruments == 1 ? "" : "s");
    }
}
