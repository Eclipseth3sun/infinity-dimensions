package com.jrock.infinity.dimension;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * All 20w14∞ easter-egg dimension names that map to hand-crafted generators.
 * Any book whose trimmed, lower-cased content exactly matches one of these
 * bypasses the SHA-256 hash and routes directly to the named dimension.
 */
public enum NamedDimension {
    // ── Documented easter-egg dims ────────────────────────────────────────────
    ANT         ("ant"),
    LIBRARY     ("library"),
    HOLES       ("holes"),
    LLAMA       ("llama"),
    MESSAGE     ("message"),
    CHESS       ("chess"),
    COLORS      ("colors"),
    INVERTED    ("inverted"),
    PATTERNS    ("patterns"),
    SPONGE      ("sponge"),
    WALL        ("wall"),
    SLIME       ("slime"),
    SKYGRID     ("skygrid"),
    CAVE        ("cave"),
    VOID        ("void"),
    // ── Additional named dims from wiki ───────────────────────────────────────
    FLOATING    ("floating"),
    UNDERGROUND ("underground"),
    GALLERY     ("gallery"),
    MUSEUM      ("museum"),
    GRID        ("grid"),
    ANCIENT     ("ancient"),
    FLAT        ("flat"),
    DARK        ("dark"),
    CLUBS       ("clubs"),
    CHECKERS    ("checkers"),
    FARM        ("farm");

    private final String id;

    private static final Set<String> ALL_IDS = Arrays.stream(values())
            .map(d -> d.id)
            .collect(Collectors.toSet());

    NamedDimension(String id) { this.id = id; }

    public String getId() { return id; }

    public static boolean isNamed(String lowercaseTrimmed) {
        return ALL_IDS.contains(lowercaseTrimmed);
    }

    public static NamedDimension byId(String id) {
        for (NamedDimension d : values()) {
            if (d.id.equals(id)) return d;
        }
        return null;
    }
}
