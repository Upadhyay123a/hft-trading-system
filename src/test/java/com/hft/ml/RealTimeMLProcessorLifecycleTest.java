package com.hft.ml;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.Test;

import com.hft.exchange.api.MultiExchangeManager;

public class RealTimeMLProcessorLifecycleTest {

    @Test
    void startStopStart_noRejectedExecution() throws Exception {
        MultiExchangeManager mem = new MultiExchangeManager(true, true, 3);
        RealTimeMLProcessor proc = new RealTimeMLProcessor(mem);

        assertDoesNotThrow(() -> {
            proc.start();
            Thread.sleep(500);
            proc.stop();
            Thread.sleep(200);
            proc.start();
            Thread.sleep(500);
            proc.stop();
        });
    }
}
