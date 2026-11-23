package io.github.rkaushikdev.galleyworks.cli;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_ERROR;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_FINDINGS;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_USAGE;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.support.Cli;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliDiffTest {

    private static final String EXTENDED = MODEL_CLAUSE + "\n\n(b)  A fee applies.";

    @TempDir
    Path tmp;

    private String write(String name, String body) {
        return Cli.write(tmp.resolve(name), makeBill("wy", "HB0142", body).rawText())
                .toString();
    }

    private String[] pair() {
        String other = MODEL_CLAUSE.replace("priority date", "diversion point");
        return new String[] {write("left.txt", MODEL_CLAUSE), write("right.txt", other)};
    }

    private Cli.Result diff(String... args) {
        String[] argv = new String[args.length + 1];
        argv[0] = "diff";
        System.arraycopy(args, 0, argv, 1, args.length);
        return run(argv);
    }

    @Test
    void identicalDocumentsExitZero() {
        String path = write("one.txt", MODEL_CLAUSE);
        Cli.Result result = diff("--jurisdiction", "wy", path, path);
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("0 changed");
    }

    @Test
    void aChangedDocumentIsAFinding() {
        String[] pair = pair();
        Cli.Result result = diff("--jurisdiction", "wy", pair[0], pair[1]);
        assertThat(result.code()).isEqualTo(EXIT_FINDINGS);
        assertThat(result.output()).contains("~ 1(a)").contains("changed");
    }

    @Test
    void theTwoSidesAreNamed() {
        String[] pair = pair();
        assertThat(diff("--jurisdiction", "wy", pair[0], pair[1]).output())
                .startsWith("wy/2025/lower/HB0142 -> wy/2025/lower/HB0142");
    }

    @Test
    void theSummaryCountsTheChurn() {
        String[] pair = pair();
        assertThat(diff("--jurisdiction", "wy", pair[0], pair[1]).output()).contains("words of churn");
    }

    @Test
    void unchangedSegmentsAreHiddenByDefault() {
        String left = write("left.txt", MODEL_CLAUSE);
        String right = write("right.txt", EXTENDED);
        List<String> lines =
                diff("--jurisdiction", "wy", left, right).output().lines().toList();
        assertThat(lines.getLast()).contains("unchanged");
        assertThat(lines).noneMatch(line -> line.startsWith("  1(a)"));
    }

    @Test
    void unchangedSegmentsCanBeShown() {
        String left = write("left.txt", MODEL_CLAUSE);
        String right = write("right.txt", EXTENDED);
        List<String> lines = diff("--show-unchanged", "--jurisdiction", "wy", left, right)
                .output()
                .lines()
                .toList();
        assertThat(lines).anyMatch(line -> line.contains("unchanged") && line.contains("1(a)"));
    }

    @Test
    void anAddedSegmentIsMarked() {
        String left = write("left.txt", MODEL_CLAUSE);
        String right = write("right.txt", EXTENDED);
        assertThat(diff("--jurisdiction", "wy", left, right).output()).contains("+ 1(b)");
    }

    @Test
    void theDefaultProfileIsVerbatim() {
        String left = write("left.txt", MODEL_CLAUSE);
        String right = write("right.txt", MODEL_CLAUSE.replace("The board", "THE BOARD"));
        assertThat(diff("--jurisdiction", "wy", left, right).code()).isEqualTo(EXIT_FINDINGS);
    }

    @Test
    void aLooserProfileIgnoresCase() {
        String left = write("left.txt", MODEL_CLAUSE);
        String right = write("right.txt", MODEL_CLAUSE.replace("The board", "THE BOARD"));
        assertThat(diff("--profile", "default", "--jurisdiction", "wy", left, right)
                        .code())
                .isEqualTo(EXIT_OK);
    }

    @Test
    void anUnknownProfileIsAnError() {
        String[] pair = pair();
        assertThat(diff("--profile", "nowhere", "--jurisdiction", "wy", pair[0], pair[1])
                        .code())
                .isEqualTo(EXIT_ERROR);
    }

    @Test
    void aMissingDocumentIsAnError() {
        String path = write("one.txt", MODEL_CLAUSE);
        assertThat(diff("--jurisdiction", "wy", path, "absent.txt").code()).isEqualTo(EXIT_ERROR);
    }

    @Test
    void theJurisdictionIsRequired() {
        String[] pair = pair();
        assertThat(diff(pair[0], pair[1]).code()).isEqualTo(EXIT_USAGE);
    }

    @Test
    void followingRenumberingNamesTheEarlierLabel() {
        String left = write("before.txt", MODEL_CLAUSE + "\n\n(b)  A fee applies.");
        String right = write("after.txt", MODEL_CLAUSE + "\n\n(c)  A fee applies.");
        List<String> lines = diff("--follow-renumbering", "--jurisdiction", "wy", left, right)
                .output()
                .lines()
                .toList();
        assertThat(lines).anyMatch(line -> line.startsWith("> 1(c)") && line.endsWith("renumbered from 1(b)"));
        assertThat(lines.getLast()).contains("0 added, 0 removed, 1 renumbered");
        assertThat(diff("--jurisdiction", "wy", left, right).output()).contains("1 added, 1 removed, 0 renumbered");
    }
}
