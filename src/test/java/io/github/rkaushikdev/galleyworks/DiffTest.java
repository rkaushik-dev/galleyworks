package io.github.rkaushikdev.galleyworks;

import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Diff.ChangeKind;
import io.github.rkaushikdev.galleyworks.Diff.InstrumentDiff;
import io.github.rkaushikdev.galleyworks.Diff.SegmentChange;
import io.github.rkaushikdev.galleyworks.canon.Profiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DiffTest {

    private static final String EXTENDED = MODEL_CLAUSE + "\n\n(b)  The board may charge a fee for a certified copy.";

    private static Instrument bill(String body) {
        return bill(body, "HB0142");
    }

    private static Instrument bill(String body, String number) {
        return makeBill("wy", number, body);
    }

    private static String reworded() {
        return MODEL_CLAUSE.replace("priority date", "diversion point");
    }

    @Test
    void aDocumentComparedWithItselfIsIdentical() {
        Instrument one = bill(MODEL_CLAUSE);
        InstrumentDiff result = Diff.compare(one, one);
        assertThat(result.isIdentical()).isTrue();
        assertThat(result.changed()).isEmpty();
        assertThat(result.totalChurn()).isZero();
    }

    @Test
    void theTwoSidesAreNamed() {
        InstrumentDiff result = Diff.compare(bill(MODEL_CLAUSE), bill(MODEL_CLAUSE, "HB0143"));
        assertThat(result.left()).isEqualTo("wy/2025/lower/HB0142");
        assertThat(result.right()).isEqualTo("wy/2025/lower/HB0143");
    }

    @Test
    void aRewordedSubsectionIsChanged() {
        String other = MODEL_CLAUSE.replace("sixty\n(60) days", "ninety (90) days");
        InstrumentDiff result = Diff.compare(bill(MODEL_CLAUSE), bill(other));
        Set<String> changed = result.changed().stream().map(SegmentChange::path).collect(Collectors.toSet());
        assertThat(changed).contains("1(a)");
        assertThat(result.isIdentical()).isFalse();
    }

    @Test
    void aChangedSegmentReportsItsChurn() {
        SegmentChange change = Diff.compare(bill(MODEL_CLAUSE), bill(reworded())).changes().stream()
                .filter(found -> found.path().equals("1(a)"))
                .findFirst()
                .orElseThrow();
        assertThat(change.kind()).isSameAs(ChangeKind.CHANGED);
        assertThat(change.wordsAdded()).isPositive();
        assertThat(change.wordsRemoved()).isPositive();
        assertThat(change.similarity()).isStrictlyBetween(0.5, 1.0);
    }

    @Test
    void anAddedSubsectionIsReported() {
        List<SegmentChange> added = Diff.compare(bill(MODEL_CLAUSE), bill(EXTENDED)).changes().stream()
                .filter(change -> change.kind() == ChangeKind.ADDED)
                .toList();
        assertThat(added.stream().map(SegmentChange::path)).containsExactly("1(b)");
        assertThat(added.getFirst().wordsAdded()).isPositive();
        assertThat(added.getFirst().similarity()).isEqualTo(0.0);
    }

    @Test
    void aRemovedSubsectionIsReported() {
        List<SegmentChange> removed = Diff.compare(bill(EXTENDED), bill(MODEL_CLAUSE)).changes().stream()
                .filter(change -> change.kind() == ChangeKind.REMOVED)
                .toList();
        assertThat(removed.stream().map(SegmentChange::path)).containsExactly("1(b)");
        assertThat(removed.getFirst().wordsRemoved()).isPositive();
    }

    @Test
    void renumberingShowsAsARemovalAndAnAddition() {
        String left = MODEL_CLAUSE + "\n\n(b)  The board may charge a fee.";
        String right = MODEL_CLAUSE + "\n\n(c)  The board may charge a fee.";
        Map<String, ChangeKind> kinds = Diff.compare(bill(left), bill(right)).changes().stream()
                .collect(Collectors.toMap(SegmentChange::path, SegmentChange::kind));
        assertThat(kinds).containsEntry("1(b)", ChangeKind.REMOVED).containsEntry("1(c)", ChangeKind.ADDED);
    }

    @Test
    void countsAddUp() {
        InstrumentDiff result = Diff.compare(bill(MODEL_CLAUSE), bill(reworded()));
        Map<String, Integer> counts = result.counts();
        assertThat(counts.values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(result.changes().size());
        assertThat(counts.get("changed")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void changesComeBackInPathOrder() {
        InstrumentDiff result = Diff.compare(bill(MODEL_CLAUSE), bill(MODEL_CLAUSE + "\n\n(b)  A fee applies."));
        List<String> paths = result.changes().stream().map(SegmentChange::path).toList();
        List<String> sorted = new ArrayList<>(paths);
        sorted.sort(null);
        assertThat(paths).isEqualTo(sorted);
    }

    @Test
    void aVerbatimProfileNoticesACaseChange() {
        String other = MODEL_CLAUSE.replace("The board", "THE BOARD");
        assertThat(Diff.compare(bill(MODEL_CLAUSE), bill(other)).isIdentical()).isFalse();
    }

    @Test
    void theDefaultProfileCanBeOverriddenToIgnoreCase() {
        String other = MODEL_CLAUSE.replace("The board", "THE BOARD");
        assertThat(Diff.compare(bill(MODEL_CLAUSE), bill(other), Profiles.profile("default"))
                        .isIdentical())
                .isTrue();
    }

    @Test
    void churnTotalsAcrossSegments() {
        InstrumentDiff result = Diff.compare(bill(MODEL_CLAUSE), bill(reworded()));
        assertThat(result.totalChurn())
                .isEqualTo(
                        result.changes().stream().mapToInt(SegmentChange::churn).sum())
                .isPositive();
    }

    private static InstrumentDiff following(String left, String right) {
        return Diff.compare(bill(left), bill(right), Profiles.profile("verbatim"), true);
    }

    @Test
    void followingRenumberingPairsAMovedSubsection() {
        String left = MODEL_CLAUSE + "\n\n(b)  The board may charge a fee.";
        String right = MODEL_CLAUSE + "\n\n(c)  The board may charge a fee.";
        List<SegmentChange> moved = following(left, right).changes().stream()
                .filter(change -> change.kind() == ChangeKind.RENUMBERED)
                .toList();
        assertThat(moved).containsExactly(SegmentChange.renumbered("1(b)", "1(c)"));
        assertThat(moved.getFirst().churn()).isZero();
        assertThat(moved.getFirst().similarity()).isEqualTo(1.0);
        assertThat(following(left, right).changes())
                .noneMatch(change -> change.kind() == ChangeKind.ADDED || change.kind() == ChangeKind.REMOVED);
    }

    @Test
    void anInsertedSubsectionShiftsTheRestWithoutRewritingThem() {
        String left = MODEL_CLAUSE + "\n\n(b)  The board may charge a fee.\n\n(c)  No fee applies to inspection.";
        String right = MODEL_CLAUSE + "\n\n(b)  The board shall keep a register.\n\n(c)  The board may charge a fee."
                + "\n\n(d)  No fee applies to inspection.";
        Map<String, ChangeKind> kinds = following(left, right).changes().stream()
                .filter(change -> change.path().startsWith("1("))
                .collect(Collectors.toMap(SegmentChange::path, SegmentChange::kind));
        assertThat(kinds)
                .containsEntry("1(a)", ChangeKind.UNCHANGED)
                .containsEntry("1(b)", ChangeKind.ADDED)
                .containsEntry("1(c)", ChangeKind.RENUMBERED)
                .containsEntry("1(d)", ChangeKind.RENUMBERED);
        Map<ChangeKind, Long> byPath = Diff.compare(bill(left), bill(right)).changes().stream()
                .filter(change -> change.path().startsWith("1("))
                .collect(Collectors.groupingBy(SegmentChange::kind, Collectors.counting()));
        assertThat(byPath).containsEntry(ChangeKind.CHANGED, 2L).containsEntry(ChangeKind.ADDED, 1L);
    }

    @Test
    void aMovedSegmentThatWasAlsoReworded() {
        String left = MODEL_CLAUSE + "\n\n(b)  The board may charge a fee.";
        String right = MODEL_CLAUSE + "\n\n(c)  The board may charge a small fee.";
        Map<String, ChangeKind> kinds = following(left, right).changes().stream()
                .collect(Collectors.toMap(SegmentChange::path, SegmentChange::kind));
        assertThat(kinds).containsEntry("1(b)", ChangeKind.REMOVED).containsEntry("1(c)", ChangeKind.ADDED);
    }

    @Test
    void aPathTakenOverByAMoveStillReportsTheTextItLost() {
        String left = MODEL_CLAUSE + "\n\n(b)  The board may charge a fee.\n\n(c)  Records are public.";
        String right = MODEL_CLAUSE + "\n\n(c)  The board may charge a fee.";
        List<SegmentChange> atC = following(left, right).changes().stream()
                .filter(change -> change.path().equals("1(c)"))
                .toList();
        assertThat(atC).extracting(SegmentChange::kind).containsExactly(ChangeKind.RENUMBERED, ChangeKind.REMOVED);
        assertThat(following(left, right).counts()).containsEntry("renumbered", 1);
    }

    @Test
    void aChangeThatKeptItsPathHasNoPreviousPath() {
        SegmentChange change =
                Diff.compare(bill(MODEL_CLAUSE), bill(reworded()), Profiles.profile("verbatim"), true)
                        .changes()
                        .stream()
                        .filter(found -> found.path().equals("1(a)"))
                        .findFirst()
                        .orElseThrow();
        assertThat(change.kind()).isSameAs(ChangeKind.CHANGED);
        assertThat(change.previousPath()).isEmpty();
    }
}
