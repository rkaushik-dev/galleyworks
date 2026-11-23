package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Citations to the session laws: an act as it was passed, cited by year and chapter rather than by
 * where it was codified.
 *
 * <p>A source note or a repealer cites the act that enacted a provision, and every state prints that
 * reference its own way: {@code 2023 Wyo. Sess. Laws ch. 42}, {@code Ch. 15, L. 2023} in Montana,
 * {@code S.L. 2023, ch. 42} in North Dakota, {@code chapter 42, Laws of 2023}. Each recognised form
 * is rewritten to {@code sesslaw <reporter> <year> ch <chapter>}, with {@code local} as the reporter
 * when the print names no state. A citation by page, as Colorado prints {@code L. 2023, p. 1234},
 * keeps the page in place of the chapter.
 */
public final class SessionLaws {

    private static final String CHAPTER = "(?:ch\\.?|chapter)\\s*(?<chapter>\\d{1,4})";
    private static final String PAGE = "(?:p\\.|page)\\s*(?<page>\\d{1,5})";
    private static final String YEAR = "(?<year>(?:18|19|20)\\d{2})";
    private static final String LAWS = "(?:s\\.?\\s?l\\.?|l\\.|laws(?:\\s+of)?)";

    private static final List<Pattern> FORMS = List.of(
            // 2023 Wyo. Sess. Laws ch. 42, 2023 Mont. Laws 215
            Regex.ignoringCase("(?<![\\w.])" + YEAR + "\\s+(?<state>"
                    + Reporter.sessionLawAlternation(Reporter.KNOWN) + ")\\s*,?\\s*(?:" + CHAPTER + "|"
                    + "(?<number>\\d{1,5}))\\b"),
            // L. 2023, ch. 15 and S.L. 2023, ch. 42 and Laws 2023, p. 1234
            Regex.ignoringCase("(?<![\\w.])" + LAWS + "\\s*" + YEAR + "\\s*,?\\s*(?:" + CHAPTER + "|" + PAGE + ")\\b"),
            // Ch. 15, L. 2023 and chapter 42, Laws of 2023
            Regex.ignoringCase("(?<![\\w.])" + CHAPTER + "\\s*,\\s*(?:session\\s+)?" + LAWS + "\\s*" + YEAR + "\\b"));

    private SessionLaws() {}

    /**
     * One session law reference.
     *
     * @param span where it is printed
     * @param reporter the state whose session laws it cites, or {@code local}
     * @param year the year of the session
     * @param unit {@code ch} for a chapter, {@code p} for a page
     * @param number the chapter or page
     */
    public record SessionLaw(TextSpan span, String reporter, String year, String unit, String number) {

        /** {@code sesslaw <reporter> <year> <unit> <number>}. */
        public String canonical() {
            return "sesslaw " + reporter + " " + year + " " + unit + " " + number;
        }
    }

    private static SessionLaw read(Matcher match) {
        String reporter = Citations.LOCAL_REPORTER;
        String state = groupOrNull(match, "state");
        if (state != null) {
            reporter = Reporter.sessionLawCodeFor(state, Reporter.KNOWN).orElse(Citations.LOCAL_REPORTER);
        }
        String chapter = groupOrNull(match, "chapter");
        String page = groupOrNull(match, "page");
        String unit = chapter != null ? "ch" : "p";
        String number = chapter != null ? chapter : page != null ? page : match.group("number");
        return new SessionLaw(
                new TextSpan(match.start(), match.end()),
                reporter,
                match.group("year"),
                unit,
                String.valueOf(Integer.parseInt(number)));
    }

    private static String groupOrNull(Matcher match, String name) {
        return match.namedGroups().containsKey(name) ? match.group(name) : null;
    }

    /** Every session law reference in {@code text}, left to right, none overlapping another. */
    public static List<SessionLaw> findSessionLaws(String text) {
        List<SessionLaw> found = new ArrayList<>();
        for (Pattern form : FORMS) {
            Matcher match = form.matcher(text);
            while (match.find()) {
                SessionLaw candidate = read(match);
                if (found.stream().noneMatch(seen -> seen.span().overlaps(candidate.span()))) {
                    found.add(candidate);
                }
            }
        }
        found.sort(Comparator.comparingInt(law -> law.span().start()));
        return List.copyOf(found);
    }
}
