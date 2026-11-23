package io.github.rkaushikdev.galleyworks.canon;

import static io.github.rkaushikdev.galleyworks.canon.CrossReferenceRule.SELF_REFERENCE;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CrossrefsTest {

    private static String rewrite(String text) {
        return rewrite(text, new CanonConfig());
    }

    private static String rewrite(String text, CanonConfig config) {
        return CanonPipeline.applyEdits(text, new CrossReferenceRule().edits(text, config))
                .text();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "this act",
                "this section",
                "this subsection",
                "this chapter",
                "that article",
                "the preceding section",
                "the following subsection",
                "the same paragraph"
            })
    void selfReferencesAreFolded(String phrase) {
        assertThat(rewrite("under " + phrase + ", the board shall act"))
                .isEqualTo("under " + SELF_REFERENCE + ", the board shall act");
    }

    @Test
    void thePluralIsFolded() {
        // "the preceding sections" is one reference, article included.
        assertThat(rewrite("these are the preceding sections")).isEqualTo("these are " + SELF_REFERENCE);
        assertThat(rewrite("under this subsections")).isEqualTo("under " + SELF_REFERENCE);
    }

    @Test
    void caseDoesNotMatter() {
        assertThat(rewrite("This Act applies")).isEqualTo(SELF_REFERENCE + " applies");
    }

    @Test
    void aNumberedSectionIsLeftAlone() {
        String text = "as provided in section 4 of the act";
        assertThat(rewrite(text)).isEqualTo(text);
    }

    @Test
    void aStatutoryCitationIsLeftAlone() {
        String text = "as provided in W.S. 41-3-101";
        assertThat(rewrite(text)).isEqualTo(text);
    }

    @Test
    void ordinaryProseIsUntouched() {
        String text = "the board shall publish a schedule";
        assertThat(rewrite(text)).isEqualTo(text);
    }

    @Test
    void theWordsOnEitherSideAreKeptApart() {
        String rewritten = rewrite("nothing in this act limits the board");
        assertThat(rewritten).isEqualTo("nothing in " + SELF_REFERENCE + " limits the board");
        assertThat(rewritten).doesNotContain("in limits");
    }

    @Test
    void severalReferencesInOneSentence() {
        String text = "this section and the preceding section apply under this act";
        assertThat(rewrite(text))
                .isEqualTo(SELF_REFERENCE + " and " + SELF_REFERENCE + " apply under " + SELF_REFERENCE);
    }

    @Test
    void theRuleCanBeSwitchedOff() {
        String text = "under this act";
        assertThat(rewrite(text, new CanonConfig().withNormalizeCitations(false)))
                .isEqualTo(text);
    }

    @Test
    void theRuleRunsInTheDefaultPipeline() {
        assertThat(Profiles.defaultRules().stream().map(Rule::name)).contains("cross-references");
    }

    @Test
    void thePipelineFoldsASelfReference() {
        CanonResult result = Profiles.canonicalize("(a)  Nothing in this act limits the board.");
        assertThat(result.text()).contains(SELF_REFERENCE).doesNotContain("this act");
    }

    @Test
    void twoActsNoLongerShareTheBoilerplateReference() {
        String left = Profiles.canonicalize("(a)  The provisions of this act apply to every district.")
                .text();
        String right = Profiles.canonicalize("(a)  The provisions of this chapter apply to every district.")
                .text();
        assertThat(left).isEqualTo(right);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "subsection (b) of this section",
                "paragraph (ii) of subsection (a) of this section",
                "subparagraph (A) of paragraph (iii) of subsection (b) of this section",
                "section 4 of this act",
                "section 2(a) of this act",
                "paragraphs (i) and (ii) of this subsection"
            })
    void aLabelledReferenceToThisInstrumentFoldsWhole(String phrase) {
        assertThat(rewrite("as required by " + phrase + ", the board shall act"))
                .isEqualTo("as required by " + SELF_REFERENCE + ", the board shall act");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "subsection (b)",
                "paragraph (iii)",
                "subparagraphs (A) through (C)",
                "paragraphs (i), (ii) and (iv)",
                "items (1) or (2)",
                "subsection (b)(ii)"
            })
    void aLetteredLabelOnItsOwnIsInternal(String phrase) {
        assertThat(rewrite("except as provided in " + phrase + " the fee is waived"))
                .isEqualTo("except as provided in " + SELF_REFERENCE + " the fee is waived");
    }

    @Test
    void reletteringDoesNotChangeTheCanonicalText() {
        String wyoming = Profiles.canonicalize(
                        "(c)  The fee described in paragraph (i) of subsection (b) of this section is waived.")
                .text();
        String montana = Profiles.canonicalize(
                        "(d)  The fee described in paragraph (i) of subsection (c) of this section is waived.")
                .text();
        assertThat(wyoming).isEqualTo(montana).contains(SELF_REFERENCE);
    }

    @Test
    void aReferenceToAnotherActKeepsItsNumber() {
        String text = "as provided in section 4 of the water code";
        assertThat(rewrite(text)).isEqualTo(text);
        assertThat(rewrite("section 4 of this act and section 5 of the water code"))
                .isEqualTo(SELF_REFERENCE + " and section 5 of the water code");
    }

    @Test
    void aLabelThatOpensABlockIsNotAReference() {
        String text = "(b)  The board shall publish the schedule.";
        assertThat(rewrite(text)).isEqualTo(text);
    }

    @Test
    void theLongestReadingWins() {
        assertThat(new CrossReferenceRule().edits("under subsection (b) of this section", new CanonConfig()))
                .singleElement()
                .satisfies(edit -> assertThat(edit.span().start()).isEqualTo(6));
    }
}
