package io.github.rkaushikdev.galleyworks.canon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.util.Regex;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ReporterTest {

    @Test
    void aPrintedFormMatchesWithAndWithoutItsPeriods() {
        Pattern form = Regex.ignoringCase("^(?:" + Reporter.patternFor("N.D. Cent. Code") + ")$");
        assertThat(form.matcher("N.D. Cent. Code").matches()).isTrue();
        assertThat(form.matcher("ND Cent Code").matches()).isTrue();
        assertThat(form.matcher("N. D. Cent. Code").matches()).isTrue();
        assertThat(form.matcher("N.D.Cent.Code").matches()).isTrue();
        assertThat(form.matcher("N.D. Central Code").matches()).isFalse();
    }

    @Test
    void aPatternNeverEndsWaitingForASpace() {
        assertThat(Reporter.patternFor("W.S.")).isEqualTo("W\\.?\\s?S\\.?");
        assertThat(Reporter.patternFor("MCA")).isEqualTo("MCA");
    }

    @Test
    void theAlternationTriesTheLongestFormFirst() {
        String alternation =
                Reporter.alternation(List.of(new Reporter("wy", List.of("Wyo. Stat.", "Wyo. Stat. Ann."))));
        assertThat(alternation.split("\\|")).hasSize(2);
        assertThat(alternation).startsWith(Reporter.patternFor("Wyo. Stat. Ann."));
    }

    @Test
    void aPrintedFormIsLookedUpWhateverItsPunctuation() {
        assertThat(Reporter.codeFor("Mont. Code Ann.", Reporter.KNOWN)).contains("mt");
        assertThat(Reporter.codeFor("mont code ann", Reporter.KNOWN)).contains("mt");
        assertThat(Reporter.codeFor("C.R.S.", Reporter.KNOWN)).contains("co");
        assertThat(Reporter.codeFor("NDCC", Reporter.KNOWN)).contains("nd");
        assertThat(Reporter.codeFor("Ill. Comp. Stat.", Reporter.KNOWN)).isEmpty();
    }

    @Test
    void sessionLawNamesAreLookedUpApartFromTheCodes() {
        assertThat(Reporter.sessionLawCodeFor("Wyo. Sess. Laws", Reporter.KNOWN))
                .contains("wy");
        assertThat(Reporter.sessionLawCodeFor("laws of montana", Reporter.KNOWN))
                .contains("mt");
        assertThat(Reporter.sessionLawCodeFor("W.S.", Reporter.KNOWN)).isEmpty();
        assertThat(Reporter.codeFor("Wyo. Sess. Laws", Reporter.KNOWN)).isEmpty();
        assertThat(new Reporter("ia", List.of("Iowa Code")).sessionLawForms()).isEmpty();
    }

    @Test
    void squashingKeepsOnlyTheLetters() {
        assertThat(Reporter.squashed("N.D. Cent. Code")).isEqualTo("ndcentcode");
        assertThat(Reporter.squashed("§ 1.")).isEmpty();
    }

    @Test
    void everyKnownCodeIsDistinct() {
        assertThat(Reporter.KNOWN.stream().map(Reporter::code)).doesNotHaveDuplicates();
        assertThat(Reporter.KNOWN.stream()
                        .flatMap(reporter -> reporter.printedForms().stream())
                        .map(Reporter::squashed))
                .doesNotHaveDuplicates();
    }

    @Test
    void aReporterNeedsAShortCodeAndAPrintedForm() {
        assertThatThrownBy(() -> new Reporter("Montana", List.of("MCA"))).isInstanceOf(ConfigError.class);
        assertThatThrownBy(() -> new Reporter("mt", List.of())).isInstanceOf(ConfigError.class);
    }
}
