package dev.fupez.rpgplayable.runtime;

import dev.fupez.rpgplayable.player.*;
import dev.fupez.rpgplayable.network.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.world.GameMode;

public final class PlayableGameTests {
    @GameTest(maxTicks = 100)
    public void combatAllocationAndDiskRejoin(TestContext context) {
        var player = createPlayer(context);
        choose(player);
        int vanillaXp = player.totalExperience;
        earnLevel(context, player);
        require(RpgGameplay.progress(player).level() == 2, "Hostile kills did not level the player");
        require(player.totalExperience == vanillaXp, "RPG reward changed vanilla XP");
        double before = player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        RpgGameplay.handleAction(player, new ProgressActionPayload(ProgressActionPayload.ALLOCATE_STRENGTH, 2));
        var expected = RpgGameplay.progress(player);
        require(expected.strength() == 1 && expected.unspentPoints() == 0, "Allocation did not spend one point");
        require(Math.abs(player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) - before - RpgGameplay.rules().strengthDamage()) < 0.00001, "Strength did not change native attack damage");
        RpgGameplay.handleAction(player, new ProgressActionPayload(ProgressActionPayload.ALLOCATE_STRENGTH, 3));
        require(RpgGameplay.progress(player).equals(expected), "Repeated allocation created points");
        var restored = reconnect(context, player);
        require(RpgGameplay.progress(restored).equals(expected), "Real player save/load lost RPG state");
        require(Math.abs(restored.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) - before - RpgGameplay.rules().strengthDamage()) < 0.00001, "Rejoin lost or doubled Strength modifier");
        var wire = new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(), context.getWorld().getRegistryManager());
        try {
            var snapshot = new ProgressSnapshotPayload(expected, RpgGameplay.rules().xpToNextLevel(expected.level()), RpgGameplay.rules().strengthBonus(expected), 7, true);
            ProgressSnapshotPayload.CODEC.encode(wire, snapshot);
            require(ProgressSnapshotPayload.CODEC.decode(wire).equals(snapshot), "Snapshot wire round trip changed state");
            var action = new ProgressActionPayload(ProgressActionPayload.ALLOCATE_STRENGTH, 123);
            ProgressActionPayload.CODEC.encode(wire, action);
            require(ProgressActionPayload.CODEC.decode(wire).equals(action), "Action wire round trip changed intent");
        } finally { wire.release(); }
        context.getWorld().getServer().getPlayerManager().remove(restored);
        context.complete();
    }

    @GameTest(maxTicks = 100)
    public void eligibilityAndSelectionReplay(TestContext context) {
        var player = createPlayer(context);
        kill(context, player, EntityType.ZOMBIE);
        require(RpgGameplay.progress(player).equals(PlayerProgress.DEFAULT), "Unselected player earned progress");
        choose(player);
        kill(context, player, EntityType.COW);
        require(RpgGameplay.progress(player).xp() == 0, "Passive mob awarded RPG XP");
        earnLevel(context, player);
        var expected = RpgGameplay.progress(player);
        choose(player);
        require(RpgGameplay.progress(player).equals(expected), "Repeated selection reset or rewarded progress");
        player.changeGameMode(GameMode.SPECTATOR);
        RpgGameplay.handleAction(player, new ProgressActionPayload(ProgressActionPayload.ALLOCATE_STRENGTH, 8));
        require(RpgGameplay.progress(player).equals(expected), "Spectator could allocate");
        player.changeGameMode(GameMode.CREATIVE);
        kill(context, player, EntityType.ZOMBIE);
        require(RpgGameplay.progress(player).equals(expected), "Creative kill awarded RPG XP");
        context.getWorld().getServer().getPlayerManager().remove(player);
        context.complete();
    }

    @GameTest(maxTicks = 100)
    public void actualDeathAndRespawnPreserveProgress(TestContext context) {
        var player = createPlayer(context);
        choose(player);
        earnLevel(context, player);
        RpgGameplay.handleAction(player, new ProgressActionPayload(ProgressActionPayload.ALLOCATE_STRENGTH, 2));
        var expected = RpgGameplay.progress(player);
        double damage = player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        require(dev.fupez.rpgplayable.ability.CleaveGameplay.request(player), "Pre-death Cleave failed");
        String beforeAttributes = player.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).getModifiers().toString();
        double beforeBase = player.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).getBaseValue();
        // Force a late default-phase reset: restoration must run after ALL default callbacks,
        // just as it must run after Fabric's default-phase attachment transfer, regardless of init order.
        var fixtureId = player.getUuid();
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
                net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE, (oldPlayer, newPlayer, alive) -> {
                    if (oldPlayer.getUuid().equals(fixtureId)) newPlayer.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE)
                            .removeModifier(net.minecraft.util.Identifier.of("rpgplayable", "strength"));
                });
        player.damage(context.getWorld(), context.getWorld().getDamageSources().genericKill(), 10000);
        require(!player.isAlive(), "Fixture player did not die");
        var restored = context.getWorld().getServer().getPlayerManager().respawnPlayer(player, false, Entity.RemovalReason.KILLED);
        require(RpgGameplay.progress(restored).equals(expected), "Death/respawn lost permanent progress");
        require(!dev.fupez.rpgplayable.ability.CleaveGameplay.request(restored), "Death reset Cleave cooldown");
        require(Math.abs(restored.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) - damage) < 0.00001, "Respawn damage mismatch: before=" + damage + " (base=" + beforeBase + ", modifiers=" + beforeAttributes
                + "), after=" + restored.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) + " (base="
                + restored.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).getBaseValue() + ", modifiers="
                + restored.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).getModifiers() + ")");
        context.getWorld().getServer().getPlayerManager().remove(restored);
        context.complete();
    }

    @GameTest(maxTicks = 140)
    public void cleaveTimingTargetsAndCooldown(TestContext context) {
        var player = createPlayer(context);
        var origin = context.getAbsolutePos(new net.minecraft.util.math.BlockPos(1, 1, 1));
        player.refreshPositionAndAngles(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
        player.setNoGravity(true);
        require(!dev.fupez.rpgplayable.ability.CleaveGameplay.request(player), "Unselected player used Cleave");
        choose(player);
        var front = context.spawnMob(EntityType.HUSK, 1, 1, 3);
        var rear = context.spawnMob(EntityType.HUSK, 1, 1, 0);
        var passive = context.spawnMob(EntityType.COW, 2, 1, 3);
        front.setAiDisabled(true); rear.setAiDisabled(true); passive.setAiDisabled(true);
        front.setNoGravity(true); rear.setNoGravity(true); passive.setNoGravity(true);
        float health = front.getHealth();
        require(dev.fupez.rpgplayable.ability.CleaveGameplay.request(player), "Warrior could not cast");
        require(!dev.fupez.rpgplayable.ability.CleaveGameplay.request(player), "Duplicate cast accepted");
        require(front.getHealth() == health, "Cleave hit before windup");
        context.waitAndRun(7, () -> {
            require(front.getHealth() < health, "Cleave did not hit at its impact tick");
            require(rear.getHealth() == rear.getMaxHealth(), "Cleave hit behind the player");
            require(passive.getHealth() == passive.getMaxHealth(), "Cleave hit a passive mob");
            require(!dev.fupez.rpgplayable.ability.CleaveGameplay.request(player), "Cleave ignored cooldown");
            float after = front.getHealth();
            context.waitAndRun(20, () -> require(front.getHealth() == after, "Cleave repeated its impact"));
        });
        context.waitAndRun(102, () -> {
            require(dev.fupez.rpgplayable.ability.CleaveGameplay.request(player), "Cooldown never became ready");
            player.changeGameMode(GameMode.SPECTATOR);
        });
        context.waitAndRun(110, () -> {
            require(!dev.fupez.rpgplayable.ability.CleaveGameplay.request(player), "Spectator could cast");
            context.getWorld().getServer().getPlayerManager().remove(player);
            context.complete();
        });
    }

    @GameTest(maxTicks = 40)
    public void cleaveInterruptedByReconnectRetainsCooldown(TestContext context) {
        var player = createPlayer(context);
        choose(player);
        var origin = context.getAbsolutePos(new net.minecraft.util.math.BlockPos(1, 1, 1));
        player.refreshPositionAndAngles(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
        var target = context.spawnMob(EntityType.HUSK, 1, 1, 3);
        target.setAiDisabled(true); target.setNoGravity(true);
        require(dev.fupez.rpgplayable.ability.CleaveGameplay.request(player), "Initial cast failed");
        var restored = reconnect(context, player);
        require(!dev.fupez.rpgplayable.ability.CleaveGameplay.request(restored), "Reconnect reset the cooldown");
        context.waitAndRun(8, () -> {
            require(target.getHealth() == target.getMaxHealth(), "Disconnected actor landed a delayed hit");
            var wire = new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(), context.getWorld().getRegistryManager());
            try {
                var state = new CleaveStatePayload(80, 100, 6, 8, CleaveStatePayload.INTERRUPTED, true);
                CleaveStatePayload.CODEC.encode(wire, state);
                require(CleaveStatePayload.CODEC.decode(wire).equals(state), "Cleave state wire mismatch");
                var intent = new CleaveRequestPayload();
                CleaveRequestPayload.CODEC.encode(wire, intent);
                require(CleaveRequestPayload.CODEC.decode(wire).equals(intent), "Cleave intent wire mismatch");
            } finally { wire.release(); }
            context.getWorld().getServer().getPlayerManager().remove(restored);
            context.complete();
        });
    }

    @GameTest(maxTicks = 30)
    public void cleaveCannotHitThroughSolidWall(TestContext context) {
        var player = createPlayer(context);
        choose(player);
        var origin = context.getAbsolutePos(new net.minecraft.util.math.BlockPos(1, 1, 1));
        player.refreshPositionAndAngles(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
        player.setNoGravity(true);
        var target = context.spawnMob(EntityType.HUSK, 1, 1, 3);
        target.setAiDisabled(true); target.setNoGravity(true);
        context.setBlockState(1, 2, 2, net.minecraft.block.Blocks.STONE);
        context.setBlockState(1, 1, 2, net.minecraft.block.Blocks.STONE);
        require(!player.canSee(target), "Wall fixture did not block line of sight");
        require(dev.fupez.rpgplayable.ability.CleaveGameplay.request(player), "Wall cast failed");
        context.waitAndRun(8, () -> {
            require(target.getHealth() == target.getMaxHealth(), "Cleave damaged through a wall");
            context.getWorld().getServer().getPlayerManager().remove(player);
            context.complete();
        });
    }

    private static ServerPlayerEntity createPlayer(TestContext context) {
        var server = context.getWorld().getServer();
        var uuid = java.util.UUID.randomUUID();
        var profile = new com.mojang.authlib.GameProfile(uuid, "rpg-" + uuid.toString().substring(0, 8));
        var player = new ServerPlayerEntity(server, context.getWorld(), profile,
                net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());
        var connection = new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerManager().onPlayerConnect(connection, player,
                net.minecraft.server.network.ConnectedClientData.createDefault(profile, false));
        player.changeGameMode(GameMode.SURVIVAL);
        // A real client acknowledges loading; without this native players intentionally reject all damage.
        player.networkHandler.onPlayerLoaded(new net.minecraft.network.packet.c2s.play.PlayerLoadedC2SPacket());
        require(player.isLoaded(), "Fixture client did not acknowledge loading");
        require(player.isAlive() && !player.isCreative() && !player.isSpectator(), "Fixture is not a live Survival player");
        return player;
    }

    private static void choose(ServerPlayerEntity player) {
        RpgGameplay.handleAction(player, new ProgressActionPayload(ProgressActionPayload.SELECT_WARRIOR, 1));
    }
    private static void earnLevel(TestContext context, ServerPlayerEntity player) {
        var rules = RpgGameplay.rules();
        int count = (int) ((rules.baseXp() + (long) rules.killXp() - 1) / rules.killXp());
        require(count <= 20, "Native fixture expects default small XP tuning");
        for (int i = 0; i < count; i++) kill(context, player, EntityType.ZOMBIE);
    }
    private static <T extends net.minecraft.entity.mob.MobEntity> void kill(TestContext context, ServerPlayerEntity player, EntityType<T> type) {
        var mob = context.spawnMob(type, 1, 1, 1);
        mob.damage(context.getWorld(), context.getWorld().getDamageSources().playerAttack(player), 10000);
    }

    // Adapted from the reference's native save/reconnect test pattern, not a synthetic codec-only load.
    private static ServerPlayerEntity reconnect(TestContext context, ServerPlayerEntity player) {
        var server = context.getWorld().getServer();
        var profile = player.getGameProfile();
        server.getPlayerManager().saveAllPlayerData();
        var file = server.getSavePath(net.minecraft.util.WorldSavePath.PLAYERDATA).resolve(player.getUuid() + ".dat");
        require(java.nio.file.Files.isRegularFile(file), "Native player save file is missing");
        server.getPlayerManager().remove(player);
        var saved = server.getPlayerManager().loadPlayerData(new net.minecraft.server.PlayerConfigEntry(profile)).orElseThrow();
        var restored = new ServerPlayerEntity(server, context.getWorld(), profile,
                net.minecraft.network.packet.c2s.common.SyncedClientOptions.createDefault());
        restored.readData(net.minecraft.storage.NbtReadView.create(net.minecraft.util.ErrorReporter.EMPTY,
                context.getWorld().getRegistryManager(), saved));
        var connection = new net.minecraft.network.ClientConnection(net.minecraft.network.NetworkSide.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerManager().onPlayerConnect(connection, restored,
                net.minecraft.server.network.ConnectedClientData.createDefault(profile, false));
        return restored;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
