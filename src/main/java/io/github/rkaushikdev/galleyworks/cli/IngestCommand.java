package io.github.rkaushikdev.galleyworks.cli;

import io.github.rkaushikdev.galleyworks.Corpus;
import io.github.rkaushikdev.galleyworks.IngestError;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.ingest.AmendatoryClauses;
import io.github.rkaushikdev.galleyworks.ingest.ReaderRegistry;
import io.github.rkaushikdev.galleyworks.ingest.SourceDocument;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code lexdrift ingest}: parse a corpus and report what came back, without indexing anything.
 *
 * <p>With {@code --amendments} each instrument is followed by the directive of every amendatory
 * section, which is the quickest way to see two bills reaching for the same provision of the code.
 */
@Command(
        name = "ingest",
        header = "parse a corpus and report what came back",
        customSynopsis = "lexdrift ingest [OPTIONS] SOURCE",
        description = "Parse every document under SOURCE without indexing anything.")
public final class IngestCommand extends Common.Base {

    @Parameters(paramLabel = "SOURCE")
    String source;

    @Option(names = "--format", paramLabel = "TEXT", description = "pin one reader for every document")
    String formatName;

    @Option(names = "--quiet", description = "print only the totals")
    boolean quiet;

    @Option(names = "--amendments", description = "list the provisions each instrument amends, creates or repeals")
    boolean amendments;

    /** Load {@code source} as a manifest when it looks like one, else as a directory. */
    public static Corpus loadCorpus(String source) {
        if (source.endsWith(".yaml") || source.endsWith(".yml")) {
            return Corpus.loadManifest(Path.of(source));
        }
        return Corpus.discover(Path.of(source));
    }

    /** One line per directive: what the section does, and to which provisions. */
    public static List<String> describeAmendments(Instrument instrument) {
        return AmendatoryClauses.read(instrument).stream()
                .map(clause -> "        section " + clause.sectionPath() + " "
                        + clause.action().value() + "s " + String.join(", ", clause.provisions()))
                .toList();
    }

    @Override
    public Integer call() {
        Corpus corpus = loadCorpus(source);
        ReaderRegistry registry = ReaderRegistry.defaultRegistry();
        int parsed = 0;
        int failed = 0;
        for (Corpus.Entry entry : corpus) {
            Optional<Instrument> instrument = parse(registry, entry);
            if (instrument.isEmpty()) {
                failed++;
                continue;
            }
            parsed++;
            if (!quiet) {
                describe(instrument.get());
            }
        }
        for (Path path : corpus.skipped()) {
            line("skipped " + path + ": file name does not name an instrument");
        }
        line(parsed + " parsed, " + failed + " failed, " + corpus.skipped().size() + " skipped");
        return failed > 0 ? Main.EXIT_FINDINGS : Main.EXIT_OK;
    }

    /** The entry read with the pinned reader or the detected one; a failure is reported and empty. */
    private Optional<Instrument> parse(ReaderRegistry registry, Corpus.Entry entry) {
        try {
            SourceDocument document = entry.source();
            Optional<String> pinned = Optional.ofNullable(formatName).or(entry::formatName);
            return Optional.of(pinned.isPresent() ? registry.read(document, pinned.get()) : registry.read(document));
        } catch (IngestError exc) {
            line("failed  " + entry.path() + ": " + exc.getMessage());
            return Optional.empty();
        }
    }

    private void describe(Instrument instrument) {
        line("parsed  " + instrument.key().slug() + "  " + instrument.sourceFormat() + "  "
                + instrument.bodySegments().size() + " segments");
        if (amendments) {
            describeAmendments(instrument).forEach(this::line);
        }
    }
}
