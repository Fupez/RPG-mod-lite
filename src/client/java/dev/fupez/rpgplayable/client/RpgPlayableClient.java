package dev.fupez.rpgplayable.client;

import dev.fupez.rpgplayable.RpgPlayable;
import net.fabricmc.api.ClientModInitializer;

public final class RpgPlayableClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientProgress.register();
        var category = net.minecraft.client.option.KeyBinding.Category.create(
                net.minecraft.util.Identifier.of("rpgplayable", "general"));
        var characterKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(
                new net.minecraft.client.option.KeyBinding("key.rpgplayable.character", org.lwjgl.glfw.GLFW.GLFW_KEY_K, category));
        dev.fupez.rpgplayable.client.hud.ProgressHud.register(characterKey);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (characterKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null)
                    client.setScreen(new dev.fupez.rpgplayable.client.screen.CharacterScreen());
            }
        });
        RpgPlayable.LOGGER.info("RPG Playable client initialized");
    }
}
