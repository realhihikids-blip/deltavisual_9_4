package dev.deltavisuals.core;

public enum Category {
    HUD("HUD"), VISUALS("Visuals"), COMBAT_VISUALS("Combat Visuals"), WORLD("World"), PLAYER("Player"),
    COSMETICS("Cosmetics"), CONTROLS("Controls"), INTERFACE("Interface"), CONFIGS("Configurations");

    public final String label;
    Category(String label) { this.label = label; }
}
