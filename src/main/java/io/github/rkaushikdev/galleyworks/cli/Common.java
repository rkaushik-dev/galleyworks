package io.github.rkaushikdev.galleyworks.cli;

import io.github.rkaushikdev.galleyworks.Engine;
import io.github.rkaushikdev.galleyworks.EngineConfig;
import io.github.rkaushikdev.galleyworks.JurisdictionRegistry;
import io.github.rkaushikdev.galleyworks.Settings;
import io.github.rkaushikdev.galleyworks.canon.Profiles;
import io.github.rkaushikdev.galleyworks.index.Index;
import io.github.rkaushikdev.galleyworks.index.SqliteIndex;
import io.github.rkaushikdev.galleyworks.ingest.ReaderRegistry;
import io.github.rkaushikdev.galleyworks.match.Confidence;
import io.github.rkaushikdev.galleyworks.report.InstrumentReport;
import io.github.rkaushikdev.galleyworks.report.Ranking;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Options every command that touches an index needs, kept in one place so {@code --index} and
 * {@code --config} mean the same thing in every subcommand.
 */
public final class Common {

    private Common() {}

    /** {@code --index}, for commands that can fall back to an in-memory index. */
    public static final class IndexOption {
        @Option(
                names = "--index",
                paramLabel = "FILE",
                converter = Choices.FilePath.class,
                description = "path to the sqlite index; omit for an in-memory one")
        Path index;

        /** The index path, when one was given. */
        public Optional<Path> path() {
            return Optional.ofNullable(index);
        }
    }

    /** {@code --index}, for commands that cannot do anything useful without one. */
    public static final class RequiredIndexOption {
        @Option(
                names = "--index",
                paramLabel = "FILE",
                required = true,
                converter = Choices.FilePath.class,
                description = "path to the sqlite index; omit for an in-memory one")
        Path index;

        /** The index path. */
        public Path path() {
            return index;
        }
    }

    /** {@code --config}, {@code --profile} and {@code --reading}. */
    public static final class EngineOptions {
        @Option(
                names = "--config",
                paramLabel = "FILE",
                converter = Choices.FilePath.class,
                description = "path to an engine configuration file")
        Path config;

        @Option(names = "--profile", paramLabel = "TEXT", description = "canonicalization profile to apply")
        String profile;

        @Option(
                names = "--reading",
                paramLabel = "[current|proposed]",
                converter = Choices.Readings.class,
                completionCandidates = Choices.Readings.class,
                description = "which reading of an amendment to index")
        String reading;

        @Option(
                names = "--jurisdiction-table",
                paramLabel = "FILE",
                converter = Choices.FilePath.class,
                description = "YAML table of jurisdictions and the ones they sit under")
        Path jurisdictionTable;

        /** The configuration these options ask for. */
        public EngineConfig config() {
            return buildConfig(Optional.ofNullable(config), Optional.ofNullable(profile), Optional.ofNullable(reading));
        }

        /** The jurisdiction table these options name, or an empty one. */
        public JurisdictionRegistry jurisdictions() {
            return jurisdictionTable == null
                    ? new JurisdictionRegistry()
                    : JurisdictionRegistry.load(jurisdictionTable);
        }

        /** A fully wired engine over the index at {@code indexPath}, or an in-memory one. */
        public Engine engine(Optional<Path> indexPath) {
            EngineConfig settings = config();
            return new Engine(
                    settings,
                    buildIndex(indexPath, settings),
                    ReaderRegistry.defaultRegistry(settings.ingest().amendmentReading()),
                    jurisdictions());
        }
    }

    /** {@code --min-confidence}, {@code --crossing-only} and {@code --top}. */
    public static final class DisplayOptions {
        @Option(
                names = "--min-confidence",
                paramLabel = "[weak|moderate|strong]",
                converter = Choices.Confidences.class,
                completionCandidates = Choices.Confidences.class,
                description = "drop findings below this confidence")
        String minConfidence;

        @Option(names = "--crossing-only", description = "print only findings that cross a jurisdiction")
        boolean crossingOnly;

        @Option(names = "--top", paramLabel = "INTEGER", description = "print only the strongest N findings")
        Integer top;

        /** {@code report} narrowed by these flags. */
        public InstrumentReport narrow(InstrumentReport report) {
            return Common.narrow(report, Optional.ofNullable(minConfidence), crossingOnly, Optional.ofNullable(top));
        }
    }

    /**
     * What every subcommand needs from the top level: somewhere to write, and the help layout the
     * subcommands share.
     */
    @Command(sortOptions = false, usageHelpWidth = 100, descriptionHeading = "%n", optionListHeading = "%nOptions:%n")
    abstract static class Base implements Subcommand {

        @Spec
        CommandSpec spec;

        /** The stream the command writes its output to. */
        PrintWriter out() {
            return spec.commandLine().getOut();
        }

        /** Write {@code text} and a newline. */
        void line(String text) {
            PrintWriter out = out();
            out.print(text);
            out.print('\n');
            out.flush();
        }

        /** Write an empty line. */
        void line() {
            line("");
        }
    }

    /**
     * The engine configuration the options ask for: the file and the environment, then the named
     * profile in place of the canon section, then the amendment reading.
     */
    public static EngineConfig buildConfig(
            Optional<Path> configPath, Optional<String> profile, Optional<String> reading) {
        EngineConfig config = Settings.loadConfig(configPath, System.getenv());
        if (profile.isEmpty() && reading.isEmpty()) {
            return config;
        }
        Map<String, Object> mapping = new LinkedHashMap<>(config.toMapping());
        profile.ifPresent(name -> mapping.put("canon", Profiles.profile(name).toMapping()));
        reading.ifPresent(value -> {
            Map<String, Object> ingest = new LinkedHashMap<>(config.ingest().toMapping());
            ingest.put("amendment_reading", value);
            mapping.put("ingest", ingest);
        });
        return EngineConfig.fromMapping(mapping);
    }

    /** Open the index at {@code indexPath}, or an in-memory one. */
    public static Index buildIndex(Optional<Path> indexPath, EngineConfig config) {
        return Index.open(indexPath, config);
    }

    /** A fully wired engine from the common options. */
    public static Engine buildEngine(
            Optional<Path> indexPath, Optional<Path> configPath, Optional<String> profile, Optional<String> reading) {
        EngineConfig config = buildConfig(configPath, profile, reading);
        return new Engine(config, buildIndex(indexPath, config));
    }

    /** Apply the display flags on top of whatever the configuration already did. */
    public static InstrumentReport narrow(
            InstrumentReport report, Optional<String> minConfidence, boolean crossingOnly, Optional<Integer> top) {
        if (minConfidence.isEmpty() && !crossingOnly && top.isEmpty()) {
            return report;
        }
        return Ranking.filterReport(report, minConfidence.map(Confidence::fromValue), crossingOnly, top);
    }

    /** Close {@code index} when it holds a database connection. */
    public static void close(Index index) {
        if (index instanceof SqliteIndex store) {
            store.close();
        }
    }
}
