package io.github.rkaushikdev.galleyworks.report;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A text source backed by instruments already in memory. */
public final class MappingTextSource implements TextSource {

    private final Map<String, Instrument> bySlug = new HashMap<>();

    /** An empty source. */
    public MappingTextSource() {
        this(List.of());
    }

    /** A source holding {@code instruments}. */
    public MappingTextSource(Iterable<Instrument> instruments) {
        instruments.forEach(this::add);
    }

    /** Make {@code instrument} available. */
    public void add(Instrument instrument) {
        bySlug.put(instrument.key().slug(), instrument);
    }

    @Override
    public Optional<Instrument> instrumentOf(InstrumentKey key) {
        return Optional.ofNullable(bySlug.get(key.slug()));
    }
}
