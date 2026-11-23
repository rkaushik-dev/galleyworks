package io.github.rkaushikdev.galleyworks.cli;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_ERROR;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_FINDINGS;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.LOCAL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.index.SqliteIndex;
import io.github.rkaushikdev.galleyworks.report.ReportFixtures;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliQueryTest {

    @TempDir
    Path tmp;

    private String indexPath;
    private String copyPath;
    private String originalPath;

    @BeforeEach
    void prepared() {
        Path root = Cli.mkdir(tmp.resolve("bills"));
        Cli.writeBill2025(root, makeBill("wy", "HB0142", MODEL_CLAUSE));
        indexPath = tmp.resolve("index.sqlite").toString();
        run("index", "--index", indexPath, root.toString());
        copyPath = Cli.write(
                        tmp.resolve("candidate.txt"),
                        makeBill("mt", "HB0311", MODEL_CLAUSE).rawText())
                .toString();
        originalPath = Cli.write(
                        tmp.resolve("original.txt"),
                        makeBill("co", "SB0007", LOCAL_CLAUSE).rawText())
                .toString();
    }

    private Cli.Result query(String jurisdiction, String... rest) {
        String[] argv = new String[rest.length + 5];
        argv[0] = "query";
        argv[1] = "--index";
        argv[2] = indexPath;
        argv[3] = "--jurisdiction";
        argv[4] = jurisdiction;
        System.arraycopy(rest, 0, argv, 5, rest.length);
        return run(argv);
    }

    @Test
    void aCopiedBillIsReported() {
        Cli.Result result = query("mt", copyPath);
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output())
                .contains("lexdrift report for mt/2025/lower/HB0311")
                .contains("wy/2025/lower/HB0142");
    }

    @Test
    void anOriginalBillReportsNothing() {
        Cli.Result result = query("co", originalPath);
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(result.output()).contains("no reuse found");
    }

    @Test
    void jsonOutputParses() throws Exception {
        JsonNode payload = new ObjectMapper()
                .readTree(query("mt", "--output", "json", copyPath).output());
        assertThat(payload.at("/instrument/jurisdiction").asText()).isEqualTo("mt");
        assertThat(payload.get("matches")).isNotEmpty();
    }

    @Test
    void csvOutputParses() {
        List<Map<String, String>> rows =
                ReportFixtures.readCsv(query("mt", "--output", "csv", copyPath).output());
        assertThat(rows).isNotEmpty();
        assertThat(rows.getFirst()).containsEntry("other_jurisdiction", "wy");
    }

    @Test
    void theFailThresholdFlagsACopy() {
        assertThat(query("mt", "--fail-over", "0.1", copyPath).code()).isEqualTo(EXIT_FINDINGS);
    }

    @Test
    void theFailThresholdReadsUnderscoresAndInfinity() {
        assertThat(query("mt", "--fail-over", "1_0e-2", copyPath).code()).isEqualTo(EXIT_FINDINGS);
        assertThat(query("mt", "--fail-over", " INF ", copyPath).code()).isEqualTo(EXIT_OK);
        assertThat(query("mt", "--fail-over", "1.0d", copyPath).code()).isEqualTo(Main.EXIT_USAGE);
        assertThat(query("mt", "--fail-over", "1__0", copyPath).code()).isEqualTo(Main.EXIT_USAGE);
    }

    @Test
    void theFailThresholdPassesAnOriginal() {
        assertThat(query("co", "--fail-over", "0.1", originalPath).code()).isEqualTo(EXIT_OK);
    }

    @Test
    void theQueriedDocumentIsNotAddedToTheIndex() {
        query("mt", copyPath);
        try (SqliteIndex store = new SqliteIndex(Path.of(indexPath))) {
            assertThat(store.instruments())
                    .extracting(InstrumentKey::jurisdiction)
                    .containsExactly("wy");
        }
    }

    @Test
    void hintsCanBeOverridden() {
        String output =
                query("mt", "--session", "2026", "--number", "HB9999", copyPath).output();
        assertThat(output).contains("mt/2026/lower/HB9999");
    }

    @Test
    void aMissingDocumentIsAnError() {
        assertThat(query("mt", "absent.txt").code()).isEqualTo(EXIT_ERROR);
    }

    @Test
    void theWidthFlagNarrowsTheReport() {
        assertThat(query("mt", "--width", "60", copyPath).output().lines())
                .allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(60));
    }

    @Test
    void aJurisdictionTableRaisesTheFloorForACityRepeatingItsState() {
        String reworded = MODEL_CLAUSE
                .replace("sixty\n(60) days", "ninety (90) days")
                .replace(
                        "the acreage irrigated under the appropriation",
                        "the acreage under cultivation in the district");
        String ordinance = Cli.write(
                        tmp.resolve("ordinance.txt"),
                        makeBill("laramie", "2024-31", reworded).rawText())
                .toString();
        String table = Cli.write(
                        tmp.resolve("jurisdictions.yaml"),
                        "jurisdictions:\n  - {slug: wy, name: Wyoming, level: state}\n"
                                + "  - {slug: laramie, name: Laramie, level: municipal, parent: wy}\n")
                .toString();
        String config = Cli.write(tmp.resolve("strict.yaml"), "match:\n  same_jurisdiction_containment: 0.85\n")
                .toString();
        assertThat(query("laramie", "--config", config, ordinance).output()).contains("wy/2025/lower/HB0142");
        assertThat(query("laramie", "--config", config, "--jurisdiction-table", table, ordinance)
                        .output())
                .contains("no reuse found");
    }

    @Test
    void anUnreadableJurisdictionTableIsAnError() {
        Cli.Result result =
                query("mt", "--jurisdiction-table", tmp.resolve("absent.yaml").toString(), copyPath);
        assertThat(result.code()).isEqualTo(EXIT_ERROR);
    }
}
