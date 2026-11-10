package com.aetherianartificer.townstead.recognition;

/**
 * Work that can be paused. {@link #step} does as much as fits before the deadline and says whether
 * everything is done, so a large scan can be spread over server ticks instead of freezing one.
 */
public interface TimeSliced {
    /**
     * @param deadlineNanos {@link System#nanoTime()} value after which the step should stop at the next
     *                      safe point; always makes some progress when there is work left
     * @return true once all of the work is finished
     */
    boolean step(long deadlineNanos);

    /** Runs the remaining work without pausing. */
    default void runToEnd() {
        while (!step(Long.MAX_VALUE)) {
            // Long.MAX_VALUE never passes, so one call finishes; the loop only guards a short return.
        }
    }
}
