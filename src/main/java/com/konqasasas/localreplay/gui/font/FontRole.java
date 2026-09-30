package com.konqasasas.localreplay.gui.font;

/** Visual text hierarchy used throughout Local Replay's native UI. */
public enum FontRole {
    /** Sans medium / normal controls and body text. */
    UI,
    /** Sans semibold / headings and primary actions. */
    UI_SEMIBOLD,
    /** Smaller, lower-priority sans text. */
    UI_SMALL,
    /** Numeric controls and ordinary time/tick values. */
    MONO,
    /** Primary playback time. */
    MONO_LARGE
}
