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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.community.bitcoinwallet.util.DateAndAmountUtils.atStartOfHour;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class H2WalletRepository implements WalletRepository {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX");

    public static final RowMapper<WalletEntry> ROW_MAPPER = (rs, rowNum) ->
        new WalletEntry(Instant.ofEpochMilli(rs.getLong("ts")),
            rs.getBigDecimal("bitcoins").setScale(8, RoundingMode.HALF_UP));

    public static final RowMapper<WalletEntry> ROW_MAPPER_WITH_DATE = (rs, rowNum) ->
        new WalletEntry(ZonedDateTime.parse(rs.getString("date_hour"), FORMATTER).toInstant(),
            rs.getBigDecimal("bitcoins").setScale(8, RoundingMode.HALF_UP));

    public static final RowMapper<WalletEntry> ROW_MAPPER_WITH_SUM = (rs, rowNum) ->
        new WalletEntry(null, rs.getBigDecimal("bitcoins").setScale(8, RoundingMode.HALF_UP));

    private static final String WALLET = "WALLET";
    private static final String BALANCE = "BALANCE";
    private static final String BALANCE_QUEUE = "BALANCE_UPDATE_QUEUE";

    // general requests
    private static final String TS_AT_START_OF_HOUR =
        "FORMATDATETIME(DATEADD('MILLISECOND', ts, DATE '1970-01-01'),'YYYY-MM-dd HH:00:00+00:00')";
    private static final String INSERT = "insert into %s(ts,bitcoins) " +
        " values(:ts, :bitcoins)";
    private static final String CLEAR = "delete from %s where 1=1";

    // sync requests
    private static final String SELECT_BALANCE_SUM = "select SUM(bitcoins) as bitcoins from WALLET " +
        "where ts < :ts " +
        "group by 1";
    private static final String SELECT_BALANCE_SUM_IN_RANGE = "select " +
        TS_AT_START_OF_HOUR + " as date_hour, SUM(bitcoins) as bitcoins from WALLET " +
        "where ts >= :from and ts < :to " +
        " group by " + TS_AT_START_OF_HOUR +
        " order by date_hour";

    // async requests
    private static final String SELECT_NEXT_FROM_QUEUE = "select ts,bitcoins from BALANCE_UPDATE_QUEUE " +
        "order by ts " +
        "limit 1";
    private static final String SELECT_ID_FROM_QUEUE = "select id from BALANCE_UPDATE_QUEUE " +
        "order by ts " +
        "limit 1";
    private static final String DELETE_FROM_QUEUE = "delete from BALANCE_UPDATE_QUEUE where id=:id";

    private static final String COUNT_BALANCES = "select count(*) c from BALANCE";
    private static final String SELECT_BALANCE_TIME_RANGE = "select ts,bitcoins from BALANCE " +
        "where (ts > :from and ts < :to) or ts = :to";
    private static final String SELECT_BALANCES_FOR_UPDATE = "select ts,bitcoins from BALANCE " +
        "where (ts > :from) limit :limit";
    private static final String SELECT_MAX_BALANCE_BEFORE_TIME_RANGE = "select ts,bitcoins from BALANCE " +
        "where (ts <= :from) " +
        "order by ts desc " +
        "limit 1";
    private static final String SELECT_LAST_BALANCE = "select ts, bitcoins from BALANCE " +
        "order by ts desc " +
        "limit 1";
    private static final String SELECT_FIRST_BALANCE = "select ts, bitcoins c from BALANCE " +
        "order by ts " +
        "limit 1";


    private static final String UPDATE_BALANCE = "update BALANCE set bitcoins=:bitcoins where" +
        " ts=:ts";


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
        Map<String, Object> values = Map.of("ts", entry.getDatetime().toEpochMilli(),
            "bitcoins", entry.getAmount());
        jdbcTemplate.update(String.format(INSERT, WALLET), values);
        if (asyncBalanceCalculation) {
            jdbcTemplate.update(String.format(INSERT, BALANCE_QUEUE), values);
        }
    }

    @Override
    public List<WalletEntry> getBalancesByHour(Instant fromExclusive, Instant toInclusive) {
        return asyncBalanceCalculation ? getBalancesByHourAsync(fromExclusive, toInclusive) :
            getBalancesByHourSync(fromExclusive, toInclusive);
    }

    private List<WalletEntry> getBalancesByHourSync(Instant from, Instant to) {
        Instant fromAtStart = atStartOfHour(from);
        Instant toStart = atStartOfHour(to);
        WalletEntry beforeFrom = getSumBeforeFrom(fromAtStart);
        BigDecimal[] incrementHolder = new BigDecimal[]{beforeFrom.getAmount()};
        List<WalletEntry> balanceByHour = getSumInRangeByHour(fromAtStart, toStart)
            .stream()
            .map(e -> {
                BigDecimal increment = incrementHolder[0];
                BigDecimal amount = e.getAmount().add(increment);
                incrementHolder[0] = increment.add(e.getAmount());
                return new WalletEntry(e.getDatetime().plus(1, ChronoUnit.HOURS), amount);
            })
            .collect(Collectors.toCollection(LinkedList::new));
        balanceByHour.add(0, beforeFrom);
        return balanceByHour;
    }

    private List<WalletEntry> getSumInRangeByHour(Instant fromAtStart, Instant toAtStart) {
        return jdbcTemplate.query(SELECT_BALANCE_SUM_IN_RANGE,
            Map.of("from", fromAtStart.toEpochMilli(),
                "to", toAtStart.toEpochMilli()), ROW_MAPPER_WITH_DATE);
    }

    private WalletEntry getSumBeforeFrom(Instant fromAtStart) {
        List<WalletEntry> ts = jdbcTemplate.query(SELECT_BALANCE_SUM,
            Map.of("ts", fromAtStart.toEpochMilli()), ROW_MAPPER_WITH_SUM);
        return ts.stream().findFirst()
            .map(w -> new WalletEntry(fromAtStart, w.getAmount()))
            .orElse(new WalletEntry(fromAtStart, DateAndAmountUtils.toBigDecimal(0.0)));
    }

    private List<WalletEntry> getBalancesByHourAsync(Instant fromExclusive, Instant toInclusive) {
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
        jdbcTemplate.update(String.format(CLEAR, WALLET), Collections.emptyMap());
        jdbcTemplate.update(String.format(CLEAR, BALANCE), Collections.emptyMap());
        jdbcTemplate.update(String.format(CLEAR, BALANCE_QUEUE), Collections.emptyMap());
    }

    @Transactional
    protected void updateBalancesFromQueue() {
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
            Map.of("from", ts.toEpochMilli(), "limit", batchSelectLimit), ROW_MAPPER);
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

    @PreDestroy
    public void shutDownExecutor() {
        if (asyncBalanceCalculation) {
            executorService.shutdownNow();
        }
    }

}
