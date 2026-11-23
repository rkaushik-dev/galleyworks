package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.ParseError;
import io.github.rkaushikdev.galleyworks.Segment;
import io.github.rkaushikdev.galleyworks.SegmentKind;
import io.github.rkaushikdev.galleyworks.SegmentPath;
import io.github.rkaushikdev.galleyworks.ingest.Blocks.ArticleBlock;
import io.github.rkaushikdev.galleyworks.ingest.Blocks.SectionBlock;
import io.github.rkaushikdev.galleyworks.ingest.EnrolledLex.LexLine;
import io.github.rkaushikdev.galleyworks.ingest.EnrolledLex.LineKind;
import io.github.rkaushikdev.galleyworks.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Reads the enrolled text format state legislatures publish.
 *
 * <p>Two passes. The first cuts the document into front matter, articles and numbered sections,
 * which fixes the boundaries once. The second nests the labelled lines inside each section, which is
 * where the interesting decisions live. An article becomes a segment of its own and the parent of
 * the sections printed under it; a section keeps its own number as its path, because sections are
 * numbered through the whole act rather than within an article.
 */
public final class EnrolledReader implements Reader {

    private static final Map<LineKind, SegmentKind> FRONT_MATTER_KINDS =
            Map.of(LineKind.TITLE, SegmentKind.TITLE, LineKind.ENACTING, SegmentKind.ENACTING);

    @Override
    public String name() {
        return "enrolled";
    }

    /** Numbered sections are the gate; the other markers only add confidence. */
    @Override
    public double sniff(SourceDocument document) {
        List<LexLine> lines = EnrolledLex.lexLines(document.text());
        if (lines.stream().noneMatch(line -> line.kind() == LineKind.SECTION)) {
            return 0.0;
        }
        double score = 0.2;
        String head = String.join("\n", document.firstLines());
        if (head.contains("Be It Enacted")) {
            score += 0.5;
        }
        if (head.contains("AN ACT")) {
            score += 0.3;
        }
        return Math.min(score, 1.0);
    }

    @Override
    public Instrument read(SourceDocument document) {
        String text = document.text();
        List<LexLine> lines = EnrolledLex.lexLines(text);
        List<SectionBlock> blocks = Blocks.splitSections(text, lines);
        if (blocks.isEmpty()) {
            throw new ParseError(
                    "enrolled text has no numbered sections", 1, 0, Context.of("origin", document.origin()));
        }
        BillHeader header = Metadata.readHeader(document.firstLines());
        List<Segment> segments = frontMatter(lines);
        List<ArticleBlock> articles = new ArrayList<>(Blocks.splitArticles(text, lines));
        OptionalInt article = OptionalInt.empty();
        for (SectionBlock block : blocks) {
            while (!articles.isEmpty()
                    && articles.getFirst().span().start() < block.span().start()) {
                ArticleBlock opened = articles.removeFirst();
                article = OptionalInt.of(segments.size());
                segments.add(articleSegment(segments.size(), opened));
            }
            segments.addAll(sectionTree(text, block, segments.size(), article));
        }
        for (ArticleBlock trailing : articles) {
            segments.add(articleSegment(segments.size(), trailing));
        }
        String title = document.hint("title").map(String::valueOf).orElseGet(() -> longTitle(text, lines));
        return Instrument.builder(Keys.resolveKey(document, header), title, text)
                .segments(segments)
                .introducedOn(header.introduced().orElse(null))
                .enactedOn(Metadata.parseEffectiveDate(text).orElse(null))
                .sourceFormat(name())
                .build();
    }

    private static List<Segment> frontMatter(List<LexLine> lines) {
        List<Segment> segments = new ArrayList<>();
        for (LexLine line : lines) {
            SegmentKind kind = FRONT_MATTER_KINDS.get(line.kind());
            if (kind != null) {
                segments.add(new Segment(segments.size(), kind, SegmentPath.ROOT, line.span()));
            }
        }
        return segments;
    }

    private static Segment articleSegment(int index, ArticleBlock article) {
        return new Segment(
                index,
                SegmentKind.ARTICLE,
                SegmentPath.of(article.label()),
                article.span(),
                article.heading(),
                OptionalInt.empty());
    }

    private static List<Segment> sectionTree(String text, SectionBlock block, int firstIndex, OptionalInt article) {
        SegmentPath path = SegmentPath.of(block.label());
        List<Segment> tree = new ArrayList<>();
        tree.add(new Segment(firstIndex, SegmentKind.SECTION, path, block.span(), block.heading(), article));
        tree.addAll(Structure.nestSection(text, block, firstIndex + 1, firstIndex, path));
        return tree;
    }

    private static String longTitle(String text, List<LexLine> lines) {
        for (LexLine line : lines) {
            if (line.kind() == LineKind.TITLE) {
                return Text.strip(line.span().sliceOf(text));
            }
        }
        return "";
    }
}
