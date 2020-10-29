package com.community.bitcoinwallet.repository;

import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.util.DateAndAmountUtils;
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

    private static final String COUNT_BALANCES = "select count(*) c from BALANCE";
    private static final String SELECT_BALANCES_FOR_UPDATE = "select ts,bitcoins from BALANCE " +
        "where (ts > :from) limit :limit";
    private static final String SELECT_LAST_BALANCE = "select ts, bitcoins from BALANCE " +
        "order by ts desc " +
        "limit 1";
    private static final String SELECT_FIRST_BALANCE = "select ts, bitcoins c from BALANCE " +
        "order by ts " +
        "limit 1";

    private static final String UPDATE_BALANCE = "update BALANCE set bitcoins=:bitcoins where" +
        " ts=:ts";

    private static final String SELECT_NEXT_FROM_QUEUE = "select ts,bitcoins from BALANCE_UPDATE_QUEUE " +
        "order by ts " +
        "limit 1";
    private static final String SELECT_ID_FROM_QUEUE = "select id from BALANCE_UPDATE_QUEUE " +
        "order by ts " +
        "limit 1";
    private static final String DELETE_FROM_QUEUE = "delete from BALANCE_UPDATE_QUEUE where id=:id";

    NamedParameterJdbcTemplate jdbcTemplate;

    public H2WalletRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void addEntry(WalletEntry entry) {
        Map<String, Object> values = Map.of("ts", entry.getDatetime().toEpochMilli(),
            "bitcoins", entry.getAmount());
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

    public WalletEntry getWalletSumBeforeFrom(Instant fromAtStart) {
        List<WalletEntry> ts = jdbcTemplate.query(
            "select SUM(bitcoins) as bitcoins from WALLET " +
                "where ts < :ts " +
                "group by 1",
            Map.of("ts", fromAtStart.toEpochMilli()), ROW_MAPPER_WITH_SUM);
        return ts.stream().findFirst()
            .map(w -> new WalletEntry(fromAtStart, w.getAmount()))
            .orElse(new WalletEntry(fromAtStart, DateAndAmountUtils.toBigDecimal(0.0)));
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

    @Transactional
    private void updateBalancesFromQueue() {
        List<WalletEntry> res = jdbcTemplate.query(SELECT_NEXT_FROM_QUEUE,
            Collections.emptyMap(), ROW_MAPPER);
        if (!res.isEmpty()) {
            incrementBalancesFromEntry(res.get(0));
            Long id = jdbcTemplate.queryForObject(SELECT_ID_FROM_QUEUE, Collections.emptyMap(),
                (rs, rowNum) -> rs.getLong("id"));
            jdbcTemplate.update(DELETE_FROM_QUEUE, Map.of("id", id));
        }
    }

    /**
     * Not really thread-safe by itself, but can be, if there is a strict transaction policy in DB.
     * Currently is either called with blocking or used by single thread executor,
     * so should by fine.
     */
    private void incrementBalancesFromEntry(WalletEntry walletEntry) {
        Instant atEndOfHour = DateAndAmountUtils.atEndOfHour(walletEntry.getDatetime());
        if (balanceTableIsEmpty()) {
            insertBalance(new WalletEntry(atEndOfHour, walletEntry.getAmount()));
            return;
        }

        List<WalletEntry> currentBalances = selectNextBatchOfBalances(walletEntry.getDatetime());
        while (!currentBalances.isEmpty()) {
            currentBalances.forEach(b -> b.setAmount(b.getAmount().add(walletEntry.getAmount())));
            updateBalances(currentBalances);
            currentBalances = selectNextBatchOfBalances(currentBalances.get(currentBalances.size() - 1).getDatetime());
        }

        WalletEntry firstEntry = getFirstEntry();
        if (firstEntry.getDatetime().isAfter(atEndOfHour)) {
            insertBalance(new WalletEntry(atEndOfHour, walletEntry.getAmount()));
        }
        WalletEntry lastEntry = getLastEntry();
        if (lastEntry.getDatetime().isBefore(atEndOfHour)) {
            insertBalance(new WalletEntry(atEndOfHour, walletEntry.getAmount().add(lastEntry.getAmount())));
        }
    }

    private boolean balanceTableIsEmpty() {
        return jdbcTemplate.queryForObject(COUNT_BALANCES, Collections.emptyMap(),
            (rs, row) -> rs.getInt("c") == 0);
    }

    private WalletEntry getFirstEntry() {
        return jdbcTemplate.queryForObject(SELECT_FIRST_BALANCE, Collections.emptyMap(), ROW_MAPPER);
    }

    private WalletEntry getLastEntry() {
        return jdbcTemplate.queryForObject(SELECT_LAST_BALANCE, Collections.emptyMap(), ROW_MAPPER);
    }

    private List<WalletEntry> selectNextBatchOfBalances(Instant ts) {
        return jdbcTemplate.query(SELECT_BALANCES_FOR_UPDATE,
            Map.of("from", ts.toEpochMilli(), "limit", 0), ROW_MAPPER);
    }

    private void updateBalances(List<WalletEntry> balancesToUpdate) {
        for (WalletEntry entry : balancesToUpdate) {
            Map<String, ? extends Number> values = Map.of("ts", entry.getDatetime().toEpochMilli(),
                "bitcoins", entry.getAmount());
            jdbcTemplate.update(UPDATE_BALANCE, values);
        }
    }

    private void insertBalance(WalletEntry entry) {
        Map<String, ? extends Number> values = Map.of("ts", entry.getDatetime().toEpochMilli(),
            "bitcoins", entry.getAmount());
        jdbcTemplate.update(String.format(INSERT, BALANCE), values);
    }

}
