package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.canon.Numerals;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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

    private record ChamberWord(String words, Chamber chamber) {}

    private static final List<ChamberWord> CHAMBER_WORDS = List.of(
            new ChamberWord("house bill", Chamber.LOWER),
            new ChamberWord("house joint resolution", Chamber.LOWER),
            new ChamberWord("senate file", Chamber.UPPER),
            new ChamberWord("senate bill", Chamber.UPPER),
            new ChamberWord("senate joint resolution", Chamber.UPPER),
            new ChamberWord("council bill", Chamber.COUNCIL),
            new ChamberWord("ordinance", Chamber.COUNCIL),
            new ChamberWord("executive order", Chamber.EXECUTIVE));

    private static final Pattern NUMBER =
            Regex.ignoringCase("(?:no\\.?|number)\\s*(?<number>[A-Z]{0,3}[- ]?\\d{1,5}(?:-\\d{1,4})?[A-Z]?)");
    private static final Pattern SESSION = Regex.compile("\\b(?<year>(?:19|20)\\d{2})\\b");
    private static final Pattern SPONSOR = Regex.ignoringCase("^sponsored by:\\s*(?<sponsor>.+)$");
    private static final Pattern EFFECT = Regex.ignoringCase(
            "(?<whole>(?<!\\bof\\s{1,4})\\bthis\\s+(?:act|ordinance|resolution)\\s+)?"
                    + "(?:(?:is|shall\\s+be|becomes|shall\\s+become)\\s+)?effective\\b|\\b(?:takes|shall\\s+take)\\s+effect\\b");
    private static final Pattern LEAD_IN =
            Regex.ignoringCase("^[\\s,]*(?:(?:on|as\\s+of|from)\\s+(?:and\\s+after\\s+)?)?");

    private Metadata() {}

    /** Scan the opening lines of a bill for its printed identity. */
    public static BillHeader readHeader(List<String> lines) {
        String number = null;
        Chamber chamber = null;
        String session = null;
        String sponsor = null;
        for (String raw : lines) {
            String line = Text.strip(raw);
            if (line.isEmpty()) {
                continue;
            }
            String lowered = Text.lower(line);
            if (chamber == null) {
                for (ChamberWord word : CHAMBER_WORDS) {
                    if (lowered.contains(word.words())) {
                        chamber = word.chamber();
                        break;
                    }
                }
            }
            if (number == null && chamber != null) {
                Matcher match = NUMBER.matcher(line);
                if (match.find()) {
                    number = match.group("number").replace(" ", "").replace("-", "");
                }
            }
            if (sponsor == null) {
                Matcher sponsored = SPONSOR.matcher(line);
                if (sponsored.lookingAt()) {
                    sponsor = Text.strip(sponsored.group("sponsor"));
                }
            }
            if (session == null) {
                Matcher year = SESSION.matcher(line);
                if (year.find()) {
                    session = year.group("year");
                }
            }
        }
        return new BillHeader(
                Optional.ofNullable(number),
                Optional.ofNullable(chamber),
                Optional.ofNullable(session),
                Optional.ofNullable(sponsor));
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
