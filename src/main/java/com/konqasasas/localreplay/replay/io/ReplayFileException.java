package com.konqasasas.localreplay.replay.io;

import java.io.IOException;

/**
 * Checked failure raised when an .lreplay file is invalid, unsupported, or
 * cannot be read/written safely.
 */
public final class ReplayFileException extends IOException {

    public ReplayFileException(String message) {
        super(message);
    }

    public ReplayFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
