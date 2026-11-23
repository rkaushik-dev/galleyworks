package io.github.rkaushikdev.galleyworks.cli;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_ERROR;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_FINDINGS;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_USAGE;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Lexdrift;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliIngestTest {

    @TempDir
    Path tmp;

    private String corpusDir(boolean broken) {
        Cli.writeBill2025(tmp, makeBill("wy", "HB0142", MODEL_CLAUSE));
        if (broken) {
            // An empty file is claimed by no reader at all, which is the ingest failure a scraped
            // corpus actually produces.
            Cli.write(tmp.resolve("mt_2025_HB0311.txt"), "   \n\n");
        }
        return tmp.toString();
    }

    @Test
    void noCommandPrintsHelp() {
        Cli.Result result = run();
        assertThat(result.code()).isEqualTo(EXIT_USAGE);
        assertThat(result.output()).contains("Usage: lexdrift");
    }

    @Test
    void theVersionFlagReportsThePackageVersion() {
        Cli.Result result = run("--version");
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).isEqualTo("lexdrift, version " + Lexdrift.VERSION + "\n");
    }

    @Test
    void ingestListsWhatParsed() {
        Cli.Result result = run("ingest", corpusDir(false));
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output())
                .contains("parsed  wy/2025/lower/HB0142  enrolled")
                .contains("1 parsed, 0 failed, 0 skipped");
    }

    @Test
    void quietPrintsOnlyTheTotals() {
        Cli.Result result = run("ingest", "--quiet", corpusDir(false));
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).doesNotContain("parsed  wy");
        assertThat(result.output().strip()).endsWith("1 parsed, 0 failed, 0 skipped");
    }

    @Test
    void aDocumentThatDoesNotParseIsAFinding() {
        Cli.Result result = run("ingest", corpusDir(true));
        assertThat(result.code()).isEqualTo(EXIT_FINDINGS);
        assertThat(result.output()).contains("failed  ").contains("1 parsed, 1 failed, 0 skipped");
    }

    @Test
    void skippedFilesAreNamed() {
        String root = corpusDir(false);
        Cli.write(tmp.resolve("notes.txt"), "scratch\n");
        Cli.Result result = run("ingest", root);
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("skipped").contains("1 parsed, 0 failed, 1 skipped");
    }

    @Test
    void aPinnedReaderIsUsed() {
        Cli.Result result = run("ingest", "--format", "plaintext", corpusDir(false));
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("plaintext");
    }

    @Test
    void aManifestIsRecognisedByItsSuffix() {
        corpusDir(false);
        Path manifest = Cli.write(
                tmp.resolve("corpus.yaml"),
                "documents:\n"
                        + "  - path: wy_2025_HB0142.txt\n"
                        + "    jurisdiction: wy\n"
                        + "    session: '2025'\n"
                        + "    number: HB0142\n");
        Cli.Result result = run("ingest", manifest.toString());
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("1 parsed");
    }

    @Test
    void aMissingDirectoryReportsAStableCode() {
        Cli.Result result = run("ingest", tmp.resolve("absent").toString());
        assertThat(result.code()).isEqualTo(EXIT_ERROR);
        assertThat(result.errors()).startsWith("lexdrift.ingest: corpus root is not a directory");
    }

    @Test
    void anUnknownCommandIsAUsageError() {
        assertThat(run("nonesuch").code()).isEqualTo(EXIT_USAGE);
    }

    @Test
    void amendmentsListWhatEachSectionChanges() {
        Cli.Result result = run("ingest", "--amendments", corpusDir(false));
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output())
                .contains("parsed  wy/2025/lower/HB0142  enrolled")
                .contains("        section 1 amends cite wy 41-3-101\n");
    }

    @Test
    void amendmentsAreNotListedUnlessAskedFor() {
        assertThat(run("ingest", corpusDir(false)).output()).doesNotContain("amends");
        assertThat(run("ingest", "--quiet", "--amendments", corpusDir(false)).output())
                .doesNotContain("amends");
    }
}
