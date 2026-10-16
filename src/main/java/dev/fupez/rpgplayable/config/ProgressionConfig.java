package dev.fupez.rpgplayable.config;

import dev.fupez.rpgplayable.player.ProgressionRules;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

public final class ProgressionConfig {
    private ProgressionConfig() { }

    public static ProgressionRules load(Path file) throws IOException {
        if (!Files.exists(file)) {
            Files.createDirectories(file.toAbsolutePath().getParent());
            try {
                Files.writeString(file, """
                        # RPG Playable server tuning. Restart the server to apply changes.
                        baseXp=100
                        xpStep=50
                        killXp=25
                        strengthDamage=0.5
                        """, StandardOpenOption.CREATE_NEW);
            } catch (java.nio.file.FileAlreadyExistsException concurrentCreation) {
                // A concurrently created user file is read, never replaced.
            }
        }
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(file)) { properties.load(reader); }
        try {
            return new ProgressionRules(
                    Integer.parseInt(properties.getProperty("baseXp", "100").trim()),
                    Integer.parseInt(properties.getProperty("xpStep", "50").trim()),
                    Integer.parseInt(properties.getProperty("killXp", "25").trim()),
                    Double.parseDouble(properties.getProperty("strengthDamage", "0.5").trim()));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid RPG configuration at " + file + ": " + invalid.getMessage(), invalid);
        }
    }
}
