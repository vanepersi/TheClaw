package dev.genesi.theclaw.model;

public enum PlayerRole {
    OPERATOR,
    CLAW,
    ANY;

    public static PlayerRole fromInput(String input) {
        if (input == null || input.isBlank()) {
            return ANY;
        }
        return switch (input.toLowerCase()) {
            case "operator", "joystick", "p1", "1", "driver" -> OPERATOR;
            case "claw", "grabber", "p2", "2" -> CLAW;
            case "any", "auto", "either" -> ANY;
            default -> null;
        };
    }

    public String display() {
        return switch (this) {
            case OPERATOR -> "Operator (Joystick)";
            case CLAW -> "Claw";
            case ANY -> "Any";
        };
    }
}
