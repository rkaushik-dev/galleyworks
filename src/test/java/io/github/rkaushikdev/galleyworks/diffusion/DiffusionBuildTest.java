package io.github.rkaushikdev.galleyworks.diffusion;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.fingerprints;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.diffusion.GraphBuilder.Chronology;
import io.github.rkaushikdev.galleyworks.diffusion.GraphBuilder.Orientation;
import io.github.rkaushikdev.galleyworks.index.MemoryIndex;
import io.github.rkaushikdev.galleyworks.match.Matcher;
import io.github.rkaushikdev.galleyworks.match.SegmentMatch;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DiffusionBuildTest {

    private static final InstrumentKey WY = new InstrumentKey("wy", "2025", Chamber.LOWER, "HB0142");
    private static final InstrumentKey MT = new InstrumentKey("mt", "2025", Chamber.LOWER, "HB0311");

    private static Instrument earlier() {
        return makeBill("wy", "HB0142", MODEL_CLAUSE, LocalDate.of(2024, 7, 1));
    }

    private static Instrument later() {
        return makeBill("mt", "HB0311", MODEL_CLAUSE, LocalDate.of(2025, 7, 1));
    }

    private static List<SegmentMatch> matches(Instrument source, Instrument probe) {
        MemoryIndex index = new MemoryIndex();
        index.addAll(fingerprints(source));
        return new Matcher(index).matchInstrument(probe);
    }

    @Test
    void theEarlierInstrumentIsTheSource() {
        Map<String, LocalDate> dates = Map.of(WY.slug(), LocalDate.of(2024, 3, 1), MT.slug(), LocalDate.of(2025, 3, 1));
        assertThat(GraphBuilder.orient(MT, WY, dates)).isEqualTo(new Orientation(WY, MT, true));
        assertThat(GraphBuilder.orient(WY, MT, dates)).isEqualTo(new Orientation(WY, MT, true));
    }

    @Test
    void aMissingDateLeavesTheEdgeUndirected() {
        Orientation oriented = GraphBuilder.orient(MT, WY, Map.of(WY.slug(), LocalDate.of(2024, 3, 1)));
        assertThat(oriented.directed()).isFalse();
        assertThat(oriented.source()).isEqualTo(MT);
        assertThat(oriented.target()).isEqualTo(WY);
    }

    @Test
    void equalDatesLeaveTheEdgeUndirected() {
        Map<String, LocalDate> dates = Map.of(WY.slug(), LocalDate.of(2025, 3, 1), MT.slug(), LocalDate.of(2025, 3, 1));
        assertThat(GraphBuilder.orient(WY, MT, dates).directed()).isFalse();
    }

    @Test
    void undirectedOrientationIsStable() {
        assertThat(GraphBuilder.orient(WY, MT, Map.of())).isEqualTo(GraphBuilder.orient(MT, WY, Map.of()));
    }

    @Test
    void buildingFromARealMatch() {
        ReuseGraph graph = GraphBuilder.buildGraph(matches(earlier(), later()), List.of(earlier(), later()));
        assertThat(graph.size()).isGreaterThanOrEqualTo(1);
        ReuseEdge edge = graph.edges().getFirst();
        assertThat(edge.source().jurisdiction()).isEqualTo("wy");
        assertThat(edge.target().jurisdiction()).isEqualTo("mt");
        assertThat(edge.directed()).isTrue();
    }

    @Test
    void theGraphRecordsDates() {
        Instrument earlier = earlier();
        assertThat(GraphBuilder.buildGraph(List.of(), List.of(earlier)).dateOf(earlier.key()))
                .contains(LocalDate.of(2024, 7, 1));
    }

    @Test
    void edgesCarryTheReusedCharacters() {
        ReuseEdge edge = GraphBuilder.buildGraph(matches(earlier(), later()), List.of(earlier(), later()))
                .edges()
                .getFirst();
        assertThat(edge.characters()).isPositive();
        assertThat(edge.passages()).isPositive();
        assertThat(edge.weight()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
    }

    @Test
    void severalMatchesOnOnePairCollapseToOneEdge() {
        List<SegmentMatch> found = matches(earlier(), later());
        ReuseGraph graph = GraphBuilder.buildGraph(found, List.of(earlier(), later()));
        assertThat(graph.size()).isEqualTo(1);
        assertThat(graph.edges().getFirst().passages()).isGreaterThanOrEqualTo(found.size());
    }

    @Test
    void buildingFromNothing() {
        ReuseGraph graph = GraphBuilder.buildGraph(List.of(), List.of());
        assertThat(graph.size()).isZero();
        assertThat(graph.nodes()).isEmpty();
    }

    @Test
    void undatedInstrumentsGiveAnUndirectedEdge() {
        Instrument earlier = makeBill("wy", "HB0142", MODEL_CLAUSE);
        Instrument later = makeBill("mt", "HB0311", MODEL_CLAUSE);
        ReuseGraph graph = GraphBuilder.buildGraph(matches(earlier, later), List.of(earlier, later));
        assertThat(graph.edges().getFirst().directed()).isFalse();
    }

    private static final LocalDate JULY = LocalDate.of(2025, 7, 1);

    private static Chronology dated(LocalDate effective, LocalDate introduced) {
        return new Chronology(Optional.ofNullable(effective), Optional.ofNullable(introduced));
    }

    private static Instrument introduced(Instrument bill, LocalDate when) {
        return new Instrument(
                bill.key(),
                bill.title(),
                bill.rawText(),
                bill.segments(),
                Optional.of(when),
                bill.enactedOn(),
                bill.sourceFormat());
    }

    @Test
    void aSharedEffectiveDateDefersToTheEarlierIntroduction() {
        Orientation oriented = GraphBuilder.orient(
                WY, MT, dated(JULY, LocalDate.of(2025, 2, 3)), dated(JULY, LocalDate.of(2025, 1, 14)));
        assertThat(oriented).isEqualTo(new Orientation(MT, WY, true));
    }

    @Test
    void theEffectiveDateStillDecidesBeforeTheIntroduction() {
        Orientation oriented = GraphBuilder.orient(
                WY,
                MT,
                dated(LocalDate.of(2024, 7, 1), LocalDate.of(2024, 2, 1)),
                dated(JULY, LocalDate.of(2023, 1, 1)));
        assertThat(oriented).isEqualTo(new Orientation(WY, MT, true));
    }

    @Test
    void aSharedDayWithOneIntroductionUnknownStaysUndirected() {
        assertThat(GraphBuilder.orient(WY, MT, dated(JULY, LocalDate.of(2025, 2, 3)), dated(JULY, null))
                        .directed())
                .isFalse();
        assertThat(GraphBuilder.orient(WY, MT, dated(JULY, JULY.minusMonths(5)), dated(JULY, JULY.minusMonths(5))))
                .isEqualTo(new Orientation(MT, WY, false));
    }

    @Test
    void anIntroductionAloneCannotStandInForAMissingEffectiveDate() {
        assertThat(GraphBuilder.orient(
                                WY, MT, dated(null, LocalDate.of(2025, 1, 2)), dated(JULY, LocalDate.of(2025, 1, 9)))
                        .directed())
                .isFalse();
        assertThat(Chronology.UNKNOWN.effective()).isEmpty();
        assertThat(Chronology.effectiveOn(JULY).introduced()).isEmpty();
    }

    @Test
    void theBuiltGraphOrientsSameDayActsByIntroduction() {
        Instrument wyoming = introduced(makeBill("wy", "HB0142", MODEL_CLAUSE, JULY), LocalDate.of(2025, 2, 20));
        Instrument montana = introduced(makeBill("mt", "HB0311", MODEL_CLAUSE, JULY), LocalDate.of(2025, 1, 6));
        ReuseGraph graph = GraphBuilder.buildGraph(matches(montana, wyoming), List.of(montana, wyoming));
        ReuseEdge edge = graph.edges().getFirst();
        assertThat(edge.directed()).isTrue();
        assertThat(edge.source().jurisdiction()).isEqualTo("mt");
        assertThat(graph.introducedOn(montana.key())).contains(LocalDate.of(2025, 1, 6));
        assertThat(Lineage.lineages(graph).getFirst().root()).contains(montana.key());
    }

    @Test
    void theReducedGraphKeepsTheIntroductionDates() {
        Instrument wyoming = introduced(makeBill("wy", "HB0142", MODEL_CLAUSE, JULY), LocalDate.of(2025, 2, 20));
        ReuseGraph graph = GraphBuilder.buildGraph(List.of(), List.of(wyoming));
        assertThat(DiffusionMetrics.transitiveReduction(graph).introducedOn(wyoming.key()))
                .contains(LocalDate.of(2025, 2, 20));
    }
}
