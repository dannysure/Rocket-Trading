package com.rockettrading.rocket_trading;

import com.rockettrading.rocket_trading.enums.AssetClass;
import com.rockettrading.rocket_trading.model.Instrument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class InstrumentServiceTests {
    private Instrument instrument;

    @BeforeEach
    public void setup() {
        instrument = new Instrument("AAPL", AssetClass.EQUITY, true);
    }

    @Test
    public void symbolIsString() {
        assertInstanceOf(String.class, instrument.getSymbol());
    }

    @Test
    @DisplayName("Checks if asset class is one of the valid enum values")
    public void instrumentIsEquityBondFundFxCryptoCash() {
        AssetClass assetClass = instrument.getAssetClass();
        assertTrue(EnumSet.allOf(AssetClass.class).contains(assetClass));
    }
}
