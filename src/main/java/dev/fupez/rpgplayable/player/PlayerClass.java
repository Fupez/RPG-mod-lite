package dev.fupez.rpgplayable.player;

public enum PlayerClass {
    UNSELECTED("none"), WARRIOR("warrior");

    private final String id;
    PlayerClass(String id) { this.id = id; }
    public String id() { return id; }
    public static PlayerClass fromId(String id) {
        for (var value : values()) if (value.id.equals(id)) return value;
        throw new IllegalArgumentException("Unknown RPG class: " + id);
    }
}
