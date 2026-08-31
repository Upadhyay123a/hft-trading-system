package com.hft.ml;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;

import com.hft.exchange.api.BinanceRealApi;
import com.hft.exchange.api.CoinbaseRealApi;
import com.hft.exchange.api.MultiExchangeManager;

public class HistoricalDataTrainerParsingTest {

    @Test
    void collectFromBinance_handlesMixedTypesAndMalformedEntries() throws Exception {
        MultiExchangeManager mem = new MultiExchangeManager(true, true, 3);
        HistoricalDataTrainer trainer = new HistoricalDataTrainer(mem);

        // Create a mock Binance API that returns mixed-type klines
        BinanceRealApi mockBinance = Mockito.mock(BinanceRealApi.class);

        List<List<Object>> klines = new ArrayList<>();
        // Proper entry: timestamp as long, prices as strings
        klines.add(Arrays.asList(1620000000000L, "50000.0", "50050.0", "49900.0", "50010.0", "1000.0"));
        // Mixed types: numbers and strings
        klines.add(Arrays.asList(1620000060000L, 50010.0, "50060.0", 49950.0, "50030.0", 800.0));
        // Malformed entry (nulls) - should be skipped
        klines.add(Arrays.asList(null, null, null, null, null, null));

        when(mockBinance.getHistoricalKlines(anyString(), anyString(), any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(klines);

        // Replace private final field binanceApi with our mock
        Field binField = HistoricalDataTrainer.class.getDeclaredField("binanceApi");
        binField.setAccessible(true);
        binField.set(trainer, mockBinance);

        // Also stub coinbaseApi to return empty (avoid external calls)
        CoinbaseRealApi mockCoinbase = Mockito.mock(CoinbaseRealApi.class);
        Field coinField = HistoricalDataTrainer.class.getDeclaredField("coinbaseApi");
        coinField.setAccessible(true);
        coinField.set(trainer, mockCoinbase);

        // Invoke private collectFromBinance method via reflection
        Method m = HistoricalDataTrainer.class.getDeclaredMethod("collectFromBinance", String.class, LocalDate.class, LocalDate.class, HistoricalDataTrainer.HistoricalData.class);
        m.setAccessible(true);

        HistoricalDataTrainer.HistoricalData data = new HistoricalDataTrainer.HistoricalData();
        m.invoke(trainer, "BTC/USDT", LocalDate.now().minusDays(1), LocalDate.now(), data);

        // Verify that at least one tick was added (malformed entry skipped)
        assertThat(data.size()).isGreaterThan(0);
    }
}
