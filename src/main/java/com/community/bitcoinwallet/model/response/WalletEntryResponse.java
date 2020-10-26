package com.community.bitcoinwallet.model.response;

import com.community.bitcoinwallet.util.ZonedDateTimeSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.ZonedDateTime;

@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class WalletEntryResponse {
    @JsonSerialize(using = ZonedDateTimeSerializer.class)
    ZonedDateTime datetime;
    double amount;
}
