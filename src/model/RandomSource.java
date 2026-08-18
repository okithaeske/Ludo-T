package model;

/**
 * Source of randomness for a single game.
 *
 * <p>Exists so that every randomness consumer ({@link Dice}, {@link MysteryCell},
 * {@code EffectHandler}) draws from an instance it was <em>given</em> rather than from a
 * process-wide singleton. One {@code RandomSource} per game means concurrent games neither
 * reseed nor consume each other's number stream, so a seeded game stays reproducible even
 * while other games run alongside it.
 *
 * <p>Implementations are <b>not</b> required to be thread-safe: a game's random source is
 * confined to whichever thread advances that game.
 */
public interface RandomSource {

    /** Returns a pseudorandom value in {@code [0, bound)}. */
    int nextInt(int bound);
}
