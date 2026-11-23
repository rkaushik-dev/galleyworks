package io.github.rkaushikdev.galleyworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Chamber;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MeasureTypeTest {

    @ParameterizedTest
    @CsvSource({
        "ASSEMBLY BILL NO. 104, LOWER",
        "HOUSE FILE NO. HF12, LOWER",
        "HOUSE CONCURRENT RESOLUTION NO. 3, LOWER",
        "SENATE CONCURRENT RESOLUTION NO. 5, UPPER",
        "LEGISLATIVE BILL 410, UNICAMERAL",
        "LEGISLATIVE RESOLUTION 22, UNICAMERAL"
    })
    void theHeaderNamesTheChamber(String line, Chamber chamber) {
        assertThat(MeasureType.named(line).map(MeasureType::chamber)).contains(chamber);
        assertThat(Metadata.readHeader(List.of(line)).chamber()).contains(chamber);
    }

    @Test
    void theLongestWordingWins() {
        assertThat(MeasureType.named("SENATE JOINT RESOLUTION NO. 4").map(MeasureType::prefix))
                .contains("SJR");
        assertThat(MeasureType.named("HOUSE JOINT RESOLUTION NO. 4").map(MeasureType::prefix))
                .contains("HJR");
        assertThat(MeasureType.named("an ordinance of the city"))
                .map(MeasureType::prefix)
                .contains("");
    }

    @Test
    void aLineNamingNoMeasureNamesNothing() {
        assertThat(MeasureType.named("Water rights; publication schedule.")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"SB0007, SB", "sf 32, SF", "HJR.4, HJR", "AB-104, AB", "LB0410, LB"})
    void aNumberIsReadForItsPrefix(String number, String prefix) {
        assertThat(MeasureType.numbered(number).map(MeasureType::prefix)).contains(prefix);
    }

    @Test
    void aNumberWithoutAKnownPrefixNamesNothing() {
        assertThat(MeasureType.numbered("2024-31")).isEmpty();
        assertThat(MeasureType.numbered("XB12")).isEmpty();
        assertThat(MeasureType.numbered("HB")).isEmpty();
        assertThat(MeasureType.numbered("SBX")).isEmpty();
    }

    @Test
    void everyPrefixIsUniqueAndUppercase() {
        List<String> prefixes = MeasureType.KNOWN.stream()
                .map(MeasureType::prefix)
                .filter(prefix -> !prefix.isEmpty())
                .toList();
        assertThat(prefixes).doesNotHaveDuplicates().allSatisfy(prefix -> assertThat(prefix)
                .matches("[A-Z]{2,3}"));
        assertThat(MeasureType.KNOWN.getFirst().words()).isEqualTo("senate concurrent resolution");
    }
}
