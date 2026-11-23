package com.jrock.infinity.block;

import com.jrock.infinity.dimension.RainbowShelfData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.Prediction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rainbow Infinite Bookshelf.
 *
 * <p>Right-click to be handed a freshly generated, <b>signed</b> Written Book with random contents,
 * written in rainbow-coloured ink. Every book is different from every other book this world has ever
 * produced: see {@link RainbowBookGenerator} and {@link RainbowShelfData}.
 *
 * <p>Sneak + right-click passes through, so you can still place blocks against the shelf.
 *
 * <p>The books are ordinary Written Books, so they work with the portals exactly like hand-written
 * ones: the dimension comes from a hash of the page text, and since every book's text is unique,
 * every book leads to its own dimension.
 */
public class RainbowBookshelfBlock extends Block {

    public RainbowBookshelfBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        // Sneaking lets players place blocks against the shelf instead of grabbing a book.
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel server)) return InteractionResult.PASS;

        RainbowShelfData data = server.getServer().getDataStorage().computeIfAbsent(RainbowShelfData.TYPE);
        long serial = data.nextSerial(); // never handed out twice, ever, in this world

        RainbowBookGenerator.BookText text = RainbowBookGenerator.generate(
                data.getSalt(), serial, ThreadLocalRandom.current().nextLong());
        ItemStack book = createSignedBook(text, serial);

        if (!player.getInventory().add(book)) {
            player.drop(book, false, Prediction.SERVER_ONLY);
        }

        server.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS,
                1.0f, 0.9f + server.getRandom().nextFloat() * 0.2f);
        server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                0.5f, 0.8f + server.getRandom().nextFloat() * 0.8f);
        server.sendParticles(ParticleTypes.END_ROD,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                10, 0.35, 0.25, 0.35, 0.03);

        return InteractionResult.SUCCESS;
    }

    // ── Book construction ─────────────────────────────────────────────────────

    /** Builds a signed (generation 0, resolved) Written Book with rainbow-coloured pages. */
    public static ItemStack createSignedBook(RainbowBookGenerator.BookText text, long serial) {
        List<Filterable<Component>> pages = new ArrayList<>(text.pages().size());
        // Start each book on a different part of the spectrum so no two books look alike.
        float startHue = (float) ((serial * 0.6180339887498949) % 1.0);
        for (int i = 0; i < text.pages().size(); i++) {
            pages.add(Filterable.passThrough(rainbow(text.pages().get(i), startHue + i * 0.11f)));
        }

        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(text.title()), text.author(), 0, pages, true));
        return stack;
    }

    /**
     * Colours the text one word at a time, cycling through the spectrum. Each word is a sibling
     * component; {@code getString()} on the page still returns the plain text, so
     * {@code DimensionHasher} (which hashes the plain text) is unaffected by the colours.
     * Words (not letters) keeps the stored book small.
     */
    private static Component rainbow(String text, float hue0) {
        MutableComponent root = Component.empty();
        float hue = hue0;
        // split after each run of whitespace: "ab cd\n\nef" -> "ab ", "cd\n\n", "ef"
        for (String chunk : text.split("(?<=\\s)")) {
            if (chunk.isEmpty()) continue;
            root.append(Component.literal(chunk)
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(hsvToRgb(hue, 1.0f, 0.72f)))));
            hue += 0.075f;
        }
        return root;
    }

    /** Plain HSV -> 0xRRGGBB (no java.awt, which is absent on some server JVMs). */
    private static int hsvToRgb(float hue, float sat, float val) {
        hue = hue - (float) Math.floor(hue);
        float h6 = hue * 6.0f;
        int sector = (int) h6;
        float f = h6 - sector;
        float p = val * (1 - sat);
        float q = val * (1 - sat * f);
        float t = val * (1 - sat * (1 - f));
        float r, g, b;
        switch (sector % 6) {
            case 0 -> { r = val; g = t;   b = p; }
            case 1 -> { r = q;   g = val; b = p; }
            case 2 -> { r = p;   g = val; b = t; }
            case 3 -> { r = p;   g = q;   b = val; }
            case 4 -> { r = t;   g = p;   b = val; }
            default -> { r = val; g = p;  b = q; }
        }
        return (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
    }
}
