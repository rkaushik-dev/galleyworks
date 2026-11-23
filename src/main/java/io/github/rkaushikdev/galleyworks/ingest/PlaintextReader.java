package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.Segment;
import io.github.rkaushikdev.galleyworks.SegmentKind;
import io.github.rkaushikdev.galleyworks.SegmentPath;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The reader of last resort, for documents with no recognisable legislative structure: every
 * blank-line separated block becomes one section. Its sniff score is deliberately low, so anything
 * else that recognises the document wins. Text with no structure can still say when it takes
 * effect, so the effective-date clause is read as it is for an enrolled bill.
 */
public final class PlaintextReader implements Reader {

    private static final Pattern BLOCK_SPLIT = Regex.compile("\\n[ \\t]*\\n");
    private static final double SNIFF_SCORE = 0.05;

    @Override
    public String name() {
        return "plaintext";
    }

    @Override
    public double sniff(SourceDocument document) {
        return Text.strip(document.text()).isEmpty() ? 0.0 : SNIFF_SCORE;
    }

    @Override
    public Instrument read(SourceDocument document) {
        String text = document.text();
        List<Segment> segments = new ArrayList<>();
        List<TextSpan> spans = blockSpans(text);
        for (int index = 0; index < spans.size(); index++) {
            segments.add(new Segment(
                    index, SegmentKind.SECTION, SegmentPath.of(String.valueOf(index + 1)), spans.get(index)));
        }
        String title = document.hint("title").map(String::valueOf).orElseGet(() -> firstLine(text));
        return Instrument.builder(Keys.resolveKey(document), title, text)
                .segments(segments)
                .introducedOn(Keys.resolveDate(document, Keys.INTRODUCED_ON, Optional.empty())
                        .orElse(null))
                .enactedOn(Keys.resolveDate(document, Keys.ENACTED_ON, Metadata.parseEffectiveDate(text))
                        .orElse(null))
                .sourceFormat(name())
                .build();
    }

    private static List<TextSpan> blockSpans(String text) {
        List<TextSpan> spans = new ArrayList<>();
        int cursor = 0;
        Matcher match = BLOCK_SPLIT.matcher(text);
        while (match.find()) {
            spans.add(new TextSpan(cursor, match.start()));
            cursor = match.end();
        }
        spans.add(new TextSpan(cursor, text.length()));
        return spans.stream()
                .map(span -> trimmed(text, span))
                .filter(span -> !span.isEmpty())
                .toList();
    }

    private static TextSpan trimmed(String text, TextSpan span) {
        int start = span.start();
        while (start < span.end() && Text.isSpace(text.charAt(start))) {
            start++;
        }
        return new TextSpan(start, Math.max(start, Blocks.trimEnd(text, span.end())));
    }

    private static String firstLine(String text) {
        for (String line : text.split("\n", -1)) {
            String stripped = Text.strip(line);
            if (!stripped.isEmpty()) {
                return stripped;
            }
        }
        return "";
    }
}
