package io.github.rkaushikdev.galleyworks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JurisdictionsTest {

    private static final String TABLE =
            """

            jurisdictions:
              - slug: wy
                name: Wyoming
                level: state
              - slug: albany-county
                name: Albany County
                level: county
                parent: wy
              - slug: laramie
                name: Laramie
                level: municipal
                parent: albany-county
                profile: municipal
            """;

    @TempDir
    Path tmp;

    private Path write(String text) throws IOException {
        Path path = tmp.resolve("jurisdictions.yaml");
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path;
    }

    private JurisdictionRegistry loaded() throws IOException {
        return JurisdictionRegistry.load(write(TABLE));
    }

    private void rejects(String table, String message) throws IOException {
        Path path = write(table);
        assertThatThrownBy(() -> JurisdictionRegistry.load(path))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining(message);
    }

    @Test
    void aTableLoads() throws IOException {
        JurisdictionRegistry registry = loaded();
        assertThat(registry.size()).isEqualTo(3);
        assertThat(registry.contains("wy")).isTrue();
        assertThat(registry.get("laramie").level()).isSameAs(GovernmentLevel.MUNICIPAL);
    }

    @Test
    void iterationIsOrdered() throws IOException {
        List<String> slugs = new ArrayList<>();
        loaded().forEach(jurisdiction -> slugs.add(jurisdiction.slug()));
        List<String> sorted = new ArrayList<>(slugs);
        sorted.sort(null);
        assertThat(slugs).isEqualTo(sorted);
    }

    @Test
    void ancestryWalksUpward() throws IOException {
        assertThat(loaded().ancestry("laramie")).containsExactly("laramie", "albany-county", "wy");
    }

    @Test
    void childrenAreFound() throws IOException {
        assertThat(loaded().childrenOf("wy").stream().map(Jurisdiction::slug)).containsExactly("albany-county");
    }

    @Test
    void aProfileIsUsedWhereNamed() throws IOException {
        JurisdictionRegistry registry = loaded();
        assertThat(registry.profileFor("laramie")).isEqualTo("municipal");
        assertThat(registry.profileFor("wy")).isEqualTo("default");
    }

    @Test
    void aProfileIsInheritedFromAnAncestor() throws IOException {
        String table = TABLE.replace("    parent: albany-county\n    profile: municipal", "    parent: albany-county")
                .replace("    level: state", "    level: state\n    profile: verbatim");
        assertThat(JurisdictionRegistry.load(write(table)).profileFor("laramie"))
                .isEqualTo("verbatim");
    }

    @Test
    void anUnknownJurisdictionIsAnError() throws IOException {
        JurisdictionRegistry registry = loaded();
        assertThatThrownBy(() -> registry.get("nowhere"))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("no such jurisdiction");
    }

    @Test
    void aDuplicateRegistrationIsRejected() {
        JurisdictionRegistry registry = new JurisdictionRegistry();
        Jurisdiction entry = new Jurisdiction("wy", "Wyoming", GovernmentLevel.STATE);
        registry.add(entry);
        assertThatThrownBy(() -> registry.add(entry))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void anUnknownProfileIsRejected() {
        JurisdictionRegistry registry = new JurisdictionRegistry();
        Jurisdiction entry = new Jurisdiction("wy", "Wyoming", GovernmentLevel.STATE);
        assertThatThrownBy(() -> registry.add(entry, "nowhere"))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("unknown canonicalization profile");
    }

    @Test
    void aDanglingParentIsRejected() throws IOException {
        rejects(
                "jurisdictions:\n  - slug: laramie\n    name: L\n    level: municipal\n    parent: wy\n",
                "unknown parent");
    }

    @Test
    void aCycleIsRejected() throws IOException {
        rejects(
                "jurisdictions:\n"
                        + "  - slug: a\n    name: A\n    level: state\n    parent: b\n"
                        + "  - slug: b\n    name: B\n    level: state\n    parent: a\n",
                "cycle");
    }

    @Test
    void anIncompleteEntryNamesWhatIsMissing() throws IOException {
        Path path = write("jurisdictions:\n  - slug: wy\n");
        assertThatThrownBy(() -> JurisdictionRegistry.load(path)).isInstanceOfSatisfying(ConfigError.class, caught -> {
            assertThat(caught.getMessage()).contains("is incomplete");
            assertThat(caught.context()).containsEntry("missing", "name,level");
        });
    }

    @Test
    void anUnknownLevelIsRejected() throws IOException {
        rejects("jurisdictions:\n  - slug: wy\n    name: W\n    level: galactic\n", "unknown government level");
    }

    @Test
    void aScalarEntryIsRejected() throws IOException {
        rejects("jurisdictions:\n  - wy\n", "must be a mapping");
    }

    @Test
    void aTableWithoutTheKeyIsRejected() throws IOException {
        rejects("other: 1\n", "needs a jurisdictions list");
    }

    @Test
    void aMissingFileIsRejected() {
        assertThatThrownBy(() -> JurisdictionRegistry.load(tmp.resolve("absent.yaml")))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("cannot be read");
    }

    @Test
    void brokenYamlIsRejected() throws IOException {
        rejects("jurisdictions: [\n", "not valid YAML");
    }

    @Test
    void relationsFollowTheParentLinks() throws IOException {
        JurisdictionRegistry registry = loaded();
        assertThat(registry.relation("laramie", "laramie")).isSameAs(JurisdictionRelation.SAME);
        assertThat(registry.relation("laramie", "wy")).isSameAs(JurisdictionRelation.ENCLOSING);
        assertThat(registry.relation("laramie", "albany-county")).isSameAs(JurisdictionRelation.ENCLOSING);
        assertThat(registry.relation("wy", "laramie")).isSameAs(JurisdictionRelation.ENCLOSED);
        assertThat(registry.relation("wy", "mt")).isSameAs(JurisdictionRelation.SEPARATE);
        assertThat(registry.relation("mt", "wy")).isSameAs(JurisdictionRelation.SEPARATE);
        assertThat(new JurisdictionRegistry().relation("mt", "mt")).isSameAs(JurisdictionRelation.SAME);
    }

    @Test
    void onlySameAndEnclosingAreRoutine() {
        assertThat(JurisdictionRelation.SAME.isRoutine()).isTrue();
        assertThat(JurisdictionRelation.ENCLOSING.isRoutine()).isTrue();
        assertThat(JurisdictionRelation.ENCLOSED.isRoutine()).isFalse();
        assertThat(JurisdictionRelation.SEPARATE.isRoutine()).isFalse();
        assertThat(JurisdictionRelation.fromValue("enclosed")).isSameAs(JurisdictionRelation.ENCLOSED);
        assertThat(JurisdictionRelation.SEPARATE).hasToString("separate");
        assertThatThrownBy(() -> JurisdictionRelation.fromValue("sibling"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
