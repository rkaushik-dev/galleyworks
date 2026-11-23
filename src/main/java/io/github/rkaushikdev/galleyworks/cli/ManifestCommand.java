package io.github.rkaushikdev.galleyworks.cli;

import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.Corpus;
import io.github.rkaushikdev.galleyworks.IngestError;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.ingest.Keys;
import io.github.rkaushikdev.galleyworks.ingest.ReaderRegistry;
import io.github.rkaushikdev.galleyworks.util.Text;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * {@code lexdrift manifest}: turn a directory of documents into a manifest.
 *
 * <p>Discovery from file names is convenient and lossy: it cannot record a chamber, a pinned reader,
 * a title or a date, and a file that does not follow the convention is skipped on every run. Writing
 * a manifest once turns those decisions into something that is reviewed and version controlled. The
 * enacted and introduced dates it writes are the ones parsed from the text, and a date corrected in
 * the manifest afterwards is the one every later run uses.
 */
@Command(
        name = "manifest",
        header = "write a corpus manifest for a directory",
        customSynopsis = "lexdrift manifest [OPTIONS] ROOT",
        description = "Write a manifest describing the corpus under ROOT.")
public final class ManifestCommand extends Common.Base {

    @Parameters(paramLabel = "ROOT")
    String root;

    @Option(
            names = "--out",
            paramLabel = "FILE",
            converter = Choices.FilePath.class,
            description = "write the manifest here instead of to standard output")
    Path outPath;

    @Option(names = "--no-enrich", description = "do not parse documents to fill in details")
    boolean noEnrich;

    @Option(names = "--force", description = "overwrite an existing manifest")
    boolean force;

    /**
     * A manifest and the files it could not name.
     *
     * @param payload the manifest mapping: a root and one record per document
     * @param skipped files whose names do not name an instrument
     */
    public record Manifest(Map<String, Object> payload, List<Path> skipped) {

        /** Copies the skipped list. */
        public Manifest {
            skipped = List.copyOf(skipped);
        }

        /** The document records. */
        @SuppressWarnings("unchecked") // built below as a list of mappings
        public List<Map<String, Object>> documents() {
            return (List<Map<String, Object>>) payload.get("documents");
        }
    }

    /** Build the manifest for {@code root}, parsing each document to fill in its details. */
    public static Manifest buildManifest(Path root) {
        return buildManifest(root, true);
    }

    /** Build the manifest for {@code root}. */
    public static Manifest buildManifest(Path root, boolean enrich) {
        Corpus corpus = Corpus.discover(root);
        ReaderRegistry registry = ReaderRegistry.defaultRegistry();
        List<Map<String, Object>> documents = new ArrayList<>();
        for (Corpus.Entry entry : corpus) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("path", posix(corpus.root().relativize(entry.path())));
            new TreeMap<>(entry.hints()).forEach((key, value) -> record.put(key, String.valueOf(value)));
            if (enrich) {
                enrich(record, registry, entry);
            }
            documents.add(record);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("root", ".");
        payload.put("documents", documents);
        return new Manifest(payload, corpus.skipped());
    }

    /** {@code payload} as block-style YAML, keys in insertion order. */
    public static String render(Map<String, Object> payload) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setAllowUnicode(true);
        options.setIndent(2);
        options.setLineBreak(DumperOptions.LineBreak.UNIX);
        return new Yaml(options).dump(payload);
    }

    @Override
    public Integer call() {
        Manifest manifest = buildManifest(Path.of(root), !noEnrich);
        String rendered = render(manifest.payload());
        if (outPath == null) {
            line(Text.rstrip(rendered));
        } else {
            if (Files.exists(outPath) && !force) {
                throw new ConfigError(
                        "manifest already exists", Context.of("path", outPath.toString(), "remedy", "pass --force"));
            }
            try {
                Files.writeString(outPath, rendered, StandardCharsets.UTF_8);
            } catch (IOException exc) {
                throw new UncheckedIOException(exc);
            }
            line(manifest.documents().size() + " documents written to " + outPath);
        }
        for (Path path : manifest.skipped()) {
            line("skipped " + path + ": file name does not name an instrument");
        }
        return manifest.skipped().isEmpty() ? Main.EXIT_OK : Main.EXIT_FINDINGS;
    }

    /** The chamber, reader and dates parsing finds; a document no reader accepts is pinned to plaintext. */
    private static void enrich(Map<String, Object> record, ReaderRegistry registry, Corpus.Entry entry) {
        Optional<Instrument> parsed = read(registry, entry);
        if (parsed.isEmpty()) {
            record.put("format", "plaintext");
            return;
        }
        Instrument instrument = parsed.get();
        record.put("chamber", instrument.key().chamber().value());
        record.put("format", instrument.sourceFormat());
        instrument.enactedOn().ifPresent(date -> record.put(Keys.ENACTED_ON, date.toString()));
        instrument.introducedOn().ifPresent(date -> record.put(Keys.INTRODUCED_ON, date.toString()));
    }

    private static Optional<Instrument> read(ReaderRegistry registry, Corpus.Entry entry) {
        try {
            return Optional.of(registry.read(entry.source()));
        } catch (IngestError exc) {
            return Optional.empty();
        }
    }

    private static String posix(Path relative) {
        List<String> parts = new ArrayList<>();
        relative.forEach(part -> parts.add(part.toString()));
        return String.join("/", parts);
    }
}
