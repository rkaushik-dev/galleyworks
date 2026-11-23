package io.github.rkaushikdev.galleyworks.ingest;

import static io.github.rkaushikdev.galleyworks.support.Fixtures.WATER_BILL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.rkaushikdev.galleyworks.Instrument;
import io.github.rkaushikdev.galleyworks.ParseError;
import io.github.rkaushikdev.galleyworks.Segment;
import io.github.rkaushikdev.galleyworks.SegmentKind;
import io.github.rkaushikdev.galleyworks.ingest.Blocks.SectionBlock;
import io.github.rkaushikdev.galleyworks.support.Fixtures;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class EnrolledReaderTest {

    private final SourceDocument waterBill = Fixtures.waterBill();

    private List<SectionBlock> blocks() {
        return Blocks.splitSections(WATER_BILL, EnrolledLex.lexLines(WATER_BILL));
    }

    private Instrument read() {
        return new EnrolledReader().read(waterBill);
    }

    @Test
    void sniffBeatsThePlaintextFallback() {
        assertThat(new EnrolledReader().sniff(waterBill)).isGreaterThan(new PlaintextReader().sniff(waterBill));
    }

    @Test
    void sniffScoresEachMarker() {
        assertThat(new EnrolledReader().sniff(waterBill)).isCloseTo(1.0, within(1e-9));
        SourceDocument partial = new SourceDocument("Section 1.  Something.\n", waterBill.hints());
        assertThat(new EnrolledReader().sniff(partial)).isCloseTo(0.2, within(1e-9));
    }

    @Test
    void sniffIsZeroWithoutNumberedSections() {
        SourceDocument plain = new SourceDocument("a memo about nothing\n", waterBill.hints());
        assertThat(new EnrolledReader().sniff(plain)).isEqualTo(0.0);
        SourceDocument titled = new SourceDocument("AN ACT relating to water.\n", waterBill.hints());
        assertThat(new EnrolledReader().sniff(titled)).isEqualTo(0.0);
    }

    @Test
    void sectionsAreSplitAtTheOpeners() {
        assertThat(blocks().stream().map(SectionBlock::label)).containsExactly("1", "2");
    }

    @Test
    void sectionSpanStopsBeforeTheNextSection() {
        String first = blocks().getFirst().span().sliceOf(WATER_BILL);
        assertThat(first).startsWith("Section 1.").doesNotContain("Section 2.").endsWith("temporary appropriation.");
    }

    @Test
    void sectionHeadingComesFromTheCatchline() {
        assertThat(blocks().get(0).heading()).isEqualTo("Schedule of appropriations.");
        assertThat(blocks().get(1).heading()).isEmpty();
    }

    @Test
    void splitSectionsOnTextWithNone() {
        assertThat(Blocks.splitSections("nothing here\n", EnrolledLex.lexLines("nothing here\n")))
                .isEmpty();
    }

    @Test
    void readBuildsTheWholeTree() {
        Instrument instrument = read();
        assertThat(instrument.segments().stream().map(Segment::kind))
                .containsExactly(
                        SegmentKind.TITLE,
                        SegmentKind.ENACTING,
                        SegmentKind.SECTION,
                        SegmentKind.SUBSECTION,
                        SegmentKind.PARAGRAPH,
                        SegmentKind.PARAGRAPH,
                        SegmentKind.SUBSECTION,
                        SegmentKind.SECTION);
        assertThat(instrument.sourceFormat()).isEqualTo("enrolled");
    }

    @Test
    void readIndexesSegmentsConsecutively() {
        Instrument instrument = read();
        assertThat(instrument.segments().stream().map(Segment::index))
                .containsExactlyElementsOf(
                        IntStream.range(0, instrument.segments().size()).boxed().toList());
    }

    @Test
    void readLinksChildrenToTheirSection() {
        Instrument instrument = read();
        Segment first = instrument.segments().stream()
                .filter(segment -> segment.kind() == SegmentKind.SECTION)
                .findFirst()
                .orElseThrow();
        assertThat(instrument.childrenOf(first.index()).stream()
                        .map(child -> child.path().printed()))
                .containsExactly("1(a)", "1(b)");
    }

    @Test
    void readGivesEverySegmentAPath() {
        assertThat(read().segments().stream()
                        .filter(segment -> segment.path().depth() > 0)
                        .map(segment -> segment.path().printed()))
                .containsExactly("1", "1(a)", "1(a)(i)", "1(a)(ii)", "1(b)", "2");
    }

    @Test
    void bodySegmentsExcludeTheFrontMatter() {
        Set<SegmentKind> front = Set.of(SegmentKind.ENACTING, SegmentKind.TITLE, SegmentKind.PREAMBLE);
        assertThat(read().bodySegments())
                .allSatisfy(segment -> assertThat(front).doesNotContain(segment.kind()));
    }

    @Test
    void longTitleIsCaptured() {
        assertThat(read().title()).startsWith("AN ACT relating to water rights");
    }

    @Test
    void titleHintOverridesTheLongTitle() {
        Map<String, Object> hints = new LinkedHashMap<>(waterBill.hints());
        hints.put("title", "Short");
        assertThat(new EnrolledReader()
                        .read(new SourceDocument(WATER_BILL, "x", hints))
                        .title())
                .isEqualTo("Short");
    }

    @Test
    void sectionPathsUseThePrintedNumber() {
        assertThat(read().segments().stream()
                        .filter(segment -> segment.kind() == SegmentKind.SECTION)
                        .map(segment -> segment.path().printed()))
                .containsExactly("1", "2");
    }

    @Test
    void missingSectionsAreAParseError() {
        SourceDocument source = new SourceDocument("AN ACT relating to nothing.\n", waterBill.hints());
        assertThatThrownBy(() -> new EnrolledReader().read(source))
                .isInstanceOf(ParseError.class)
                .hasMessageContaining("no numbered sections");
    }

    @Test
    void segmentSpansStayInsideTheDocument() {
        Instrument instrument = read();
        for (Segment segment : instrument.segments()) {
            assertThat(segment.span().end())
                    .isLessThanOrEqualTo(instrument.rawText().length());
            assertThat(segment.span().sliceOf(instrument.rawText()).strip()).isNotEmpty();
        }
    }

    @Test
    void theIntroductionDateIsKeptBesideTheEffectiveDate() {
        String text = WATER_BILL.replace(
                "HOUSE BILL NO. HB0142\n", "HOUSE BILL NO. HB0142\n\nIntroduced: January 13, 2025\n");
        Instrument instrument = new EnrolledReader().read(Fixtures.makeSource(text));
        assertThat(instrument.introducedOn()).contains(LocalDate.of(2025, 1, 13));
        assertThat(instrument.enactedOn()).contains(LocalDate.of(2025, 7, 1));
        assertThat(instrument.effectiveDate()).contains(LocalDate.of(2025, 7, 1));
        assertThat(read().introducedOn()).isEmpty();
    }
}
