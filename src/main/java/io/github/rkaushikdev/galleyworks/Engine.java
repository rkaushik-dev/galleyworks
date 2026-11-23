package io.github.rkaushikdev.galleyworks;

import io.github.rkaushikdev.galleyworks.diffusion.GraphBuilder;
import io.github.rkaushikdev.galleyworks.diffusion.ReuseGraph;
import io.github.rkaushikdev.galleyworks.fingerprint.Fingerprinter;
import io.github.rkaushikdev.galleyworks.fingerprint.SegmentFingerprint;
import io.github.rkaushikdev.galleyworks.index.Index;
import io.github.rkaushikdev.galleyworks.index.MemoryIndex;
import io.github.rkaushikdev.galleyworks.ingest.ReaderRegistry;
import io.github.rkaushikdev.galleyworks.match.BoilerplateFilter;
import io.github.rkaushikdev.galleyworks.match.Dedupe;
import io.github.rkaushikdev.galleyworks.match.Matcher;
import io.github.rkaushikdev.galleyworks.match.SegmentMatch;
import io.github.rkaushikdev.galleyworks.match.StandardClauses;
import io.github.rkaushikdev.galleyworks.report.InstrumentReport;
import io.github.rkaushikdev.galleyworks.report.MappingTextSource;
import io.github.rkaushikdev.galleyworks.report.Ranking;
import io.github.rkaushikdev.galleyworks.report.Reports;
import io.github.rkaushikdev.galleyworks.util.Log;
import io.github.rkaushikdev.galleyworks.util.Stopwatch;
import io.github.rkaushikdev.galleyworks.util.Timestamps;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;

/**
 * The engine facade: ingest, index, match and report, in that order.
 *
 * <p>Everything below it is composable on its own; this is the one object that knows the order. It
 * also holds what the stages cannot share otherwise: the text source used to quote the other side
 * of a match, and the boilerplate filter, which can only be learned once the corpus is indexed. An
 * instrument must be indexed before any later instrument is matched, and must not be matched
 * against an index that already contains it, or every segment matches itself perfectly; {@link
 * #runOver} indexes and matches in one forward pass, which satisfies both.
 */
public final class Engine {

    private static final Logger LOG = Log.get("pipeline");

    private final EngineConfig config;
    private final Index index;
    private final ReaderRegistry registry;
    private final JurisdictionRegistry jurisdictions;
    private final MappingTextSource texts = new MappingTextSource();
    private final Stopwatch stopwatch = new Stopwatch();
    private final Fingerprinter fingerprinter;
    private final BoilerplateFilter standard;
    private BoilerplateFilter boilerplate;

    /**
     * The result of matching one instrument.
     *
     * @param report the ranked, filtered report
     * @param matches the deduplicated matches behind it
     */
    public record Analysis(InstrumentReport report, List<SegmentMatch> matches) {

        /** Copies the matches. */
        public Analysis {
            matches = List.copyOf(matches);
        }
    }

    /** An engine under the default configuration over an in-memory index. */
    public Engine() {
        this(new EngineConfig());
    }

    /** An engine under {@code config} over an in-memory index. */
    public Engine(EngineConfig config) {
        this(config, new MemoryIndex(config));
    }

    /** An engine under {@code config} over {@code index}. */
    public Engine(EngineConfig config, Index index) {
        this(config, index, ReaderRegistry.defaultRegistry(config.ingest().amendmentReading()));
    }

    /** An engine under {@code config} over {@code index}, reading with {@code registry}. */
    public Engine(EngineConfig config, Index index, ReaderRegistry registry) {
        this(config, index, registry, new JurisdictionRegistry());
    }

    /**
     * An engine under {@code config} over {@code index}, reading with {@code registry}, that knows
     * from {@code jurisdictions} which jurisdiction sits inside which.
     */
    public Engine(EngineConfig config, Index index, ReaderRegistry registry, JurisdictionRegistry jurisdictions) {
        this.config = config;
        this.index = index;
        this.registry = registry;
        this.jurisdictions = jurisdictions;
        this.fingerprinter = new Fingerprinter(config);
        this.standard =
                config.match().ignoreStandardClauses() ? StandardClauses.filter(config) : new BoilerplateFilter();
        this.boilerplate = standard;
    }

    /** The configuration. */
    public EngineConfig config() {
        return config;
    }

    /** The index instruments are added to. */
    public Index index() {
        return index;
    }

    /** The readers documents are parsed with. */
    public ReaderRegistry registry() {
        return registry;
    }

    /** The jurisdiction table, empty when none was given. */
    public JurisdictionRegistry jurisdictions() {
        return jurisdictions;
    }

    /** Where other instruments' text is quoted from. */
    public MappingTextSource texts() {
        return texts;
    }

    /** The boilerplate filter matching runs under. */
    public BoilerplateFilter boilerplate() {
        return boilerplate;
    }

    /** The per-stage timings. */
    public Stopwatch stopwatch() {
        return stopwatch;
    }

    /** Parse every document in {@code corpus}. */
    public List<Instrument> ingest(Corpus corpus) {
        List<Instrument> instruments = stopwatch.stage("ingest", () -> Corpus.readAll(corpus, registry));
        instruments.forEach(texts::add);
        LOG.atInfo().addKeyValue("documents", instruments.size()).log("corpus ingested");
        return instruments;
    }

    /** Index one instrument, returning how many segments went in. */
    public int add(Instrument instrument) {
        texts.add(instrument);
        List<SegmentFingerprint> records = stopwatch.stage("fingerprint", () -> fingerprinter.fingerprint(instrument));
        return stopwatch.stage("index", () -> index.addAll(records));
    }

    /** Index every instrument, returning how many segments went in. */
    public int addAll(Iterable<Instrument> instruments) {
        int total = 0;
        for (Instrument instrument : instruments) {
            total += add(instrument);
        }
        return total;
    }

    /** Learn the corpus boilerplate from whatever is indexed so far, with the default ratio. */
    public int learnBoilerplate(int minSegments) {
        return learnBoilerplate(0.2, minSegments);
    }

    /**
     * Learn the corpus boilerplate from whatever is indexed so far. The standard clauses stay
     * suppressed when the configuration asks for that, whatever the corpus teaches.
     */
    public int learnBoilerplate(double maxRatio, int minSegments) {
        boilerplate = BoilerplateFilter.learn(index, maxRatio, minSegments).union(standard);
        return boilerplate.size();
    }

    /** A matcher over the index under the current boilerplate filter. */
    public Matcher matcher() {
        return new Matcher(index, config, boilerplate, jurisdictions);
    }

    /** Match one instrument and build its report, stamped now. */
    public Analysis analyse(Instrument instrument) {
        return analyse(instrument, Optional.empty());
    }

    /** Match one instrument and build its report, stamped {@code now} when given. */
    public Analysis analyse(Instrument instrument, Optional<OffsetDateTime> now) {
        List<SegmentMatch> matches =
                stopwatch.stage("match", () -> Dedupe.dedupeMatches(matcher().matchInstrument(instrument)));
        InstrumentReport report = stopwatch.stage(
                "report",
                () -> Ranking.applyReportConfig(
                        Reports.buildReport(
                                instrument, matches, texts, now.orElseGet(Timestamps::nowUtc), config.match()),
                        config.report()));
        return new Analysis(report, matches);
    }

    /** Ingest a corpus, then index and match it in one forward pass, stamped now. */
    public RunResult run(Corpus corpus) {
        return run(corpus, Optional.empty());
    }

    /** Ingest a corpus, then index and match it in one forward pass. */
    public RunResult run(Corpus corpus, Optional<OffsetDateTime> now) {
        return runOver(ingest(corpus), now);
    }

    /** Index and match an already parsed sequence, in order, stamped now. */
    public RunResult runOver(List<Instrument> instruments) {
        return runOver(instruments, Optional.empty());
    }

    /** Index and match an already parsed sequence, in order. */
    public RunResult runOver(List<Instrument> instruments, Optional<OffsetDateTime> now) {
        List<InstrumentReport> reports = new ArrayList<>();
        List<SegmentMatch> found = new ArrayList<>();
        int indexed = 0;
        for (Instrument instrument : instruments) {
            Analysis analysis = analyse(instrument, now);
            reports.add(analysis.report());
            found.addAll(analysis.matches());
            indexed += add(instrument);
        }
        ReuseGraph graph = stopwatch.stage("diffusion", () -> GraphBuilder.buildGraph(found, instruments));
        LOG.atInfo()
                .addKeyValue("documents", instruments.size())
                .addKeyValue("matches", found.size())
                .addKeyValue("edges", graph.size())
                .log("run finished");
        return new RunResult(instruments, reports, found, graph, indexed, stopwatch.report());
    }
}
