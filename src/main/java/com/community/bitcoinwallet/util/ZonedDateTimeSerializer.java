package com.community.bitcoinwallet.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class ZonedDateTimeSerializer extends StdSerializer<ZonedDateTime> {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter FORMATTER_FOR_UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public ZonedDateTimeSerializer() {
        super(ZonedDateTime.class);
    }

    @Override
    public void serialize(ZonedDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStringField("datetime", datetimeToString(value));
    }

    public static String datetimeToString(ZonedDateTime ts) {
        if (ts == null) {
            return null;
        }

        return ts.getZone().getId().equals(ZoneOffset.UTC.getId()) ?
            FORMATTER_FOR_UTC.format(ts) + "+00:00" : FORMATTER.format(ts);
    }
}
