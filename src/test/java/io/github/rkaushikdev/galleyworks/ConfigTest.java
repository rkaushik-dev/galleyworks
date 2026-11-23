package io.github.rkaushikdev.galleyworks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigTest {

    @Test
    void defaultsAreUsable() {
        EngineConfig cfg = new EngineConfig();
        assertThat(cfg.shingle().mode()).isSameAs(ShingleMode.WORD);
        assertThat(cfg.lsh().rows()).isEqualTo(4);
        assertThat(cfg.match().minContainment()).isCloseTo(0.35, within(1e-9));
    }

    @Test
    void configIsFrozen() throws NoSuchFieldException {
        // A record cannot be assigned to, so the compiler enforces immutability and the test
        // checks that the configuration types are records of final fields.
        assertThat(ShingleConfig.class.isRecord()).isTrue();
        Field width = ShingleConfig.class.getDeclaredField("width");
        assertThat(Modifier.isFinal(width.getModifiers())).isTrue();
        assertThat(EngineConfig.class.isRecord()).isTrue();
    }

    @Test
    void unknownKeysAreRejected() {
        assertThatThrownBy(() -> EngineConfig.fromMapping(Map.of("shingle", Map.of("width", 4, "widht", 5))))
                .isInstanceOf(ConfigError.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void bandsMustDividePermutations() {
        assertThatThrownBy(() -> new LshConfig(100, 32, 0x5EED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not divide");
    }

    @Test
    void rowsAndThresholdFollowTheBanding() {
        LshConfig cfg = new LshConfig(128, 16, 0x5EED);
        assertThat(cfg.rows()).isEqualTo(8);
        assertThat(cfg.approximateThreshold()).isStrictlyBetween(0.0, 1.0);
        LshConfig wider = new LshConfig(128, 64, 0x5EED);
        assertThat(wider.approximateThreshold()).isLessThan(cfg.approximateThreshold());
    }

    @Test
    void guaranteeLengthFollowsWinnowing() {
        ShingleConfig cfg = new ShingleConfig().withWidth(5).withWinnowWindow(4);
        assertThat(cfg.guaranteeLength()).isEqualTo(8);
    }

    @Test
    void rangesAreEnforced() {
        assertThatThrownBy(() -> new ShingleConfig().withWidth(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LshConfig().withPermutations(4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EngineConfig.fromMapping(Map.of("match", Map.of("min_containment", 1.5))))
                .isInstanceOf(ConfigError.class);
    }

    @Test
    void roundTripsThroughAMapping() {
        EngineConfig cfg = EngineConfig.fromMapping(Map.of(
                "shingle", Map.of("mode", "character", "width", 9, "winnow_window", 6),
                "lsh", Map.of("permutations", 64, "bands", 8),
                "canon", Map.of("profile", "wy", "normalize_citations", false),
                "match", Map.of("min_containment", 0.6, "order", "length")));
        assertThat(cfg.shingle().mode()).isSameAs(ShingleMode.CHARACTER);
        assertThat(cfg.canon().profile()).isEqualTo("wy");
        assertThat(EngineConfig.fromMapping(cfg.toMapping())).isEqualTo(cfg);
    }

    @Test
    void digestIsStableAndShapeSensitive() {
        EngineConfig base = new EngineConfig();
        assertThat(base.digest()).isEqualTo(new EngineConfig().digest());
        assertThat(base.digest()).hasSize(16);
        EngineConfig changed = new EngineConfig().withShingle(new ShingleConfig().withWidth(6));
        assertThat(changed.digest()).isNotEqualTo(base.digest());
    }

    @Test
    void digestIgnoresMatchThresholds() {
        EngineConfig base = new EngineConfig();
        Map<String, Object> mapping = new LinkedHashMap<>(base.toMapping());
        mapping.put("match", Map.of("min_containment", 0.9));
        EngineConfig retuned = EngineConfig.fromMapping(mapping);
        assertThat(retuned.digest()).isEqualTo(base.digest());
    }

    @Test
    void canonProfileTogglesAreIndependent() {
        CanonConfig canon = new CanonConfig().withNormalizeNumbers(false);
        assertThat(canon.normalizeNumbers()).isFalse();
        assertThat(canon.normalizeCitations()).isTrue();
    }

    @Test
    void standardClausesAreIgnoredUnlessTheConfigurationSaysOtherwise() {
        assertThat(new EngineConfig().match().ignoreStandardClauses()).isTrue();
        EngineConfig counting = EngineConfig.fromMapping(Map.of("match", Map.of("ignore_standard_clauses", false)));
        assertThat(counting.match().ignoreStandardClauses()).isFalse();
        assertThat(counting.match().toMapping()).containsEntry("ignore_standard_clauses", false);
        assertThat(counting.digest()).isEqualTo(new EngineConfig().digest());
        assertThat(new MatchConfig().withMinContainment(0.5).ignoreStandardClauses())
                .isTrue();
        assertThat(new MatchConfig().withOrder("length").withIgnoreStandardClauses(false))
                .isEqualTo(new MatchConfig().withIgnoreStandardClauses(false).withOrder("length"));
    }
}
