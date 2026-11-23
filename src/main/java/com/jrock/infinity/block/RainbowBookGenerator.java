package com.jrock.infinity.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Generates the text of the books handed out by the {@link RainbowBookshelfBlock}.
 *
 * <p><b>This class deliberately has no Minecraft imports</b> so the uniqueness guarantee can be
 * unit-tested with plain {@code java}.
 *
 * <h3>Why a book can never repeat</h3>
 * Every book is derived from a {@code serial} number that the world hands out exactly once
 * (see {@code RainbowShelfData}: a persisted, ever-increasing counter shared by <i>all</i>
 * rainbow bookshelves in the world). The serial is written into the book's text itself — in
 * the title and in a colophon on the final page — and Infinity Dimensions derives a book's
 * dimension from an MD5 of its page text. Two different serials therefore always yield two
 * different page texts (and so two different dimensions), no matter what the random prose
 * rolls turn out to be. The random prose only decides how the book <i>reads</i>.
 */
public final class RainbowBookGenerator {

    private RainbowBookGenerator() {}

    /** Plain-text result; the block converts it to a signed Written Book. */
    public record BookText(String title, String author, List<String> pages) {}

    /** Vanilla limits: titles are capped at 32 characters. */
    private static final int MAX_TITLE = 32;
    /** Keep each page comfortably inside what the book screen can display. */
    private static final int MAX_PAGE = 250;

    // Kept short on purpose: "<adj> <noun> #<serial>" must fit into 32 chars.
    private static final String[] TITLE_ADJ = {
        "Prismatic", "Radiant", "Spectral", "Opal", "Vivid", "Gleaming", "Iridescent",
        "Lucid", "Dappled", "Glimmering", "Kindled", "Azure", "Crimson", "Golden",
        "Verdant", "Violet", "Amber", "Cobalt", "Scarlet", "Teal", "Hollow", "Lost",
        "Singing", "Drifting", "Endless", "Secret", "Silent", "Woven", "Burning", "Pale"
    };
    private static final String[] TITLE_NOUN = {
        "Atlas", "Archive", "Garden", "Tide", "Lantern", "Cipher", "Chronicle", "Spiral",
        "Orchard", "Compass", "Mirror", "Voyage", "Reverie", "Canticle", "Almanac",
        "Meadow", "Harbor", "Prism", "Journal", "Folio", "Rhapsody", "Labyrinth",
        "Cartograph", "Lexicon", "Bestiary", "Parable", "Verse", "Hymnal", "Ledger", "Tome"
    };

    private static final String[] AUTHORS = {
        "Iris the Archivist", "Prism Wren", "Aurora Quill", "Ivo Spectra", "Sol Chromatic",
        "Hue Merriweather", "Opaline Vance", "Lumen Ashby", "Nim the Colorist", "Vesper Glass",
        "Rowan Rainbowell", "Tansy Brightwater", "Cobalt Ferris", "Marigold Finch",
        "The Spectral Scribe", "Fable Redfern", "Zephyr Lumen", "Saffron Dusk"
    };

    private static final String[] COLORS = {
        "crimson", "scarlet", "amber", "golden", "lime", "emerald", "teal", "azure",
        "cobalt", "indigo", "violet", "magenta", "rose", "silver", "pearl", "copper"
    };
    private static final String[] ADJ = {
        "ancient", "quiet", "restless", "luminous", "forgotten", "towering", "whispering",
        "hollow", "weathered", "shimmering", "patient", "crooked", "gentle", "endless",
        "drifting", "stubborn", "moonlit", "ember-lit", "frost-bitten", "wandering"
    };
    private static final String[] NOUN = {
        "lighthouse", "orchard", "river", "staircase", "library", "caravan", "mountain",
        "lantern", "bridge", "cartographer", "garden", "storm", "clocktower", "harbor",
        "fox", "pilgrim", "archive", "window", "forest", "tide", "cathedral", "violin"
    };
    // aligned arrays: VERB3[i] is the third-person form of VERB[i]
    private static final String[] VERB = {
        "remember", "unravel", "guard", "follow", "paint", "swallow", "answer",
        "carry", "mend", "forget", "borrow", "burn", "count", "outlast", "hum to"
    };
    private static final String[] VERB3 = {
        "remembers", "unravels", "guards", "follows", "paints", "swallows", "answers",
        "carries", "mends", "forgets", "borrows", "burns", "counts", "outlasts", "hums to"
    };

    private static final String[] PLACE = {
        "beyond the last shelf", "under a glass moon", "where the road folds in half",
        "at the edge of the map", "inside a sleeping volcano", "between two sunrises",
        "past the singing dunes", "beneath the floating docks", "in the seventh corridor",
        "above the clouds of ink", "behind the waterfall", "along the salt road"
    };
    private static final String[] OMEN = {
        "Nobody who read this page ever finished the chapter.",
        "The margin notes disagree with the text.",
        "A single page is missing, and everyone knows which one.",
        "The ink changes color if you read it twice.",
        "Somewhere, a door is still waiting to be opened.",
        "The librarians pretend not to hear it.",
        "It smells faintly of rain and old paper.",
        "Those who follow it do not return the same."
    };

    /** SplitMix64 finaliser: a bijection on 64-bit values, so distinct inputs never collide. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    /** Deterministic overload (nonce 0). Handy for tests. */
    public static BookText generate(long worldSalt, long serial) {
        return generate(worldSalt, serial, 0L);
    }

    /**
     * @param worldSalt random, per-world constant (makes the sequence differ between worlds)
     * @param serial    unique number issued once by the world's counter (starts at 1)
     * @param nonce     fresh random value per dispensed book. Normally redundant (the serial is
     *                  already unique), but SavedData is only flushed on autosave, so after a
     *                  server crash the counter can roll back and re-issue a serial. The nonce
     *                  keeps those re-issued books different.
     */
    public static BookText generate(long worldSalt, long serial, long nonce) {
        // The seed fingerprint ("Codex") printed in the colophon covers salt, serial AND nonce.
        long seed = mix(worldSalt ^ (serial * 0x9E3779B97F4A7C15L) ^ (nonce * 0xD6E8FEB86659FD93L));
        Random rng = new Random(seed);

        String serialTag = Long.toString(serial, 36).toUpperCase(Locale.ROOT);

        // ---- title (<= 32 chars): "<Adj> <Noun> #<serial>" -----------------------------------
        String title = pick(rng, TITLE_ADJ) + " " + pick(rng, TITLE_NOUN) + " #" + serialTag;
        if (title.length() > MAX_TITLE) {
            // only reachable for absurdly large serials; the serial itself is never truncated
            title = "Rainbow Tome #" + serialTag;
            if (title.length() > MAX_TITLE) title = "#" + serialTag;
        }

        String author = pick(rng, AUTHORS);

        // ---- pages -----------------------------------------------------------------------------
        // 4..9 pages. Never 16, so the hex-ID rule in DimensionHasher can never match.
        int pageCount = 4 + rng.nextInt(6);
        List<String> pages = new ArrayList<>(pageCount);
        for (int i = 0; i < pageCount; i++) {
            StringBuilder sb = new StringBuilder();
            if (i == 0) {
                sb.append(title).append("\n\nby ").append(author).append("\n\n");
            } else {
                sb.append("~ ").append(i + 1).append(" ~\n");
            }
            int sentences = 2 + rng.nextInt(3);
            for (int s = 0; s < sentences; s++) {
                String sentence = sentence(rng);
                if (sb.length() + sentence.length() + 1 > MAX_PAGE) break;
                sb.append(sentence).append(' ');
            }
            if (rng.nextInt(3) == 0 && sb.length() + 60 < MAX_PAGE) {
                sb.append('\n').append(pick(rng, OMEN));
            }
            pages.add(trim(sb.toString().strip()));
        }

        // ---- colophon: guarantees uniqueness (serial + seed fingerprint) ---------------------------
        String colophon = "\n\n~ Colophon ~\nVolume " + serialTag
                + "\nCodex " + String.format("%016x", seed);
        int last = pages.size() - 1;
        pages.set(last, trim(pages.get(last), MAX_PAGE - colophon.length()) + colophon);

        return new BookText(title, author, pages);
    }

    private static String sentence(Random rng) {
        return switch (rng.nextInt(7)) {
            case 0 -> cap("the " + pick(rng, ADJ) + " " + pick(rng, NOUN) + " " + verb3(rng)
                    + " the " + pick(rng, COLORS) + " " + pick(rng, NOUN) + ".");
            case 1 -> cap(pick(rng, PLACE) + ", " + a(pick(rng, COLORS)) + " " + pick(rng, NOUN)
                    + " " + verb3(rng) + " the " + pick(rng, ADJ) + " " + pick(rng, NOUN) + ".");
            case 2 -> "They say the " + pick(rng, COLORS) + " light " + verb3(rng) + " the "
                    + pick(rng, NOUN) + " " + pick(rng, PLACE) + ".";
            case 3 -> {
                String adj = pick(rng, ADJ);
                yield "On the " + ordinal(1 + rng.nextInt(99)) + " day " + a(adj) + " " + pick(rng, NOUN)
                        + " arrived, carrying nothing but " + (2 + rng.nextInt(11)) + " "
                        + pick(rng, COLORS) + " keys.";
            }
            case 4 -> "\"" + cap(pick(rng, ADJ)) + " " + plural(pick(rng, NOUN)) + " never lie,\" said the "
                    + pick(rng, NOUN) + ", \"they only " + pick(rng, VERB) + " the truth.\"";
            case 5 -> "Follow the " + pick(rng, COLORS) + " thread " + pick(rng, PLACE)
                    + " and the " + pick(rng, NOUN) + " will " + pick(rng, VERB) + " you.";
            default -> {
                String adj = pick(rng, ADJ);
                yield cap(pick(rng, PLACE)) + " stands " + a(adj) + " " + pick(rng, NOUN)
                        + ", " + pick(rng, COLORS) + " as " + pick(rng, COLORS) + " " + plural(pick(rng, NOUN)) + ".";
            }
        };
    }

    /** third-person verb form (consumes exactly one rng value, like pick()). */
    private static String verb3(Random rng) { return VERB3[rng.nextInt(VERB3.length)]; }

    /** "a"/"an" + word. */
    private static String a(String word) {
        char c = Character.toLowerCase(word.charAt(0));
        return ("aeiou".indexOf(c) >= 0 ? "an " : "a ") + word;
    }

    private static String plural(String n) {
        if (n.endsWith("y") && n.length() > 1 && "aeiou".indexOf(n.charAt(n.length() - 2)) < 0)
            return n.substring(0, n.length() - 1) + "ies";
        if (n.endsWith("x") || n.endsWith("s") || n.endsWith("ch") || n.endsWith("sh")) return n + "es";
        return n + "s";
    }

    private static String ordinal(int n) {
        int m100 = n % 100;
        if (m100 >= 11 && m100 <= 13) return n + "th";
        return switch (n % 10) { case 1 -> n + "st"; case 2 -> n + "nd"; case 3 -> n + "rd"; default -> n + "th"; };
    }

    private static String pick(Random rng, String[] a) { return a[rng.nextInt(a.length)]; }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String trim(String s) { return trim(s, MAX_PAGE); }

    private static String trim(String s, int max) {
        if (max < 0) max = 0;
        return s.length() <= max ? s : s.substring(0, max).stripTrailing();
    }
}
