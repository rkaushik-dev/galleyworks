package io.github.rkaushikdev.galleyworks.canon;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SessionLawsTest {

    private static String rewrite(String text) {
        return CanonPipeline.applyEdits(text, new Citations.CitationRule().edits(text, new CanonConfig()))
                .text();
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
            2023 Wyo. Sess. Laws ch. 42      | sesslaw wy 2023 ch 42
            2023 Wyo Sess Laws, chapter 42   | sesslaw wy 2023 ch 42
            2019 Mont. Laws ch. 215          | sesslaw mt 2019 ch 215
            2019 Laws of Montana ch. 215     | sesslaw mt 2019 ch 215
            2021 N.D. Sess. Laws ch. 9       | sesslaw nd 2021 ch 9
            2023 Wyo. Sess. Laws 142         | sesslaw wy 2023 p 142
            Ch. 15, L. 2023                  | sesslaw local 2023 ch 15
            S.L. 2023, ch. 42                | sesslaw local 2023 ch 42
            Laws 2023, ch. 015               | sesslaw local 2023 ch 15
            chapter 42, Laws of 2023         | sesslaw local 2023 ch 42
            chapter 42, Session Laws of 2023 | sesslaw local 2023 ch 42
            L. 2023, p. 1234                 | sesslaw local 2023 p 1234
            """)
    void everyPrintedFormFoldsToOne(String printed, String canonical) {
        assertThat(rewrite(printed)).isEqualTo(canonical);
    }

    @Test
    void aSourceNoteKeepsItsSurroundings() {
        assertThat(rewrite("History: En. Sec. 1, Ch. 15, L. 2023; amd. Sec. 4, Ch. 88, L. 2025."))
                .isEqualTo("History: En. Sec. 1, sesslaw local 2023 ch 15; amd. Sec. 4, sesslaw local 2025 ch 88.");
    }

    @Test
    void theChapterIsNotReadAsACodeSection() {
        String text = "as amended by chapter 42, Laws of 2023, and W.S. 41-3-101";
        assertThat(rewrite(text)).isEqualTo("as amended by sesslaw local 2023 ch 42, and cite wy 41-3-101");
    }

    @Test
    void aReferenceIsReportedWithItsSpanAndParts() {
        String text = "see 2023 Wyo. Sess. Laws ch. 42 for the act";
        List<SessionLaws.SessionLaw> found = SessionLaws.findSessionLaws(text);
        assertThat(found).hasSize(1);
        SessionLaws.SessionLaw law = found.getFirst();
        assertThat(law.span().sliceOf(text)).isEqualTo("2023 Wyo. Sess. Laws ch. 42");
        assertThat(law.reporter()).isEqualTo("wy");
        assertThat(law.year()).isEqualTo("2023");
        assertThat(law.unit()).isEqualTo("ch");
        assertThat(law.number()).isEqualTo("42");
    }

    @Test
    void ordinaryUsesOfLawsAndChapterAreLeftAlone() {
        String text = "The laws of 2023 and chapter 4 of this title apply. Public law 2023 is not a chapter.";
        assertThat(SessionLaws.findSessionLaws(text)).isEmpty();
        assertThat(rewrite("the 2023 session laws were printed")).isEqualTo("the 2023 session laws were printed");
    }

    @Test
    void aYearHasToBeAYear() {
        assertThat(SessionLaws.findSessionLaws("Laws 1234, ch. 5")).isEmpty();
        assertThat(SessionLaws.findSessionLaws("BL. 2023, ch. 5")).isEmpty();
    }

    @Test
    void twoReferencesComeBackInTextOrder() {
        List<SessionLaws.SessionLaw> found =
                SessionLaws.findSessionLaws("chapter 9, Laws of 2021 and 2023 Wyo. Sess. Laws ch. 42");
        assertThat(found.stream().map(SessionLaws.SessionLaw::canonical))
                .containsExactly("sesslaw local 2021 ch 9", "sesslaw wy 2023 ch 42");
    }

    @Test
    void theRuleIsSwitchedOffWithCitations() {
        String text = "S.L. 2023, ch. 42";
        assertThat(new Citations.CitationRule().edits(text, new CanonConfig().withNormalizeCitations(false)))
                .isEmpty();
    }
}
