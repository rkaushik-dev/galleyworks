package io.github.rkaushikdev.galleyworks.cli;

import io.github.rkaushikdev.galleyworks.Diff;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.canon.Profiles;
import io.github.rkaushikdev.galleyworks.ingest.ReaderRegistry;
import io.github.rkaushikdev.galleyworks.util.Floats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code lexdrift diff}: what changed between two prints of one instrument.
 *
 * <p>No index is involved. The default profile is {@code verbatim}, because a comparison of two
 * prints of the same act should notice a renumbering and a capitalisation change; pass {@code
 * --profile default} to see only substantive edits.
 */
@Command(
        name = "diff",
        header = "compare two prints of one instrument",
        customSynopsis = "lexdrift diff [OPTIONS] LEFT RIGHT",
        description = "Compare LEFT with RIGHT, segment by segment.")
public final class DiffCommand extends Common.Base {

    private static final Map<Diff.ChangeKind, String> MARKS = Map.of(
            Diff.ChangeKind.UNCHANGED, " ",
            Diff.ChangeKind.CHANGED, "~",
            Diff.ChangeKind.ADDED, "+",
            Diff.ChangeKind.REMOVED, "-",
            Diff.ChangeKind.RENUMBERED, ">");

    @Parameters(index = "0", paramLabel = "LEFT")
    String left;

    @Parameters(index = "1", paramLabel = "RIGHT")
    String right;

    @Option(
            names = "--jurisdiction",
            paramLabel = "TEXT",
            required = true,
            description = "jurisdiction slug for both documents")
    String jurisdiction;

    @Option(names = "--session", paramLabel = "TEXT", description = "session, when the text does not say")
    String session;

    @Option(names = "--number", paramLabel = "TEXT", description = "instrument number, when the text omits it")
    String number;

    @Option(
            names = "--profile",
            paramLabel = "TEXT",
            defaultValue = "verbatim",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            description = "canonicalization profile to compare under")
    String profile;

    @Option(names = "--show-unchanged", description = "list segments that did not change")
    boolean showUnchanged;

    @Option(
            names = "--follow-renumbering",
            description = "pair a segment that moved to a new label with its earlier self")
    boolean followRenumbering;

    /** One line per segment, plus a summary. */
    public static List<String> renderDiff(Diff.InstrumentDiff result, boolean showUnchanged) {
        List<String> lines = new ArrayList<>();
        lines.add(result.left() + " -> " + result.right());
        for (Diff.SegmentChange change : result.changes()) {
            if (change.isMaterial() || showUnchanged) {
                lines.add(MARKS.get(change.kind()) + " " + padded(change.path(), 16) + " "
                        + padded(change.kind().value(), 10) + " " + detail(change));
            }
        }
        Map<String, Integer> counts = result.counts();
        lines.add(counts.get("changed") + " changed, " + counts.get("added") + " added, " + counts.get("removed")
                + " removed, " + counts.get("renumbered") + " renumbered, " + counts.get("unchanged")
                + " unchanged, " + result.totalChurn() + " words of churn");
        return lines;
    }

    private static String detail(Diff.SegmentChange change) {
        return change.previousPath()
                .map(from -> "from " + from)
                .orElseGet(() -> "similarity " + Floats.fixed(change.similarity(), 2) + ", +" + change.wordsAdded()
                        + " -" + change.wordsRemoved() + " words");
    }

    @Override
    public Integer call() {
        ReaderRegistry registry = ReaderRegistry.defaultRegistry();
        Instrument before = registry.read(QueryCommand.Hints.read(left, jurisdiction, session, number));
        Instrument after = registry.read(QueryCommand.Hints.read(right, jurisdiction, session, number));
        Diff.InstrumentDiff result = Diff.compare(before, after, Profiles.profile(profile), followRenumbering);
        renderDiff(result, showUnchanged).forEach(this::line);
        return result.isIdentical() ? Main.EXIT_OK : Main.EXIT_FINDINGS;
    }

    /** {@code text} padded with spaces to {@code width} code points, as a left-aligned field. */
    private static String padded(String text, int width) {
        int length = text.codePointCount(0, text.length());
        return length >= width ? text : text + " ".repeat(width - length);
    }
}
