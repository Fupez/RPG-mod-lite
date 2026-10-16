package dev.fupez.rpgplayable.stats;

public enum Stat {
    STRENGTH("strength"), VITALITY("vitality"), AGILITY("agility"), INTELLIGENCE("intelligence"), SPIRIT("spirit");
    private final String id;
    Stat(String id) { this.id = id; }
    public String id() { return id; }
}
