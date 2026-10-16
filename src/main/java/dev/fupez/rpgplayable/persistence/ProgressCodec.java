package dev.fupez.rpgplayable.persistence;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.fupez.rpgplayable.player.PlayerClass;
import dev.fupez.rpgplayable.player.PlayerProgress;

public final class ProgressCodec {
    private static final int SCHEMA_VERSION = 1;
    private record Stored(int schema, String playerClass, int level, long xp, int points, int strength) { }
    private static final Codec<Stored> STORED = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("schema_version").forGetter(Stored::schema),
            Codec.STRING.fieldOf("class").forGetter(Stored::playerClass),
            Codec.INT.fieldOf("level").forGetter(Stored::level),
            Codec.LONG.fieldOf("xp").forGetter(Stored::xp),
            Codec.INT.fieldOf("unspent_points").forGetter(Stored::points),
            Codec.INT.fieldOf("strength").forGetter(Stored::strength)
    ).apply(instance, Stored::new));

    public static final Codec<PlayerProgress> CODEC = STORED.flatXmap(saved -> {
        try {
            if (saved.schema() != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported RPG save schema " + saved.schema());
            return DataResult.success(new PlayerProgress(PlayerClass.fromId(saved.playerClass()),
                    saved.level(), saved.xp(), saved.points(), saved.strength()));
        } catch (IllegalArgumentException invalid) {
            return DataResult.error(invalid::getMessage);
        }
    }, current -> DataResult.success(new Stored(SCHEMA_VERSION, current.playerClass().id(),
            current.level(), current.xp(), current.unspentPoints(), current.strength())));

    private ProgressCodec() { }
}
