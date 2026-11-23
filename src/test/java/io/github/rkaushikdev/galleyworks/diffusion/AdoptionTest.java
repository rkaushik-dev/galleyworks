package io.github.rkaushikdev.galleyworks.diffusion;

import static io.github.rkaushikdev.galleyworks.diffusion.ReuseGraphTest.key;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.InstrumentKey;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdoptionTest {

    private static final InstrumentKey WY = key("wy", "HB0142");
    private static final InstrumentKey WY_AMENDED = key("wy", "HB0207");
    private static final InstrumentKey MT = key("mt", "HB0311");
    private static final InstrumentKey ND = key("nd", "HB0044");
    private static final InstrumentKey CO = key("co", "SB0007");

    private static ReuseGraph family() {
        ReuseGraph graph = new ReuseGraph();
        graph.add(new ReuseEdge(WY, MT, 1, 300, 0.9, true));
        graph.add(new ReuseEdge(WY, WY_AMENDED, 1, 300, 0.9, true));
        graph.add(new ReuseEdge(MT, ND, 1, 300, 0.9, true));
        graph.add(new ReuseEdge(ND, CO, 1, 300, 0.9, false));
        graph.note(WY, Optional.of(LocalDate.of(2023, 7, 1)));
        graph.note(MT, Optional.of(LocalDate.of(2024, 10, 1)));
        graph.note(WY_AMENDED, Optional.of(LocalDate.of(2024, 7, 1)));
        graph.note(ND, Optional.of(LocalDate.of(2024, 8, 1)));
        return graph;
    }

    private static Lineage lineage(ReuseGraph graph) {
        return Lineage.lineages(graph).getFirst();
    }

    @Test
    void eachJurisdictionAdoptsOnceInDateOrder() {
        ReuseGraph graph = family();
        List<Adoption> adoptions = Adoption.adoptions(graph, lineage(graph));
        assertThat(adoptions).extracting(Adoption::jurisdiction).containsExactly("wy", "nd", "mt", "co");
        Adoption wyoming = adoptions.getFirst();
        assertThat(wyoming.first()).isEqualTo(WY);
        assertThat(wyoming.when()).contains(LocalDate.of(2023, 7, 1));
        assertThat(wyoming.reenactments()).isEqualTo(1);
        assertThat(adoptions.getLast().when()).isEmpty();
    }

    @Test
    void aLaterEnactmentInAnAdoptingJurisdictionIsAReenactment() {
        ReuseGraph graph = family();
        List<Adoption> adoptions = Adoption.adoptions(graph, lineage(graph));
        assertThat(Adoption.isReenactment(adoptions, WY_AMENDED)).isTrue();
        assertThat(Adoption.isReenactment(adoptions, WY)).isFalse();
        assertThat(Adoption.isReenactment(adoptions, MT)).isFalse();
    }

    @Test
    void theCurveCountsAdoptersCumulativelyByYear() {
        ReuseGraph graph = family();
        assertThat(Adoption.curve(Adoption.adoptions(graph, lineage(graph))))
                .containsExactly(Map.entry(2023, 1), Map.entry(2024, 3));
        assertThat(Adoption.curve(List.of())).isEmpty();
    }

    @Test
    void theMetricsCountAdoptersNotInstruments() {
        ReuseGraph graph = family();
        DiffusionMetrics metrics = DiffusionMetrics.measure(graph, lineage(graph));
        assertThat(metrics.members()).isEqualTo(5);
        assertThat(metrics.jurisdictions()).isEqualTo(4);
        assertThat(metrics.reenactments()).isEqualTo(1);
    }
}
