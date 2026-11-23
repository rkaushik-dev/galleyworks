package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.canon.Numerals;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulling identity and dates out of a bill's front matter.
 *
 * <p>Hints from the caller always win; this is the fallback for a corpus that arrives as a
 * directory of text files with nothing but the text, which is the normal case when a legislature
 * publishes bills as printed.
 */
public final class Metadata {

    private static final Pattern NUMBER =
            Regex.ignoringCase("(?:no\\.?|number)\\s*(?<number>[A-Z]{0,3}[- ]?\\d{1,5}(?:-\\d{1,4})?[A-Z]?)");
    private static final Pattern SESSION = Regex.compile("\\b(?<year>(?:19|20)\\d{2})\\b");
    private static final Pattern SPONSOR = Regex.ignoringCase("^(?:sponsored|introduced) by:?\\s*(?<sponsor>.+)$");
    private static final Pattern INTRODUCED =
            Regex.ignoringCase("^(?:introduced|prefiled|first\\s+reading)(?:\\s+on)?\\s*:?\\s*");
    private static final Pattern EFFECT = Regex.ignoringCase(
            "(?<whole>(?<!\\bof\\s{1,4})\\bthis\\s+(?:act|ordinance|resolution)\\s+)?"
                    + "(?:(?:is|shall\\s+be|becomes|shall\\s+become)\\s+)?effective\\b|\\b(?:takes|shall\\s+take)\\s+effect\\b");
    private static final Pattern LEAD_IN =
            Regex.ignoringCase("^[\\s,]*(?:(?:on|as\\s+of|from)\\s+(?:and\\s+after\\s+)?)?");

    private Metadata() {}

    /**
     * Scan the opening lines of a bill for its printed identity and history.
     *
     * <p>Each field is taken from the first line that carries it, with one dependency: the number is
     * only read on or after the line naming the kind of bill, because a header prints the drafting
     * office's job number ({@code 23LSO-0142}) above the bill number and the two look alike.
     */
    public static BillHeader readHeader(List<String> lines) {
        List<String> printed =
                lines.stream().map(Text::strip).filter(line -> !line.isEmpty()).toList();
        int chamberLine = firstLine(printed, line -> chamberIn(line).isPresent());
        Optional<Chamber> chamber = chamberLine < 0 ? Optional.empty() : chamberIn(printed.get(chamberLine));
        Optional<String> number = chamberLine < 0
                ? Optional.empty()
                : firstOf(printed.subList(chamberLine, printed.size()), Metadata::numberIn);
        return new BillHeader(
                number,
                chamber,
                firstOf(printed, line -> group(SESSION.matcher(line), "year", false)),
                firstOf(printed, line -> group(SPONSOR.matcher(line), "sponsor", true)
                        .map(Text::strip)),
                firstOf(printed, Metadata::introducedIn));
    }

    private static Optional<Chamber> chamberIn(String line) {
        return MeasureType.named(line).map(MeasureType::chamber);
    }

    private static Optional<String> numberIn(String line) {
        return group(NUMBER.matcher(line), "number", false)
                .map(found -> found.replace(" ", "").replace("-", ""));
    }

    /** The date on an {@code Introduced: January 10, 2024} or {@code Prefiled December 1, 2023} line. */
    private static Optional<LocalDate> introducedIn(String line) {
        Matcher label = INTRODUCED.matcher(line);
        if (!label.lookingAt()) {
            return Optional.empty();
        }
        return Numerals.findDates(line).stream()
                .filter(printed -> printed.span().start() == label.end())
                .map(Numerals.PrintedDate::date)
                .findFirst();
    }

    private static Optional<String> group(Matcher match, String name, boolean anchored) {
        return (anchored ? match.lookingAt() : match.find()) ? Optional.of(match.group(name)) : Optional.empty();
    }

    private static int firstLine(List<String> lines, Predicate<String> test) {
        for (int position = 0; position < lines.size(); position++) {
            if (test.test(lines.get(position))) {
                return position;
            }
        }
        return -1;
    }

    private static <T> Optional<T> firstOf(List<String> lines, Function<String, Optional<T>> reader) {
        return lines.stream().map(reader).flatMap(Optional::stream).findFirst();
    }

    /**
     * The date the instrument takes effect, from its effective-date clause.
     *
     * <p>The clause is written {@code is effective July 1, 2025}, {@code shall take effect on the
     * first day of July, 2025} or {@code becomes effective on and after January 1, 2026}. An act
     * often carries several, one for a delayed section and one for the rest, and the one that names
     * {@code this act} as its subject is the instrument's own; without one, the first dated clause
     * is used. A clause that takes effect on passage names no date and yields nothing.
     */
    public static Optional<LocalDate> parseEffectiveDate(String text) {
        List<Numerals.PrintedDate> dates = Numerals.findDates(text);
        Optional<LocalDate> first = Optional.empty();
        Matcher clause = EFFECT.matcher(text);
        while (clause.find()) {
            Optional<LocalDate> dated = dateAfter(text, clause.end(), dates);
            if (dated.isPresent() && clause.group("whole") != null) {
                return dated;
            }
            if (first.isEmpty()) {
                first = dated;
            }
        }
        return first;
    }

    private static Optional<LocalDate> dateAfter(String text, int end, List<Numerals.PrintedDate> dates) {
        Matcher lead = LEAD_IN.matcher(text).region(end, text.length());
        int start = lead.lookingAt() ? lead.end() : end;
        return dates.stream()
                .filter(printed -> printed.span().start() == start)
                .map(Numerals.PrintedDate::date)
                .findFirst();
    }
}
