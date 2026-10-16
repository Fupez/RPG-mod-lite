package dev.fupez.rpgplayable;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RpgPlayable implements ModInitializer {
    public static final String MOD_ID = "rpgplayable";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        dev.fupez.rpgplayable.player.RpgGameplay.initialize();
        LOGGER.info("RPG Playable initialized");
    }
}
