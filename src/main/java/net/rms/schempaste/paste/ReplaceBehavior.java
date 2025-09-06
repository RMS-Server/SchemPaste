package net.rms.schempaste.paste;

public enum ReplaceBehavior {
    NONE,
    ALL,
    WITH_NON_AIR;

    public static ReplaceBehavior fromString(String s) {
        if (s == null) return ALL;
        String v = s.trim().toLowerCase();
        switch (v) {
            case "none":
                return NONE;
            case "non_air":
            case "with_non_air":
                return WITH_NON_AIR;
            case "all":
            default:
                return ALL;
        }
    }

    public String asString() {
        switch (this) {
            case NONE:
                return "none";
            case WITH_NON_AIR:
                return "with_non_air";
            case ALL:
            default:
                return "all";
        }
    }
}
