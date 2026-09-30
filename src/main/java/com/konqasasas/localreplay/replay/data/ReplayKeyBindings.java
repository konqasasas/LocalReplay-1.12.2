package com.konqasasas.localreplay.replay.data;

public final class ReplayKeyBindings {

    private final String forward;
    private final String left;
    private final String back;
    private final String right;
    private final String jump;
    private final String sprint;
    private final String sneak;

    public ReplayKeyBindings(
            String forward,
            String left,
            String back,
            String right,
            String jump,
            String sprint,
            String sneak
    ) {
        this.forward = requireLabel(forward, "forward");
        this.left = requireLabel(left, "left");
        this.back = requireLabel(back, "back");
        this.right = requireLabel(right, "right");
        this.jump = requireLabel(jump, "jump");
        this.sprint = requireLabel(sprint, "sprint");
        this.sneak = requireLabel(sneak, "sneak");
    }

    public String getForward() {
        return forward;
    }

    public String getLeft() {
        return left;
    }

    public String getBack() {
        return back;
    }

    public String getRight() {
        return right;
    }

    public String getJump() {
        return jump;
    }

    public String getSprint() {
        return sprint;
    }

    public String getSneak() {
        return sneak;
    }

    private static String requireLabel(String label, String name) {
        if (label == null) {
            throw new IllegalArgumentException(name + " key label cannot be null");
        }

        String trimmed = label.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " key label cannot be empty");
        }
        if (trimmed.length() > 64) {
            throw new IllegalArgumentException(name + " key label is too long");
        }
        return trimmed;
    }
}
