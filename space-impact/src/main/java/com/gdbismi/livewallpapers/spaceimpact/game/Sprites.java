package com.gdbismi.livewallpapers.spaceimpact.game;

/** Pixel art in the spirit of the original Nokia game. Ships face right, aliens face left. */
final class Sprites {
    private Sprites() {}

    static final Sprite PLAYER = new Sprite(
            "##........",
            "####......",
            ".#####....",
            "##########",
            ".#####....",
            "####......",
            "##........");

    static final Sprite[] BUG = {
            new Sprite(
                    "..####..",
                    ".#.##.#.",
                    "########",
                    "#.####.#",
                    ".#....#.",
                    "#......#"),
            new Sprite(
                    "..####..",
                    ".#.##.#.",
                    "########",
                    "#.####.#",
                    "..#..#..",
                    ".#....#."),
    };

    static final Sprite[] ORB = {
            new Sprite(
                    "...#...",
                    ".#####.",
                    ".#...#.",
                    "##.#.##",
                    ".#...#.",
                    ".#####.",
                    "...#..."),
            new Sprite(
                    "#.....#",
                    ".#####.",
                    ".#...#.",
                    ".#.#.#.",
                    ".#...#.",
                    ".#####.",
                    "#.....#"),
    };

    static final Sprite[] JET = {
            new Sprite(
                    "....###..",
                    "..#####.#",
                    "#########",
                    "..#####.#",
                    "....###.."),
            new Sprite(
                    "....###.#",
                    "..#####..",
                    "#########",
                    "..#####..",
                    "....###.#"),
    };

    static final Sprite[] ROCK = {
            new Sprite(
                    ".####.",
                    "##..##",
                    "#.##.#",
                    "#.##.#",
                    "##..##",
                    ".####."),
            new Sprite(
                    ".#..#.",
                    "######",
                    ".#..#.",
                    ".#..#.",
                    "######",
                    ".#..#."),
    };

    static final Sprite[] EXPLOSION = {
            new Sprite(
                    ".......",
                    ".......",
                    "..#.#..",
                    "...#...",
                    "..#.#..",
                    ".......",
                    "......."),
            new Sprite(
                    ".......",
                    ".#...#.",
                    "..#.#..",
                    ".......",
                    "..#.#..",
                    ".#...#.",
                    "......."),
            new Sprite(
                    "#.....#",
                    "...#...",
                    ".......",
                    "#.....#",
                    ".......",
                    "...#...",
                    "#.....#"),
    };

    static final Sprite PICKUP = new Sprite(
            "..#..",
            ".###.",
            "##.##",
            ".###.",
            "..#..");

    static final Sprite HEART = new Sprite(
            ".#.#.",
            "#####",
            ".###.",
            "..#..");

    static final Sprite MISSILE = new Sprite(
            "##...",
            "#####",
            "##...");

    static final Sprite PLAYER_SHOT = new Sprite("###");

    static final Sprite ENEMY_SHOT = new Sprite("##");
}
