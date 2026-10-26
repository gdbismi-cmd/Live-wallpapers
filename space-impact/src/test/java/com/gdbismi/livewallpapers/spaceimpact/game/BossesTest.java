package com.gdbismi.livewallpapers.spaceimpact.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.Test;

public class BossesTest {

    @Test
    public void everyBossIsDifferentAndFitsTheScreen() {
        Random rng = new Random(1);
        Set<String> designs = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            Sprite[] frames = Bosses.create(rng);
            assertEquals(2, frames.length);
            Sprite s = frames[0];
            assertTrue(s.w >= Bosses.MIN_W && s.w <= Bosses.MAX_W);
            assertTrue(s.h >= Bosses.MIN_H && s.h <= Bosses.MAX_H);
            assertEquals(s.w, frames[1].w);
            assertEquals(s.h, frames[1].h);

            StringBuilder key = new StringBuilder();
            int lit = 0;
            for (int y = 0; y < s.h; y++) {
                for (int x = 0; x < s.w; x++) {
                    boolean on = s.get(x, y);
                    key.append(on ? '#' : '.');
                    if (on) lit++;
                }
            }
            assertTrue("boss should be solid enough to read", lit > s.w * s.h / 7);
            designs.add(key.toString());
        }
        assertTrue("bosses should almost never repeat", designs.size() >= 98);
    }
}
