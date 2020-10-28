package com.community.bitcoinwallet.repository;

import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.util.DateAndAmountUtils;
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

    private static final long PRECISION = (long) Math.pow(10, 8);

    private static final String INSERT_INTO_WALLET = "insert into WALLET(ts,bitcoins,b_cents) " +
        " values(:ts,:bitcoins,:b_cents)";
    private static final String SELECT_IN_TIME_RANGE = "select ts,bitcoins,b_cents from WALLET " +
        "where (ts < :to) " +
        "order by ts";
    private static final String CLEAR = "delete from WALLET";

    NamedParameterJdbcTemplate jdbcTemplate;

    @Override
    public void addEntry(WalletEntry entry) {
        jdbcTemplate.update(INSERT_INTO_WALLET,
            Map.of("ts", entry.getDatetime().toEpochMilli(),
                "bitcoins", entry.getAmount().longValue(),
                "b_cents", (int) (entry.getAmount().remainder(BigDecimal.ONE).doubleValue() * PRECISION)));
    }

    @Override
    public List<WalletEntry> getBalancesByHour(Instant fromExclusive, Instant toInclusive) {
        return jdbcTemplate.query(SELECT_IN_TIME_RANGE,
            Map.of("from", fromExclusive.toEpochMilli(),
                "to", toInclusive.toEpochMilli()), (rs, rowNum) ->
                new WalletEntry(Instant.ofEpochMilli(rs.getLong("ts")),
                    DateAndAmountUtils.toBigDecimal((double) rs.getLong("bitcoins") +
                        ((double) rs.getInt("b_cents")) / PRECISION)));
    }

    @Override
    public void clear() {
        jdbcTemplate.execute(CLEAR, ps -> null);
    }

}
