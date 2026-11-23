package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.ingest.EnrolledLex.LexLine;
import io.github.rkaushikdev.galleyworks.ingest.EnrolledLex.LineKind;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Cutting lexed lines into articles and numbered sections. The split is deliberately dumb: a
 * section starts at a {@code Section N.} line and ends where the next section or article starts,
 * and an article starts at its heading and ends where the next article starts.
 */
public final class Blocks {

    private Blocks() {}

    /**
     * One numbered section and the lines that belong to it.
     *
     * @param label the printed section number
     * @param heading the statute catchline, or empty
     * @param span the section, without trailing whitespace
     * @param lines the lexed lines it owns
     */
    public record SectionBlock(String label, String heading, TextSpan span, List<LexLine> lines) {

        /** Copies the lines. */
        public SectionBlock {
            lines = List.copyOf(lines);
        }

        /** True when the section covers no text. */
        public boolean isEmpty() {
            return span.isEmpty();
        }
    }

    /**
     * One article or part heading and the stretch of the act it governs.
     *
     * @param label the printed word and number, such as {@code article III}
     * @param heading the heading printed after the number, or empty
     * @param span from the heading to the next article, without trailing whitespace
     */
    public record ArticleBlock(String label, String heading, TextSpan span) {

        /** True when {@code block} begins inside this article. */
        public boolean holds(SectionBlock block) {
            return span.start() <= block.span().start() && block.span().start() < span.end();
        }
    }

    /** Cut the lexed lines into numbered sections. */
    public static List<SectionBlock> splitSections(String text, List<LexLine> lines) {
        List<SectionBlock> blocks = new ArrayList<>();
        for (LexLine opener : openers(lines, LineKind.SECTION)) {
            int following = nextStart(text, lines, opener, Set.of(LineKind.SECTION, LineKind.ARTICLE));
            List<LexLine> owned = lines.stream()
                    .filter(line -> opener.span().start() <= line.span().start()
                            && line.span().start() < following)
                    .toList();
            blocks.add(new SectionBlock(
                    opener.label(),
                    headingFor(text, owned),
                    new TextSpan(opener.span().start(), trimEnd(text, following)),
                    owned));
        }
        return blocks;
    }

    /** Cut the lexed lines into articles; an act printed without them has none. */
    public static List<ArticleBlock> splitArticles(String text, List<LexLine> lines) {
        List<ArticleBlock> blocks = new ArrayList<>();
        for (LexLine opener : openers(lines, LineKind.ARTICLE)) {
            int following = nextStart(text, lines, opener, Set.of(LineKind.ARTICLE));
            String heading =
                    opener.body().map(body -> Text.strip(body.sliceOf(text))).orElse("");
            blocks.add(new ArticleBlock(
                    opener.label(), heading, new TextSpan(opener.span().start(), trimEnd(text, following))));
        }
        return blocks;
    }

    private static List<LexLine> openers(List<LexLine> lines, LineKind kind) {
        return lines.stream().filter(line -> line.kind() == kind).toList();
    }

    /** Where the first line of one of {@code kinds} after {@code opener} starts, else the end. */
    private static int nextStart(String text, List<LexLine> lines, LexLine opener, Set<LineKind> kinds) {
        return lines.stream()
                .filter(line -> line.span().start() > opener.span().start() && kinds.contains(line.kind()))
                .mapToInt(line -> line.span().start())
                .findFirst()
                .orElse(text.length());
    }

    private static String headingFor(String text, List<LexLine> lines) {
        for (LexLine line : lines) {
            if (line.kind() == LineKind.CATCHLINE && line.body().isPresent()) {
                return Text.strip(line.body().get().sliceOf(text));
            }
        }
        return "";
    }

    /** Move {@code end} back over trailing whitespace. */
    public static int trimEnd(String text, int end) {
        int cursor = end;
        while (cursor > 0 && Text.isSpace(text.charAt(cursor - 1))) {
            cursor--;
        }
        return cursor;
    }
}
