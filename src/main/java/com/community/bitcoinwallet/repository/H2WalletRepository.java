package com.community.bitcoinwallet.repository;

import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.util.DateAndAmountUtils;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class H2WalletRepository implements WalletRepository {

    private static final long PRECISION = (long) Math.pow(10, 8);
    public static final RowMapper<WalletEntry> ROW_MAPPER = (rs, rowNum) ->
        new WalletEntry(Instant.ofEpochMilli(rs.getLong("ts")),
            DateAndAmountUtils.toBigDecimal((double) rs.getLong("bitcoins") +
                ((double) rs.getInt("b_cents")) / PRECISION));

    private static final String WALLET = "WALLET";
    private static final String BALANCE = "BALANCE";
    private static final String BALANCE_QUEUE = "BALANCE_UPDATE_QUEUE";

    private static final String SELECT_BALANCE_TIME_RANGE = "select ts,bitcoins,b_cents from BALANCE " +
        "where (ts > :from and ts < :to) or ts = :to";
    private static final String COUNT_BALANCES = "select count(*) c from BALANCE";
    private static final String SELECT_BALANCES_FOR_UPDATE = "select ts,bitcoins,b_cents from BALANCE " +
        "where (ts > :from) limit :limit";
    private static final String SELECT_MAX_BALANCE_BEFORE_TIME_RANGE = "select ts,bitcoins,b_cents from BALANCE " +
        "where (ts <= :from) " +
        "order by ts desc " +
        "limit 1";
    private static final String SELECT_LAST_BALANCE = "select ts, bitcoins, b_cents from BALANCE " +
        "order by ts desc " +
        "limit 1";
    private static final String SELECT_FIRST_BALANCE = "select ts, bitcoins, b_cents c from BALANCE " +
        "order by ts " +
        "limit 1";

    private static final String UPDATE_BALANCE = "update BALANCE set bitcoins=:bitcoins,b_cents=:b_cents where" +
        " ts=:ts";

    private static final String SELECT_NEXT_FROM_QUEUE = "select ts,bitcoins,b_cents from BALANCE_UPDATE_QUEUE " +
        "order by ts " +
        "limit 1";
    private static final String SELECT_ID_FROM_QUEUE = "select id from BALANCE_UPDATE_QUEUE " +
        "order by ts " +
        "limit 1";
    private static final String DELETE_FROM_QUEUE = "delete from BALANCE_UPDATE_QUEUE where id=:id";

    private static final String INSERT = "insert into %s(ts,bitcoins,b_cents) " +
        " values(:ts, :bitcoins, :b_cents)";
    private static final String CLEAR = "delete from %s where 1=1";

    boolean asyncBalanceCalculation;
    NamedParameterJdbcTemplate jdbcTemplate;
    ScheduledExecutorService executorService;
    int batchSelectLimit;

    public H2WalletRepository(boolean asyncBalanceCalculation,
                              NamedParameterJdbcTemplate jdbcTemplate,
                              long millisPeriod, int batchSelectLimit) {
        this.jdbcTemplate = jdbcTemplate;
        this.asyncBalanceCalculation = asyncBalanceCalculation;
        this.batchSelectLimit = batchSelectLimit;
        if (asyncBalanceCalculation) {
            this.executorService = Executors.newSingleThreadScheduledExecutor();
            executorService.scheduleAtFixedRate(this::updateBalancesFromQueue, 0,
                millisPeriod, TimeUnit.MILLISECONDS);
        } else {
            executorService = null;
        }
    }

    @Override
    @Transactional
    public void addEntry(WalletEntry entry) {
        Map<String, ? extends Number> values = Map.of("ts", entry.getDatetime().toEpochMilli(),
            "bitcoins", entry.getAmount().longValue(),
            "b_cents", (int) (entry.getAmount().remainder(BigDecimal.ONE).doubleValue() * PRECISION));
        jdbcTemplate.update(String.format(INSERT, WALLET), values);
        if (asyncBalanceCalculation) {
            jdbcTemplate.update(String.format(INSERT, BALANCE_QUEUE), values);
        } else {
            incrementBalancesFromEntry(entry);
        }
    }

    @Override
    public List<WalletEntry> getBalancesByHour(Instant fromExclusive, Instant toInclusive) {
        List<WalletEntry> res = new LinkedList<>(jdbcTemplate.query(SELECT_BALANCE_TIME_RANGE,
            Map.of("from", fromExclusive.toEpochMilli(),
                "to", toInclusive.toEpochMilli()), ROW_MAPPER));

        Instant instant = DateAndAmountUtils.atStartOfHour(fromExclusive);
        if (res.isEmpty() || res.get(0).getDatetime().isAfter(instant)) {
            List<WalletEntry> beforTimeRange = jdbcTemplate.query(SELECT_MAX_BALANCE_BEFORE_TIME_RANGE,
                Map.of("from", fromExclusive.toEpochMilli()), ROW_MAPPER);
            res.add(0, beforTimeRange.isEmpty() ?
                new WalletEntry(instant, DateAndAmountUtils.toBigDecimal("0.0")) :
                beforTimeRange.get(0));
        }
        return res;
    }

    @Override
    @Transactional
    public void clear() {
        jdbcTemplate.execute(String.format(CLEAR, WALLET), ps -> null);
        jdbcTemplate.execute(String.format(CLEAR, BALANCE), ps -> null);
        jdbcTemplate.execute(String.format(CLEAR, BALANCE_QUEUE), ps -> null);
    }

    @Transactional
    private void updateBalancesFromQueue() {
        List<WalletEntry> res = jdbcTemplate.query(SELECT_NEXT_FROM_QUEUE,
            Collections.emptyMap(), ROW_MAPPER);
        if (!res.isEmpty()) {
            incrementBalancesFromEntry(res.get(0));
            Long id = jdbcTemplate.queryForObject(SELECT_ID_FROM_QUEUE, Collections.emptyMap(),
                (rs, rowNum) -> rs.getLong("id"));
            jdbcTemplate.execute(DELETE_FROM_QUEUE, Map.of("id", id), ps -> null);
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
            Map.of("from", ts.toEpochMilli(), "limit", batchSelectLimit), ROW_MAPPER);
    }

    private void updateBalances(List<WalletEntry> balancesToUpdate) {
        for (WalletEntry entry : balancesToUpdate) {
            Map<String, ? extends Number> values = Map.of("ts", entry.getDatetime().toEpochMilli(),
                "bitcoins", entry.getAmount().longValue(),
                "b_cents", (int) (entry.getAmount().remainder(BigDecimal.ONE).doubleValue() * PRECISION));
            jdbcTemplate.update(UPDATE_BALANCE, values);
        }
    }

    private void insertBalance(WalletEntry entry) {
        Map<String, ? extends Number> values = Map.of("ts", entry.getDatetime().toEpochMilli(),
            "bitcoins", entry.getAmount().longValue(),
            "b_cents", (int) (entry.getAmount().remainder(BigDecimal.ONE).doubleValue() * PRECISION));
        jdbcTemplate.update(String.format(INSERT, BALANCE), values);
    }

    @PreDestroy
    public void shutDownExecutor() {
        if (asyncBalanceCalculation) {
            executorService.shutdownNow();
        }
    }

}
