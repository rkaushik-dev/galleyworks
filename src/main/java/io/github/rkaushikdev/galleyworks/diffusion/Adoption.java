package io.github.rkaushikdev.galleyworks.diffusion;

import io.github.rkaushikdev.galleyworks.InstrumentKey;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * One jurisdiction's adoption of a family's language: the first instrument it enacted it in, and how
 * often it enacted it again.
 *
 * <p>Diffusion is counted in adopters, not instruments. A state that enacts a model act and amends
 * it twice is one adoption with two re-enactments, and counting it three times would make a policy
 * look like it spread when it only stayed. The cumulative adoption curve, adopters by year, is the
 * figure the diffusion literature draws: a slow start, a steep middle, a long tail.
 *
 * @param jurisdiction the adopting jurisdiction
 * @param first its earliest member of the family
 * @param when when that member took effect, if known
 * @param reenactments its later members in the family
 */
public record Adoption(String jurisdiction, InstrumentKey first, Optional<LocalDate> when, int reenactments) {

    /** The adoption in each jurisdiction of {@code lineage}, earliest first, undated ones last by slug. */
    public static List<Adoption> adoptions(ReuseGraph graph, Lineage lineage) {
        List<InstrumentKey> ordered = new ArrayList<>(lineage.members());
        ordered.sort(Comparator.comparing(
                        (InstrumentKey member) -> graph.dateOf(member).orElse(LocalDate.MAX))
                .thenComparing(InstrumentKey::slug));
        Map<String, Adoption> found = new LinkedHashMap<>();
        for (InstrumentKey member : ordered) {
            Adoption earlier = found.get(member.jurisdiction());
            found.put(
                    member.jurisdiction(),
                    earlier == null
                            ? new Adoption(member.jurisdiction(), member, graph.dateOf(member), 0)
                            : new Adoption(
                                    earlier.jurisdiction(),
                                    earlier.first(),
                                    earlier.when(),
                                    earlier.reenactments() + 1));
        }
        return List.copyOf(found.values());
    }

    /** True when {@code member} is a later enactment in a jurisdiction that had already adopted. */
    public static boolean isReenactment(List<Adoption> adoptions, InstrumentKey member) {
        return adoptions.stream()
                .anyMatch(adoption -> adoption.jurisdiction().equals(member.jurisdiction())
                        && !adoption.first().equals(member));
    }

    /** Jurisdictions that had adopted by the end of each year with a dated adoption, cumulatively. */
    public static SortedMap<Integer, Integer> curve(List<Adoption> adoptions) {
        SortedMap<Integer, Integer> yearly = new TreeMap<>();
        adoptions.forEach(adoption -> adoption.when().ifPresent(when -> yearly.merge(when.getYear(), 1, Integer::sum)));
        SortedMap<Integer, Integer> cumulative = new TreeMap<>();
        int total = 0;
        for (Map.Entry<Integer, Integer> year : yearly.entrySet()) {
            total += year.getValue();
            cumulative.put(year.getKey(), total);
        }
        return Collections.unmodifiableSortedMap(cumulative);
    }
}
