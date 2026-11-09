package com.aetherianartificer.townstead.recognition;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MatchGridTest {
    private record P(int x, int z) {}

    private static MatchGrid gridOf(List<P> points) {
        return new MatchGrid(points.size(), i -> points.get(i).x(), i -> points.get(i).z());
    }

    private static List<P> scatter(Random random, int n, int spread) {
        List<P> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) points.add(new P(random.nextInt(spread * 2) - spread, random.nextInt(spread * 2) - spread));
        return points;
    }

    @Test void answersEveryEntryWithinRadiusInAscendingOrder() {
        Random random = new Random(7);
        // Negative and positive coordinates, so cell keys across the origin are exercised.
        List<P> points = scatter(random, 4000, 300);
        MatchGrid grid = gridOf(points);
        for (int q = 0; q < 400; q++) {
            int x = random.nextInt(600) - 300, z = random.nextInt(600) - 300, radius = 1 + random.nextInt(60);
            int[] near = grid.near(x, z, radius);

            for (int i = 1; i < near.length; i++) assertTrue(near[i - 1] < near[i], "ascending and unique");

            List<Integer> expected = new ArrayList<>();
            for (int i = 0; i < points.size(); i++) {
                long dx = points.get(i).x() - x, dz = points.get(i).z() - z;
                if (dx * dx + dz * dz <= (long) radius * radius) expected.add(i);
            }
            List<Integer> got = new ArrayList<>();
            for (int i : near) got.add(i);
            assertTrue(got.containsAll(expected), "grid dropped an entry that is within the radius");
        }
    }

    @Test void filteringTheAnswerGivesExactlyTheBruteForceListInTheSameOrder() {
        Random random = new Random(11);
        List<P> points = scatter(random, 3000, 200);
        MatchGrid grid = gridOf(points);
        for (int q = 0; q < 200; q++) {
            int x = random.nextInt(400) - 200, z = random.nextInt(400) - 200, radius = 1 + random.nextInt(40);
            long r2 = (long) radius * radius;
            List<Integer> viaGrid = new ArrayList<>();
            for (int i : grid.near(x, z, radius)) {
                long dx = points.get(i).x() - x, dz = points.get(i).z() - z;
                if (dx * dx + dz * dz <= r2) viaGrid.add(i);
            }
            List<Integer> brute = new ArrayList<>();
            for (int i = 0; i < points.size(); i++) {
                long dx = points.get(i).x() - x, dz = points.get(i).z() - z;
                if (dx * dx + dz * dz <= r2) brute.add(i);
            }
            assertEquals(brute, viaGrid);
        }
    }

    @Test void emptyListAndEmptyNeighbourhoodAnswerNothing() {
        assertEquals(0, gridOf(List.of()).near(0, 0, 50).length);
        assertEquals(0, gridOf(List.of(new P(1000, 1000))).near(0, 0, 16).length);
    }

    @Test void handlesManyEntriesInOneCell() {
        List<P> points = new ArrayList<>();
        for (int i = 0; i < 500; i++) points.add(new P(3, 5));
        int[] near = gridOf(points).near(3, 5, 1);
        assertEquals(500, near.length);
        assertEquals(499, near[499]);
    }

    /** Not an assertion on time; prints the before/after cost of the per-candidate neighbour query. */
    @Test void benchmarkNeighbourQuery() {
        Random random = new Random(3);
        // A big modded village: thousands of matched blocks, one candidate query per seed.
        List<P> points = scatter(random, 8000, 160);
        MatchGrid grid = gridOf(points);
        int seeds = 4000, radius = 16;
        long r2 = (long) radius * radius;

        long t0 = System.nanoTime();
        long checksumOld = 0;
        for (int s = 0; s < seeds; s++) {
            P seed = points.get(s);
            for (int i = 0; i < points.size(); i++) {
                long dx = points.get(i).x() - seed.x(), dz = points.get(i).z() - seed.z();
                if (dx * dx + dz * dz <= r2) checksumOld += i;
            }
        }
        long oldMs = (System.nanoTime() - t0) / 1_000_000;

        t0 = System.nanoTime();
        long checksumNew = 0;
        for (int s = 0; s < seeds; s++) {
            P seed = points.get(s);
            for (int i : grid.near(seed.x(), seed.z(), radius)) {
                long dx = points.get(i).x() - seed.x(), dz = points.get(i).z() - seed.z();
                if (dx * dx + dz * dz <= r2) checksumNew += i;
            }
        }
        long newMs = (System.nanoTime() - t0) / 1_000_000;

        assertEquals(checksumOld, checksumNew);
        System.out.println("BENCH matchgrid: " + seeds + " seeds x " + points.size() + " matched blocks, full scan "
                + oldMs + " ms, grid " + newMs + " ms");
    }
}
