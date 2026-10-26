package com.gdbismi.livewallpapers.spaceimpact.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;
import org.junit.Test;

public class SpaceImpactGameTest {

    @Test
    public void runsForAnHourOfGameTimeAndProgresses() {
        SpaceImpactGame game = new SpaceImpactGame(new Random(42));
        game.resize(90, 200);
        int bestScore = 0;
        int bestLevel = 0;
        for (int i = 0; i < 60 * 60 * SpaceImpactGame.TICKS_PER_SECOND; i++) {
            game.tick();
            if (i % 5 == 0) game.render();
            bestScore = Math.max(bestScore, game.getScore());
            bestLevel = Math.max(bestLevel, game.getLevel());
        }
        assertTrue("autopilot should score points", bestScore > 500);
        assertTrue("autopilot should beat at least one boss", bestLevel >= 2);
    }

    @Test
    public void rendersLitPixelsAtManySizes() {
        int[][] sizes = {{48, 48}, {84, 48}, {90, 200}, {200, 90}, {120, 260}};
        for (int[] size : sizes) {
            SpaceImpactGame game = new SpaceImpactGame(new Random(7));
            game.resize(size[0], size[1]);
            for (int i = 0; i < 20 * SpaceImpactGame.TICKS_PER_SECOND; i++) {
                game.tick();
                game.render();
                game.onTap(size[0] / 2f, size[1] / 2f);
            }
            int[] intensity = game.getIntensity();
            assertEquals(Math.max(48, size[0]) * Math.max(48, size[1]), intensity.length);
            int lit = 0;
            for (int v : intensity) {
                assertTrue(v >= 0 && v <= 255);
                if (v == 255) lit++;
            }
            assertTrue(lit > 0);
        }
    }
}
