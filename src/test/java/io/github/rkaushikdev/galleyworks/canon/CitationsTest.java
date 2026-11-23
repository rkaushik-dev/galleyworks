package io.github.rkaushikdev.galleyworks.canon;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CitationsTest {

    private static String rewrite(String text) {
        return rewrite(text, new CanonConfig());
    }

    private static String rewrite(String text, CanonConfig config) {
        return CanonPipeline.applyEdits(text, new Citations.CitationRule().edits(text, config))
                .text();
    }

    @ParameterizedTest
    @ValueSource(strings = {"W.S. 41-3-101", "W.S. 41-3-101", "Wyo. Stat. Ann. § 41-3-101", "WS 41-3-101"})
    void stateReportersAllCollapseToOneForm(String text) {
        assertThat(rewrite(text)).isEqualTo("cite wy 41-3-101");
    }

    @Test
    void aBareSectionWordIsLocal() {
        assertThat(rewrite("section 41-3-101")).isEqualTo("cite local 41-3-101");
        assertThat(rewrite("§ 41-3-101")).isEqualTo("cite local 41-3-101");
        assertThat(rewrite("§§ 41-3-101")).isEqualTo("cite local 41-3-101");
    }

    @Test
    void subsectionsArePreserved() {
        assertThat(rewrite("W.S. 41-3-101(a)(ii)")).isEqualTo("cite wy 41-3-101(a)(ii)");
    }

    @Test
    void subsectionCaseIsFolded() {
        assertThat(rewrite("W.S. 41-3-101(A)")).isEqualTo("cite wy 41-3-101(a)");
    }

    @Test
    void aFederalTitleIsKept() {
        assertThat(rewrite("42 U.S.C. § 1983")).isEqualTo("cite usc42 1983");
    }

    @Test
    void federalRegulationsAreRecognised() {
        assertThat(rewrite("40 C.F.R. 122.26")).isEqualTo("cite cfr40 122.26");
    }

    @Test
    void twoReportersAreNotConfused() {
        assertThat(rewrite("under W.S. 41-3-101 and 42 U.S.C. 1983"))
                .isEqualTo("under cite wy 41-3-101 and cite usc42 1983");
    }

    @Test
    void aLeadingCountBeforeSectionIsNotATitle() {
        assertThat(rewrite("amends 3 sections 41-3-101")).isEqualTo("amends 3 sections 41-3-101");
    }

    @Test
    void plainProseIsUntouched() {
        String text = "The board shall publish a schedule.";
        assertThat(rewrite(text)).isEqualTo(text);
    }

    @Test
    void aLoneNumberIsNotACitation() {
        assertThat(rewrite("not later than 41-3-101 days")).isEqualTo("not later than 41-3-101 days");
    }

    @Test
    void citationsReportTheirSpans() {
        String text = "see W.S. 41-3-101 today";
        Citations.Citation citation = Citations.findCitations(text).getFirst();
        assertThat(citation.span().sliceOf(text)).isEqualTo("W.S. 41-3-101");
        assertThat(citation.reporter()).isEqualTo("wy");
        assertThat(citation.number()).isEqualTo("41-3-101");
    }

    @Test
    void theRuleCanBeSwitchedOff() {
        String text = "W.S. 41-3-101";
        assertThat(rewrite(text, new CanonConfig().withNormalizeCitations(false)))
                .isEqualTo(text);
    }

    @Test
    void twoStylingsOfOneProvisionBecomeIdentical() {
        String first = rewrite("as provided in W.S. 41-3-101(a), the board shall act");
        String second = rewrite("as provided in section 41-3-101(a), the board shall act");
        assertThat(first.replace("cite wy", "cite X")).isEqualTo(second.replace("cite local", "cite X"));
    }

    @Test
    void aLetteredStatuteNumberIsRecognised() {
        assertThat(rewrite("W.S. 41-3-101a")).isEqualTo("cite wy 41-3-101a");
    }

    @Test
    void dottedNumberingIsRecognised() {
        assertThat(rewrite("section 15.04.020")).isEqualTo("cite local 15.04.020");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"Mont. Code Ann. § 85-2-101", "MCA 85-2-101", "section 85-2-101, MCA", "Mont Code Ann 85-2-101"})
    void montanaCitationsCollapseToOneForm(String text) {
        assertThat(rewrite(text)).isEqualTo("cite mt 85-2-101");
    }

    @ParameterizedTest
    @ValueSource(strings = {"N.D.C.C. § 61-04-01", "N.D. Cent. Code 61-04-01", "section 61-04-01, NDCC"})
    void northDakotaCitationsCollapseToOneForm(String text) {
        assertThat(rewrite(text)).isEqualTo("cite nd 61-04-01");
    }

    @ParameterizedTest
    @ValueSource(strings = {"C.R.S. 35-65-101", "Colo. Rev. Stat. § 35-65-101", "section 35-65-101, C.R.S."})
    void coloradoCitationsCollapseToOneForm(String text) {
        assertThat(rewrite(text)).isEqualTo("cite co 35-65-101");
    }

    @Test
    void aTrailingReporterIsConsumedWithItsComma() {
        assertThat(rewrite("under section 85-2-101(a), MCA, the department shall act"))
                .isEqualTo("under cite mt 85-2-101(a), the department shall act");
    }

    @Test
    void aTrailingWordThatIsNoReporterStaysOutsideTheCitation() {
        assertThat(rewrite("section 85-2-101, made")).isEqualTo("cite local 85-2-101, made");
        assertThat(rewrite("section 85-2-101, MCAT")).isEqualTo("cite local 85-2-101, MCAT");
    }

    @Test
    void aReporterHasToStartAWord() {
        assertThat(rewrite("the news 41-3-101 bulletin")).isEqualTo("the news 41-3-101 bulletin");
    }

    @Test
    void theLeadingAndTrailingStylesOfOneStateAgree() {
        assertThat(rewrite("as provided in section 35-65-101(b), C.R.S."))
                .isEqualTo(rewrite("as provided in Colo. Rev. Stat. 35-65-101(b)"));
    }

    @Test
    void aTrailingReporterIsRecordedOnTheCitation() {
        String text = "see section 61-04-01(2), N.D.C.C. here";
        Citations.Citation citation = Citations.findCitations(text).getFirst();
        assertThat(citation.reporter()).isEqualTo("nd");
        assertThat(citation.subsections()).isEqualTo("(2)");
        assertThat(citation.title()).isEmpty();
        assertThat(citation.span().sliceOf(text)).isEqualTo("section 61-04-01(2), N.D.C.C.");
    }

    @Test
    void aCountBeforeATrailingStyleCitationIsNotATitle() {
        assertThat(Citations.findCitations("amends 3 sections 85-2-101, MCA")).isEmpty();
    }
}
