package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.IngestError;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.util.Text;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Building an instrument's identity and dates from caller hints, falling back to the front matter.
 *
 * <p>A manifest is where a corpus maintainer corrects what the printed text gets wrong: a bill whose
 * effective-date clause names the wrong year, or one that never prints its introduction date. Its
 * {@code enacted_on} and {@code introduced_on} entries therefore win over anything parsed, exactly as
 * its jurisdiction and number do.
 */
public final class Keys {

    private Keys() {}

    /** The hint that names when an instrument took effect. */
    public static final String ENACTED_ON = "enacted_on";

    /** The hint that names when an instrument was introduced. */
    public static final String INTRODUCED_ON = "introduced_on";

    /**
     * The date hint {@code name} carries, else {@code printed}. A manifest's YAML hands an unquoted
     * {@code 2025-07-01} over as a timestamp and a quoted one as text, and both are accepted.
     *
     * @throws IngestError when the hint is present and is not a calendar date
     */
    public static Optional<LocalDate> resolveDate(SourceDocument document, String name, Optional<LocalDate> printed) {
        Optional<Object> hinted = document.hint(name);
        if (hinted.isEmpty()) {
            return printed;
        }
        Object raw = hinted.get();
        if (raw instanceof LocalDate date) {
            return Optional.of(date);
        }
        if (raw instanceof Date stamp) {
            return Optional.of(stamp.toInstant().atZone(ZoneOffset.UTC).toLocalDate());
        }
        try {
            return Optional.of(LocalDate.parse(Text.strip(String.valueOf(raw))));
        } catch (DateTimeParseException exc) {
            throw new IngestError(
                    "source document date hint is not a date",
                    Context.of("origin", document.origin(), "hint", name, "value", String.valueOf(raw)),
                    exc);
        }
    }

    /** The key {@code document}'s hints name, with nothing printed to fall back on. */
    public static InstrumentKey resolveKey(SourceDocument document) {
        return resolveKey(document, BillHeader.EMPTY);
    }

    /**
     * The key for {@code document}: hints always win, then the printed header.
     *
     * <p>Jurisdiction is never inferred, because a printed bill does not reliably name the state
     * that enacted it and guessing it would merge two corpora that must stay apart. The chamber is
     * different: a hint names it, else the header does, else the number's prefix does, since {@code
     * SB0007} cannot have started in the House. Only a number with no prefix falls back to the lower
     * chamber.
     */
    public static InstrumentKey resolveKey(SourceDocument document, BillHeader printed) {
        Optional<String> session = document.hint("session").map(String::valueOf).or(printed::session);
        Optional<String> number = document.hint("number").map(String::valueOf).or(printed::number);
        Optional<String> jurisdiction = document.hint("jurisdiction").map(String::valueOf);
        List<String> missing = new ArrayList<>();
        if (jurisdiction.isEmpty()) {
            missing.add("jurisdiction");
        }
        if (session.isEmpty()) {
            missing.add("session");
        }
        if (number.isEmpty()) {
            missing.add("number");
        }
        if (!missing.isEmpty()) {
            throw new IngestError(
                    "source document is missing identity hints",
                    Context.of("origin", document.origin(), "missing", String.join(",", missing)));
        }
        Chamber chamber = document.hint("chamber")
                .map(raw -> hintedChamber(document, raw))
                .or(printed::chamber)
                .or(() -> MeasureType.numbered(number.get()).map(MeasureType::chamber))
                .orElse(Chamber.LOWER);
        return new InstrumentKey(jurisdiction.get(), session.get(), chamber, number.get());
    }

    private static Chamber hintedChamber(SourceDocument document, Object raw) {
        if (raw instanceof Chamber known) {
            return known;
        }
        try {
            return Chamber.fromValue(String.valueOf(raw));
        } catch (IllegalArgumentException exc) {
            throw new IngestError(
                    "source document names an unknown chamber",
                    Context.of("origin", document.origin(), "chamber", String.valueOf(raw)),
                    exc);
        }
    }
}
