package io.github.rkaushikdev.galleyworks.cli;

import static io.github.rkaushikdev.galleyworks.cli.Main.EXIT_FINDINGS;
import static io.github.rkaushikdev.galleyworks.support.Cli.run;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.MODEL_CLAUSE;
import static io.github.rkaushikdev.galleyworks.support.CorpusFixtures.makeBill;
import static io.github.rkaushikdev.galleyworks.support.GoldenCorpus.goldenInstruments;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.RunResult;
import io.github.rkaushikdev.galleyworks.diffusion.Lineage;
import io.github.rkaushikdev.galleyworks.diffusion.ReuseEdge;
import io.github.rkaushikdev.galleyworks.diffusion.ReuseGraph;
import io.github.rkaushikdev.galleyworks.support.Cli;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTimelineTest {

    @TempDir
    Path tmp;

    private record Family(ReuseGraph graph, Lineage lineage) {}

    private static Family stateFamily() {
        RunResult result = new Engine().runOver(goldenInstruments());
        Lineage family = Lineage.lineages(result.graph()).stream()
                .filter(lineage -> lineage.size() == 3)
                .findFirst()
                .orElseThrow();
        return new Family(result.graph(), family);
    }

    private static List<String> rendered() {
        Family family = stateFamily();
        return DiffusionCommand.renderTimeline(family.graph(), family.lineage());
    }

    @Test
    void theTimelineListsEveryMember() {
        Family family = stateFamily();
        List<String> lines = DiffusionCommand.renderTimeline(family.graph(), family.lineage());
        assertThat(lines).hasSize(family.lineage().size() + 1);
        assertThat(lines.getFirst().strip()).isEqualTo("timeline:");
    }

    @Test
    void theFirstMemberIsTheOrigin() {
        assertThat(rendered().get(1)).contains("origin");
    }

    @Test
    void laterMembersReportTheirOffset() {
        String body = String.join("\n", rendered());
        assertThat(body).contains("days later").contains("characters from");
    }

    @Test
    void theDatesRunForward() {
        List<String> dates = rendered().subList(1, rendered().size()).stream()
                .map(line -> line.strip().split("\\s+", -1)[0])
                .toList();
        assertThat(dates).isSorted();
    }

    @Test
    void theCommandPrintsATimeline() {
        Cli.Result result = run("diffusion", "--timeline", Cli.goldenCorpus(tmp).toString());
        assertThat(result.code()).isEqualTo(EXIT_FINDINGS);
        assertThat(result.output()).contains("timeline:").contains("origin");
    }

    @Test
    void theTimelineIsOffByDefault() {
        assertThat(run("diffusion", Cli.goldenCorpus(tmp).toString()).output()).doesNotContain("timeline:");
    }

    @Test
    void aCleanCorpusReportsNoCycles() {
        assertThat(run("diffusion", Cli.goldenCorpus(tmp).toString()).output()).doesNotContain("dates disagree");
    }

    @Test
    void theAdoptionCurveIsPrintedOnRequest() {
        Family family = stateFamily();
        assertThat(DiffusionCommand.renderCurve(family.graph(), family.lineage()))
                .isEqualTo("  adopted: 2023 1, 2024 2, 2025 3");
        String output =
                run("diffusion", "--by-year", Cli.goldenCorpus(tmp).toString()).output();
        assertThat(output).contains("  adopted: 2023 1, 2024 2, 2025 3").contains("  adopted: 2024 1, 2025 2");
        assertThat(run("diffusion", Cli.goldenCorpus(tmp).toString()).output()).doesNotContain("adopted:");
    }

    @Test
    void anUndatedFamilyHasNoCurve() {
        ReuseGraph graph = new ReuseGraph();
        graph.add(new ReuseEdge(
                goldenInstruments().get(0).key(), goldenInstruments().get(1).key(), 1, 100, 0.9, false));
        assertThat(DiffusionCommand.renderCurve(graph, Lineage.lineages(graph).getFirst()))
                .isEqualTo("  adopted: no dated adoptions");
    }

    @Test
    void aReenactmentIsMarkedInTheTimelineAndTheSummary() {
        Path root = Cli.mkdir(tmp.resolve("bills"));
        String clause = MODEL_CLAUSE;
        Cli.writeBill(root, makeBill("wy", "HB0142", clause, "2023", LocalDate.of(2023, 7, 1)));
        Cli.writeBill(root, makeBill("wy", "HB0207", clause, "2025", LocalDate.of(2025, 7, 1)));
        Cli.writeBill(root, makeBill("mt", "HB0311", clause, "2024", LocalDate.of(2024, 7, 1)));
        String output = run("diffusion", "--timeline", root.toString()).output();
        assertThat(output).contains("family of 3 across mt, wy, 1 of them re-enacting earlier adoptions");
        assertThat(output.lines().filter(line -> line.endsWith(", re-enacted")))
                .singleElement()
                .satisfies(line -> assertThat(line).contains("wy/2025/lower/HB0207"));
    }
}
