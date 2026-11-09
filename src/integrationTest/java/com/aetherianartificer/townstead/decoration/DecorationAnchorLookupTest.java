package com.aetherianartificer.townstead.decoration;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DecorationAnchorLookupTest {
    @BeforeAll static void bootstrap() {
        //? if <1.21 {
        /*SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        *///?}
    }

    private static List<DecorationDefinition> bundled() throws Exception {
        List<DecorationDefinition> out = new ArrayList<>();
        for (String name : List.of("cool_spot", "flower_bed", "fountain", "haystack", "hearth", "lamp_post", "well")) {
            try (var stream = DecorationAnchorLookupTest.class.getResourceAsStream("/data/townstead/decoration/" + name + ".json")) {
                assertNotNull(stream, name);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                var definition = DecorationDefinition.parse(ResourceLocation.tryParse("townstead:" + name), json);
                assertNotNull(definition, name);
                out.add(definition);
            }
        }
        return out;
    }

    private static List<BlockState> everyState() {
        List<BlockState> states = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) states.addAll(block.getStateDefinition().getPossibleStates());
        return states;
    }

    @Test void lookupAgreesWithAskingEveryDefinitionForEveryBlockState() throws Exception {
        List<DecorationDefinition> definitions = bundled();
        var lookup = DecorationRecognizer.anchorLookup(definitions);
        int anchors = 0;
        for (BlockState state : everyState()) {
            List<DecorationDefinition> expected = new ArrayList<>();
            for (DecorationDefinition definition : definitions) if (definition.isAnchor(state)) expected.add(definition);
            assertEquals(expected, List.of(lookup.apply(state)), String.valueOf(state));
            // Repeated asks come from the memo and must not change.
            assertEquals(expected, List.of(lookup.apply(state)), String.valueOf(state));
            if (!expected.isEmpty()) anchors++;
        }
        assertTrue(anchors > 0, "the bundled decorations anchor on at least one block state");
    }

    @Test void definitionOrderIsKeptSoRecognitionOrderDoesNotChange() throws Exception {
        List<DecorationDefinition> definitions = new ArrayList<>(bundled());
        var water = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse("minecraft:water")).defaultBlockState();
        var forward = DecorationRecognizer.anchorLookup(definitions).apply(water);
        java.util.Collections.reverse(definitions);
        var backward = DecorationRecognizer.anchorLookup(definitions).apply(water);
        assertTrue(forward.length > 0);
        List<DecorationDefinition> reversed = new ArrayList<>(List.of(forward));
        java.util.Collections.reverse(reversed);
        assertEquals(reversed, List.of(backward));
    }

    /** Not an assertion on time; prints the cost of the refresh's per-block anchor test before and after. */
    @Test void benchmarkPerBlockAnchorTest() throws Exception {
        List<DecorationDefinition> definitions = bundled();
        List<BlockState> palette = new ArrayList<>(everyState());
        // A village column is dominated by a few hundred distinct states.
        Random random = new Random(5);
        java.util.Collections.shuffle(palette, random);
        List<BlockState> village = palette.subList(0, 300);
        int blocks = 4_000_000;
        BlockState[] column = new BlockState[blocks];
        for (int i = 0; i < blocks; i++) column[i] = village.get(random.nextInt(village.size()));

        long t0 = System.nanoTime();
        long hitsOld = 0;
        for (BlockState state : column) {
            if (state.isAir()) continue;
            for (DecorationDefinition definition : definitions) if (definition.isAnchor(state)) hitsOld++;
        }
        long oldMs = (System.nanoTime() - t0) / 1_000_000;

        var lookup = DecorationRecognizer.anchorLookup(definitions);
        t0 = System.nanoTime();
        long hitsNew = 0;
        for (BlockState state : column) {
            if (state.isAir()) continue;
            hitsNew += lookup.apply(state).length;
        }
        long newMs = (System.nanoTime() - t0) / 1_000_000;

        assertEquals(hitsOld, hitsNew);
        System.out.println("BENCH decoration: " + blocks + " blocks x " + definitions.size() + " definitions, per-definition test "
                + oldMs + " ms, memoized lookup " + newMs + " ms");
    }
}
