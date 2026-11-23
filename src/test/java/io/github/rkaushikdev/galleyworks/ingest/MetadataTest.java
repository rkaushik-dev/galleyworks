package io.github.rkaushikdev.galleyworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Chamber;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class MetadataTest {

    private static final List<String> FRONT = List.of(
            "2025",
            "",
            "STATE OF WYOMING                                   25LSO-0142",
            "",
            "HOUSE BILL NO. HB0142",
            "",
            "Water rights; publication schedule.",
            "",
            "Sponsored by: Joint Agriculture Committee");

    @Test
    void readsThePrintedIdentity() {
        BillHeader header = Metadata.readHeader(FRONT);
        assertThat(header.number()).contains("HB0142");
        assertThat(header.chamber()).contains(Chamber.LOWER);
        assertThat(header.session()).contains("2025");
        assertThat(header.sponsor()).contains("Joint Agriculture Committee");
        assertThat(header.isComplete()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "HOUSE BILL NO. HB0142, LOWER",
        "SENATE FILE NO. SF0032, UPPER",
        "SENATE BILL NO. 14, UPPER",
        "COUNCIL BILL NO. 2024-31, COUNCIL",
        "ORDINANCE NO. 1188, COUNCIL",
        "EXECUTIVE ORDER NO. 2025-04, EXECUTIVE"
    })
    void chamberWords(String line, Chamber chamber) {
        assertThat(Metadata.readHeader(List.of(line)).chamber()).contains(chamber);
    }

    @Test
    void numberStripsSpacesAndDashes() {
        assertThat(Metadata.readHeader(List.of("COUNCIL BILL NO. 2024-31")).number())
                .contains("202431");
        assertThat(Metadata.readHeader(List.of("SENATE FILE NO. SF 0032")).number())
                .contains("SF0032");
    }

    @Test
    void theFirstChamberWordWins() {
        BillHeader header =
                Metadata.readHeader(List.of("HOUSE BILL NO. HB0142", "referred to the SENATE FILE calendar"));
        assertThat(header.chamber()).contains(Chamber.LOWER);
        assertThat(header.number()).contains("HB0142");
    }

    @Test
    void aNumberWithoutAChamberIsIgnored() {
        assertThat(Metadata.readHeader(List.of("DOCKET NO. 1234")).number()).isEmpty();
    }

    @Test
    void blankHeaderIsIncomplete() {
        BillHeader header = Metadata.readHeader(List.of("", "   "));
        assertThat(header).isEqualTo(BillHeader.EMPTY);
        assertThat(header.isComplete()).isFalse();
    }

    @Test
    void sessionTakesTheFirstFourDigitYear() {
        assertThat(Metadata.readHeader(List.of("2025 GENERAL SESSION", "amends the 1977 act"))
                        .session())
                .contains("2025");
    }

    @Test
    void aYearLikeNumberIsNotConfusedWithASession() {
        assertThat(Metadata.readHeader(List.of("HOUSE BILL NO. HB0142")).session())
                .isEmpty();
    }

    @Test
    void sponsorIsTrimmed() {
        assertThat(Metadata.readHeader(List.of("Sponsored by:    Senator Case  "))
                        .sponsor())
                .contains("Senator Case");
    }

    @Test
    void effectiveDateIsParsed() {
        assertThat(Metadata.parseEffectiveDate("This act is effective July 1, 2025."))
                .contains(LocalDate.of(2025, 7, 1));
    }

    @Test
    void effectiveDateIsCaseInsensitive() {
        assertThat(Metadata.parseEffectiveDate("EFFECTIVE MARCH 15, 2026")).contains(LocalDate.of(2026, 3, 15));
    }

    @Test
    void effectiveDateReturnsNoneWhenAbsent() {
        assertThat(Metadata.parseEffectiveDate("This act takes effect immediately."))
                .isEmpty();
    }

    @Test
    void impossibleDatesAreRejected() {
        assertThat(Metadata.parseEffectiveDate("effective February 30, 2025")).isEmpty();
        assertThat(Metadata.parseEffectiveDate("effective Smarch 3, 2025")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "This act takes effect July 1, 2025.",
                "This act shall take effect on July 1, 2025.",
                "This act is effective on the first day of July, 2025.",
                "This act becomes effective on and after July 1, 2025.",
                "This ordinance shall be effective from July 1st, 2025.",
                "This act shall become effective as of the 1st day of July, 2025."
            })
    void everyEffectiveDateWordingIsRead(String clause) {
        assertThat(Metadata.parseEffectiveDate("Section 9.  " + clause)).contains(LocalDate.of(2025, 7, 1));
    }

    @Test
    void theClauseForTheWholeActWinsOverADelayedSection() {
        String text = "Section 3.  Section 2 of this act is effective January 1, 2026.\n\n"
                + "Section 4.  Except as provided in section 3, this act is effective July 1, 2025.";
        assertThat(Metadata.parseEffectiveDate(text)).contains(LocalDate.of(2025, 7, 1));
    }

    @Test
    void withoutAWholeActClauseTheFirstDatedClauseIsUsed() {
        String text = "Section 2 is effective January 1, 2026. Section 3 is effective July 1, 2026.";
        assertThat(Metadata.parseEffectiveDate(text)).contains(LocalDate.of(2026, 1, 1));
    }

    @Test
    void aDateHasToFollowTheClause() {
        assertThat(Metadata.parseEffectiveDate("Reports filed before July 1, 2025 are effective immediately."))
                .isEmpty();
        assertThat(Metadata.parseEffectiveDate("This act takes effect upon passage and approval, July 1, 2025."))
                .isEmpty();
    }

    @Test
    void anUndatedWholeActClauseDoesNotHideADatedOne() {
        String text = "This act is effective immediately. Section 5 is effective March 1, 2026.";
        assertThat(Metadata.parseEffectiveDate(text)).contains(LocalDate.of(2026, 3, 1));
    }
}
