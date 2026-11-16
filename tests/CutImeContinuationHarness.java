package dev.pocket.notebook.web;

/** Runs the production state machine without an Android emulator. */
public final class CutImeContinuationHarness {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        CutImeContinuation c = new CutImeContinuation();
        c.arm(1, 100, true, true);
        check(c.diagnosticState(110).contains("cutToken=1 cutCompleted=false cutRemainingMs=-1"), "Snapshot identifies an armed Cut");
        check(!c.complete(1, 150, true, true, true), "Do not show before late hide");
        check(c.diagnosticState(200).contains("cutToken=1 cutCompleted=true cutRemainingMs=950"), "Snapshot shows completion deadline without consuming it");
        check(c.insetsChanged(1, 250, false, true, true), "Late hide consumes continuation");
        check(c.diagnosticState(260).contains("cutToken=-1 cutCompleted=false cutRemainingMs=0"), "Snapshot identifies a consumed Cut");
        check(!c.insetsChanged(1, 260, false, true, true), "Never show twice");

        c.arm(2, 300, true, true);
        check(!c.insetsChanged(2, 320, false, true, true), "Wait for Cut completion");
        check(c.complete(2, 350, false, true, true), "Hide before completion is supported");

        c.arm(3, 400, true, true); c.cancel();
        check(!c.complete(3, 450, false, true, true), "Back/new gesture cancels");
        c.arm(4, 500, false, true);
        check(!c.complete(4, 520, false, true, true), "Closed IME stays closed");

        c.arm(5, 600, true, true);
        check(!c.complete(5, 650, true, true, true), "Wait for a late hide");
        check(!c.insetsChanged(5, 1700, false, true, true), "Expired lease never reopens later");
        c.arm(6, 1800, true, true);
        check(!c.complete(6, 1850, false, false, true), "Lost focus cancels");
        check(!c.hasPending(), "Lost-focus state is cleared");

        c.arm(7, 1900, true, true);
        check(!c.complete(6, 1920, false, true, true), "Ignore stale callback");
        check(c.complete(7, 1950, false, true, true), "Stale callback preserves current lease");

        c.arm(8, 2000, true, true);
        check(!c.complete(8, 2050, true, true, true), "Already-visible IME is not reopened");
        check(!c.insetsChanged(8, 2100, true, true, true), "Visible animation needs no show");
        c.arm(9, 2200, true, true);
        check(!c.insetsChanged(9, 2400, false, true, true), "Large Cut still waits for completion");
        check(c.complete(9, 4500, false, true, true), "Slow Cut keeps prior IME intent");
        // Device trace 13:29:33.928: window focus is lost before completion,
        // returns 28ms later, then hidden insets arrive. No revision cancel.
        c.arm(10, 5000, true, true);
        check(!c.complete(10, 5076, false, true, false), "Wait while clipboard window has focus");
        check(c.hasPending(), "Transient window loss retains Cut intent");
        check(c.insetsChanged(10, 5077, false, true, true), "Focus return completes a hidden Cut once");
        check(!c.insetsChanged(10, 5115, false, true, true), "Later hidden inset does not show twice");

        // Device trace 13:30:04: completion precedes focus loss and hide.
        c.arm(11, 6000, true, true);
        check(!c.complete(11, 6040, true, true, true), "Wait for clipboard hide");
        check(!c.insetsChanged(11, 6060, false, true, false), "Never show IME into another window");
        check(c.hasPending(), "Hidden state while unfocused retains bounded lease");
        check(c.insetsChanged(11, 6070, false, true, true), "Restore on focus return");

        c.arm(12, 7000, true, true);
        check(!c.complete(12, 7040, true, true, true), "Wait for window event");
        check(!c.insetsChanged(12, 7060, true, true, true), "Successful Cut needs no show");
        check(!c.insetsChanged(12, 8100, false, true, true), "Long focus loss expires rather than resurrecting IME");
        check(!c.hasPending(), "Expired transient focus lease is cleared");

        c.arm(13, 9000, true, true);
        c.cancel(); // Activity ON_PAUSE, Back, new touch, hidden editor.
        check(!c.complete(13, 9020, false, true, true), "Actual activity exit cancels even if focus returns");
        c.arm(14, 10000, true, true);
        check(!c.complete(14, 10020, false, false, true), "Actual editor focus loss still cancels");
        check(!c.hasPending(), "Native focus loss does not retain a lease");
        System.out.println("Cut IME: 14 event-order/cancellation scenarios passed");
    }
}
