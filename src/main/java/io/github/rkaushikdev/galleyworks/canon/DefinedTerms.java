package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Defined terms and the quotation marks around them.
 *
 * <p>A definitions section reads {@code "Board" means the state board of control}, and the term is
 * then quoted, unquoted or capitalised at every later use depending on the drafter. The engine
 * strips the quotes from any phrase the document itself defines, so {@code "board"} and {@code
 * board} fingerprint alike. Only phrases the document defines are touched: stripping every quoted
 * phrase would destroy the difference between a quotation and a definition.
 *
 * <p>Drafting manuals do not agree on the defining verb. Besides {@code means} a definition is
 * written with {@code includes}, {@code shall mean}, {@code has the meaning given in} or {@code is
 * defined as}, and two synonyms are defined together as {@code "appropriator" or "user" means}.
 * Each of those is a definition of every term it quotes.
 */
public final class DefinedTerms {

    private static final String OPENERS = "\"'“‘";
    private static final String CLOSERS = "\"'”’";
    private static final String QUOTE = "[" + OPENERS + "][^" + CLOSERS + "]{1,80}[" + CLOSERS + "]";
    private static final String QUOTED = "[" + OPENERS + "](?<term>[^" + CLOSERS + "]{1,80})[" + CLOSERS + "]";
    private static final String VERB = "(?:means|mean|includes|include|shall\\s+(?:mean|include)"
            + "|has\\s+the\\s+meaning|have\\s+the\\s+meaning|is\\s+defined\\s+as)\\b";
    private static final Pattern DEFINITION =
            Regex.ignoringCase("(?<head>" + QUOTE + "(?:\\s*(?:,|,?\\s*or|,?\\s*and)\\s*" + QUOTE + ")*)\\s+" + VERB);
    private static final Pattern QUOTED_PHRASE = Regex.ignoringCase(QUOTED);
    private static final int MAX_TERM_WORDS = 6;

    private DefinedTerms() {}

    /**
     * A term the document defines, and where it does so.
     *
     * @param term the term as printed, trimmed
     * @param span where the term is printed inside its definition
     */
    public record Definition(String term, TextSpan span) {

        /** The term lowercased with its whitespace collapsed, as later uses are compared. */
        public String key() {
            return Text.squash(Text.lower(term));
        }
    }

    /** Every definition in {@code text}, one per term it defines, in document order. */
    public static List<Definition> findDefinitions(String text) {
        List<Definition> found = new ArrayList<>();
        Matcher match = DEFINITION.matcher(text);
        while (match.find()) {
            Matcher term = QUOTED_PHRASE.matcher(text).region(match.start("head"), match.end("head"));
            while (term.find()) {
                definition(term).ifPresent(found::add);
            }
        }
        return found;
    }

    private static Optional<Definition> definition(Matcher quoted) {
        String term = Text.strip(quoted.group("term"));
        if (term.isEmpty() || Text.words(term).size() > MAX_TERM_WORDS) {
            return Optional.empty();
        }
        return Optional.of(new Definition(term, new TextSpan(quoted.start("term"), quoted.end("term"))));
    }

    /**
     * True when {@code inner}, as quoted, is a defined term or its plural. A definition of the
     * singular governs the plural, so a quoted {@code "appropriators"} is a use of the defined
     * {@code "appropriator"}.
     */
    static boolean usesDefinedTerm(String inner, Set<String> terms) {
        String key = Text.squash(Text.lower(inner));
        if (terms.contains(key)) {
            return true;
        }
        return (key.endsWith("es") && terms.contains(key.substring(0, key.length() - 2)))
                || (key.endsWith("s") && terms.contains(key.substring(0, key.length() - 1)));
    }

    private static List<Edit> quotedOccurrences(String text, Set<String> terms) {
        return QUOTED_PHRASE
                .matcher(text)
                .results()
                .filter(match -> usesDefinedTerm(match.group("term"), terms))
                .map(match -> new Edit(new TextSpan(match.start(), match.end()), match.group("term")))
                .toList();
    }

    /** Strips quotation marks from terms the document defines. */
    public static final class DefinedTermRule implements Rule {

        @Override
        public String name() {
            return "defined-terms";
        }

        @Override
        public List<Edit> edits(String text, CanonConfig config) {
            List<Definition> definitions = findDefinitions(text);
            if (definitions.isEmpty()) {
                return List.of();
            }
            return quotedOccurrences(
                    text, definitions.stream().map(Definition::key).collect(Collectors.toSet()));
        }
    }
}
