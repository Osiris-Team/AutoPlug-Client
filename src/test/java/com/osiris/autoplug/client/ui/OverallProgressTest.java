package com.osiris.autoplug.client.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OverallProgressTest {
    @Test void estimateMovesForwardAndSlowsWithoutPretendingToComplete() {
        OverallProgress progress = new OverallProgress(); progress.start();
        int previous = progress.value(), start = previous;
        for (int i = 0; i < 30; i++) { int next = progress.tick(); assertTrue(next >= previous); previous = next; }
        int earlyGain = previous - start, laterStart = previous;
        for (int i = 0; i < 30; i++) { int next = progress.tick(); assertTrue(next >= previous); previous = next; }
        assertTrue(previous - laterStart < earlyGain);
        for (int i = 0; i < 2000; i++) { int next = progress.tick(); assertTrue(next >= previous && next < 100); previous = next; }
        assertTrue(previous <= 95);
    }

    @Test void completionStaysCompleteUntilAnotherOperationExplicitlyStarts() {
        OverallProgress progress = new OverallProgress(); progress.start(); progress.tick();
        assertEquals(100, progress.complete()); assertEquals(100, progress.value()); assertEquals(100, progress.tick());
        progress.start(); assertTrue(progress.value() > 0 && progress.value() < 100);
    }
}
