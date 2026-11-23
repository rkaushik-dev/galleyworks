package io.github.rkaushikdev.galleyworks.canon;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DefinedTermsTest {

    private static final String DEFINITIONS = "As used in this act, \"board\" means the state board of control and "
            + "\"appropriation\" means a right to divert water.";

    private static String rewrite(String text) {
        return CanonPipeline.applyEdits(text, new DefinedTerms.DefinedTermRule().edits(text, new CanonConfig()))
                .text();
    }

    private static List<String> terms(String text) {
        return DefinedTerms.findDefinitions(text).stream()
                .map(DefinedTerms.Definition::term)
                .toList();
    }

    @Test
    void definitionsAreFound() {
        assertThat(terms(DEFINITIONS)).containsExactly("board", "appropriation");
    }

    @Test
    void definitionSpansCoverTheTermOnly() {
        DefinedTerms.Definition definition =
                DefinedTerms.findDefinitions(DEFINITIONS).getFirst();
        assertThat(definition.span().sliceOf(DEFINITIONS)).isEqualTo("board");
    }

    @Test
    void definitionKeysAreFolded() {
        List<DefinedTerms.Definition> found = DefinedTerms.findDefinitions("\"State  Board\" means the agency.");
        assertThat(found.getFirst().key()).isEqualTo("state board");
    }

    @Test
    void curlyQuotesAreRecognised() {
        assertThat(terms("“board” means the agency.")).containsExactly("board");
    }

    @Test
    void singleQuotesAreRecognised() {
        assertThat(terms("'board' means the agency.")).containsExactly("board");
    }

    @Test
    void aLongPhraseIsNotADefinedTerm() {
        String text = "\"a very long quoted phrase that runs on and on here\" means something.";
        assertThat(DefinedTerms.findDefinitions(text)).isEmpty();
    }

    @Test
    void textWithNoDefinitions() {
        assertThat(DefinedTerms.findDefinitions("The board shall act.")).isEmpty();
    }

    @Test
    void quotesAreStrippedFromTheDefinitionItself() {
        assertThat(rewrite("\"board\" means the agency.")).isEqualTo("board means the agency.");
    }

    @Test
    void quotesAreStrippedFromLaterUses() {
        String text = "\"board\" means the agency. The \"board\" shall publish.";
        assertThat(rewrite(text)).isEqualTo("board means the agency. The board shall publish.");
    }

    @Test
    void anUndefinedQuotationKeepsItsQuotes() {
        String text = "\"board\" means the agency. The report said \"the schedule was late\".";
        assertThat(rewrite(text)).contains("\"the schedule was late\"");
    }

    @Test
    void caseDoesNotMatterForLaterUses() {
        String text = "\"board\" means the agency. The \"Board\" shall publish.";
        assertThat(rewrite(text)).isEqualTo("board means the agency. The Board shall publish.");
    }

    @Test
    void multiWordTermsAreHandled() {
        String text = "\"water right\" means a right. A \"water right\" is transferable.";
        assertThat(rewrite(text)).isEqualTo("water right means a right. A water right is transferable.");
    }

    @Test
    void twoDefinitionsInOneSentence() {
        String rewritten = rewrite(DEFINITIONS);
        assertThat(rewritten).doesNotContain("\"").contains("board means the state board of control");
    }

    @Test
    void aDocumentWithNoDefinitionsIsUntouched() {
        String text = "The report said \"late\".";
        assertThat(rewrite(text)).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "includes",
                "include",
                "shall mean",
                "shall include",
                "has the meaning given in W.S. 41-1-101",
                "is defined as"
            })
    void everyDefiningVerbIsADefinition(String verb) {
        assertThat(terms("As used in this section, \"district\" " + verb + " an irrigation district."))
                .containsExactly("district");
    }

    @Test
    void synonymsDefinedTogetherAreEachDefined() {
        assertThat(terms("\"Appropriator\" or \"user\" means a holder of record."))
                .containsExactly("Appropriator", "user");
        assertThat(terms("\"Board\", \"commission\" and \"department\" mean the agency."))
                .containsExactly("Board", "commission", "department");
    }

    @Test
    void theTermFormIsADefinition() {
        assertThat(terms("the term \"season\" means the irrigation season.")).containsExactly("season");
    }

    @Test
    void aQuotationFollowedByAnotherVerbIsNoDefinition() {
        assertThat(terms("The sign shall read \"no fee\" and contain the schedule."))
                .isEmpty();
        assertThat(terms("The word \"means\" appears here.")).isEmpty();
    }

    @Test
    void laterUsesOfEverySynonymLoseTheirQuotes() {
        String text = "\"Appropriator\" or \"user\" means a holder. A \"user\" shall file.";
        assertThat(rewrite(text)).isEqualTo("Appropriator or user means a holder. A user shall file.");
    }

    @Test
    void aQuotedPluralIsAUseOfTheDefinedSingular() {
        String text = "\"Appropriator\" means a holder. All \"appropriators\" and \"ditches\" are listed.";
        assertThat(rewrite(text))
                .isEqualTo("Appropriator means a holder. All appropriators and \"ditches\" are listed.");
        assertThat(rewrite("\"Ditch\" includes a canal. Two \"ditches\" meet."))
                .isEqualTo("Ditch includes a canal. Two ditches meet.");
    }

    @Test
    void thePluralCheckNeedsTheSingularToBeDefined() {
        assertThat(DefinedTerms.usesDefinedTerm("boards", Set.of("board"))).isTrue();
        assertThat(DefinedTerms.usesDefinedTerm("Boards", Set.of("board"))).isTrue();
        assertThat(DefinedTerms.usesDefinedTerm("boardes", Set.of("board"))).isTrue();
        assertThat(DefinedTerms.usesDefinedTerm("bards", Set.of("board"))).isFalse();
        assertThat(DefinedTerms.usesDefinedTerm("s", Set.of("board"))).isFalse();
        assertThat(DefinedTerms.usesDefinedTerm("water  right", Set.of("water right")))
                .isTrue();
    }
}
