package dev.fupez.rpgplayable.abilities;

/** Pure resource operations shared by server ticks and deterministic tests. */
public record ResourcePool(double mana, double stamina) {
    public ResourcePool {
        if (!Double.isFinite(mana) || !Double.isFinite(stamina) || mana < 0 || stamina < 0)
            throw new IllegalArgumentException("Invalid resource pool");
    }
    public boolean canSpend(ResourceType type, double amount) {
        if (!Double.isFinite(amount) || amount < 0) return false;
        return (type == ResourceType.MANA ? mana : stamina) >= amount;
    }
    public ResourcePool spend(ResourceType type, double amount) {
        if (!canSpend(type, amount)) throw new IllegalArgumentException("Insufficient resource");
        return type == ResourceType.MANA ? new ResourcePool(Math.max(0, mana - amount), stamina)
                : new ResourcePool(mana, Math.max(0, stamina - amount));
    }
    public ResourcePool recover(double maxMana, double maxStamina, double manaRate, double staminaRate) {
        if (!Double.isFinite(maxMana) || !Double.isFinite(maxStamina) || !Double.isFinite(manaRate) || !Double.isFinite(staminaRate)
                || maxMana < 0 || maxStamina < 0 || manaRate < 0 || staminaRate < 0)
            throw new IllegalArgumentException("Invalid resource recovery");
        return new ResourcePool(Math.min(maxMana, mana + manaRate), Math.min(maxStamina, stamina + staminaRate));
    }
}
