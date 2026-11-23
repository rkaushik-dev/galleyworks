package io.github.rkaushikdev.galleyworks.diffusion;

import io.github.rkaushikdev.galleyworks.InstrumentKey;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * What one family looks like once it is measured: how far the language travelled, how many
 * jurisdictions adopted it and how often they came back to it, over how long. Adopters are counted
 * once each, by {@link Adoption}, however many times a legislature re-enacts the text.
 *
 * @param members instruments in the family
 * @param jurisdictions distinct jurisdictions among them, which is the number of adopters
 * @param reenactments members enacted in a jurisdiction that had already adopted the language
 * @param edges edges inside the family
 * @param directedEdges edges the dates oriented
 * @param characters reused characters across every edge
 * @param first the earliest member date
 * @param last the latest member date
 */
public record DiffusionMetrics(
        int members,
        int jurisdictions,
        int reenactments,
        int edges,
        int directedEdges,
        int characters,
        Optional<LocalDate> first,
        Optional<LocalDate> last) {

    /** Days between the first and last dated member, when both are known. */
    public OptionalLong spanDays() {
        if (first.isEmpty() || last.isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(ChronoUnit.DAYS.between(first.get(), last.get()));
    }

    /** True when the family never left one jurisdiction: one adopter, however many enactments. */
    public boolean isSingleJurisdiction() {
        return jurisdictions <= 1;
    }

    /** Measure one family inside {@code graph}. */
    public static DiffusionMetrics measure(ReuseGraph graph, Lineage lineage) {
        Set<InstrumentKey> members = new HashSet<>(lineage.members());
        List<ReuseEdge> edges = graph.edges().stream()
                .filter(edge -> members.contains(edge.source()) && members.contains(edge.target()))
                .toList();
        List<LocalDate> dates =
                members.stream().map(graph::dateOf).flatMap(Optional::stream).toList();
        List<Adoption> adoptions = Adoption.adoptions(graph, lineage);
        return new DiffusionMetrics(
                members.size(),
                adoptions.size(),
                adoptions.stream().mapToInt(Adoption::reenactments).sum(),
                edges.size(),
                (int) edges.stream().filter(ReuseEdge::directed).count(),
                edges.stream().mapToInt(ReuseEdge::characters).sum(),
                dates.stream().min(LocalDate::compareTo),
                dates.stream().max(LocalDate::compareTo));
    }

    /** Drop directed edges that a longer directed path already implies. */
    public static ReuseGraph transitiveReduction(ReuseGraph graph) {
        ReuseGraph reduced = new ReuseGraph();
        for (InstrumentKey member : graph.nodes()) {
            reduced.note(member, graph.dateOf(member), graph.introducedOn(member));
        }
        for (ReuseEdge edge : graph.edges()) {
            if (edge.directed() && reaches(graph, edge.source(), edge.target(), edge.pair())) {
                continue;
            }
            reduced.add(edge);
        }
        return reduced;
    }

    /** Is {@code goal} reachable from {@code start} along directed edges without using {@code skip}? */
    private static boolean reaches(ReuseGraph graph, InstrumentKey start, InstrumentKey goal, List<String> skip) {
        java.util.Deque<InstrumentKey> stack = new java.util.ArrayDeque<>(List.of(start));
        Set<String> seen = new HashSet<>(List.of(start.slug()));
        while (!stack.isEmpty()) {
            InstrumentKey current = stack.pop();
            for (ReuseEdge edge : graph.outEdges(current)) {
                if (!edge.directed() || edge.pair().equals(skip)) {
                    continue;
                }
                if (edge.target().equals(goal)) {
                    return true;
                }
                if (seen.add(edge.target().slug())) {
                    stack.push(edge.target());
                }
            }
        }
        return false;
    }
}
