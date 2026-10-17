package dev.fupez.rpgplayable.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class RpgPlayableClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientProgress.register(); ClientAbilities.register();
        dev.fupez.rpgplayable.client.fx.ClientVisualEffects.register();
        var category = KeyBinding.Category.create(Identifier.of("rpgplayable", "general"));
        var character = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.rpgplayable.character", GLFW.GLFW_KEY_K, category));
        KeyBinding[] abilities = new KeyBinding[3];
        int[] defaults = {GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X};
        for (int i = 0; i < 3; i++) abilities[i] = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.rpgplayable.ability_" + (i + 1), defaults[i], category));
        dev.fupez.rpgplayable.client.hud.ProgressHud.register(character);
        dev.fupez.rpgplayable.client.hud.AbilityHud.register(abilities);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (int slot = 0; slot < 3; slot++) while (abilities[slot].wasPressed())
                if (client.currentScreen == null) ClientAbilities.request(slot);
            while (character.wasPressed()) if (client.player != null && client.currentScreen == null)
                client.setScreen(new dev.fupez.rpgplayable.client.screen.CharacterScreen());
        });
    }
}
