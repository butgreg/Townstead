package com.aetherianartificer.townstead.recognition;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * A coarse horizontal grid over a list of positions, so "which entries lie within R blocks of
 * here" reads a handful of cells instead of the whole list. A village refresh asks that once per
 * candidate site, which over a few thousand matched blocks was the dominant cost.
 *
 * <p>Answers are a <em>superset</em> (every entry in an overlapping cell) in <em>ascending list
 * order</em>. Callers keep their exact distance test, and iterating in the original order keeps
 * tie-breaks identical to scanning the full list.
 */
final class MatchGrid {
    private static final int SHIFT = 4;

    private final Map<Long, int[]> cells;

    /** Scratch for the caller, one byte per entry: 0 unknown, 1 and 2 are the caller's two answers. */
    final byte[] roomMemo;

    MatchGrid(int size, IntUnaryOperator xOf, IntUnaryOperator zOf) {
        this.roomMemo = new byte[size];
        Map<Long, int[]> built = new HashMap<>();
        // Indices are appended in ascending order, so each cell's list stays sorted.
        for (int i = 0; i < size; i++) {
            long key = key(xOf.applyAsInt(i) >> SHIFT, zOf.applyAsInt(i) >> SHIFT);
            int[] cell = built.get(key);
            if (cell == null) {
                cell = new int[4];
            } else if (cell[0] + 1 >= cell.length) {
                cell = Arrays.copyOf(cell, cell.length * 2);
            }
            cell[++cell[0]] = i;
            built.put(key, cell);
        }
        this.cells = built;
    }

    /** Indices of every entry in a cell that overlaps the square of {@code radius} around (x, z), ascending. */
    int[] near(int x, int z, int radius) {
        int minCx = (x - radius) >> SHIFT, maxCx = (x + radius) >> SHIFT;
        int minCz = (z - radius) >> SHIFT, maxCz = (z + radius) >> SHIFT;
        int total = 0;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                int[] cell = cells.get(key(cx, cz));
                if (cell != null) total += cell[0];
            }
        }
        int[] out = new int[total];
        int at = 0;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                int[] cell = cells.get(key(cx, cz));
                if (cell == null) continue;
                System.arraycopy(cell, 1, out, at, cell[0]);
                at += cell[0];
            }
        }
        Arrays.sort(out);
        return out;
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }
}
