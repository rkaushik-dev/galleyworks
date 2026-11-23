package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Normalizing the quantities legislative text is full of.
 *
 * <p>Drafters write a quantity twice: {@code sixty (60) days}, {@code one hundred dollars
 * ($100.00)}, {@code five percent (5%)}. Two chambers pick different halves of that pair when they
 * re-typeset, and money is written with and without cents in the same act. Patterns are tried in
 * priority order and a later pattern is skipped where it would overlap an earlier match, so a
 * doubled amount of money is claimed once by the money rule rather than twice.
 *
 * <p>Dates come in the calendar form {@code July 1, 2025} and in the formal one a drafter uses for
 * an effective date, {@code the first day of July, 2025}. Both become {@code date 2025-07-01}. A
 * date that does not exist, such as {@code February 30, 2025}, is left as printed rather than
 * rewritten into something that looks valid.
 */
public final class Numerals {

    /** Month names and their numbers. */
    public static final Map<String, Integer> MONTHS = months();

    /** The ordinal words a day of the month is written with, {@code first} to {@code thirty-first}. */
    public static final List<String> ORDINAL_DAYS = ordinalDays();

    private static final List<String> NUMBER_WORDS = List.of(("zero one two three four five six seven eight nine ten"
                    + " eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty thirty forty"
                    + " fifty sixty seventy eighty ninety hundred thousand million billion and")
            .split(" "));
    private static final String NUM_WORD = "(?:" + String.join("|", NUMBER_WORDS) + ")";
    private static final String WORDS = "\\b" + NUM_WORD + "(?:[\\s-]+" + NUM_WORD + ")*";
    private static final String MONTH_NAMES = String.join("|", MONTHS.keySet());

    private record NumeralPattern(String name, Pattern regex, Function<Matcher, String> render) {}

    private static final List<NumeralPattern> PATTERNS = List.of(
            new NumeralPattern(
                    "money-doubled",
                    Regex.ignoringCase(WORDS + "\\s+dollars?\\s*\\(\\s*\\$\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)\\s*\\)"),
                    Numerals::money),
            new NumeralPattern("money", Regex.compile("\\$\\s*(?<amount>[\\d,]+(?:\\.\\d{2})?)"), Numerals::money),
            new NumeralPattern(
                    "percent-doubled",
                    Regex.ignoringCase(
                            WORDS + "\\s+per\\s?cent(?:um)?\\s*\\(\\s*(?<amount>\\d+(?:\\.\\d+)?)\\s*%?\\s*\\)"),
                    Numerals::percent),
            new NumeralPattern(
                    "percent",
                    Regex.ignoringCase("(?<amount>\\d+(?:\\.\\d+)?)\\s*(?:%|per\\s?cent(?:um)?|percent)"),
                    Numerals::percent),
            new NumeralPattern(
                    "date-formal",
                    Regex.ignoringCase("\\b(?:the\\s+)?(?:(?<word>" + ordinalAlternation()
                            + ")|(?<day>\\d{1,2})(?:st|nd|rd|th))\\s+day\\s+of\\s+(?<month>" + MONTH_NAMES
                            + "),?\\s+(?:in\\s+the\\s+year\\s+)?(?<year>\\d{4})\\b"),
                    Numerals::date),
            new NumeralPattern(
                    "date",
                    Regex.ignoringCase("\\b(?<month>" + MONTH_NAMES
                            + ")\\s+(?<day>\\d{1,2})(?:st|nd|rd|th)?,?\\s+(?<year>\\d{4})\\b"),
                    Numerals::date),
            new NumeralPattern(
                    "doubled-numeral",
                    Regex.ignoringCase(WORDS + "\\s*\\(\\s*(?<amount>[\\d,]+(?:\\.\\d+)?)\\s*\\)"),
                    match -> digits(match.group("amount"))),
            new NumeralPattern(
                    "ordinal",
                    Regex.ignoringCase("\\b(?<amount>\\d+)(?:st|nd|rd|th)\\b"),
                    match -> match.group("amount")));

    private Numerals() {}

    private static Map<String, Integer> months() {
        Map<String, Integer> months = new LinkedHashMap<>();
        for (Month month : Month.values()) {
            months.put(month.name().toLowerCase(Locale.ROOT), month.getValue());
        }
        return Collections.unmodifiableMap(months);
    }

    private static List<String> ordinalDays() {
        List<String> units =
                List.of("first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth");
        List<String> days = new ArrayList<>(units);
        days.addAll(List.of(
                "tenth",
                "eleventh",
                "twelfth",
                "thirteenth",
                "fourteenth",
                "fifteenth",
                "sixteenth",
                "seventeenth",
                "eighteenth",
                "nineteenth",
                "twentieth"));
        units.forEach(unit -> days.add("twenty-" + unit));
        days.add("thirtieth");
        days.add("thirty-first");
        return List.copyOf(days);
    }

    /** Longest first, so {@code twenty-first} is not read as {@code twenty} and a stray word. */
    private static String ordinalAlternation() {
        return ORDINAL_DAYS.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(day -> day.replace("-", "[\\s-]"))
                .collect(Collectors.joining("|"));
    }

    /**
     * The date {@code month day, year} names, when it exists.
     *
     * @param month a month name in any case
     * @param day the day of the month
     * @param year the year
     */
    public static Optional<LocalDate> dateOf(String month, int day, int year) {
        Integer number = MONTHS.get(Text.lower(month));
        if (number == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.of(year, number, day));
        } catch (DateTimeException exc) {
            return Optional.empty();
        }
    }

    /** The day an ordinal word names: {@code first} is 1, {@code twenty first} is 21. */
    public static OptionalInt ordinalDay(String word) {
        int found = ORDINAL_DAYS.indexOf(Text.squash(Text.lower(word)).replace(' ', '-'));
        return found < 0 ? OptionalInt.empty() : OptionalInt.of(found + 1);
    }

    private static String digits(String raw) {
        String cleaned = raw.replace(",", "");
        if (cleaned.endsWith(".00")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned;
    }

    private static String money(Matcher match) {
        return "usd " + digits(match.group("amount"));
    }

    private static String percent(Matcher match) {
        return digits(match.group("amount")) + " pct";
    }

    private static String date(Matcher match) {
        String word = match.namedGroups().containsKey("word") ? match.group("word") : null;
        int day = word != null ? ordinalDay(word).orElseThrow() : Integer.parseInt(match.group("day"));
        return dateOf(match.group("month"), day, Integer.parseInt(match.group("year")))
                .map(when -> "date " + when)
                .orElse(match.group());
    }

    /** Non-overlapping edits that put every quantity into one form, left to right. */
    public static List<Edit> normalizeNumerals(String text) {
        List<TextSpan> claimed = new ArrayList<>();
        List<Edit> found = new ArrayList<>();
        for (NumeralPattern pattern : PATTERNS) {
            Matcher match = pattern.regex().matcher(text);
            while (match.find()) {
                TextSpan span = new TextSpan(match.start(), match.end());
                if (claimed.stream().anyMatch(span::overlaps)) {
                    continue;
                }
                String replacement = pattern.render().apply(match);
                claimed.add(span);
                if (!replacement.equals(span.sliceOf(text))) {
                    found.add(new Edit(span, replacement));
                }
            }
        }
        found.sort(Comparator.comparingInt(edit -> edit.span().start()));
        return found;
    }

    /** Rewrites money, percentages, dates and doubled numerals. */
    public static final class NumeralRule implements Rule {

        @Override
        public String name() {
            return "numerals";
        }

        @Override
        public List<Edit> edits(String text, CanonConfig config) {
            if (!config.normalizeNumbers()) {
                return List.of();
            }
            return normalizeNumerals(text);
        }
    }
}
