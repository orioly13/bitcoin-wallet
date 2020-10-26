package com.community.bitcoinwallet.model.response;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.springframework.lang.Nullable;

@Data
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class GeneralResponseData {
    Status status;
    @Nullable
    String message;
}
