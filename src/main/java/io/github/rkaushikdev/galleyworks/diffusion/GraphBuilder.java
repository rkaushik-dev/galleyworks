package io.github.rkaushikdev.galleyworks.diffusion;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.match.Passage;
import io.github.rkaushikdev.galleyworks.match.SegmentMatch;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Turning matches into a dated, directed reuse graph.
 *
 * <p>Direction comes from the calendar and nothing else. A match says two instruments share text;
 * it cannot say which one wrote it, and inferring direction from length or score would be inventing
 * evidence. When both instruments carry an effective date the earlier one is the source. Two acts
 * often take effect on the same day, the first of July being the default in many states, and then
 * the earlier introduction settles it: the bill filed first is the one the other could have copied.
 * Otherwise the edge is undirected with a stable source chosen by slug, so a run over the same
 * corpus produces the same graph.
 */
public final class GraphBuilder {

    private GraphBuilder() {}

    /**
     * One pair, oriented.
     *
     * @param source the instrument the edge leaves
     * @param target the instrument the edge reaches
     * @param directed whether dates settled the order
     */
    public record Orientation(InstrumentKey source, InstrumentKey target, boolean directed) {}

    /**
     * What the corpus knows about when one instrument happened.
     *
     * @param effective when it took effect, or the best date standing in for that
     * @param introduced when it was introduced
     */
    public record Chronology(Optional<LocalDate> effective, Optional<LocalDate> introduced) {

        /** Nothing known. */
        public static final Chronology UNKNOWN = new Chronology(Optional.empty(), Optional.empty());

        /** The dates {@code instrument} carries. */
        public static Chronology of(Instrument instrument) {
            return new Chronology(instrument.effectiveDate(), instrument.introducedOn());
        }

        /** A chronology with only an effective date. */
        public static Chronology effectiveOn(LocalDate when) {
            return new Chronology(Optional.ofNullable(when), Optional.empty());
        }
    }

    private static final List<Function<Chronology, Optional<LocalDate>>> CLOCKS =
            List.of(Chronology::effective, Chronology::introduced);

    /** The source, target and directedness of one pair under effective {@code dates} alone. */
    public static Orientation orient(InstrumentKey left, InstrumentKey right, Map<String, LocalDate> dates) {
        return orient(
                left,
                right,
                Chronology.effectiveOn(dates.get(left.slug())),
                Chronology.effectiveOn(dates.get(right.slug())));
    }

    /**
     * The source, target and directedness of one pair. The effective dates decide; equal ones defer
     * to the introduction dates; a date missing on either side at the step that has to decide leaves
     * the edge undirected.
     */
    public static Orientation orient(
            InstrumentKey left, InstrumentKey right, Chronology leftWhen, Chronology rightWhen) {
        for (Function<Chronology, Optional<LocalDate>> clock : CLOCKS) {
            Optional<LocalDate> leftDate = clock.apply(leftWhen);
            Optional<LocalDate> rightDate = clock.apply(rightWhen);
            if (leftDate.isEmpty() || rightDate.isEmpty()) {
                break;
            }
            int order = leftDate.get().compareTo(rightDate.get());
            if (order != 0) {
                return order < 0 ? new Orientation(left, right, true) : new Orientation(right, left, true);
            }
        }
        if (left.slug().compareTo(right.slug()) <= 0) {
            return new Orientation(left, right, false);
        }
        return new Orientation(right, left, false);
    }

    /** One edge from one match, oriented under {@code dates}. */
    public static ReuseEdge edgeFor(SegmentMatch match, Map<String, Chronology> dates) {
        InstrumentKey query = match.query().key();
        InstrumentKey other = match.other().key();
        Orientation oriented = orient(
                query,
                other,
                dates.getOrDefault(query.slug(), Chronology.UNKNOWN),
                dates.getOrDefault(other.slug(), Chronology.UNKNOWN));
        int characters = match.passages().stream().mapToInt(Passage::length).sum();
        return new ReuseEdge(
                oriented.source(),
                oriented.target(),
                match.passages().size(),
                characters,
                match.score().bestContainment(),
                oriented.directed());
    }

    /** Fold every match into one graph with no dates. */
    public static ReuseGraph buildGraph(Iterable<SegmentMatch> matches) {
        return buildGraph(matches, List.of());
    }

    /** Fold every match into one graph, dated from {@code instruments}. */
    public static ReuseGraph buildGraph(Iterable<SegmentMatch> matches, Iterable<Instrument> instruments) {
        ReuseGraph graph = new ReuseGraph();
        Map<String, Chronology> dates = new HashMap<>();
        for (Instrument instrument : instruments) {
            graph.note(instrument.key(), instrument.effectiveDate(), instrument.introducedOn());
            dates.put(instrument.key().slug(), Chronology.of(instrument));
        }
        for (SegmentMatch match : matches) {
            graph.add(edgeFor(match, dates));
        }
        return graph;
    }
}
