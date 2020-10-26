package com.community.bitcoinwallet.model.requests;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.ZonedDateTime;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AddWalletEntryRequest {
    ZonedDateTime datetime;
    Double amount;
}
