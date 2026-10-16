package dev.fupez.rpgplayable.config;

import dev.fupez.rpgplayable.ability.CleaveRules;
import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

public final class CleaveConfig {
    private CleaveConfig() { }
    public static CleaveRules load(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) {
            try {
                Files.writeString(path, "# Cleave tuning; restart the server after changes. Timings are ticks.\nwindupTicks=6\nrecoveryTicks=8\ncooldownTicks=100\nrange=3\ndamage=6\n", StandardOpenOption.CREATE_NEW);
            } catch (FileAlreadyExistsException ignored) { /* Preserve a concurrent creator's tuning. */ }
        }
        var p = new Properties();
        try (var reader = Files.newBufferedReader(path)) { p.load(reader); }
        return new CleaveRules(Integer.parseInt(p.getProperty("windupTicks", "6")),
                Integer.parseInt(p.getProperty("recoveryTicks", "8")),
                Integer.parseInt(p.getProperty("cooldownTicks", "100")),
                Double.parseDouble(p.getProperty("range", "3")), Double.parseDouble(p.getProperty("damage", "6")));
    }
}
