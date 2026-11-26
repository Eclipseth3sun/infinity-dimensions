# Endless Portals patch

Extract over the repo root (paths mirror the repo). Overwrites: ModBlocks.java, InfinityDimensions.java,
lang/en_us.json, tags/block/mineable/axe.json, and the 9 infinity_portal_*.png textures.

New: PortalBreakHandler, RainbowBookshelfBlock, RainbowBookGenerator, RainbowShelfData, rainbow_bookshelf
blockstate/model/item/loot table/recipe, shelf textures, gen_textures.py (regenerates all textures).

Untested in-game and not compiled against MC 26.3 (syntax-parsed only). If the build fails, check first:
PlayerBlockBreakEvents.AFTER, the SavedDataType constructor in RainbowShelfData, Style/TextColor in RainbowBookshelfBlock.
