package io.github.rkaushikdev.galleyworks.cli;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_FINDINGS;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.LOCAL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.support.Cli;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliDiffusionTest {

    @TempDir
    Path tmp;

    private String corpus;

    @BeforeEach
    void corpus() {
        Path root = Cli.mkdir(tmp.resolve("bills"));
        Cli.writeBill2025(root, makeBill("wy", "HB0142", MODEL_CLAUSE, LocalDate.of(2023, 7, 1)));
        Cli.writeBill2025(root, makeBill("mt", "HB0311", MODEL_CLAUSE, LocalDate.of(2024, 7, 1)));
        Cli.writeBill2025(root, makeBill("nd", "HB0044", MODEL_CLAUSE, LocalDate.of(2025, 7, 1)));
        Cli.writeBill2025(root, makeBill("co", "SB0007", LOCAL_CLAUSE, LocalDate.of(2025, 8, 1)));
        corpus = root.toString();
    }

    private static int edges(String output) {
        return Integer.parseInt(output.split("families over ", -1)[1].split(" ", -1)[0]);
    }

    @Test
    void aFamilyIsReported() {
        Cli.Result result = run("diffusion", corpus);
        assertThat(result.code()).isEqualTo(EXIT_FINDINGS);
        assertThat(result.output()).contains("family of 3").contains("earliest: wy/2025/lower/HB0142 (dated)");
    }

    @Test
    void theRootIsMarked() {
        assertThat(run("diffusion", corpus).output()).contains("* wy/2025/lower/HB0142");
    }

    @Test
    void theSpreadIsMeasured() {
        assertThat(run("diffusion", corpus).output()).contains("spread over 731 days");
    }

    @Test
    void theUnrelatedBillIsNotAFamily() {
        assertThat(run("diffusion", corpus).output()).doesNotContain("co/2025/lower/SB0007");
    }

    @Test
    void theFamilyCountIsPrinted() {
        assertThat(run("diffusion", corpus).output()).contains("1 families over");
    }

    @Test
    void minMembersFilters() {
        Cli.Result result = run("diffusion", "--min-members", "9", corpus);
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("0 families");
    }

    @Test
    void crossingOnlyKeepsTheFamily() {
        assertThat(run("diffusion", "--crossing-only", corpus).output()).contains("family of 3");
    }

    @Test
    void crossingOnlyDropsASingleJurisdictionFamily() {
        Path root = Cli.mkdir(tmp.resolve("single"));
        for (String number : new String[] {"HB0001", "HB0002"}) {
            Cli.writeBill2025(root, makeBill("wy", number, MODEL_CLAUSE));
        }
        Cli.Result result = run("diffusion", "--crossing-only", root.toString());
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("0 families");
    }

    @Test
    void reductionLowersTheEdgeCount() {
        int plain = edges(run("diffusion", corpus).output());
        int reduced = edges(run("diffusion", "--reduce", corpus).output());
        assertThat(reduced).isLessThanOrEqualTo(plain);
    }

    @Test
    void aDotFileIsWritten() {
        Path dot = tmp.resolve("graph.dot");
        Cli.Result result = run("diffusion", "--dot", dot.toString(), corpus);
        assertThat(dot).exists();
        assertThat(Cli.read(dot)).startsWith("digraph reuse {");
        assertThat(result.output()).contains("graph written to");
    }

    @Test
    void aCorpusWithNoReuseExitsZero() {
        Path root = Cli.mkdir(tmp.resolve("clean"));
        Cli.writeBill2025(root, makeBill("co", "SB0007", LOCAL_CLAUSE));
        Cli.Result result = run("diffusion", root.toString());
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("0 families");
    }

    @Test
    void aJurisdictionTableShowsHowTheLanguageMoved() {
        Path root = Cli.mkdir(tmp.resolve("hierarchy"));
        Cli.writeBill2025(root, makeBill("wy", "HB0142", MODEL_CLAUSE, LocalDate.of(2023, 7, 1)));
        Cli.writeBill2025(root, makeBill("mt", "HB0311", MODEL_CLAUSE, LocalDate.of(2024, 7, 1)));
        Cli.writeBill2025(root, makeBill("laramie", "2024-31", MODEL_CLAUSE, LocalDate.of(2025, 4, 1)));
        String table = Cli.write(
                        tmp.resolve("jurisdictions.yaml"),
                        "jurisdictions:\n  - {slug: wy, name: Wyoming, level: state}\n"
                                + "  - {slug: mt, name: Montana, level: state}\n"
                                + "  - {slug: laramie, name: Laramie, level: municipal, parent: wy}\n")
                .toString();
        String output =
                run("diffusion", "--jurisdiction-table", table, root.toString()).output();
        assertThat(output).contains("  moved: ").contains(" downward").contains(" horizontal");
        assertThat(run("diffusion", root.toString()).output()).doesNotContain("moved:");
    }
}
