package io.github.rkaushikdev.galleyworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.ingest.AmendatoryClauses.Action;
import io.github.rkaushikdev.galleyworks.ingest.AmendatoryClauses.AmendatoryClause;
import io.github.rkaushikdev.galleyworks.support.Fixtures;
import io.github.rkaushikdev.galleyworks.support.GoldenCorpus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AmendatoryClausesTest {

    private static Optional<AmendatoryClause> directive(String text) {
        return AmendatoryClauses.directive("1", text);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
            Section 1.  W.S. 41-3-101 is amended to read:           | AMEND
            Section 2.  W.S. 41-3-105 is created to read:           | CREATE
            Section 3.  W.S. 41-3-110 is hereby repealed.           | REPEAL
            Section 4.  W.S. 41-3-112 is renumbered as 41-3-113.    | RENUMBER
            Sec. 5.  Section 85-2-101, MCA, is enacted to read:     | CREATE
            """)
    void theVerbSaysWhatTheSectionDoes(String text, Action action) {
        assertThat(directive(text).orElseThrow().action()).isSameAs(action);
    }

    @Test
    void everyProvisionTheDirectiveNamesIsListed() {
        AmendatoryClause clause = directive("Section 3.  W.S. 41-3-110(a), 41-3-111 and 41-3-112 are repealed.")
                .orElseThrow();
        assertThat(clause.provisions()).containsExactly("cite wy 41-3-110(a)", "cite wy 41-3-111", "cite wy 41-3-112");
    }

    @Test
    void aBareNumberIsALocalProvision() {
        assertThat(directive("Section 1.  85-2-101 is amended to read:")
                        .orElseThrow()
                        .provisions())
                .containsExactly("cite local 85-2-101");
    }

    @Test
    void aCitationInsideTheNewTextIsNotTheTarget() {
        String text = "Section 1.  W.S. 41-3-101 is amended to read:\n\n(a)  As provided in W.S. 9-1-101, the board.";
        assertThat(directive(text).orElseThrow().provisions()).containsExactly("cite wy 41-3-101");
    }

    @Test
    void aSectionWithoutADirectiveHasNone() {
        assertThat(directive("Section 2.  This act is effective July 1, 2025.")).isEmpty();
        assertThat(directive("Section 3.  The board's rules are amended by the board each year."))
                .isEmpty();
        assertThat(directive("Section 4.  The board shall publish.\n\nW.S. 41-3-101 is amended to read:"))
                .isEmpty();
    }

    @Test
    void anInstrumentListsItsDirectivesInOrder() {
        Instrument bill = new EnrolledReader().read(Fixtures.waterBill());
        assertThat(AmendatoryClauses.read(bill))
                .containsExactly(new AmendatoryClause("1", Action.AMEND, List.of("cite wy 41-3-101")));
    }

    @Test
    void theGoldenStatesEachAmendTheirOwnCode() {
        assertThat(GoldenCorpus.goldenInstruments().stream()
                        .limit(3)
                        .flatMap(instrument -> AmendatoryClauses.read(instrument).stream())
                        .map(clause -> clause.provisions().getFirst()))
                .containsExactly("cite wy 41-3-101", "cite local 85-2-101", "cite local 61-04-01");
    }

    @Test
    void actionsPrintLowercase() {
        assertThat(Action.REPEAL.value()).isEqualTo("repeal");
        assertThat(Action.CREATE).hasToString("create");
    }
}
