package com.community.bitcoinwallet.model;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class WalletEntry implements Comparable<WalletEntry> {
    Instant datetime;
    BigDecimal amount;

    @Override
    public int compareTo(WalletEntry o) {
        return datetime.compareTo(o.datetime);
    }
}
