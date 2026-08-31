package com.hft.strategy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.Test;

public class AIEnhancedLifecycleTest {
    @Test
    public void testAIEnhancedStartsAndShutdownsCleanly() throws Exception {
        AIEnhancedStrategy strat = new AIEnhancedStrategy(1, 1, 10);
        strat.initialize();

        // Let scheduled tasks run briefly
        Thread.sleep(200);

        // Fetch signals
        var signals = strat.getCurrentAISignals();
        // Should not throw and should return possibly null or not
        // Now shutdown and ensure no exception
        strat.shutdown();

        // After shutdown, calling getCurrentAISignals must be safe
        assertDoesNotThrow(() -> strat.getCurrentAISignals());
    }
}
