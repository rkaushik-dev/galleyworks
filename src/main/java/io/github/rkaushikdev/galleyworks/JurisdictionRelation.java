package io.github.rkaushikdev.galleyworks;

import java.util.Arrays;

/**
 * Where one jurisdiction stands relative to another in the hierarchy of government.
 *
 * <p>Reuse means something different along each relation. Across {@link #SEPARATE} jurisdictions it
 * is diffusion, the question this tool exists for. Inside one jurisdiction it is routine. From an
 * {@link #ENCLOSING} jurisdiction, a city ordinance repeating its own state's statute, it is usually
 * compliance: the city is required to say what the state already said. From an {@link #ENCLOSED}
 * one, a state adopting a city's ordinance, it is the bottom-up diffusion that is rare and worth
 * seeing.
 */
public enum JurisdictionRelation {
    SAME("same"),
    ENCLOSING("enclosing"),
    ENCLOSED("enclosed"),
    SEPARATE("separate");

    private final String value;

    JurisdictionRelation(String value) {
        this.value = value;
    }

    /** The lowercase name written in reports. */
    public String value() {
        return value;
    }

    /** True for the relations along which reuse is expected rather than evidence of diffusion. */
    public boolean isRoutine() {
        return this == SAME || this == ENCLOSING;
    }

    /** The relation named {@code value}. */
    public static JurisdictionRelation fromValue(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("'" + value + "' is not a valid JurisdictionRelation"));
    }

    @Override
    public String toString() {
        return value;
    }
}
