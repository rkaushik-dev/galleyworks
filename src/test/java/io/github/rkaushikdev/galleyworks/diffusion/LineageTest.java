package io.github.rkaushikdev.galleyworks.diffusion;

import static io.github.rkaushikdev.galleyworks.diffusion.ReuseGraphTest.key;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.InstrumentKey;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LineageTest {

    private static final InstrumentKey WY = key("wy", "HB0142");
    private static final InstrumentKey MT = key("mt", "HB0311");
    private static final InstrumentKey ND = key("nd", "HB0044");
    private static final InstrumentKey CO = key("co", "SB0007");

    private static ReuseGraph graphWith(InstrumentKey[]... pairs) {
        ReuseGraph graph = new ReuseGraph();
        for (InstrumentKey[] pair : pairs) {
            graph.add(new ReuseEdge(pair[0], pair[1], 1, 100, 0.8, true));
        }
        return graph;
    }

    private static InstrumentKey[] pair(InstrumentKey source, InstrumentKey target) {
        return new InstrumentKey[] {source, target};
    }

    private static void date(ReuseGraph graph, InstrumentKey key, int year, int month, int day) {
        graph.note(key, Optional.of(LocalDate.of(year, month, day)));
    }

    @Test
    void anEmptyGraphHasNoFamilies() {
        assertThat(Lineage.components(new ReuseGraph())).isEmpty();
        assertThat(Lineage.lineages(new ReuseGraph())).isEmpty();
    }

    @Test
    void oneEdgeIsOneFamily() {
        List<InstrumentKey> expected = new ArrayList<>(List.of(WY, MT));
        expected.sort(null);
        assertThat(Lineage.components(graphWith(pair(WY, MT)))).containsExactly(expected);
    }

    @Test
    void aChainIsOneFamily() {
        List<List<InstrumentKey>> found = Lineage.components(graphWith(pair(WY, MT), pair(MT, ND)));
        assertThat(found).hasSize(1);
        assertThat(found.getFirst()).hasSize(3);
    }

    @Test
    void disconnectedPairsAreSeparateFamilies() {
        assertThat(Lineage.components(graphWith(pair(WY, MT), pair(ND, CO)))).hasSize(2);
    }

    @Test
    void familiesComeBackLargestFirst() {
        List<Integer> sizes =
                Lineage.components(graphWith(pair(WY, MT), pair(MT, ND), pair(CO, key("ut", "HB0001")))).stream()
                        .map(List::size)
                        .toList();
        List<Integer> descending = new ArrayList<>(sizes);
        descending.sort(Collections.reverseOrder());
        assertThat(sizes).isEqualTo(descending);
    }

    @Test
    void theEarliestDatedMemberIsTheRoot() {
        ReuseGraph graph = graphWith(pair(WY, MT), pair(MT, ND));
        date(graph, WY, 2023, 5, 1);
        date(graph, MT, 2024, 5, 1);
        date(graph, ND, 2025, 5, 1);
        assertThat(Lineage.earliest(graph, List.of(MT, ND, WY))).contains(WY);
    }

    @Test
    void anUndatedFamilyHasNoRoot() {
        ReuseGraph graph = graphWith(pair(WY, MT));
        assertThat(Lineage.earliest(graph, List.of(WY, MT))).isEmpty();
        assertThat(Lineage.lineages(graph).getFirst().root()).isEmpty();
    }

    @Test
    void aPartlyDatedFamilyIsNotConfident() {
        ReuseGraph graph = graphWith(pair(WY, MT));
        date(graph, WY, 2023, 5, 1);
        Lineage lineage = Lineage.lineages(graph).getFirst();
        assertThat(lineage.root()).contains(WY);
        assertThat(lineage.datedMembers()).isEqualTo(1);
        assertThat(lineage.confident()).isFalse();
    }

    @Test
    void aFullyDatedFamilyIsConfident() {
        ReuseGraph graph = graphWith(pair(WY, MT));
        date(graph, WY, 2023, 5, 1);
        date(graph, MT, 2024, 5, 1);
        Lineage lineage = Lineage.lineages(graph).getFirst();
        assertThat(lineage.confident()).isTrue();
        assertThat(lineage.rootDate()).contains(LocalDate.of(2023, 5, 1));
    }

    @Test
    void aTieOnDatesBreaksOnTheSlug() {
        ReuseGraph graph = graphWith(pair(WY, MT));
        date(graph, WY, 2024, 1, 1);
        date(graph, MT, 2024, 1, 1);
        InstrumentKey lowest = List.of(WY, MT).stream()
                .min(Comparator.comparing(InstrumentKey::slug))
                .orElseThrow();
        assertThat(Lineage.earliest(graph, List.of(WY, MT))).contains(lowest);
    }

    @Test
    void aFamilyReportsItsJurisdictions() {
        Lineage lineage =
                Lineage.lineages(graphWith(pair(WY, MT), pair(MT, ND))).getFirst();
        assertThat(lineage.jurisdictions()).containsExactly("mt", "nd", "wy");
        assertThat(lineage.spreads()).isTrue();
        assertThat(lineage.size()).isEqualTo(3);
    }

    @Test
    void aSingleJurisdictionFamilyDoesNotSpread() {
        assertThat(Lineage.lineages(graphWith(pair(key("wy", "HB0001"), key("wy", "HB0002"))))
                        .getFirst()
                        .spreads())
                .isFalse();
    }

    @Test
    void aTieOnDatesBreaksOnTheIntroductionBeforeTheSlug() {
        ReuseGraph graph = graphWith(pair(MT, WY));
        graph.note(MT, Optional.of(LocalDate.of(2024, 7, 1)), Optional.of(LocalDate.of(2024, 2, 9)));
        graph.note(WY, Optional.of(LocalDate.of(2024, 7, 1)), Optional.of(LocalDate.of(2024, 1, 15)));
        assertThat(Lineage.earliest(graph, List.of(MT, WY))).contains(WY);
        assertThat(Lineage.lineages(graph).getFirst().root()).contains(WY);
    }

    @Test
    void aKnownIntroductionSortsBeforeAnUnknownOne() {
        ReuseGraph graph = graphWith(pair(MT, WY));
        graph.note(MT, Optional.of(LocalDate.of(2024, 7, 1)), Optional.of(LocalDate.of(2024, 2, 9)));
        date(graph, WY, 2024, 7, 1);
        assertThat(Lineage.earliest(graph, List.of(WY, MT))).contains(MT);
        assertThat(graph.introducedOn(WY)).isEmpty();
    }

    @Test
    void anIntroductionNeverOutranksAnEarlierEffectiveDate() {
        ReuseGraph graph = graphWith(pair(MT, WY));
        graph.note(MT, Optional.of(LocalDate.of(2024, 7, 2)), Optional.of(LocalDate.of(2023, 1, 9)));
        graph.note(WY, Optional.of(LocalDate.of(2024, 7, 1)), Optional.empty());
        assertThat(Lineage.earliest(graph, List.of(MT, WY))).contains(WY);
    }
}
