package io.github.rkaushikdev.galleyworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.IngestError;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.support.Fixtures;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ResolveKeyTest {

    private static final BillHeader FULL_HEADER = new BillHeader("HB0142", Chamber.LOWER, "2025");

    private static SourceDocument source(Map<String, Object> hints) {
        return new SourceDocument("x", "t.txt", hints);
    }

    private static SourceDocument waterBill() {
        return new SourceDocument(Fixtures.WATER_BILL, "x", Map.of("jurisdiction", "wy"));
    }

    @Test
    void hintsAloneAreEnough() {
        InstrumentKey key =
                Keys.resolveKey(source(Map.of("jurisdiction", "wy", "session", "2025", "number", "HB0142")));
        assertThat(key.slug()).isEqualTo("wy/2025/lower/HB0142");
    }

    @Test
    void theHeaderFillsInWhatTheHintsOmit() {
        InstrumentKey key = Keys.resolveKey(source(Map.of("jurisdiction", "wy")), FULL_HEADER);
        assertThat(key.slug()).isEqualTo("wy/2025/lower/HB0142");
    }

    @Test
    void hintsWinOverTheHeader() {
        InstrumentKey key = Keys.resolveKey(
                source(Map.of("jurisdiction", "wy", "number", "HB0999", "chamber", "upper")), FULL_HEADER);
        assertThat(key.number()).isEqualTo("HB0999");
        assertThat(key.chamber()).isSameAs(Chamber.UPPER);
    }

    @Test
    void jurisdictionIsNeverInferred() {
        assertThatThrownBy(() -> Keys.resolveKey(source(Map.of()), FULL_HEADER))
                .isInstanceOfSatisfying(IngestError.class, caught -> {
                    assertThat(caught.getMessage()).contains("missing identity hints");
                    assertThat(caught.context()).containsEntry("missing", "jurisdiction");
                });
    }

    @Test
    void everyMissingFieldIsNamed() {
        assertThatThrownBy(() -> Keys.resolveKey(source(Map.of())))
                .isInstanceOfSatisfying(IngestError.class, caught -> assertThat(caught.context())
                        .containsEntry("missing", "jurisdiction,session,number"));
    }

    @Test
    void chamberDefaultsToLowerWhenNothingSaysOtherwise() {
        InstrumentKey key = Keys.resolveKey(source(Map.of("jurisdiction", "wy", "session", "2025", "number", "HB1")));
        assertThat(key.chamber()).isSameAs(Chamber.LOWER);
    }

    @Test
    void chamberComesFromTheHeaderWhenHintsAreSilent() {
        BillHeader header = new BillHeader("SF0032", Chamber.UPPER, "2025");
        assertThat(Keys.resolveKey(source(Map.of("jurisdiction", "wy")), header).chamber())
                .isSameAs(Chamber.UPPER);
    }

    @Test
    void anUnknownChamberHintIsRejected() {
        SourceDocument document =
                source(Map.of("jurisdiction", "wy", "session", "2025", "number", "HB1", "chamber", "senate"));
        assertThatThrownBy(() -> Keys.resolveKey(document))
                .isInstanceOf(IngestError.class)
                .hasMessageContaining("unknown chamber");
    }

    @Test
    void theReaderIdentifiesABillFromItsFrontMatter() {
        assertThat(new EnrolledReader().read(waterBill()).key().slug()).isEqualTo("wy/2025/lower/HB0142");
    }

    @Test
    void theReaderReadsTheEffectiveDate() {
        var instrument = new EnrolledReader().read(waterBill());
        assertThat(instrument.enactedOn()).contains(LocalDate.of(2025, 7, 1));
        assertThat(instrument.effectiveDate()).contains(LocalDate.of(2025, 7, 1));
    }

    @Test
    void aBillWithNoEffectiveClauseHasNoDate() {
        String trimmed = Fixtures.WATER_BILL.replace("This act is effective July 1, 2025.", "This act is void.");
        SourceDocument document = new SourceDocument(trimmed, "x", Map.of("jurisdiction", "wy"));
        assertThat(new EnrolledReader().read(document).enactedOn()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "SB0007, UPPER",
        "SF0032, UPPER",
        "HF12, LOWER",
        "AB-104, LOWER",
        "LB0410, UNICAMERAL",
        "EO 2025-04, EXECUTIVE"
    })
    void aPrefixedNumberNamesItsChamberWhenNothingElseDoes(String number, Chamber chamber) {
        InstrumentKey key = Keys.resolveKey(source(Map.of("jurisdiction", "co", "session", "2025", "number", number)));
        assertThat(key.chamber()).isSameAs(chamber);
    }

    @Test
    void theHeaderOutranksTheNumberPrefix() {
        BillHeader header = new BillHeader("SB0007", Chamber.LOWER, "2025");
        assertThat(Keys.resolveKey(source(Map.of("jurisdiction", "co")), header).chamber())
                .isSameAs(Chamber.LOWER);
    }

    @Test
    void aChamberHintOutranksTheNumberPrefix() {
        InstrumentKey key = Keys.resolveKey(
                source(Map.of("jurisdiction", "co", "session", "2025", "number", "SB0007", "chamber", Chamber.LOWER)));
        assertThat(key.chamber()).isSameAs(Chamber.LOWER);
    }

    @Test
    void anUnknownPrefixFallsBackToTheLowerChamber() {
        InstrumentKey key =
                Keys.resolveKey(source(Map.of("jurisdiction", "laramie", "session", "2024", "number", "ZZ12")));
        assertThat(key.chamber()).isSameAs(Chamber.LOWER);
    }

    @Test
    void aDateHintWinsOverThePrintedDate() {
        Optional<LocalDate> printed = Optional.of(LocalDate.of(2025, 7, 1));
        SourceDocument document = source(Map.of("enacted_on", "2025-10-01"));
        assertThat(Keys.resolveDate(document, Keys.ENACTED_ON, printed)).contains(LocalDate.of(2025, 10, 1));
        assertThat(Keys.resolveDate(source(Map.of()), Keys.ENACTED_ON, printed)).isEqualTo(printed);
        assertThat(Keys.resolveDate(source(Map.of("enacted_on", "")), Keys.ENACTED_ON, printed))
                .isEqualTo(printed);
    }

    @Test
    void aDateHintIsReadWhateverTheYamlMadeOfIt() {
        Date stamp =
                Date.from(LocalDate.of(2024, 4, 1).atStartOfDay(ZoneOffset.UTC).toInstant());
        assertThat(Keys.resolveDate(source(Map.of("introduced_on", stamp)), Keys.INTRODUCED_ON, Optional.empty()))
                .contains(LocalDate.of(2024, 4, 1));
        assertThat(Keys.resolveDate(
                        source(Map.of("introduced_on", LocalDate.of(2024, 4, 2))),
                        Keys.INTRODUCED_ON,
                        Optional.empty()))
                .contains(LocalDate.of(2024, 4, 2));
        assertThat(Keys.resolveDate(
                        source(Map.of("introduced_on", " 2024-04-03 ")), Keys.INTRODUCED_ON, Optional.empty()))
                .contains(LocalDate.of(2024, 4, 3));
    }

    @Test
    void aDateHintThatIsNoDateIsRejected() {
        assertThatThrownBy(() ->
                        Keys.resolveDate(source(Map.of("enacted_on", "July 2025")), Keys.ENACTED_ON, Optional.empty()))
                .isInstanceOfSatisfying(IngestError.class, caught -> {
                    assertThat(caught.getMessage()).contains("date hint is not a date");
                    assertThat(caught.context())
                            .containsEntry("hint", "enacted_on")
                            .containsEntry("value", "July 2025");
                });
    }

    @Test
    void theEnrolledReaderTakesTheManifestDates() {
        SourceDocument corrected = new SourceDocument(
                Fixtures.WATER_BILL,
                "x",
                Map.of("jurisdiction", "wy", "enacted_on", "2025-10-01", "introduced_on", "2025-01-13"));
        var instrument = new EnrolledReader().read(corrected);
        assertThat(instrument.enactedOn()).contains(LocalDate.of(2025, 10, 1));
        assertThat(instrument.introducedOn()).contains(LocalDate.of(2025, 1, 13));
        assertThat(new EnrolledReader().read(waterBill()).enactedOn()).contains(LocalDate.of(2025, 7, 1));
    }
}
