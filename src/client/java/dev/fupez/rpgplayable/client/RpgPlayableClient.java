package dev.fupez.rpgplayable.client;

import dev.fupez.rpgplayable.RpgPlayable;
import net.fabricmc.api.ClientModInitializer;

public final class RpgPlayableClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RpgPlayable.LOGGER.info("RPG Playable client initialized");
    }
}
