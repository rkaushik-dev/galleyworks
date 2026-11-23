package io.github.rkaushikdev.galleyworks.diffusion;

import io.github.rkaushikdev.galleyworks.InstrumentKey;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The reuse graph: nodes are instruments, and an edge means "this much text of the target already
 * appears in the source", collapsed to one edge per pair.
 *
 * <p>Edges are directed even though a match is symmetric, because the question the graph exists to
 * answer is which instrument came first. Direction is assigned from enactment dates, not from the
 * match, and an edge between two instruments with no usable dates is recorded as undirected.
 * Iterating the graph yields its edges ordered by their slug pair.
 */
public final class ReuseGraph implements Iterable<ReuseEdge> {

    private static final Comparator<ReuseEdge> BY_PAIR = Comparator.comparing(
                    (ReuseEdge edge) -> edge.source().slug())
            .thenComparing(edge -> edge.target().slug());

    private final Map<List<String>, ReuseEdge> edges = new HashMap<>();
    private final Map<String, Optional<LocalDate>> dates = new HashMap<>();
    private final Map<String, LocalDate> introductions = new HashMap<>();
    private final Map<String, InstrumentKey> keys = new HashMap<>();

    /** Number of edges. */
    public int size() {
        return edges.size();
    }

    @Override
    public Iterator<ReuseEdge> iterator() {
        return edges().iterator();
    }

    /** Record an instrument with no known date. */
    public void note(InstrumentKey key) {
        note(key, Optional.empty());
    }

    /** Record an instrument and, when known, the date it took effect; a date is never forgotten. */
    public void note(InstrumentKey key, Optional<LocalDate> enacted) {
        keys.put(key.slug(), key);
        if (enacted.isPresent() || !dates.containsKey(key.slug())) {
            dates.put(key.slug(), enacted);
        }
    }

    /** Record an instrument with its effective date and, when known, its introduction date. */
    public void note(InstrumentKey key, Optional<LocalDate> enacted, Optional<LocalDate> introduced) {
        note(key, enacted);
        introduced.ifPresent(when -> introductions.put(key.slug(), when));
    }

    /** The date {@code key} was introduced, if known. */
    public Optional<LocalDate> introducedOn(InstrumentKey key) {
        return Optional.ofNullable(introductions.get(key.slug()));
    }

    /** The date {@code key} took effect, if known. */
    public Optional<LocalDate> dateOf(InstrumentKey key) {
        return dates.getOrDefault(key.slug(), Optional.empty());
    }

    /** Add or merge one edge, returning the edge as it now stands. */
    public ReuseEdge add(ReuseEdge edge) {
        note(edge.source());
        note(edge.target());
        ReuseEdge existing = edges.get(edge.pair());
        ReuseEdge merged = existing == null ? edge : existing.merged(edge);
        edges.put(edge.pair(), merged);
        return merged;
    }

    /** Add every edge, returning how many were added. */
    public int addAll(Iterable<ReuseEdge> batch) {
        int count = 0;
        for (ReuseEdge edge : batch) {
            add(edge);
            count++;
        }
        return count;
    }

    /** Every instrument, sorted. */
    public List<InstrumentKey> nodes() {
        List<InstrumentKey> sorted = new ArrayList<>(keys.values());
        sorted.sort(null);
        return sorted;
    }

    /** Every edge, ordered by slug pair. */
    public List<ReuseEdge> edges() {
        List<ReuseEdge> sorted = new ArrayList<>(edges.values());
        sorted.sort(BY_PAIR);
        return sorted;
    }

    /** The edges leaving {@code key}. */
    public List<ReuseEdge> outEdges(InstrumentKey key) {
        return edges().stream().filter(edge -> edge.source().equals(key)).toList();
    }

    /** The edges arriving at {@code key}. */
    public List<ReuseEdge> inEdges(InstrumentKey key) {
        return edges().stream().filter(edge -> edge.target().equals(key)).toList();
    }

    /** The edge from {@code source} to {@code target}, if there is one. */
    public Optional<ReuseEdge> edgeBetween(InstrumentKey source, InstrumentKey target) {
        return Optional.ofNullable(edges.get(List.of(source.slug(), target.slug())));
    }

    /** Every instrument joined to {@code key} in either direction. */
    public Set<InstrumentKey> neighbours(InstrumentKey key) {
        Set<InstrumentKey> found = new HashSet<>();
        for (ReuseEdge edge : edges.values()) {
            if (edge.source().equals(key)) {
                found.add(edge.target());
            } else if (edge.target().equals(key)) {
                found.add(edge.source());
            }
        }
        return found;
    }

    /** The sum of every edge's weight. */
    public double totalWeight() {
        double total = 0.0;
        for (ReuseEdge edge : edges()) {
            total += edge.weight();
        }
        return total;
    }
}
