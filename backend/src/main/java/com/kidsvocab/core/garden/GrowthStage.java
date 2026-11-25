package com.kidsvocab.core.garden;

/** How far a plant in the word garden has grown; stored as its ordinal. */
public enum GrowthStage {
    SEED, SPROUT, YOUNG, BLOOMING, FRUIT;

    /** The next stage; the last one stays where it is. */
    public GrowthStage next() {
        return this == FRUIT ? FRUIT : values()[ordinal() + 1];
    }

    public static GrowthStage ofCode(int code) {
        if (code < 0 || code >= values().length) {
            throw new IllegalArgumentException("unknown growth stage " + code);
        }
        return values()[code];
    }
}
