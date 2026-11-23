package io.github.rkaushikdev.galleyworks.diffusion;

import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.JurisdictionRegistry;
import io.github.rkaushikdev.galleyworks.JurisdictionRelation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which way language moved along one edge of the reuse graph: across to a peer, down the hierarchy
 * of government, or up it.
 *
 * <p>Policy diffusion is studied along both axes. Horizontal diffusion is one state taking another's
 * statute; vertical diffusion runs between levels, a city implementing its state's act or, less
 * often, a state adopting what its cities tried first. The direction of an edge comes from the
 * dates, so an undirected edge between two levels is only known to be vertical.
 */
public enum Spread {
    HORIZONTAL("horizontal"),
    DOWNWARD("downward"),
    UPWARD("upward"),
    VERTICAL("vertical"),
    INTERNAL("internal");

    private final String value;

    Spread(String value) {
        this.value = value;
    }

    /** The lowercase name written in output. */
    public String value() {
        return value;
    }

    /** How the language on {@code edge} moved, under the hierarchy {@code jurisdictions} records. */
    public static Spread of(ReuseEdge edge, JurisdictionRegistry jurisdictions) {
        JurisdictionRelation relation = jurisdictions.relation(
                edge.target().jurisdiction(), edge.source().jurisdiction());
        return switch (relation) {
            case SAME -> INTERNAL;
            case SEPARATE -> HORIZONTAL;
            case ENCLOSING -> edge.directed() ? DOWNWARD : VERTICAL;
            case ENCLOSED -> edge.directed() ? UPWARD : VERTICAL;
        };
    }

    /** How many edges inside {@code lineage} moved each way, every kind present, in declaration order. */
    public static Map<Spread, Integer> tally(ReuseGraph graph, Lineage lineage, JurisdictionRegistry jurisdictions) {
        Set<InstrumentKey> members = new HashSet<>(lineage.members());
        Map<Spread, Integer> counts = new EnumMap<>(Spread.class);
        Arrays.stream(values()).forEach(kind -> counts.put(kind, 0));
        for (ReuseEdge edge : graph.edges()) {
            if (members.contains(edge.source()) && members.contains(edge.target())) {
                counts.merge(of(edge, jurisdictions), 1, Integer::sum);
            }
        }
        return counts;
    }

    /** {@code 2 horizontal, 1 downward}: the kinds that occur, in declaration order. */
    public static String describe(Map<Spread, Integer> counts) {
        List<String> parts = new ArrayList<>();
        counts.forEach((kind, count) -> {
            if (count > 0) {
                parts.add(count + " " + kind.value());
            }
        });
        return parts.isEmpty() ? "no edges" : String.join(", ", parts);
    }

    @Override
    public String toString() {
        return value;
    }
}
