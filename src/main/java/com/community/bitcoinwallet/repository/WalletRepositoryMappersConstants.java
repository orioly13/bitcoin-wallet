package com.community.bitcoinwallet.repository;

import com.community.bitcoinwallet.model.WalletEntry;
import org.springframework.jdbc.core.RowMapper;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class WalletRepositoryMappersConstants {

    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX");

    public static final RowMapper<WalletEntry> ROW_MAPPER = (rs, rowNum) ->
        new WalletEntry(Instant.ofEpochMilli(rs.getLong("ts")),
            rs.getBigDecimal("bitcoins").setScale(8, RoundingMode.HALF_UP));
    public static final RowMapper<WalletEntry> ROW_MAPPER_WITH_DATE = (rs, rowNum) ->
        new WalletEntry(ZonedDateTime.parse(rs.getString("date_hour"), FORMATTER).toInstant(),
            rs.getBigDecimal("bitcoins").setScale(8, RoundingMode.HALF_UP));
    public static final RowMapper<WalletEntry> ROW_MAPPER_WITH_SUM = (rs, rowNum) ->
        new WalletEntry(null, rs.getBigDecimal("bitcoins").setScale(8, RoundingMode.HALF_UP));

    // yeah :-/, we really should switch to something:-)
    public static final String TS_AT_START_OF_HOUR =
        "FORMATDATETIME(DATEADD('MILLISECOND', ts, DATE '1970-01-01'),'YYYY-MM-dd HH:00:00+00:00')";
    public static final String WALLET = "WALLET";
    public static final String BALANCE = "BALANCE";
    public static final String BALANCE_QUEUE = "BALANCE_UPDATE_QUEUE";

    public static final String INSERT = "insert into %s(ts,bitcoins) values(:ts, :bitcoins)";
    public static final String CLEAR = "delete from %s where 1=1";
}
