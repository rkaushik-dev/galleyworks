package io.github.rkaushikdev.galleyworks.ingest;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.Segment;
import io.github.rkaushikdev.galleyworks.SegmentKind;
import io.github.rkaushikdev.galleyworks.canon.Citations;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import io.github.rkaushikdev.galleyworks.util.TextSpan;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What each section of a bill does to the existing code.
 *
 * <p>An amendatory bill opens most sections with a directive: {@code W.S. 41-3-101 is amended to
 * read:}, {@code W.S. 41-3-105 is created to read:}, {@code W.S. 41-3-110 and 41-3-111 are
 * repealed.} The directive names the provisions the section touches, which is what tells two bills
 * of one session apart from two bills amending the same section of law. The directive is read up to
 * its colon or the end of its paragraph, whichever comes first: a citation further down is a
 * reference inside the new text, not a provision being changed.
 */
public final class AmendatoryClauses {

    private static final Pattern OPENER = Regex.ignoringCase("^(?:Section|Sec\\.|§)\\s*\\S+?\\.\\s*");
    private static final Pattern DIRECTIVE = Regex.ignoringCase(
            "\\b(?:is|are)\\s+(?:hereby\\s+)?(?<verb>" + "amended|created|repealed|renumbered|enacted)\\b");
    private static final Pattern BARE_NUMBER =
            Regex.compile("(?<![\\w.-])\\d+(?:-\\d+){1,3}[a-z]?(?:\\([A-Za-z0-9]{1,5}\\))*");
    private static final Pattern DIRECTIVE_END = Regex.compile(":|\\n[ \\t]*\\n");

    private AmendatoryClauses() {}

    /** What a directive does to the provisions it names. */
    public enum Action {
        AMEND("amend"),
        CREATE("create"),
        REPEAL("repeal"),
        RENUMBER("renumber");

        private final String value;

        Action(String value) {
            this.value = value;
        }

        /** The lowercase name written in output. */
        public String value() {
            return value;
        }

        static Action of(String verb) {
            return switch (Text.lower(verb)) {
                case "created", "enacted" -> CREATE;
                case "repealed" -> REPEAL;
                case "renumbered" -> RENUMBER;
                default -> AMEND;
            };
        }

        @Override
        public String toString() {
            return value;
        }
    }

    /**
     * One section's directive.
     *
     * @param sectionPath the printed path of the section carrying it
     * @param action what it does
     * @param provisions the provisions it names, in canonical citation form, in order
     */
    public record AmendatoryClause(String sectionPath, Action action, List<String> provisions) {

        /** Copies the provisions. */
        public AmendatoryClause {
            provisions = List.copyOf(provisions);
        }
    }

    /** The directive of every section of {@code instrument} that carries one, in document order. */
    public static List<AmendatoryClause> read(Instrument instrument) {
        List<AmendatoryClause> found = new ArrayList<>();
        for (Segment segment : instrument.segments()) {
            if (segment.kind() == SegmentKind.SECTION) {
                directive(segment.path().printed(), segment.textOf(instrument)).ifPresent(found::add);
            }
        }
        return found;
    }

    /** The directive opening a section whose text is {@code text}, if it has one. */
    public static Optional<AmendatoryClause> directive(String sectionPath, String text) {
        Matcher opener = OPENER.matcher(text);
        int start = opener.lookingAt() ? opener.end() : 0;
        Matcher end = DIRECTIVE_END.matcher(text).region(start, text.length());
        String sentence = text.substring(start, end.find() ? end.start() : text.length());
        Matcher verb = DIRECTIVE.matcher(sentence);
        if (!verb.find()) {
            return Optional.empty();
        }
        List<String> provisions = provisions(sentence.substring(0, verb.start()));
        if (provisions.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AmendatoryClause(sectionPath, Action.of(verb.group("verb")), provisions));
    }

    /** Every provision {@code subject} names: its citations, then any bare section numbers. */
    private static List<String> provisions(String subject) {
        Set<String> found = new LinkedHashSet<>();
        List<TextSpan> cited = new ArrayList<>();
        String reporter = Citations.LOCAL_REPORTER;
        for (Citations.Citation citation : Citations.findCitations(subject)) {
            found.add(citation.canonical());
            cited.add(citation.span());
            reporter = citation.reporter();
        }
        Matcher bare = BARE_NUMBER.matcher(subject);
        while (bare.find()) {
            TextSpan span = new TextSpan(bare.start(), bare.end());
            if (cited.stream().noneMatch(span::overlaps)) {
                // "W.S. 41-3-110 and 41-3-111": the second number is in the code the first one named.
                found.add("cite " + reporter + " " + Text.lower(bare.group()));
            }
        }
        return List.copyOf(found);
    }
}
