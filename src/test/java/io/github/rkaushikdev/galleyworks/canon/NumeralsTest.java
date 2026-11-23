package io.github.rkaushikdev.galleyworks.canon;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.galleyworks.CanonConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class NumeralsTest {

    private static String rewrite(String text) {
        return rewrite(text, new CanonConfig());
    }

    private static String rewrite(String text, CanonConfig config) {
        return CanonPipeline.applyEdits(text, new Numerals.NumeralRule().edits(text, config))
                .text();
    }

    @Test
    void aDoubledNumeralKeepsOnlyTheDigits() {
        assertThat(rewrite("not later than sixty (60) days")).isEqualTo("not later than 60 days");
    }

    @Test
    void aDoubledNumeralWithAHyphenatedWord() {
        assertThat(rewrite("within twenty-one (21) days")).isEqualTo("within 21 days");
    }

    @Test
    void moneyWrittenTwiceCollapses() {
        assertThat(rewrite("a fee of one hundred dollars ($100.00)")).isEqualTo("a fee of usd 100");
    }

    @Test
    void bareMoneyIsNormalized() {
        assertThat(rewrite("a fee of $1,500.00")).isEqualTo("a fee of usd 1500");
    }

    @Test
    void moneyWithoutCents() {
        assertThat(rewrite("$250")).isEqualTo("usd 250");
    }

    @Test
    void moneyKeepsRealCents() {
        assertThat(rewrite("$0.75")).isEqualTo("usd 0.75");
    }

    @Test
    void percentWrittenTwiceCollapses() {
        assertThat(rewrite("a rate of five percent (5%)")).isEqualTo("a rate of 5 pct");
    }

    @Test
    void perCentumIsRecognised() {
        assertThat(rewrite("sixty-five per centum (65%)")).isEqualTo("65 pct");
    }

    @Test
    void aBarePercentageIsNormalized() {
        assertThat(rewrite("increase of 7.5%")).isEqualTo("increase of 7.5 pct");
    }

    @Test
    void datesBecomeIso() {
        assertThat(rewrite("effective July 1, 2025")).isEqualTo("effective date 2025-07-01");
    }

    @Test
    void datesWithoutAComma() {
        assertThat(rewrite("on March 15 2026")).isEqualTo("on date 2026-03-15");
    }

    @Test
    void ordinalsLoseTheirSuffix() {
        assertThat(rewrite("the 1st day and the 22nd day")).isEqualTo("the 1 day and the 22 day");
    }

    @Test
    void moneyWinsOverTheDoubledNumeralPattern() {
        assertThat(rewrite("one hundred dollars ($100.00) per acre")).isEqualTo("usd 100 per acre");
    }

    @Test
    void percentWinsOverTheDoubledNumeralPattern() {
        assertThat(rewrite("five percent (5%) of the total")).isEqualTo("5 pct of the total");
    }

    @Test
    void editsNeverOverlap() {
        String text = "sixty (60) days, one hundred dollars ($100.00), and five percent (5%)";
        List<Edit> edits = Numerals.normalizeNumerals(text);
        for (int i = 1; i < edits.size(); i++) {
            assertThat(edits.get(i - 1).span().end())
                    .isLessThanOrEqualTo(edits.get(i).span().start());
        }
    }

    @Test
    void severalQuantitiesInOneSentence() {
        String text = "pay one hundred dollars ($100.00) within thirty (30) days at five percent (5%)";
        assertThat(rewrite(text)).isEqualTo("pay usd 100 within 30 days at 5 pct");
    }

    @Test
    void plainProseIsUntouched() {
        String text = "The board shall publish a schedule.";
        assertThat(rewrite(text)).isEqualTo(text);
    }

    @Test
    void aLoneNumberIsLeftAlone() {
        assertThat(rewrite("section 4 applies")).isEqualTo("section 4 applies");
    }

    @Test
    void theRuleCanBeSwitchedOff() {
        String text = "sixty (60) days";
        assertThat(rewrite(text, new CanonConfig().withNormalizeNumbers(false))).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "the first day of July, 2025",
                "the 1st day of July, 2025",
                "first day of July 2025",
                "the First Day of July, in the year 2025",
                "July 1st, 2025"
            })
    void formalDatesBecomeIso(String printed) {
        assertThat(rewrite("effective " + printed + ".")).isEqualTo("effective date 2025-07-01.");
    }

    @Test
    void compoundOrdinalDaysAreRead() {
        assertThat(rewrite("on the twenty-first day of March, 2024")).isEqualTo("on date 2024-03-21");
        assertThat(rewrite("on the twenty first day of March, 2024")).isEqualTo("on date 2024-03-21");
        assertThat(rewrite("on the thirty-first day of December, 2024")).isEqualTo("on date 2024-12-31");
        assertThat(rewrite("on the thirtieth day of June, 2024")).isEqualTo("on date 2024-06-30");
    }

    @Test
    void aDateThatDoesNotExistIsLeftAsPrinted() {
        assertThat(rewrite("on February 30, 2025")).isEqualTo("on February 30, 2025");
        assertThat(rewrite("on the thirty-first day of April, 2025"))
                .isEqualTo("on the thirty-first day of April, 2025");
    }

    @Test
    void anOrdinalDayWithoutAMonthIsStillJustAnOrdinal() {
        assertThat(rewrite("the 1st day of the session")).isEqualTo("the 1 day of the session");
    }

    @Test
    void ordinalWordsAreNumbered() {
        assertThat(Numerals.ORDINAL_DAYS).hasSize(31).startsWith("first").endsWith("thirty-first");
        assertThat(Numerals.ordinalDay("Twenty-Second")).hasValue(22);
        assertThat(Numerals.ordinalDay("twenty  second")).hasValue(22);
        assertThat(Numerals.ordinalDay("thirty-second")).isEmpty();
    }

    @Test
    void datesAreBuiltOnlyWhenTheyExist() {
        assertThat(Numerals.dateOf("July", 1, 2025)).contains(LocalDate.of(2025, 7, 1));
        assertThat(Numerals.dateOf("FEBRUARY", 29, 2024)).contains(LocalDate.of(2024, 2, 29));
        assertThat(Numerals.dateOf("February", 29, 2025)).isEmpty();
        assertThat(Numerals.dateOf("Smarch", 1, 2025)).isEmpty();
        assertThat(Numerals.MONTHS).containsEntry("september", 9).hasSize(12);
    }

    @Test
    void printedDatesAreFoundInBothFormsWithTheirSpans() {
        String text = "from the first day of July, 2025 until December 31, 2026, not February 30, 2025";
        List<Numerals.PrintedDate> dates = Numerals.findDates(text);
        assertThat(dates.stream().map(Numerals.PrintedDate::date))
                .containsExactly(LocalDate.of(2025, 7, 1), LocalDate.of(2026, 12, 31));
        assertThat(dates.getFirst().span().sliceOf(text)).isEqualTo("the first day of July, 2025");
        assertThat(dates.getLast().span().sliceOf(text)).isEqualTo("December 31, 2026");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
            a vote of two-thirds (2/3) of the members  | a vote of 2/3 of the members
            a vote of two-thirds of the members        | a vote of 2/3 of the members
            three fourths of the land                  | 3/4 of the land
            one quarter of the fee                     | 1/4 of the fee
            one-half (1/2) of the cost                 | 1/2 of the cost
            """)
    void writtenFractionsFoldToDigits(String printed, String folded) {
        assertThat(rewrite(printed)).isEqualTo(folded);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            textBlock =
                    """
            a tax of one-half of one percent (0.5%) | a tax of 0.5 pct
            a tax of one-half of one percent        | a tax of 0.5 pct
            a tax of one-tenth of one per cent      | a tax of 0.1 pct
            a tax of three-fourths of 1%            | a tax of 0.75 pct
            a tax of one-third of one percent       | a tax of 0.3333 pct
            """)
    void aFractionOfAPercentBecomesThePercentage(String printed, String folded) {
        assertThat(rewrite(printed)).isEqualTo(folded);
    }

    @Test
    void trailingZerosNoLongerDistinguishTwoAmounts() {
        assertThat(rewrite("a rate of 7.50%"))
                .isEqualTo(rewrite("a rate of 7.5%"))
                .isEqualTo("a rate of 7.5 pct");
        assertThat(rewrite("$5.50")).isEqualTo("usd 5.5");
        assertThat(rewrite("ten (10.0) days")).isEqualTo("10 days");
    }

    @Test
    void aBareDenominatorIsNotAFraction() {
        String text = "half of the fee and a third reading";
        assertThat(rewrite(text)).isEqualTo(text);
    }
}
