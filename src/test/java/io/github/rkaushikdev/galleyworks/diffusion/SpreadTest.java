package io.github.rkaushikdev.galleyworks.diffusion;

import static io.github.rkaushikdev.galleyworks.diffusion.ReuseGraphTest.key;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.GovernmentLevel;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.Jurisdiction;
import io.github.rkaushikdev.galleyworks.JurisdictionRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpreadTest {

    private static final InstrumentKey WY = key("wy", "HB0142");
    private static final InstrumentKey MT = key("mt", "HB0311");
    private static final InstrumentKey LARAMIE = key("laramie", "2024-31");
    private static final InstrumentKey CHEYENNE = key("cheyenne", "2025-08");

    private static JurisdictionRegistry wyoming() {
        JurisdictionRegistry registry = new JurisdictionRegistry();
        registry.add(new Jurisdiction("wy", "Wyoming", GovernmentLevel.STATE));
        registry.add(new Jurisdiction("mt", "Montana", GovernmentLevel.STATE));
        registry.add(new Jurisdiction("laramie", "Laramie", GovernmentLevel.MUNICIPAL, "wy"));
        registry.add(new Jurisdiction("cheyenne", "Cheyenne", GovernmentLevel.MUNICIPAL, "wy"));
        return registry;
    }

    private static ReuseEdge edge(InstrumentKey source, InstrumentKey target, boolean directed) {
        return new ReuseEdge(source, target, 1, 200, 0.9, directed);
    }

    @Test
    void peersAreHorizontal() {
        assertThat(Spread.of(edge(WY, MT, true), wyoming())).isSameAs(Spread.HORIZONTAL);
        assertThat(Spread.of(edge(LARAMIE, CHEYENNE, true), wyoming())).isSameAs(Spread.HORIZONTAL);
    }

    @Test
    void aStateToOneOfItsCitiesIsDownward() {
        assertThat(Spread.of(edge(WY, LARAMIE, true), wyoming())).isSameAs(Spread.DOWNWARD);
    }

    @Test
    void aCityToItsStateIsUpward() {
        assertThat(Spread.of(edge(LARAMIE, WY, true), wyoming())).isSameAs(Spread.UPWARD);
    }

    @Test
    void anUndatedEdgeBetweenLevelsIsOnlyVertical() {
        assertThat(Spread.of(edge(WY, LARAMIE, false), wyoming())).isSameAs(Spread.VERTICAL);
        assertThat(Spread.of(edge(LARAMIE, WY, false), wyoming())).isSameAs(Spread.VERTICAL);
    }

    @Test
    void twoInstrumentsOfOneBodyAreInternal() {
        assertThat(Spread.of(edge(WY, key("wy", "HB0999"), true), wyoming())).isSameAs(Spread.INTERNAL);
    }

    @Test
    void withoutATableEveryCrossingIsHorizontal() {
        assertThat(Spread.of(edge(WY, LARAMIE, true), new JurisdictionRegistry()))
                .isSameAs(Spread.HORIZONTAL);
    }

    @Test
    void aFamilyIsTalliedOverItsOwnEdges() {
        ReuseGraph graph = new ReuseGraph();
        graph.add(edge(WY, MT, true));
        graph.add(edge(WY, LARAMIE, true));
        graph.add(edge(LARAMIE, CHEYENNE, false));
        graph.add(edge(key("co", "SB0007"), key("nd", "HB0044"), true));
        Lineage family = Lineage.lineages(graph).getFirst();
        Map<Spread, Integer> counts = Spread.tally(graph, family, wyoming());
        assertThat(counts)
                .containsEntry(Spread.HORIZONTAL, 2)
                .containsEntry(Spread.DOWNWARD, 1)
                .containsEntry(Spread.UPWARD, 0)
                .hasSize(Spread.values().length);
        assertThat(Spread.describe(counts)).isEqualTo("2 horizontal, 1 downward");
    }

    @Test
    void anEmptyTallyIsDescribedAsSuch() {
        Lineage nobody = new Lineage(Optional.empty(), List.of(), Optional.empty(), 0);
        assertThat(Spread.describe(Spread.tally(new ReuseGraph(), nobody, wyoming())))
                .isEqualTo("no edges");
        assertThat(Spread.UPWARD).hasToString("upward");
    }
}
