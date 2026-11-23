package io.github.rkaushikdev.galleyworks.cli;

import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.InstrumentKey;
import io.github.rkaushikdev.galleyworks.JurisdictionRegistry;
import io.github.rkaushikdev.galleyworks.RunResult;
import io.github.rkaushikdev.galleyworks.diffusion.DiffusionMetrics;
import io.github.rkaushikdev.galleyworks.diffusion.Lineage;
import io.github.rkaushikdev.galleyworks.diffusion.ReuseGraph;
import io.github.rkaushikdev.galleyworks.diffusion.Spread;
import io.github.rkaushikdev.galleyworks.diffusion.Timeline;
import io.github.rkaushikdev.galleyworks.report.DotWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code lexdrift diffusion}: report which families of instruments share text.
 *
 * <p>Given {@code --jurisdiction-table}, each family also says how its edges moved: across between
 * peers, down from a state to its cities, or up from a city to its state.
 */
@Command(
        name = "diffusion",
        header = "report which families of instruments share text",
        customSynopsis = "lexdrift diffusion [OPTIONS] SOURCE",
        description = "Report the families of instruments sharing text in SOURCE.")
public final class DiffusionCommand extends Common.Base {

    @Parameters(paramLabel = "SOURCE")
    String source;

    @Mixin
    Common.IndexOption index;

    @Mixin
    Common.EngineOptions engineOptions;

    @Option(
            names = "--min-members",
            paramLabel = "INTEGER",
            defaultValue = "2",
            showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
            description = "ignore smaller families")
    int minMembers;

    @Option(names = "--crossing-only", description = "only families that cross a jurisdiction")
    boolean crossingOnly;

    @Option(names = "--timeline", description = "print each family in date order")
    boolean showTimeline;

    @Option(names = "--reduce", description = "drop edges a longer path implies")
    boolean reduceEdges;

    @Option(
            names = "--dot",
            paramLabel = "FILE",
            converter = Choices.FilePath.class,
            description = "also write the graph as graphviz dot")
    Path dotPath;

    /** One block of text about one family. */
    public static List<String> describe(Lineage lineage, DiffusionMetrics metrics) {
        return describe(lineage, metrics, Optional.empty());
    }

    /**
     * One block of text about one family, with the way its edges moved through the hierarchy of
     * government when a jurisdiction table supplied one.
     */
    public static List<String> describe(
            Lineage lineage, DiffusionMetrics metrics, Optional<Map<Spread, Integer>> spread) {
        String root = lineage.root().map(InstrumentKey::slug).orElse("unknown");
        String confidence = lineage.confident() ? "dated" : "partly dated";
        OptionalLong span = metrics.spanDays();
        String reach = metrics.characters() + " characters across " + metrics.edges() + " edges";
        List<String> lines = new ArrayList<>();
        lines.add("family of " + lineage.size() + " across " + String.join(", ", lineage.jurisdictions()));
        lines.add("  earliest: " + root + " (" + confidence + ")");
        lines.add(
                span.isPresent()
                        ? "  spread over " + span.getAsLong() + " days, " + reach
                        : "  " + reach + ", no dates");
        spread.ifPresent(counts -> lines.add("  moved: " + Spread.describe(counts)));
        for (InstrumentKey member : lineage.members()) {
            String marker = lineage.root().filter(member::equals).isPresent() ? "*" : " ";
            lines.add("   " + marker + " " + member.slug());
        }
        return lines;
    }

    /** One line per member, in the order the text reached them. */
    public static List<String> renderTimeline(ReuseGraph graph, Lineage lineage) {
        List<Timeline.TimelineStep> steps = Timeline.timeline(graph, lineage);
        if (steps.isEmpty()) {
            return List.of();
        }
        Timeline.TimelineStep origin = steps.getFirst();
        List<String> lines = new ArrayList<>();
        lines.add("  timeline:");
        for (Timeline.TimelineStep step : steps) {
            String when = step.when().map(Object::toString).orElse("undated");
            OptionalLong gap = step.daysAfter(origin);
            String offset = gap.isEmpty() || gap.getAsLong() == 0 ? "" : ", " + gap.getAsLong() + " days later";
            String inherited = step.isOrigin()
                    ? "origin"
                    : step.inheritedCharacters() + " characters from "
                            + step.sources().size() + " source(s)";
            lines.add("    " + when + offset + "  " + step.key().slug() + "  " + inherited);
        }
        return lines;
    }

    @Override
    public Integer call() {
        Engine engine = engineOptions.engine(index.path());
        try {
            return run(engine);
        } finally {
            Common.close(engine.index());
        }
    }

    private int run(Engine engine) {
        RunResult result = engine.run(IngestCommand.loadCorpus(source));
        ReuseGraph graph = reduceEdges ? DiffusionMetrics.transitiveReduction(result.graph()) : result.graph();
        List<Lineage> families = Lineage.lineages(graph).stream()
                .filter(lineage -> lineage.size() >= minMembers && (!crossingOnly || lineage.spreads()))
                .toList();
        JurisdictionRegistry jurisdictions = engine.jurisdictions();
        for (Lineage lineage : families) {
            Optional<Map<Spread, Integer>> spread = jurisdictions.size() == 0
                    ? Optional.empty()
                    : Optional.of(Spread.tally(graph, lineage, jurisdictions));
            describe(lineage, DiffusionMetrics.measure(graph, lineage), spread).forEach(this::line);
            if (showTimeline) {
                renderTimeline(graph, lineage).forEach(this::line);
            }
            line();
        }
        for (List<String> cycle : Timeline.findCycles(graph)) {
            line("warning: dates disagree around " + String.join(" -> ", cycle));
        }
        line(families.size() + " families over " + graph.size() + " edges");
        if (dotPath != null) {
            writeDot(graph);
        }
        return families.isEmpty() ? Main.EXIT_OK : Main.EXIT_FINDINGS;
    }

    private void writeDot(ReuseGraph graph) {
        try (Writer stream = Files.newBufferedWriter(dotPath, StandardCharsets.UTF_8)) {
            DotWriter.writeDot(graph, stream, "reuse");
        } catch (IOException exc) {
            throw new UncheckedIOException(exc);
        }
        line("graph written to " + dotPath);
    }
}
