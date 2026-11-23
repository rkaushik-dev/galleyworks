package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import java.util.Optional;

/**
 * Supplies an indexed instrument, so a report can quote the other side of a match and read the
 * structure around it. The matcher never holds the other document, so the caller provides one of
 * these.
 */
public interface TextSource {

    /** The instrument {@code key} names, when it is available. */
    Optional<Instrument> instrumentOf(InstrumentKey key);

    /** The raw text of {@code key}, when it is available. */
    default Optional<String> textOf(InstrumentKey key) {
        return instrumentOf(key).map(Instrument::rawText);
    }
}
