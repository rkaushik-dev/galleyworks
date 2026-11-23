package io.github.rkaushikdev.galleyworks.report;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.JurisdictionRegistry;
import io.github.rkaushikdev.galleyworks.MatchConfig;
import io.github.rkaushikdev.galleyworks.RunResult;
import io.github.rkaushikdev.galleyworks.match.Explanation;
import io.github.rkaushikdev.galleyworks.match.SegmentMatch;
import io.github.rkaushikdev.galleyworks.match.SharedContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SurroundingsTest {

    private static final LocalDate JULY = LocalDate.of(2025, 7, 1);

    private static Instrument house() {
        return makeBill("wy", "HB0142", MODEL_CLAUSE, "2025", JULY);
    }

    private static Instrument senate() {
        return makeBill("wy", "SF0032", MODEL_CLAUSE, "2025", JULY);
    }

    private static RunResult companions() {
        return new Engine().runOver(List.of(house(), senate()));
    }

    @Test
    void twoBillsOfOneSessionAmendingOneSectionAreCompanions() {
        assertThat(Surroundings.companionProvisions(senate(), house())).containsExactly("cite wy 41-3-101");
    }

    @Test
    void anotherSessionOrAnotherStateIsNoCompanion() {
        assertThat(Surroundings.companionProvisions(senate(), makeBill("wy", "HB0142", MODEL_CLAUSE, "2023", JULY)))
                .isEmpty();
        assertThat(Surroundings.companionProvisions(senate(), makeBill("mt", "HB0142", MODEL_CLAUSE, "2025", JULY)))
                .isEmpty();
        assertThat(Surroundings.companionProvisions(senate(), senate())).isEmpty();
    }

    @Test
    void theReportNamesCompanionBills() {
        ReportedMatch finding = companions()
                .reportFor("wy/2025/lower/SF0032")
                .orElseThrow()
                .matches()
                .getFirst();
        assertThat(finding.reasons())
                .contains("both bills of the session amend cite wy 41-3-101, as companion bills do")
                .contains("both sit under the catchline \"Schedule of appropriations\"");
    }

    @Test
    void companionsCostConfidenceLikeOtherExpectedReuse() {
        RunResult result = companions();
        SegmentMatch match = result.matches().getFirst();
        Explanation plain = Explanation.explain(
                match, new MatchConfig(), new JurisdictionRegistry(), new SharedContext(Optional.empty(), List.of()));
        Explanation companion = Explanation.explain(
                match,
                new MatchConfig(),
                new JurisdictionRegistry(),
                new SharedContext(Optional.empty(), List.of("cite wy 41-3-101")));
        assertThat(companion.confidence().compareTo(plain.confidence())).isLessThanOrEqualTo(0);
        assertThat(companion.reasons()).hasSize(plain.reasons().size() + 1);
    }

    @Test
    void theContextBetweenTwoInstrumentsCarriesBothParts() {
        RunResult result = companions();
        SharedContext shared = Surroundings.between(senate(), result.matches().getFirst(), house());
        assertThat(shared.catchline()).contains("Schedule of appropriations");
        assertThat(shared.companions()).isTrue();
        assertThat(SharedContext.NONE.companions()).isFalse();
        assertThat(SharedContext.NONE.catchline()).isEmpty();
    }
}
