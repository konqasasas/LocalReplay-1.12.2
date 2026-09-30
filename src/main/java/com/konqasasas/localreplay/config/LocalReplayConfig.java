package com.konqasasas.localreplay.config;

import java.util.Arrays;

public final class LocalReplayConfig {

    public static final boolean DEFAULT_RECORDING = true;
    public static final int DEFAULT_REPLAY_LENGTH_SECONDS = 30;
    public static final boolean DEFAULT_INTERPOLATION = true;
    public static final int DEFAULT_DECIMAL_PLACES = 3;

    public static final int MIN_DECIMAL_PLACES = 0;
    public static final int MAX_DECIMAL_PLACES = 6;

    private static final int[] ALLOWED_REPLAY_LENGTHS_SECONDS = {10, 15, 30, 60, 120};

    private boolean recording = DEFAULT_RECORDING;
    private int replayLengthSeconds = DEFAULT_REPLAY_LENGTH_SECONDS;
    private boolean interpolation = DEFAULT_INTERPOLATION;
    private int decimalPlaces = DEFAULT_DECIMAL_PLACES;

    public LocalReplayConfig() {
    }

    public static LocalReplayConfig defaults() {
        return new LocalReplayConfig();
    }

    /**
     * Repairs values that are outside the v1 settings contract.
     *
     * @return true when at least one value was changed.
     */
    public boolean normalize() {
        boolean changed = false;

        if (!isAllowedReplayLength(replayLengthSeconds)) {
            replayLengthSeconds = DEFAULT_REPLAY_LENGTH_SECONDS;
            changed = true;
        }

        if (decimalPlaces < MIN_DECIMAL_PLACES || decimalPlaces > MAX_DECIMAL_PLACES) {
            decimalPlaces = DEFAULT_DECIMAL_PLACES;
            changed = true;
        }

        return changed;
    }

    public boolean isRecording() {
        return recording;
    }

    public void setRecording(boolean recording) {
        this.recording = recording;
    }

    public int getReplayLengthSeconds() {
        return replayLengthSeconds;
    }

    public void setReplayLengthSeconds(int replayLengthSeconds) {
        if (!isAllowedReplayLength(replayLengthSeconds)) {
            throw new IllegalArgumentException("Unsupported replay length: " + replayLengthSeconds);
        }
        this.replayLengthSeconds = replayLengthSeconds;
    }

    public boolean isInterpolation() {
        return interpolation;
    }

    public void setInterpolation(boolean interpolation) {
        this.interpolation = interpolation;
    }

    public int getDecimalPlaces() {
        return decimalPlaces;
    }

    public void setDecimalPlaces(int decimalPlaces) {
        if (decimalPlaces < MIN_DECIMAL_PLACES || decimalPlaces > MAX_DECIMAL_PLACES) {
            throw new IllegalArgumentException("decimalPlaces must be between "
                    + MIN_DECIMAL_PLACES + " and " + MAX_DECIMAL_PLACES);
        }
        this.decimalPlaces = decimalPlaces;
    }

    public static boolean isAllowedReplayLength(int seconds) {
        for (int allowed : ALLOWED_REPLAY_LENGTHS_SECONDS) {
            if (allowed == seconds) {
                return true;
            }
        }
        return false;
    }

    public static int[] getAllowedReplayLengthsSeconds() {
        return Arrays.copyOf(ALLOWED_REPLAY_LENGTHS_SECONDS, ALLOWED_REPLAY_LENGTHS_SECONDS.length);
    }
}
