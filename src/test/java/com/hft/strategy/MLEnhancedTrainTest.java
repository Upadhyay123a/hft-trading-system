package com.hft.strategy;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import org.junit.jupiter.api.Test;

public class MLEnhancedTrainTest {
    @Test
    public void testTrainModelWithSyntheticData() {
        MLEnhancedMarketMakingStrategy strat = new MLEnhancedMarketMakingStrategy(1, 0.02, 1, 10);
        strat.initialize();

        List<double[]> historical = new ArrayList<>();
        // generate 120 samples (indicatorLookback is 100)
        for (int i = 0; i < 120; i++) {
            double price = 10000 + i * 1.0;
            double volume = 100 + (i % 10) * 5;
            historical.add(new double[]{price, volume});
        }

        assertDoesNotThrow(() -> strat.trainModel(historical));
        // After training, isTrained flag isn't accessible; ensure getStats doesn't throw
        assertDoesNotThrow(() -> strat.getStats().toString());
    }
}
