package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A kind of legislative measure: what the header calls it, the prefix its number is printed with,
 * and the chamber it originates in.
 *
 * <p>The chamber is part of an instrument's key, and a header does not always say it in words. A
 * corpus discovered from file names carries only the number, and the number is enough: {@code
 * SF0032} is a Senate File, {@code AB12} an Assembly Bill, {@code LB0410} a bill of the Nebraska
 * Legislature, which has one chamber. The table lists the measures the state legislatures and
 * local councils print.
 *
 * @param words what the header prints, lowercase
 * @param prefix the letters the number is printed with, uppercase, or empty when there are none
 * @param chamber the chamber of origin
 */
public record MeasureType(String words, String prefix, Chamber chamber) {

    /** Every measure the header and number readers recognise, longest wording first. */
    public static final List<MeasureType> KNOWN = List.of(
                    new MeasureType("house bill", "HB", Chamber.LOWER),
                    new MeasureType("house file", "HF", Chamber.LOWER),
                    new MeasureType("house joint resolution", "HJR", Chamber.LOWER),
                    new MeasureType("house concurrent resolution", "HCR", Chamber.LOWER),
                    new MeasureType("house resolution", "HR", Chamber.LOWER),
                    new MeasureType("assembly bill", "AB", Chamber.LOWER),
                    new MeasureType("assembly joint resolution", "AJR", Chamber.LOWER),
                    new MeasureType("senate bill", "SB", Chamber.UPPER),
                    new MeasureType("senate file", "SF", Chamber.UPPER),
                    new MeasureType("senate joint resolution", "SJR", Chamber.UPPER),
                    new MeasureType("senate concurrent resolution", "SCR", Chamber.UPPER),
                    new MeasureType("senate resolution", "SR", Chamber.UPPER),
                    new MeasureType("legislative bill", "LB", Chamber.UNICAMERAL),
                    new MeasureType("legislative resolution", "LR", Chamber.UNICAMERAL),
                    new MeasureType("council bill", "CB", Chamber.COUNCIL),
                    new MeasureType("ordinance", "", Chamber.COUNCIL),
                    new MeasureType("executive order", "EO", Chamber.EXECUTIVE))
            .stream()
            .sorted(Comparator.comparingInt((MeasureType type) -> type.words().length())
                    .reversed())
            .toList();

    private static final Pattern PREFIX = Regex.ignoringCase("^(?<prefix>[A-Z]{1,3})[\\s.-]*\\d");

    /** The measure a header line names, the longest wording that appears in it. */
    public static Optional<MeasureType> named(String line) {
        String lowered = Text.lower(line);
        return KNOWN.stream().filter(type -> lowered.contains(type.words())).findFirst();
    }

    /** The measure whose prefix {@code number} is printed with. */
    public static Optional<MeasureType> numbered(String number) {
        Matcher match = PREFIX.matcher(Text.strip(number));
        if (!match.lookingAt()) {
            return Optional.empty();
        }
        String prefix = Text.upper(match.group("prefix"));
        return KNOWN.stream().filter(type -> type.prefix().equals(prefix)).findFirst();
    }
}
