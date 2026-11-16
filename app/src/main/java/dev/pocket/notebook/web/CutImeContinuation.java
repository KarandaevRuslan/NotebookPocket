package dev.pocket.notebook.web;

/** One-shot Cut/IME ordering, independent of Android and tested on the JVM.
 * A deadline is checked on events; no timer or retry loop is scheduled. */
public final class CutImeContinuation {
    private int revision = -1;
    private long deadline;
    private boolean completed;

    public void arm(int token, long now, boolean wasVisible, boolean canKeep) {
        cancel();
        if (!wasVisible || !canKeep) return;
        revision = token;
        // A large Cut may take longer than an animation. Only the completed
        // operation gets a short window for a late platform hide.
        deadline = Long.MAX_VALUE;
    }

    public boolean complete(int token, long now, boolean visible, boolean canKeep, boolean windowFocused) {
        if (!valid(token, now, canKeep)) return false;
        if (!completed) { completed = true; deadline = now + 1000; }
        return consumeIfHidden(visible, windowFocused);
    }

    public boolean insetsChanged(int token, long now, boolean visible, boolean canKeep, boolean windowFocused) {
        return valid(token, now, canKeep) && consumeIfHidden(visible, windowFocused);
    }

    public boolean hasPending() { return revision >= 0; }
    /** Scalar debug snapshot only; never changes the continuation. */
    public String diagnosticState(long now) {
        return " cutToken=" + revision + " cutCompleted=" + completed + " cutRemainingMs="
            + (revision < 0 ? 0 : !completed ? -1 : deadline - now);
    }
    public void cancel() { revision = -1; completed = false; }

    private boolean valid(int token, long now, boolean canKeep) {
        if (revision < 0 || revision != token) return false;
        if (now > deadline || !canKeep) {
            cancel();
            return false;
        }
        return true;
    }

    private boolean consumeIfHidden(boolean visible, boolean windowFocused) {
        // Clipboard UI may briefly take window focus without leaving editing.
        // Retain the bounded lease, but never show IME in that other window.
        if (!completed || visible || !windowFocused) return false;
        cancel();
        return true;
    }
}
