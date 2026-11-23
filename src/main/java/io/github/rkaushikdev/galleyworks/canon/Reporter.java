package io.github.rkaushikdev.galleyworks.canon;

import io.github.rkaushikdev.galleyworks.ConfigError;
import io.github.rkaushikdev.galleyworks.Context;
import io.github.rkaushikdev.galleyworks.util.Regex;
import io.github.rkaushikdev.galleyworks.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A statutory code and the abbreviations it is cited under.
 *
 * <p>Each code has one short form the canonical citation records and several printed forms a
 * drafter may use for it: Montana cites {@code MCA} and {@code Mont. Code Ann.}, North Dakota
 * {@code N.D.C.C.} and {@code N.D. Cent. Code}, Colorado {@code C.R.S.} and {@code Colo. Rev.
 * Stat.}. A state also publishes its session laws, the acts as passed in each session, under a name
 * of their own: {@code Wyo. Sess. Laws}, {@code Laws of Montana}. A printed form is matched with its
 * periods and the space after each period optional, because a re-typeset bill drops them as often
 * as it keeps them.
 *
 * @param code the short form written in the canonical citation
 * @param printedForms the abbreviations of the code a drafter prints, with their periods
 * @param sessionLawForms the names the state's session laws are printed under
 */
public record Reporter(String code, List<String> printedForms, List<String> sessionLawForms) {

    /** Every code the citation rule recognises. */
    public static final List<Reporter> KNOWN = List.of(
            new Reporter("usc", List.of("U.S.C.")),
            new Reporter("cfr", List.of("C.F.R.")),
            new Reporter("wy", List.of("W.S.", "Wyo. Stat. Ann.", "Wyo. Stat."), List.of("Wyo. Sess. Laws")),
            new Reporter("mt", List.of("MCA", "Mont. Code Ann."), List.of("Mont. Laws", "Laws of Montana")),
            new Reporter("nd", List.of("N.D.C.C.", "N.D. Cent. Code"), List.of("N.D. Sess. Laws")),
            new Reporter("co", List.of("C.R.S.", "Colo. Rev. Stat."), List.of("Colo. Sess. Laws")));

    private static final Pattern NOT_LETTER = Regex.compile("[^a-z]");

    /** Validates the code and requires at least one printed form. */
    public Reporter {
        printedForms = List.copyOf(printedForms);
        sessionLawForms = List.copyOf(sessionLawForms);
        if (!code.matches("[a-z]{2,8}")) {
            throw new ConfigError("reporter code must be two to eight lowercase letters", Context.of("code", code));
        }
        if (printedForms.isEmpty()) {
            throw new ConfigError("reporter needs a printed form", Context.of("code", code));
        }
    }

    /** A code whose session laws are not cited by name. */
    public Reporter(String code, List<String> printedForms) {
        this(code, printedForms, List.of());
    }

    /** {@code raw} lowercased with everything but letters removed: {@code N.D. Cent. Code} reads {@code ndcentcode}. */
    public static String squashed(String raw) {
        return NOT_LETTER.matcher(Text.lower(raw)).replaceAll("");
    }

    /** The regular expression one printed form is matched with, periods and spaces optional. */
    public static String patternFor(String printed) {
        StringBuilder out = new StringBuilder();
        for (char c : printed.toCharArray()) {
            if (c == '.') {
                out.append("\\.?");
            } else if (c == ' ') {
                out.append("\\s?");
            } else if (Character.isLetterOrDigit(c)) {
                out.append(c);
            } else {
                out.append(Pattern.quote(String.valueOf(c)));
            }
            if (c == '.') {
                out.append("\\s?");
            }
        }
        String pattern = out.toString();
        return pattern.endsWith("\\s?") ? pattern.substring(0, pattern.length() - 3) : pattern;
    }

    /** One alternation over every printed form in {@code reporters}, longest first. */
    public static String alternation(List<Reporter> reporters) {
        List<String> forms = new ArrayList<>();
        reporters.forEach(reporter -> forms.addAll(reporter.printedForms()));
        return alternationOf(forms);
    }

    /** One alternation over every session law name in {@code reporters}, longest first. */
    public static String sessionLawAlternation(List<Reporter> reporters) {
        List<String> forms = new ArrayList<>();
        reporters.forEach(reporter -> forms.addAll(reporter.sessionLawForms()));
        return alternationOf(forms);
    }

    private static String alternationOf(List<String> forms) {
        forms.sort(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()));
        return String.join("|", forms.stream().map(Reporter::patternFor).toList());
    }

    /** The code {@code printed} abbreviates, among {@code reporters}. */
    public static Optional<String> codeFor(String printed, List<Reporter> reporters) {
        return lookup(printed, reporters, false);
    }

    /** The code whose session laws are printed as {@code printed}, among {@code reporters}. */
    public static Optional<String> sessionLawCodeFor(String printed, List<Reporter> reporters) {
        return lookup(printed, reporters, true);
    }

    private static Optional<String> lookup(String printed, List<Reporter> reporters, boolean sessionLaws) {
        String wanted = squashed(printed);
        return reporters.stream()
                .filter(reporter -> (sessionLaws ? reporter.sessionLawForms() : reporter.printedForms())
                        .stream().anyMatch(form -> squashed(form).equals(wanted)))
                .map(Reporter::code)
                .findFirst();
    }
}
