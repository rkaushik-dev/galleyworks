package io.github.rkaushikdev.galleyworks.cli;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_ERROR;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_FINDINGS;
import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_OK;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Corpus;
import io.github.rkaushikdev.galleyworks.Settings;
import io.github.rkaushikdev.galleyworks.support.Cli;
import io.github.rkaushikdev.galleyworks.support.Fixtures;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliManifestTest {

    @TempDir
    Path tmp;

    private Path corpus;

    @BeforeEach
    void corpus() {
        corpus = Cli.goldenCorpus(tmp);
    }

    @Test
    void aManifestNamesEveryDocument() {
        ManifestCommand.Manifest manifest = ManifestCommand.buildManifest(corpus);
        assertThat(manifest.documents()).hasSize(6);
        assertThat(manifest.skipped()).isEmpty();
    }

    @Test
    void pathsAreRelativeAndPosix() {
        List<Object> paths = ManifestCommand.buildManifest(corpus).documents().stream()
                .map(document -> document.get("path"))
                .toList();
        assertThat(paths).allSatisfy(path -> assertThat(String.valueOf(path)).doesNotContain("\\"));
        assertThat(paths).contains("wy_2023_HB0142.txt");
    }

    @Test
    void enrichmentFillsInTheChamberAndDate() {
        Map<String, Object> row = ManifestCommand.buildManifest(corpus).documents().stream()
                .filter(document -> document.get("number").equals("SB0007"))
                .findFirst()
                .orElseThrow();
        assertThat(row)
                .containsEntry("chamber", "upper")
                .containsEntry("format", "enrolled")
                .containsEntry("enacted_on", "2025-09-01");
    }

    @Test
    void enrichmentCanBeSwitchedOff() {
        assertThat(ManifestCommand.buildManifest(corpus, false).documents())
                .allSatisfy(document -> assertThat(document).doesNotContainKey("chamber"));
    }

    @Test
    void theManifestRoundTripsThroughTheLoader() {
        Path target = corpus.resolve("corpus.yaml");
        run("manifest", "--out", target.toString(), corpus.toString());
        Corpus loaded = Corpus.loadManifest(target);
        assertThat(loaded.size()).isEqualTo(6);
        assertThat(Corpus.readAll(loaded)).hasSize(6);
    }

    @Test
    void theCommandPrintsToStdout() {
        Cli.Result result = run("manifest", corpus.toString());
        assertThat(result.code()).isEqualTo(EXIT_OK);
        Map<?, ?> payload = Settings.yaml().load(result.output());
        assertThat((List<?>) payload.get("documents")).hasSize(6);
    }

    @Test
    void theCommandWritesAFile() {
        Path target = tmp.resolve("corpus.yaml");
        Cli.Result result = run("manifest", "--out", target.toString(), corpus.toString());
        assertThat(result.code()).isEqualTo(EXIT_OK);
        assertThat(target).exists();
        assertThat(result.output()).contains("6 documents written");
    }

    @Test
    void anExistingManifestIsNotOverwritten() {
        Path target = Cli.write(tmp.resolve("corpus.yaml"), "documents: []\n");
        assertThat(run("manifest", "--out", target.toString(), corpus.toString())
                        .code())
                .isEqualTo(EXIT_ERROR);
        assertThat(Cli.read(target)).isEqualTo("documents: []\n");
    }

    @Test
    void forceOverwrites() {
        Path target = Cli.write(tmp.resolve("corpus.yaml"), "documents: []\n");
        assertThat(run("manifest", "--force", "--out", target.toString(), corpus.toString())
                        .code())
                .isEqualTo(EXIT_OK);
        assertThat(Cli.read(target)).contains("wy_2023_HB0142.txt");
    }

    @Test
    void skippedFilesAreAFinding() {
        Cli.write(corpus.resolve("notes.txt"), "scratch\n");
        Cli.Result result = run("manifest", corpus.toString());
        assertThat(result.code()).isEqualTo(EXIT_FINDINGS);
        assertThat(result.output()).contains("skipped");
    }

    @Test
    void theLayoutIsBlockStyleWithQuotedNumbers() {
        String rendered = ManifestCommand.render(
                ManifestCommand.buildManifest(corpus, false).payload());
        assertThat(rendered)
                .startsWith("root: .\ndocuments:\n- path: cheyenne_2025_2025-08.txt\n  jurisdiction: cheyenne\n"
                        + "  number: 2025-08\n  session: '2025'\n- path: co_2025_SB0007.txt\n");
    }

    @Test
    void aMissingDirectoryIsAnError() {
        assertThat(run("manifest", tmp.resolve("absent").toString()).code()).isEqualTo(EXIT_ERROR);
    }

    @Test
    void enrichmentKeepsTheIntroductionApartFromTheEnactment() {
        Path root = Cli.mkdir(tmp.resolve("introduced"));
        Cli.write(
                root.resolve("wy_2025_HB0142.txt"),
                Fixtures.WATER_BILL.replace(
                        "HOUSE BILL NO. HB0142\n", "HOUSE BILL NO. HB0142\n\nIntroduced: January 13, 2025\n"));
        Map<String, Object> row =
                ManifestCommand.buildManifest(root).documents().getFirst();
        assertThat(row).containsEntry("enacted_on", "2025-07-01").containsEntry("introduced_on", "2025-01-13");
    }

    @Test
    void aDateCorrectedInTheManifestIsTheOneUsed() {
        Path manifest = tmp.resolve("corpus.yaml");
        Cli.write(
                manifest,
                "root: .\ndocuments:\n  - path: " + corpus.getFileName() + "/co_2025_SB0007.txt\n"
                        + "    jurisdiction: co\n    session: '2025'\n    number: SB0007\n    enacted_on: 2026-01-01\n");
        var instruments = Corpus.readAll(Corpus.loadManifest(manifest));
        assertThat(instruments.getFirst().enactedOn()).contains(LocalDate.of(2026, 1, 1));
    }
}
