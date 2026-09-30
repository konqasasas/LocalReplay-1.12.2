package com.konqasasas.localreplay.gui.theme;

/**
 * Local Replay's modern dark tool palette.
 *
 * Keep colors restrained: interaction state and hierarchy should come mostly
 * from spacing/surface contrast, not from bright outlines everywhere.
 */
public final class LocalReplayTheme {

    public static final int CANVAS = 0xFF0E1115;
    public static final int SURFACE = 0xFF151A20;
    public static final int SURFACE_SUBTLE = 0xFF12171C;
    public static final int SURFACE_RAISED = 0xFF1A2027;
    public static final int SURFACE_SELECTED = 0xFF20344C;

    public static final int CONTROL = 0xFF1A2027;
    public static final int CONTROL_HOVER = 0xFF222A33;
    public static final int CONTROL_PRESSED = 0xFF27313B;
    public static final int CONTROL_ACTIVE = 0xFF202832;
    public static final int CONTROL_PRIMARY = 0xFF2E66B8;
    public static final int CONTROL_PRIMARY_HOVER = 0xFF3974C8;
    public static final int CONTROL_DANGER_HOVER = 0xFF3A2328;

    public static final int LINE = 0xFF2B343F;
    public static final int LINE_STRONG = 0xFF3B4754;
    public static final int LINE_CONTROL = 0xFF536171;

    public static final int TEXT = 0xFFDCE3E8;
    public static final int TEXT_STRONG = 0xFFEEF2F5;
    public static final int TEXT_MUTED = 0xFF98A4AF;
    public static final int TEXT_FAINT = 0xFF6F7B86;

    public static final int START = 0xFF6FCF87;
    public static final int LAP = 0xFFC8A348;
    public static final int GOAL = 0xFFE26D74;
    public static final int FOCUS = 0xFF6EA8FE;
    public static final int FOCUS_SOFT = 0xFF4F83D1;

    /** Replay control dock: translucent, but not a full-screen black strip. */
    public static final int OVERLAY_SURFACE = 0xEE11161C;
    public static final int OVERLAY_LINE = 0xAA697988;
    public static final int MODAL_BACKDROP = 0x99000000;

    private LocalReplayTheme() {
    }
}
