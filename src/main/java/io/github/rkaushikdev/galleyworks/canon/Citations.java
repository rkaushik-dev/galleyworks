package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Statutory citation normalization.
 *
 * <p>The same provision is cited half a dozen ways depending on who is drafting: {@code W.S.
 * 41-3-101}, {@code Wyo. Stat. Ann. § 41-3-101}, {@code section 41-3-101}. Montana and Colorado put
 * the code after the number instead, as in {@code section 85-2-101, MCA} and {@code section
 * 35-65-101, C.R.S.}. Every recognised form is rewritten to {@code cite <reporter>
 * <number><subsections>}. The reporter is kept, because 41-3-101 in a state code and 41-3-101 in a
 * municipal code are not the same provision. The codes recognised are {@link Reporter#KNOWN}.
 */
public final class Citations {

    /** The reporter recorded for a citation that names no code. */
    public static final String LOCAL_REPORTER = "local";

    private static final String NUMBER = "\\d+(?:[-.]\\d+){0,3}[a-z]?";
    private static final String SUBSECTIONS = "(?:\\([A-Za-z0-9]{1,5}\\))*";
    private static final String REPORTERS = Reporter.alternation(Reporter.KNOWN);
    private static final Pattern CITE = Regex.ignoringCase("(?:(?<title>\\d{1,2})\\s+)?"
            + "(?:(?<![A-Za-z])(?<reporter>" + REPORTERS + ")"
            + "|(?<word>sections?|secs?\\.?|§{1,2}))"
            + "\\s*(?:§{1,2}\\s*)?"
            + "(?<number>" + NUMBER + ")"
            + "(?<subs>" + SUBSECTIONS + ")"
            + "(?:,\\s*(?<trailing>" + REPORTERS + ")(?![A-Za-z]))?");
    private static final Pattern SEPARATED = Regex.compile("\\d[-.]\\d");

    private Citations() {}

    /**
     * A recognised statutory reference.
     *
     * @param span where it is printed
     * @param reporter the code it cites, or {@code local}
     * @param number the section number, lowercased
     * @param subsections the subsection chain, lowercased
     * @param title the federal title, if one was printed
     */
    public record Citation(TextSpan span, String reporter, String number, String subsections, Optional<String> title) {

        /** {@code cite <reporter><title> <number><subsections>}. */
        public String canonical() {
            String cited = title.map(value -> reporter + value).orElse(reporter);
            return "cite " + cited + " " + number + subsections;
        }
    }

    private static String reporterCode(String raw) {
        if (raw == null) {
            return LOCAL_REPORTER;
        }
        return Reporter.codeFor(raw, Reporter.KNOWN).orElse(LOCAL_REPORTER);
    }

    /** Every citation in {@code text}, left to right. */
    public static List<Citation> findCitations(String text) {
        List<Citation> found = new ArrayList<>();
        Matcher match = CITE.matcher(text);
        while (match.find()) {
            String leading = match.group("reporter");
            String reporter = reporterCode(leading != null ? leading : match.group("trailing"));
            String title = match.group("title");
            if (title != null && leading == null) {
                // A bare number in front of "section" is a count, not a title.
                continue;
            }
            if (reporter.equals(LOCAL_REPORTER)
                    && !SEPARATED.matcher(match.group("number")).find()) {
                // "section 4" is a cross-reference inside one act, not a citation.
                continue;
            }
            String subs = match.group("subs");
            found.add(new Citation(
                    new TextSpan(match.start(), match.end()),
                    reporter,
                    Text.lower(match.group("number")),
                    subs == null ? "" : Text.lower(subs),
                    Optional.ofNullable(title)));
        }
        return found;
    }

    /** Rewrites recognised citations into their canonical form. */
    public static final class CitationRule implements Rule {

        @Override
        public String name() {
            return "citations";
        }

        @Override
        public List<Edit> edits(String text, CanonConfig config) {
            if (!config.normalizeCitations()) {
                return List.of();
            }
            return findCitations(text).stream()
                    .map(citation -> new Edit(citation.span(), citation.canonical()))
                    .toList();
        }
    }
}
