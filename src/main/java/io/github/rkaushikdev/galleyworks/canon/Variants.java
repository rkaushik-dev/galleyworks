package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Spelling variants, and gender-neutral revisions, that mean the same thing.
 *
 * <p>Legislative drafting manuals disagree, and the same clause is re-typed with {@code judgement}
 * for {@code judgment} as it crosses a state line. Only pairs that are interchangeable in a statute
 * are listed: {@code insure} and {@code ensure} are different words in an insurance code and
 * folding them would erase a distinction the text is making on purpose.
 *
 * <p>Most codes have also been through a gender-neutral revision, an act that rewrites {@code
 * chairman} as {@code chair}, {@code fireman} as {@code firefighter} and {@code his} as {@code his or
 * her} throughout without changing what any provision does. A clause copied from a code before its
 * revision would otherwise differ from one copied after it in every sentence that names an officer,
 * so both spellings fold to one form. These revisions can span several words, and the longest one
 * that matches is taken.
 */
public final class Variants {

    /** Each variant spelling of one word and the form it folds to. */
    public static final Map<String, String> VARIANTS = table(
            """
            acknowledgement = acknowledgment
            adviser = advisor
            amongst = among
            authorise = authorize
            authorised = authorized
            behaviour = behavior
            cancelled = canceled
            colour = color
            defence = defense
            enrolment = enrollment
            favour = favor
            fulfil = fulfill
            instalment = installment
            judgement = judgment
            labour = labor
            licence = license
            neighbouring = neighboring
            offence = offense
            organisation = organization
            practise = practice
            programme = program
            recognise = recognize
            recognised = recognized
            shew = show
            travelling = traveling
            whilst = while
            """);

    /** Each wording a gender-neutral revision replaces or introduces, and the form both fold to. */
    public static final Map<String, String> REVISIONS = table(
            """
            chairman = chair
            chairwoman = chair
            chairperson = chair
            chairmen = chairs
            fireman = firefighter
            firemen = firefighters
            policeman = police officer
            policemen = police officers
            workman = worker
            workmen = workers
            draftsman = drafter
            he or she = he
            she or he = he
            his or her = his
            her or his = his
            him or her = him
            himself or herself = himself
            """);

    private static final Pattern WORD = Regex.compile("[A-Za-z]+");
    private static final Pattern REVISION = Regex.ignoringCase("\\b(?:"
            + REVISIONS.keySet().stream()
                    .sorted(Comparator.comparingInt(String::length).reversed())
                    .map(phrase -> phrase.replace(" ", "\\s+"))
                    .collect(Collectors.joining("|"))
            + ")\\b");

    private Variants() {}

    /** A {@code from = to} table, one pair per line, in the order written. */
    private static Map<String, String> table(String pairs) {
        Map<String, String> table = new LinkedHashMap<>();
        for (String line : pairs.strip().split("\n")) {
            String[] halves = line.split("=", 2);
            table.put(Text.strip(halves[0]), Text.strip(halves[1]));
        }
        return Collections.unmodifiableMap(table);
    }

    /** The preferred form of {@code word}, or the word itself. */
    public static String canonicalSpelling(String word) {
        return VARIANTS.getOrDefault(Text.lower(word), word);
    }

    /** The form a gender-neutral wording folds to, or the wording itself. */
    public static String canonicalRevision(String phrase) {
        return REVISIONS.getOrDefault(Text.squash(Text.lower(phrase)), phrase);
    }

    /** Folds interchangeable spellings and gender-neutral wordings to one form. */
    public static final class VariantSpellingRule implements Rule {

        @Override
        public String name() {
            return "variant-spelling";
        }

        @Override
        public List<Edit> edits(String text, CanonConfig config) {
            if (!config.foldVariants()) {
                return List.of();
            }
            List<Edit> edits = new ArrayList<>();
            Matcher revision = REVISION.matcher(text);
            while (revision.find()) {
                edits.add(
                        new Edit(new TextSpan(revision.start(), revision.end()), canonicalRevision(revision.group())));
            }
            Matcher word = WORD.matcher(text);
            while (word.find()) {
                TextSpan span = new TextSpan(word.start(), word.end());
                String preferred = VARIANTS.get(Text.lower(word.group()));
                if (preferred != null
                        && edits.stream().noneMatch(edit -> edit.span().overlaps(span))) {
                    edits.add(new Edit(span, preferred));
                }
            }
            edits.removeIf(edit -> edit.replacement().equals(edit.span().sliceOf(text)));
            edits.sort(Comparator.comparingInt(edit -> edit.span().start()));
            return edits;
        }
    }
}
