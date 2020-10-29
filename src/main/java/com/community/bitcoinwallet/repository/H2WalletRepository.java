package com.community.bitcoinwallet.repository;

import com.community.bitcoinwallet.model.WalletEntry;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.community.bitcoinwallet.repository.WalletRepositoryMappersConstants.*;


@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class H2WalletRepository {

    NamedParameterJdbcTemplate jdbcTemplate;

    public H2WalletRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void addEntry(WalletEntry entry) {
        Map<String, Object> values = entryToMap(entry);
        jdbcTemplate.update(String.format(INSERT, WALLET), values);
        jdbcTemplate.update(String.format(INSERT, BALANCE_QUEUE), values);
    }

    @Transactional
    public void clear() {
        jdbcTemplate.update(String.format(CLEAR, WALLET), Collections.emptyMap());
        jdbcTemplate.update(String.format(CLEAR, BALANCE), Collections.emptyMap());
        jdbcTemplate.update(String.format(CLEAR, BALANCE_QUEUE), Collections.emptyMap());
    }

    public List<WalletEntry> getWalletSumInRangeByHour(Instant fromAtStart, Instant toAtStart) {
        return jdbcTemplate.query("select " +
                TS_AT_START_OF_HOUR + " as date_hour, SUM(bitcoins) as bitcoins from WALLET " +
                "where ts >= :from and ts < :to " +
                " group by " + TS_AT_START_OF_HOUR +
                " order by date_hour",
            Map.of("from", fromAtStart.toEpochMilli(),
                "to", toAtStart.toEpochMilli()), ROW_MAPPER_WITH_DATE);
    }

    public Optional<WalletEntry> getWalletSumBeforeFrom(Instant fromAtStart) {
        List<WalletEntry> ts = jdbcTemplate.query(
            "select SUM(bitcoins) as bitcoins from WALLET " +
                "where ts < :ts " +
                "group by 1",
            Map.of("ts", fromAtStart.toEpochMilli()), ROW_MAPPER_WITH_SUM);
        return ts.stream().findFirst();
    }

    public List<WalletEntry> getBalancesWithinRange(Instant fromExclusive, Instant toInclusive) {
        return jdbcTemplate.query("select ts,bitcoins from BALANCE " +
                "where (ts > :from and ts < :to) or ts = :to",
            Map.of("from", fromExclusive.toEpochMilli(),
                "to", toInclusive.toEpochMilli()), ROW_MAPPER);
    }

    public Optional<WalletEntry> getBalanceBeforeRange(Instant from) {
        return jdbcTemplate.query("select ts,bitcoins from BALANCE " +
                "where (ts <= :from) " +
                "order by ts desc " +
                "limit 1",
            Map.of("from", from.toEpochMilli()), ROW_MAPPER)
            .stream().findFirst();
    }

    /**
     * Should happen inside the transaction, otherwise we can lose some updates.
     */
    @Transactional
    public Optional<WalletEntry> getFirstEventAndClearQueue() {
        Optional<WalletEntry> first = jdbcTemplate.query("select ts,bitcoins from BALANCE_UPDATE_QUEUE " +
                "order by ts " +
                "limit 1",
            Collections.emptyMap(), ROW_MAPPER).stream().findFirst();
        jdbcTemplate.update(String.format(CLEAR, BALANCE_QUEUE), Collections.emptyMap());
        return first;
    }

    @Transactional
    public void mergeIntoBalances(List<WalletEntry> balancesToUpdate) {
        for (WalletEntry entry : balancesToUpdate) {
            jdbcTemplate.update("merge into BALANCE(ts,bitcoins) values(:ts,:bitcoins)", entryToMap(entry));
        }
    }

    public Optional<Instant> getLastBalanceTs() {
        return jdbcTemplate.query("select MAX(ts) ts_max from WALLET " +
            "group by 1", Collections.emptyMap(), (rs, rowNum) -> Instant.ofEpochMilli(rs.getLong("ts_max")))
            .stream().findFirst();
    }


    private Map<String, Object> entryToMap(WalletEntry entry) {
        return Map.of("ts", entry.getDatetime().toEpochMilli(),
            "bitcoins", entry.getAmount());
    }

}
