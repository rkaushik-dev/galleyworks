package io.github.rkaushikdev.galleyworks.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.galleyworks.IngestError;
import io.github.rkaushikdev.galleyworks.Instrument;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IngestPlaintextTest {

    private static final Map<String, Object> HINTS =
            Map.of("jurisdiction", "wy", "session", "2025", "number", "HB0142");

    private static final String BODY =
            "AN ACT relating to water.\n\nThe board shall publish a schedule.\n\n\nEffective July 1.\n";

    @TempDir
    Path tmp;

    private static SourceDocument doc() {
        return doc(BODY, Map.of());
    }

    private static SourceDocument doc(String text, Map<String, Object> extra) {
        Map<String, Object> hints = new LinkedHashMap<>(HINTS);
        hints.putAll(extra);
        return new SourceDocument(text, "t.txt", hints);
    }

    @Test
    void readerProtocolIsSatisfied() {
        assertThat(new PlaintextReader()).isInstanceOf(Reader.class);
    }

    @Test
    void fromPathReadsUtf8() throws IOException {
        Path path = tmp.resolve("bill.txt");
        Files.writeString(path, "señor\n", StandardCharsets.UTF_8);
        SourceDocument source = SourceDocument.fromPath(path, Map.of("jurisdiction", "wy"));
        assertThat(source.text()).isEqualTo("señor\n");
        assertThat(source.hint("jurisdiction")).contains("wy");
    }

    @Test
    void fromPathReportsAMissingFile() {
        assertThatThrownBy(() -> SourceDocument.fromPath(tmp.resolve("absent.txt")))
                .isInstanceOf(IngestError.class)
                .hasMessageContaining("cannot be read");
    }

    @Test
    void firstLinesIsCapped() {
        String text = IntStream.range(0, 200).mapToObj(String::valueOf).collect(Collectors.joining("\n"));
        assertThat(new SourceDocument(text).firstLines()).hasSize(40);
    }

    @Test
    void blocksBecomeSections() {
        Instrument instrument = new PlaintextReader().read(doc());
        assertThat(instrument.segments()).hasSize(3);
        assertThat(instrument.sourceFormat()).isEqualTo("plaintext");
        List<String> texts = instrument.textFor(instrument.segments());
        assertThat(texts.get(0)).isEqualTo("AN ACT relating to water.");
        assertThat(texts.get(2)).isEqualTo("Effective July 1.");
    }

    @Test
    void pathsAreNumberedFromOne() {
        assertThat(new PlaintextReader().read(doc()).segments().stream().map(segment -> segment.path()
                        .printed()))
                .containsExactly("1", "2", "3");
    }

    @Test
    void blankDocumentYieldsNoSegments() {
        Instrument instrument = new PlaintextReader().read(doc("   \n\n  \n", Map.of()));
        assertThat(instrument.segments()).isEmpty();
        assertThat(instrument.title()).isEmpty();
    }

    @Test
    void titleHintWinsOverTheFirstLine() {
        assertThat(new PlaintextReader()
                        .read(doc(BODY, Map.of("title", "Water rights")))
                        .title())
                .isEqualTo("Water rights");
    }

    @Test
    void sniffIsLowButNonZeroForText() {
        PlaintextReader reader = new PlaintextReader();
        assertThat(reader.sniff(doc())).isCloseTo(0.05, within(1e-9));
        assertThat(reader.sniff(doc("  \n ", Map.of()))).isEqualTo(0.0);
    }

    @Test
    void whitespaceOnlyBlocksAreDropped() {
        Instrument instrument = new PlaintextReader().read(doc("a\n\n   \n\nb", Map.of()));
        assertThat(instrument.textFor(instrument.segments())).containsExactly("a", "b");
    }

    @Test
    void aPlainDocumentStillReadsItsEffectiveDate() {
        Instrument instrument =
                new PlaintextReader().read(doc(BODY + "\n\nThis act takes effect July 1, 2025.\n", Map.of()));
        assertThat(instrument.enactedOn()).contains(LocalDate.of(2025, 7, 1));
        assertThat(instrument.introducedOn()).isEmpty();
    }

    @Test
    void manifestDatesAreAppliedToAPlainDocument() {
        Instrument instrument = new PlaintextReader()
                .read(doc(BODY, Map.of("enacted_on", "2024-03-01", "introduced_on", LocalDate.of(2024, 1, 9))));
        assertThat(instrument.enactedOn()).contains(LocalDate.of(2024, 3, 1));
        assertThat(instrument.introducedOn()).contains(LocalDate.of(2024, 1, 9));
    }
}
