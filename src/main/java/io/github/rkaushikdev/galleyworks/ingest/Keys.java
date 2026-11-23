package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.Chamber;
import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.IngestError;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Building an instrument's identity from caller hints, falling back to the front matter. */
public final class Keys {

    private Keys() {}

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
