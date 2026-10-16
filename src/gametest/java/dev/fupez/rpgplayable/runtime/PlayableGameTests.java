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
        require(RpgGameplay.progress(player).level().equals(java.math.BigInteger.TWO), "Hostile kills did not level the player");
        require(player.totalExperience == vanillaXp, "RPG reward changed vanilla XP");
        double before = player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
        RpgGameplay.handleAction(player, new ProgressActionPayload(ProgressActionPayload.ALLOCATE_STRENGTH, 2));
        var expected = RpgGameplay.progress(player);
        require(expected.strength().equals(java.math.BigInteger.ONE) && expected.unspentPoints().signum() == 0, "Allocation did not spend one point");
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
        require(RpgGameplay.progress(player).xp().signum() == 0, "Passive mob awarded RPG XP");
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
        require(dev.fupez.rpgplayable.abilities.AbilityRuntime.request(player, 0), "Pre-death ability failed");
        require(dev.fupez.rpgplayable.abilities.AbilityRuntime.snapshot(player).stamina()
                < dev.fupez.rpgplayable.abilities.AbilityRuntime.snapshot(player).maxStamina(), "Pre-death cast did not spend stamina");
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
        var combat = dev.fupez.rpgplayable.abilities.AbilityRuntime.snapshot(restored);
        require(combat.cooldowns()[0] > 0, "Death reset ability cooldown");
        require(combat.stamina() == combat.maxStamina(), "Death did not restore resource capacity");
        require(Math.abs(restored.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) - damage) < 0.00001, "Respawn damage mismatch: before=" + damage + " (base=" + beforeBase + ", modifiers=" + beforeAttributes
                + "), after=" + restored.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) + " (base="
                + restored.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).getBaseValue() + ", modifiers="
                + restored.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).getModifiers() + ")");
        context.getWorld().getServer().getPlayerManager().remove(restored);
        context.complete();
    }

    @GameTest(maxTicks = 100)
    public void threeClassesFiveStatsAndNativePersistence(TestContext context) {
        int[] selections = {ProgressActionPayload.SELECT_WARRIOR, ProgressActionPayload.SELECT_MAGE,
                ProgressActionPayload.SELECT_ROGUE};
        PlayerClass[] classes = {PlayerClass.WARRIOR, PlayerClass.MAGE, PlayerClass.ROGUE};
        for (int i = 0; i < classes.length; i++) {
            var player = createPlayer(context);
            RpgGameplay.handleAction(player, new ProgressActionPayload(selections[i], 1));
            require(RpgGameplay.progress(player).playerClass() == classes[i], "Class selection failed: " + classes[i]);
            var base = RpgGameplay.derived(player);
            double damage = player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
            double mining = player.getAttributeValue(EntityAttributes.BLOCK_BREAK_SPEED);
            double health = player.getAttributeValue(EntityAttributes.MAX_HEALTH);
            double movement = player.getAttributeValue(EntityAttributes.MOVEMENT_SPEED);
            double speed = player.getAttributeValue(EntityAttributes.ATTACK_SPEED);
            RpgGameplay.awardXp(player, 1000);
            for (int action : new int[] {ProgressActionPayload.ALLOCATE_STRENGTH, ProgressActionPayload.ALLOCATE_VITALITY,
                    ProgressActionPayload.ALLOCATE_AGILITY, ProgressActionPayload.ALLOCATE_INTELLIGENCE,
                    ProgressActionPayload.ALLOCATE_SPIRIT})
                RpgGameplay.handleAction(player, new ProgressActionPayload(action, action + 20));
            var expected = RpgGameplay.progress(player);
            for (var stat : dev.fupez.rpgplayable.stats.Stat.values())
                require(expected.stat(stat).equals(java.math.BigInteger.ONE), "Missing allocation: " + stat);
            require(player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) > damage, "Strength had no native damage effect");
            require(player.getAttributeValue(EntityAttributes.BLOCK_BREAK_SPEED) > mining, "Strength had no native mining effect");
            require(player.getAttributeValue(EntityAttributes.MAX_HEALTH) > health, "Vitality had no native health effect");
            require(player.getAttributeValue(EntityAttributes.MOVEMENT_SPEED) > movement, "Agility had no native movement effect");
            require(player.getAttributeValue(EntityAttributes.ATTACK_SPEED) > speed, "Agility had no native attack speed effect");
            var derived = RpgGameplay.derived(player);
            require(derived.magicMultiplier() > base.magicMultiplier() && derived.maxMana() > base.maxMana(),
                    "Intelligence had no magic/mana effect");
            require(derived.manaRegen() > base.manaRegen() && derived.staminaRegen() > base.staminaRegen()
                    && derived.supportMultiplier() > base.supportMultiplier(), "Spirit had no regeneration/support effect");
            // Class selection is immutable, even with valid alternate class requests.
            RpgGameplay.handleAction(player, new ProgressActionPayload(selections[(i + 1) % 3], 60));
            require(RpgGameplay.progress(player).equals(expected), "Class replay changed allocated progress");
            var restored = reconnect(context, player);
            require(RpgGameplay.progress(restored).equals(expected), "Disk rejoin lost one of the five stats");
            require(Math.abs(restored.getAttributeValue(EntityAttributes.MAX_HEALTH) - (health + derived.maxHealthBonus() - base.maxHealthBonus())) < 0.0001,
                    "Disk rejoin lost or doubled Vitality");
            context.getWorld().getServer().getPlayerManager().remove(restored);
        }
        context.complete();
    }

    @GameTest(maxTicks = 100)
    public void warriorHeavyStrike(TestContext c) { abilityEffect(c, PlayerClass.WARRIOR, 0, false); }
    @GameTest(maxTicks = 100)
    public void warriorCharge(TestContext c) { abilityEffect(c, PlayerClass.WARRIOR, 1, true); }
    @GameTest(maxTicks = 100)
    public void warriorGroundSlam(TestContext c) { abilityEffect(c, PlayerClass.WARRIOR, 2, false); }
    @GameTest(maxTicks = 100)
    public void mageFirebolt(TestContext c) { abilityEffect(c, PlayerClass.MAGE, 0, false); }
    @GameTest(maxTicks = 100)
    public void mageArcaneBurst(TestContext c) { abilityEffect(c, PlayerClass.MAGE, 1, false); }
    @GameTest(maxTicks = 100)
    public void mageBlink(TestContext c) { abilityEffect(c, PlayerClass.MAGE, 2, true); }
    @GameTest(maxTicks = 100)
    public void rogueDash(TestContext c) { abilityEffect(c, PlayerClass.ROGUE, 0, true); }
    @GameTest(maxTicks = 100)
    public void rogueBackstab(TestContext c) { abilityEffect(c, PlayerClass.ROGUE, 1, false); }
    @GameTest(maxTicks = 100)
    public void rogueFanOfKnives(TestContext c) { abilityEffect(c, PlayerClass.ROGUE, 2, false); }

    private static void abilityEffect(TestContext context, PlayerClass clazz, int slot, boolean movement) {
        var player = createPlayer(context);
        arena(context, player);
        require(!dev.fupez.rpgplayable.abilities.AbilityRuntime.request(player, slot), "Unselected player cast an ability");
        select(player, clazz);
        var definition = dev.fupez.rpgplayable.abilities.AbilityCatalog.get(clazz, slot);
        var target = context.spawnMob(EntityType.HUSK, 1, 1, 3);
        target.setAiDisabled(true); target.setNoGravity(true); target.setYaw(0);
        var passive = context.spawnMob(EntityType.COW, 2, 1, 3);
        passive.setAiDisabled(true); passive.setNoGravity(true);
        var start = player.getEntityPos();
        float health = target.getHealth();
        var before = dev.fupez.rpgplayable.abilities.AbilityRuntime.snapshot(player);
        require(dev.fupez.rpgplayable.abilities.AbilityRuntime.request(player, slot), "Cast rejected: " + definition.id());
        var after = dev.fupez.rpgplayable.abilities.AbilityRuntime.snapshot(player);
        double spent = definition.resource() == dev.fupez.rpgplayable.abilities.ResourceType.MANA
                ? before.mana() - after.mana() : before.stamina() - after.stamina();
        require(Math.abs(spent - definition.cost()) < 0.0001, "Wrong native resource payment: " + definition.id());
        require(after.cooldowns()[slot] > 0, "Cast did not start cooldown");
        require(!dev.fupez.rpgplayable.abilities.AbilityRuntime.request(player, slot), "Duplicate cast accepted");
        var rejected = dev.fupez.rpgplayable.abilities.AbilityRuntime.snapshot(player);
        require(rejected.mana() == after.mana() && rejected.stamina() == after.stamina(), "Rejected duplicate spent resources");
        require(target.getHealth() == health, "Ability hit before windup");
        context.waitAndRun(25, () -> {
            if (movement) require(player.getEntityPos().squaredDistanceTo(start) > 0.5, "Movement ability did not move: " + definition.id());
            if (!movement || clazz == PlayerClass.WARRIOR)
                require(target.getHealth() < health, "Native hostile impact missing: " + definition.id());
            require(passive.getHealth() == passive.getMaxHealth(), "Ability damaged passive mob");
            var regenerated = dev.fupez.rpgplayable.abilities.AbilityRuntime.snapshot(player);
            double current = definition.resource() == dev.fupez.rpgplayable.abilities.ResourceType.MANA
                    ? regenerated.mana() : regenerated.stamina();
            double paid = definition.resource() == dev.fupez.rpgplayable.abilities.ResourceType.MANA ? after.mana() : after.stamina();
            require(current > paid, "Resource did not regenerate");
            require(!dev.fupez.rpgplayable.abilities.AbilityRuntime.request(player, slot), "Ability ignored its cooldown");
            context.getWorld().getServer().getPlayerManager().remove(player);
            context.complete();
        });
    }

    @GameTest(maxTicks = 100)
    public void interruptedCastReconnectAndWallValidation(TestContext context) {
        var player = createPlayer(context);
        arena(context, player);
        select(player, PlayerClass.WARRIOR);
        var target = context.spawnMob(EntityType.HUSK, 1, 1, 3);
        target.setAiDisabled(true); target.setNoGravity(true);
        require(dev.fupez.rpgplayable.abilities.AbilityRuntime.request(player, 0), "Initial cast failed");
        var restored = reconnect(context, player);
        arena(context, restored);
        require(!dev.fupez.rpgplayable.abilities.AbilityRuntime.request(restored, 0), "Reconnect reset cooldown");
        context.waitAndRun(16, () -> {
            require(target.getHealth() == target.getMaxHealth(), "Disconnected cast landed delayed impact");
            restored.changeGameMode(GameMode.SPECTATOR);
            require(!dev.fupez.rpgplayable.abilities.AbilityRuntime.request(restored, 1), "Spectator cast an ability");
            restored.changeGameMode(GameMode.SURVIVAL);
            for (int x = 0; x <= 6; x++) for (int y = 1; y <= 3; y++)
                context.setBlockState(x, y, 2, net.minecraft.block.Blocks.STONE);
            require(!restored.canSee(target), "Wall fixture did not block visibility");
            var start = restored.getEntityPos();
            require(!dev.fupez.rpgplayable.abilities.AbilityRuntime.request(restored, 1), "Charge accepted solid wall");
            require(restored.getEntityPos().equals(start), "Blocked charge moved actor");
            context.getWorld().getServer().getPlayerManager().remove(restored);
            context.complete();
        });
    }

    @GameTest(maxTicks = 90)
    public void cooldownExpiresAndNormalAttacksRecover(TestContext context) {
        var player = createPlayer(context);
        arena(context, player); choose(player);
        var target = context.spawnMob(EntityType.HUSK, 1, 1, 3);
        target.setAiDisabled(true); target.setNoGravity(true);
        require(dev.fupez.rpgplayable.abilities.AbilityRuntime.request(player, 0), "Initial cast failed");
        require(net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.invoker().interact(player,
                context.getWorld(), net.minecraft.util.Hand.MAIN_HAND, target, null) == net.minecraft.util.ActionResult.FAIL,
                "Basic attacks were not blocked during windup");
        context.waitAndRun(16, () -> require(net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.invoker().interact(player,
                context.getWorld(), net.minecraft.util.Hand.MAIN_HAND, target, null) == net.minecraft.util.ActionResult.PASS,
                "Basic attack restriction survived recovery"));
        context.waitAndRun(62, () -> {
            require(dev.fupez.rpgplayable.abilities.AbilityRuntime.snapshot(player).cooldowns()[0] == 0, "Cooldown did not expire");
            require(dev.fupez.rpgplayable.abilities.AbilityRuntime.request(player, 0), "Ready ability could not be reused");
            context.getWorld().getServer().getPlayerManager().remove(player);
            context.complete();
        });
    }

    private static void select(ServerPlayerEntity player, PlayerClass clazz) {
        int action = switch (clazz) {
            case WARRIOR -> ProgressActionPayload.SELECT_WARRIOR;
            case MAGE -> ProgressActionPayload.SELECT_MAGE;
            case ROGUE -> ProgressActionPayload.SELECT_ROGUE;
            default -> throw new IllegalArgumentException("Unselected fixture");
        };
        RpgGameplay.handleAction(player, new ProgressActionPayload(action, 1));
    }

    private static void arena(TestContext context, ServerPlayerEntity player) {
        for (int x = 0; x <= 6; x++) for (int z = 0; z <= 7; z++)
            context.setBlockState(x, 0, z, net.minecraft.block.Blocks.STONE);
        var origin = context.getAbsolutePos(new net.minecraft.util.math.BlockPos(1, 1, 1));
        player.refreshPositionAndAngles(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0, 0);
        player.setNoGravity(true);
        player.setOnGround(true);
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
        restored.networkHandler.onPlayerLoaded(new net.minecraft.network.packet.c2s.play.PlayerLoadedC2SPacket());
        require(restored.isLoaded(), "Reconnected fixture did not acknowledge loading");
        return restored;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
