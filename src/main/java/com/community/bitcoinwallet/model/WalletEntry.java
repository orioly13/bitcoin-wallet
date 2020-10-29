package com.community.bitcoinwallet.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WalletEntry {
    Instant datetime;
    BigDecimal amount;

    @Override
    public String toString() {
        return "WalletEntry(" +
            "datetime=" + datetime +
            ", amount=" + (amount == null ? null : amount.stripTrailingZeros()) +
            ')';
    }
}
