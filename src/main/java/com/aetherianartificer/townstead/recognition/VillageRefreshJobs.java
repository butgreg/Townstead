package com.aetherianartificer.townstead.recognition;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.decoration.DecorationRecognizer;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs village refreshes a slice per server tick. A whole-village sweep takes tens of seconds, and
 * done in one go it stops the server for that long: nobody can move and the watchdog may fire.
 * Here every tick gives the refresh a fixed budget and the game carries on in between.
 */
public final class VillageRefreshJobs {
    /** Per tick; the rest of the 50 ms tick stays for the game. */
    private static final long BUDGET_NANOS = 15_000_000L;

    private static final Map<String, Job> ACTIVE = new LinkedHashMap<>();

    private VillageRefreshJobs() {}

    /** What the refresh found, handed to the finishing callback. */
    public record Outcome(int createdBuildings, int refreshedBuildings, int decorations) {}

    @FunctionalInterface
    public interface Completion {
        /** @param player the requester if still online, else null */
        void done(ServerLevel level, Village village, ServerPlayer player, Outcome outcome);
    }

    private static final class Job {
        final ServerLevel level;
        final Village village;
        final UUID requester;
        final OptionalBuildingRecognition.RefreshTask buildings;
        final DecorationRecognizer.Refresh decorations;
        final Completion completion;
        final long startedNanos = System.nanoTime();
        long busyNanos;
        int ticks;
        boolean buildingsDone;

        Job(ServerLevel level, Village village, ServerPlayer player, Completion completion) {
            this.level = level;
            this.village = village;
            this.requester = player.getUUID();
            this.buildings = OptionalBuildingRecognition.refreshTask(level, village);
            this.decorations = DecorationRecognizer.refreshTask(level, village);
            this.completion = completion;
        }
    }

    private static String key(ServerLevel level, Village village) {
        return level.dimension().location() + "#" + village.getId();
    }

    /** @return false when a refresh of this village is already running */
    public static boolean start(ServerLevel level, Village village, ServerPlayer player, Completion completion) {
        String key = key(level, village);
        if (ACTIVE.containsKey(key)) return false;
        ACTIVE.put(key, new Job(level, village, player, completion));
        return true;
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        // One job per tick, oldest first, so two refreshes never double the cost of a tick.
        Iterator<Map.Entry<String, Job>> it = ACTIVE.entrySet().iterator();
        Map.Entry<String, Job> entry = it.next();
        Job job = entry.getValue();
        if (job.level.getServer() != server) {
            it.remove();
            return;
        }
        long begin = System.nanoTime();
        long deadline = begin + BUDGET_NANOS;
        boolean finished;
        try {
            if (!job.buildingsDone) job.buildingsDone = job.buildings.step(deadline);
            finished = job.buildingsDone && job.decorations.step(deadline);
        } catch (RuntimeException e) {
            Townstead.LOGGER.error("[VillageRefresh] refresh failed", e);
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(job.requester);
            if (player != null) {
                player.displayClientMessage(Component.translatable("townstead.refresh.failed"), true);
            }
            return;
        }
        job.busyNanos += System.nanoTime() - begin;
        job.ticks++;
        if (!finished) return;
        it.remove();
        Townstead.LOGGER.info("[VillageRefresh] finished in {} ticks, {} ms of work spread over {} ms",
                job.ticks, job.busyNanos / 1_000_000L, (System.nanoTime() - job.startedNanos) / 1_000_000L);
        ServerPlayer player = server.getPlayerList().getPlayer(job.requester);
        OptionalBuildingRecognition.RefreshResult built = job.buildings.result();
        job.completion.done(job.level, job.village, player,
                new Outcome(built.created(), built.refreshed(), job.decorations.added()));
    }

    /** For tests and shutdown. */
    public static List<String> activeKeys() {
        return new ArrayList<>(ACTIVE.keySet());
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
