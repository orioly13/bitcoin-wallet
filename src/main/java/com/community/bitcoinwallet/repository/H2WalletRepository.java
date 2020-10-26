package com.community.bitcoinwallet.repository;

import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.util.WalletEntryUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class H2WalletRepository implements WalletRepository {

    private static final String INSERT_INTO_WALLET = "insert into WALLET(ts,dollars,cents) " +
        " values(:ts,:dollars,:cents)";
    private static final String SELECT_IN_TIME_RANGE = "select ts,dollars,cents from WALLET " +
        "where (ts >= :from) and (ts < :to) " +
        "order by ts";

    NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void addEntry(WalletEntry entry) {
        jdbcTemplate.update(INSERT_INTO_WALLET,
            Map.of("ts", entry.getDatetime().toEpochMilli(),
                "dollars", entry.getAmount().longValue(),
                "cents", (int) (entry.getAmount().remainder(BigDecimal.ONE).doubleValue() * 100)));
    }

    @Override
    public List<WalletEntry> getEntries(Instant fromInclusive, Instant to) {
        return jdbcTemplate.query(SELECT_IN_TIME_RANGE,
            Map.of("from", fromInclusive.toEpochMilli(),
                "to", to.toEpochMilli()), (rs, rowNum) ->
                new WalletEntry(Instant.ofEpochMilli(rs.getLong("ts")),
                    WalletEntryUtils.toBigDecimal((double) rs.getLong("dollars") +
                        ((double) rs.getInt("cents")) / 100)));
    }

}
