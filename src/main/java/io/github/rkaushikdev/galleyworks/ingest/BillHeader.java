package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.Chamber;
import java.time.LocalDate;
import java.util.Optional;

/**
 * What the printed front matter says about a bill.
 *
 * @param number the printed number
 * @param chamber the chamber of origin
 * @param session the session year
 * @param sponsor who sponsored it
 * @param introduced the day it was introduced or prefiled
 */
public record BillHeader(
        Optional<String> number,
        Optional<Chamber> chamber,
        Optional<String> session,
        Optional<String> sponsor,
        Optional<LocalDate> introduced) {

    /** A header that says nothing. */
    public static final BillHeader EMPTY =
            new BillHeader(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    /** A header naming a number, chamber and session, with no sponsor. */
    public BillHeader(String number, Chamber chamber, String session) {
        this(Optional.of(number), Optional.of(chamber), Optional.of(session), Optional.empty(), Optional.empty());
    }

    /** True when the number, chamber and session are all known. */
    public boolean isComplete() {
        return number.isPresent() && chamber.isPresent() && session.isPresent();
    }
}
