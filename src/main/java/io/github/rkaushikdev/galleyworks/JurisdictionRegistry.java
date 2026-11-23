package io.github.rkaushikdev.galleyworks;

import io.github.rkaushikdev.galleyworks.canon.Profiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Every jurisdiction a corpus knows about, with the hierarchy and the drafting profile each is
 * canonicalized under.
 *
 * <p>A corpus that mixes a state code with the ordinances of three of its cities needs to know which
 * is which: an ordinance that repeats a state statute is compliance, and a state statute that repeats
 * an ordinance is something else entirely. Cycles are rejected at load rather than at traversal. A
 * cycle in a table of twelve entries is a typo, and finding it in the middle of a corpus run is
 * finding it too late. Iterating yields the jurisdictions sorted.
 */
public final class JurisdictionRegistry implements Iterable<Jurisdiction> {

    private static final String DEFAULT_PROFILE = "default";

    private final Map<String, Jurisdiction> bySlug = new HashMap<>();
    private final Map<String, String> profiles = new HashMap<>();

    /** Number of jurisdictions. */
    public int size() {
        return bySlug.size();
    }

    /** True when {@code slug} is registered. */
    public boolean contains(String slug) {
        return bySlug.containsKey(slug);
    }

    @Override
    public Iterator<Jurisdiction> iterator() {
        return new TreeSet<>(bySlug.values()).iterator();
    }

    /** Register one jurisdiction under the default profile. */
    public void add(Jurisdiction jurisdiction) {
        add(jurisdiction, DEFAULT_PROFILE);
    }

    /** Register one jurisdiction under {@code profile}. */
    public void add(Jurisdiction jurisdiction, String profile) {
        if (bySlug.containsKey(jurisdiction.slug())) {
            throw new ConfigError("jurisdiction is already registered", Context.of("slug", jurisdiction.slug()));
        }
        if (!Profiles.PROFILES.containsKey(profile)) {
            throw new ConfigError(
                    "unknown canonicalization profile",
                    Context.of(
                            "slug",
                            jurisdiction.slug(),
                            "profile",
                            profile,
                            "known",
                            String.join(",", new TreeSet<>(Profiles.PROFILES.keySet()))));
        }
        bySlug.put(jurisdiction.slug(), jurisdiction);
        profiles.put(jurisdiction.slug(), profile);
    }

    /** The jurisdiction registered as {@code slug}. */
    public Jurisdiction get(String slug) {
        Jurisdiction found = bySlug.get(slug);
        if (found == null) {
            throw new ConfigError("no such jurisdiction", Context.of("slug", slug));
        }
        return found;
    }

    /**
     * The profile this jurisdiction is canonicalized under. Falls back to the nearest ancestor that
     * names one, which is how a city inherits its state's drafting conventions without repeating them.
     */
    public String profileFor(String slug) {
        for (String step : ancestry(slug)) {
            String named = profiles.get(step);
            if (named != null && !named.equals(DEFAULT_PROFILE)) {
                return named;
            }
        }
        return DEFAULT_PROFILE;
    }

    /** The slug, then each parent upward. */
    public List<String> ancestry(String slug) {
        return get(slug).ancestry(bySlug);
    }

    /**
     * Where {@code other} stands relative to {@code query}: the same body, one that encloses it, one
     * it encloses, or neither. A slug the registry does not know is separate from everything but
     * itself, because nothing says otherwise.
     */
    public JurisdictionRelation relation(String query, String other) {
        if (query.equals(other)) {
            return JurisdictionRelation.SAME;
        }
        if (contains(query) && ancestry(query).contains(other)) {
            return JurisdictionRelation.ENCLOSING;
        }
        if (contains(other) && ancestry(other).contains(query)) {
            return JurisdictionRelation.ENCLOSED;
        }
        return JurisdictionRelation.SEPARATE;
    }

    /** The jurisdictions directly under {@code slug}, sorted. */
    public List<Jurisdiction> childrenOf(String slug) {
        return new ArrayList<>(new TreeSet<>(bySlug.values().stream()
                .filter(jurisdiction ->
                        jurisdiction.parent().filter(slug::equals).isPresent())
                .toList()));
    }

    /** Reject dangling parents and cycles. */
    public void validate() {
        for (Jurisdiction jurisdiction : bySlug.values()) {
            if (jurisdiction.parent().isPresent()
                    && !bySlug.containsKey(jurisdiction.parent().get())) {
                throw new ConfigError(
                        "jurisdiction names an unknown parent",
                        Context.of(
                                "slug",
                                jurisdiction.slug(),
                                "parent",
                                jurisdiction.parent().get()));
            }
            jurisdiction.ancestry(bySlug);
        }
    }

    private record Loaded(Jurisdiction jurisdiction, String profile) {}

    private static Loaded entry(Object raw, int position) {
        if (!(raw instanceof Map<?, ?> fields)) {
            throw new ConfigError("jurisdiction entry must be a mapping", Context.of("entry", position));
        }
        List<String> missing = new ArrayList<>();
        for (String key : List.of("slug", "name", "level")) {
            if (!fields.containsKey(key)) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new ConfigError(
                    "jurisdiction entry is incomplete",
                    Context.of("entry", position, "missing", String.join(",", missing)));
        }
        GovernmentLevel level;
        try {
            level = GovernmentLevel.fromValue(String.valueOf(fields.get("level")));
        } catch (IllegalArgumentException exc) {
            throw new ConfigError(
                    "unknown government level",
                    Context.of("entry", position, "level", String.valueOf(fields.get("level"))),
                    exc);
        }
        Object parent = fields.get("parent");
        Jurisdiction jurisdiction = new Jurisdiction(
                String.valueOf(fields.get("slug")),
                String.valueOf(fields.get("name")),
                level,
                parent == null ? Optional.empty() : Optional.of(String.valueOf(parent)));
        Object profile = fields.containsKey("profile") ? fields.get("profile") : DEFAULT_PROFILE;
        return new Loaded(jurisdiction, String.valueOf(profile));
    }

    /** Load a registry from a YAML file holding a {@code jurisdictions} list. */
    public static JurisdictionRegistry load(Path path) {
        Object raw;
        try {
            raw = Settings.yaml().load(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException exc) {
            throw new ConfigError("jurisdiction table cannot be read", Context.of("path", path.toString()), exc);
        } catch (YAMLException exc) {
            throw new ConfigError("jurisdiction table is not valid YAML", Context.of("path", path.toString()), exc);
        }
        if (!(raw instanceof Map<?, ?> table) || !(table.get("jurisdictions") instanceof List<?> entries)) {
            throw new ConfigError("jurisdiction table needs a jurisdictions list", Context.of("path", path.toString()));
        }
        JurisdictionRegistry registry = new JurisdictionRegistry();
        for (int position = 0; position < entries.size(); position++) {
            Loaded loaded = entry(entries.get(position), position);
            registry.add(loaded.jurisdiction(), loaded.profile());
        }
        registry.validate();
        return registry;
    }
}
