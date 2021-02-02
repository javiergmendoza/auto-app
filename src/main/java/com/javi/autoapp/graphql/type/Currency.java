package com.javi.autoapp.graphql.type;

import java.util.HashMap;
import lombok.Getter;

public enum Currency {
    XLM("XLM-USD"),
    BTC("BTC-USD"),
    ETH("ETH-USD"),
    LTC("LTC-USD"),
    BCH("BCH-USD"),
    EOS("EOS-USD"),
    DASH("DASH-USD"),
    OXT("OXT-USD"),
    MKR("MKR-USD"),
    ATOM("ATOM-USD"),
    XTZ("XTZ-USD"),
    ETC("ETC-USD"),
    OMG("OMG-USD"),
    ZEC("ZEC-USD"),
    LINK("LINK-USD"),
    REP("REP-USD"),
    ZRX("ZRX-USD"),
    ALGO("ALGO-USD"),
    DAI("DAI-USD"),
    KNC("KNC-USD"),
    COMP("COMP-USD"),
    BAND("BAND-USD"),
    NMR("NMR-USD"),
    CGLD("CGLD-USD"),
    UMA("UMA-USD"),
    LRC("LRC-USD"),
    YFI("YFI-USD"),
    UNI("UNI-USD"),
    REN("REN-USD"),
    BAL("BAL-USD"),
    WBTC("WBTC-USD"),
    NU("NU-USD"),
    FIL("FIL-USD"),
    AAVE("AAVE-USD"),
    GRT("GRT-USD"),
    BNT("BNT-USD"),
    SNX("SNX-USD"),
    USD("USD");

    @Getter
    private final String label;
    private static final HashMap<String, Currency> MAP = new HashMap<>();

    Currency(String label) {
        this.label = label;
    }

    public static Currency getByLabel(String label) {
        return MAP.get(label);
    }

    static {
        for (Currency field : Currency.values()) {
            MAP.put(field.getLabel(), field);
        }
    }
}
