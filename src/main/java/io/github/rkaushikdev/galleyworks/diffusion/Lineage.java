package io.github.rkaushikdev.galleyworks.diffusion;

import io.github.rkaushikdev.galleyworks.InstrumentKey;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * One family of instruments sharing text, directly or through intermediaries, rooted at its
 * earliest dated member: the best available candidate for where the language originated.
 *
 * <p>"Best available" is doing real work: the earliest instrument in the corpus is only the
 * earliest one indexed, and a model bill drafted by an association that never appears in any
 * legislature will never be the root. {@link #confident()} says whether the dates support the claim
 * at all.
 *
 * @param root the earliest dated member, if any member is dated
 * @param members every member, sorted
 * @param rootDate when the root took effect
 * @param datedMembers members with a known date
 */
public record Lineage(
        Optional<InstrumentKey> root, List<InstrumentKey> members, Optional<LocalDate> rootDate, int datedMembers) {

    /** Copies the members. */
    public Lineage {
        members = List.copyOf(members);
    }

    /** Number of members. */
    public int size() {
        return members.size();
    }

    /** The members' jurisdictions, sorted and distinct. */
    public List<String> jurisdictions() {
        return List.copyOf(
                new TreeSet<>(members.stream().map(InstrumentKey::jurisdiction).toList()));
    }

    /** True when the family crosses more than one jurisdiction. */
    public boolean spreads() {
        return jurisdictions().size() > 1;
    }

    /** True only when every member is dated, so the root is not a guess. */
    public boolean confident() {
        return root.isPresent() && datedMembers == size();
    }

    /** Connected components of the graph, ignoring edge direction, largest first. */
    public static List<List<InstrumentKey>> components(ReuseGraph graph) {
        Set<String> seen = new HashSet<>();
        List<List<InstrumentKey>> found = new ArrayList<>();
        for (InstrumentKey start : graph.nodes()) {
            if (!seen.add(start.slug())) {
                continue;
            }
            Deque<InstrumentKey> stack = new ArrayDeque<>(List.of(start));
            List<InstrumentKey> member = new ArrayList<>();
            while (!stack.isEmpty()) {
                InstrumentKey current = stack.pop();
                member.add(current);
                for (InstrumentKey neighbour : graph.neighbours(current)) {
                    if (seen.add(neighbour.slug())) {
                        stack.push(neighbour);
                    }
                }
            }
            member.sort(null);
            found.add(member);
        }
        found.sort(Comparator.comparingInt((List<InstrumentKey> group) -> group.size())
                .reversed()
                .thenComparing(group -> group.getFirst().slug()));
        return found;
    }

    /**
     * The earliest dated instrument among {@code members}. Two that took effect the same day are
     * ordered by introduction, an introduced one before one whose introduction is not known, and the
     * slug breaks what is left, which is the order {@link GraphBuilder} orients edges in.
     */
    public static Optional<InstrumentKey> earliest(ReuseGraph graph, Iterable<InstrumentKey> members) {
        Comparator<InstrumentKey> order = Comparator.comparing(
                        (InstrumentKey member) -> graph.dateOf(member).orElseThrow())
                .thenComparing(
                        member -> graph.introducedOn(member),
                        Comparator.comparing((Optional<LocalDate> when) -> when.orElse(LocalDate.MAX)))
                .thenComparing(InstrumentKey::slug);
        List<InstrumentKey> dated = new ArrayList<>();
        members.forEach(member -> {
            if (graph.dateOf(member).isPresent()) {
                dated.add(member);
            }
        });
        return dated.stream().min(order);
    }

    /** Split the graph into families, each with its earliest member as root. */
    public static List<Lineage> lineages(ReuseGraph graph) {
        List<Lineage> found = new ArrayList<>();
        for (List<InstrumentKey> group : components(graph)) {
            Optional<InstrumentKey> root = earliest(graph, group);
            int dated = (int) group.stream()
                    .filter(member -> graph.dateOf(member).isPresent())
                    .count();
            found.add(new Lineage(root, group, root.flatMap(graph::dateOf), dated));
        }
        return found;
    }
}
