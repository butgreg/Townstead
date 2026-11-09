package com.aetherianartificer.townstead.decoration;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A cheap "can this variant be met around here at all" test for one village scan.
 *
 * <p>Evaluating a variant reads every block of a (2r+1)^3 cube around an anchor. When an anchor is
 * a common block (water anchors the fountains, so every river and ocean block is one) nearly all of
 * those reads are wasted on a place that cannot satisfy the variant. A requirement that needs N of
 * some block can only be met if at least one chunk section the cube touches contains that block
 * somewhere, and a section's palette answers that without reading a single block. The answer for a
 * (section, requirement) pair is kept for the scan, so most anchors cost a few map lookups.
 *
 * <p>It only ever says "impossible" when the requirement's blocks appear in none of the sections
 * the cube overlaps, in which case the full evaluation would also have failed. Anything it cannot
 * tell (a chunk that is not loaded) is answered "possible", leaving the decision to the full test.
 */
final class VariantFeasibility {
    private static final int MAX_REQUIREMENTS = 256;

    private final ServerLevel level;
    private final Map<DecorationDefinition.Requirement, Integer> ids = new IdentityHashMap<>();
    private final Map<Long, Boolean> sectionHas = new HashMap<>();

    VariantFeasibility(ServerLevel level) {
        this.level = level;
    }

    /** False only when some requirement's block exists in none of the sections around the anchor. */
    boolean possible(DecorationDefinition.Variant variant, BlockPos anchor, int radius) {
        for (DecorationDefinition.Requirement requirement : variant.requires()) {
            if (requirement.count() <= 0) continue;
            if (!anySectionHas(requirement, anchor, radius)) return false;
        }
        return true;
    }

    private boolean anySectionHas(DecorationDefinition.Requirement requirement, BlockPos anchor, int radius) {
        Integer known = ids.get(requirement);
        if (known == null) {
            known = ids.size();
            ids.put(requirement, known);
        }
        int id = known;
        if (id >= MAX_REQUIREMENTS) return true;

        int minCx = (anchor.getX() - radius) >> 4, maxCx = (anchor.getX() + radius) >> 4;
        int minCz = (anchor.getZ() - radius) >> 4, maxCz = (anchor.getZ() + radius) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) return true;
                LevelChunkSection[] sections = chunk.getSections();
                int minSection = Math.max(0, chunk.getSectionIndex(anchor.getY() - radius));
                int maxSection = Math.min(sections.length - 1, chunk.getSectionIndex(anchor.getY() + radius));
                for (int index = minSection; index <= maxSection; index++) {
                    long key = ((long) (cx & 0x3FFFFF) << 42) | ((long) (cz & 0x3FFFFF) << 20)
                            | ((long) (index & 0xFFF) << 8) | id;
                    Boolean has = sectionHas.get(key);
                    if (has == null) {
                        LevelChunkSection section = sections[index];
                        has = section == null || section.maybeHas(requirement::matches);
                        sectionHas.put(key, has);
                    }
                    if (has) return true;
                }
            }
        }
        return false;
    }
}
