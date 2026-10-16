package dev.fupez.rpgplayable.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.fupez.rpgplayable.player.PlayerClass;
import dev.fupez.rpgplayable.player.PlayerProgress;
import java.math.BigInteger;

public final class ProgressCodec {
    private record Legacy(String playerClass, int level, long xp, int points, int strength) { }
    private record Stored(String playerClass, BigInteger level, BigInteger xp, BigInteger points, BigInteger strength,
                          BigInteger vitality, BigInteger agility, BigInteger intelligence, BigInteger spirit) { }
    private static final Codec<BigInteger> COUNTER = Codec.STRING.comapFlatMap(text -> {
        try {
            var value = new BigInteger(text);
            if (value.signum() < 0) return DataResult.error(() -> "RPG counters cannot be negative");
            return DataResult.success(value);
        } catch (NumberFormatException invalid) { return DataResult.error(() -> "Invalid RPG counter"); }
    }, BigInteger::toString);
    private static final MapCodec<PlayerProgress> LEGACY = RecordCodecBuilder.<Legacy>mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("class").forGetter(Legacy::playerClass),
            Codec.INT.fieldOf("level").forGetter(Legacy::level),
            Codec.LONG.fieldOf("xp").forGetter(Legacy::xp),
            Codec.INT.fieldOf("unspent_points").forGetter(Legacy::points),
            Codec.INT.fieldOf("strength").forGetter(Legacy::strength)
    ).apply(instance, Legacy::new)).flatXmap(saved -> {
        try {
            if (saved.level() > 100 || (saved.level() == 100 && saved.xp() != 0))
                throw new IllegalArgumentException("Invalid legacy RPG level");
            return DataResult.success(new PlayerProgress(PlayerClass.fromId(saved.playerClass()), saved.level(),
                    saved.xp(), saved.points(), saved.strength()));
        } catch (IllegalArgumentException invalid) { return DataResult.error(invalid::getMessage); }
    }, current -> DataResult.error(() -> "Legacy RPG saves are decode-only; use schema 2"));
    private static final MapCodec<PlayerProgress> CURRENT = RecordCodecBuilder.<Stored>mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("class").forGetter(Stored::playerClass),
            COUNTER.fieldOf("level").forGetter(Stored::level),
            COUNTER.fieldOf("xp").forGetter(Stored::xp),
            COUNTER.fieldOf("unspent_points").forGetter(Stored::points),
            COUNTER.fieldOf("strength").forGetter(Stored::strength),
            COUNTER.fieldOf("vitality").forGetter(Stored::vitality),
            COUNTER.fieldOf("agility").forGetter(Stored::agility),
            COUNTER.fieldOf("intelligence").forGetter(Stored::intelligence),
            COUNTER.fieldOf("spirit").forGetter(Stored::spirit)
    ).apply(instance, Stored::new)).flatXmap(saved -> {
        try {
            return DataResult.success(new PlayerProgress(PlayerClass.fromId(saved.playerClass()), saved.level(),
                    saved.xp(), saved.points(), saved.strength(), saved.vitality(), saved.agility(), saved.intelligence(), saved.spirit()));
        } catch (IllegalArgumentException invalid) { return DataResult.error(invalid::getMessage); }
    }, current -> DataResult.success(new Stored(current.playerClass().id(), current.level(), current.xp(),
            current.unspentPoints(), current.strength(), current.vitality(), current.agility(), current.intelligence(), current.spirit())));

    /** Read both historical numeric saves and arbitrary-precision schema 2; always write schema 2. */
    public static final Codec<PlayerProgress> CODEC = Codec.INT.partialDispatch("schema_version",
            current -> DataResult.success(2), schema -> switch (schema) {
                case 1 -> DataResult.success(LEGACY);
                case 2 -> DataResult.success(CURRENT);
                default -> DataResult.error(() -> "Unsupported RPG save schema " + schema);
            });
    private ProgressCodec() { }
}
