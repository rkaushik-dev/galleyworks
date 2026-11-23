package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Folds internal cross-references such as {@code this act}, {@code the preceding subsection} and
 * {@code subsection (b) of this section} to one marker.
 *
 * <p>Every act says these and each means something different in each act, so left alone they are
 * strong shared fingerprints that carry no information about copying. A labelled reference is the
 * same kind of thing: when a clause crosses a state line its subsections are relettered, so {@code
 * paragraph (ii) of subsection (a)} arrives as {@code paragraph (ii) of subsection (b)} without a
 * word of drafting having changed. References are folded to a marker rather than deleted: deleting
 * them would join the words on either side into a shingle that never appeared in either document. A
 * numbered section standing on its own, such as {@code section 4 of the act}, is left alone, because
 * nothing ties it to the instrument it is printed in.
 */
public final class CrossReferenceRule implements Rule {

    /** The marker a self-reference folds to. */
    public static final String SELF_REFERENCE = "xref";

    private static final List<String> UNITS =
            List.of("act", "chapter", "article", "section", "subsection", "paragraph", "subparagraph", "title");
    private static final String UNIT = "(?:" + String.join("|", UNITS) + ")s?";
    private static final String SELF = "(?:this|that|the\\s+(?:preceding|succeeding|following|same))\\s+" + UNIT;
    private static final String LABEL = "\\(\\w{1,5}\\)";
    private static final String LABELS =
            LABEL + "(?:\\s*" + LABEL + ")*(?:\\s*(?:,|,?\\s*and|,?\\s*or|through|to)\\s*" + LABEL + ")*";
    private static final String LOWER_UNIT = "(?:subsection|paragraph|subparagraph|item)s?";
    private static final String NAMED =
            "(?:" + UNIT + "|items?)\\s+(?:" + LABELS + "|\\d+[a-z]?(?:\\s*" + LABELS + ")?)";

    /** Tried in order; a later pattern never claims text an earlier one already folded. */
    private static final List<Pattern> PATTERNS = List.of(
            // subsection (b) of this section; paragraph (ii) of subsection (a) of this section
            Regex.ignoringCase("\\b" + NAMED + "(?:\\s+(?:of|in)\\s+" + NAMED + ")*\\s+(?:of|in)\\s+" + SELF + "\\b"),
            // subsection (b), paragraphs (i) through (iii): a lettered label is always internal
            Regex.ignoringCase("\\b" + LOWER_UNIT + "\\s+" + LABELS),
            // this act, the preceding subsection
            Regex.ignoringCase("\\b" + SELF + "\\b"));

    @Override
    public String name() {
        return "cross-references";
    }

    @Override
    public List<Edit> edits(String text, CanonConfig config) {
        if (!config.normalizeCitations()) {
            return List.of();
        }
        List<Edit> edits = new ArrayList<>();
        for (Pattern pattern : PATTERNS) {
            Matcher match = pattern.matcher(text);
            while (match.find()) {
                TextSpan span = new TextSpan(match.start(), match.end());
                if (edits.stream().noneMatch(edit -> edit.span().overlaps(span))) {
                    edits.add(new Edit(span, SELF_REFERENCE));
                }
            }
        }
        edits.sort(Comparator.comparingInt(edit -> edit.span().start()));
        return edits;
    }
}
