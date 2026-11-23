package io.github.rkaushikdev.galleyworks.match;

import java.util.List;
import java.util.Optional;

/**
 * What two matched segments share beyond their words: the statute catchline they sit under, and the
 * provisions of the code both of their bills set out to amend.
 *
 * <p>The matcher sees fingerprints only, so this comes from whoever can read both instruments. Each
 * part is a separate kind of evidence. A shared catchline points at a common source for the text. A
 * shared provision, between two bills of one session in one jurisdiction, says they are companion
 * bills: the House and Senate versions of one measure, which match each other because they are
 * meant to.
 *
 * @param catchline the catchline both segments sit under, as the query prints it
 * @param companionProvisions provisions both bills amend, when they are bills of one session
 */
public record SharedContext(Optional<String> catchline, List<String> companionProvisions) {

    /** Nothing shared, or nothing known. */
    public static final SharedContext NONE = new SharedContext(Optional.empty(), List.of());

    /** Copies the provisions. */
    public SharedContext {
        companionProvisions = List.copyOf(companionProvisions);
    }

    /** True when the two bills look like companions. */
    public boolean companions() {
        return !companionProvisions.isEmpty();
    }
}
