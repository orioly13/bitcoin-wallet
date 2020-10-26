package com.community.bitcoinwallet.util;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;

public class ZonedDateTimeSerializerTest {

    @Test
    public void convertsNullToNull() {
        Assertions.assertThat(ZonedDateTimeSerializer.datetimeToString(null)).isNull();
    }

    @Test
    public void convertsTimeZonesToCorrectOffsets() {
        Assertions.assertThat(ZonedDateTimeSerializer.datetimeToString(
            Instant.parse("2020-10-11T12:00:00Z").atZone(ZoneOffset.UTC)))
            .isEqualTo("2020-10-11T12:00:00+00:00");
        Assertions.assertThat(ZonedDateTimeSerializer.datetimeToString(
            Instant.parse("2020-10-11T12:00:00Z").atZone(ZoneOffset.ofHours(1))))
            .isEqualTo("2020-10-11T13:00:00+01:00");
        Assertions.assertThat(ZonedDateTimeSerializer.datetimeToString(
            Instant.parse("2020-10-11T12:00:00Z").atZone(ZoneOffset.ofHours(12))))
            .isEqualTo("2020-10-12T00:00:00+12:00");
        Assertions.assertThat(ZonedDateTimeSerializer.datetimeToString(
            Instant.parse("2020-10-11T12:00:00Z").atZone(ZoneOffset.ofHours(-1))))
            .isEqualTo("2020-10-11T11:00:00-01:00");
        Assertions.assertThat(ZonedDateTimeSerializer.datetimeToString(
            Instant.parse("2020-10-11T12:00:00Z").atZone(ZoneOffset.ofHours(-12))))
            .isEqualTo("2020-10-11T00:00:00-12:00");
    }
}