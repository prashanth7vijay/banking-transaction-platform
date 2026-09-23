package com.platform.customer360.benchmark;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Slf4j
public class Customer360DatasetSeeder {

    private static final int BATCH_SIZE = 2_000;
    private static final String[] STATUSES = {"COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED", "PENDING_APPROVAL", "FAILED"};
    private static final String[] RISK_LEVELS = {"LOW", "LOW", "LOW", "MEDIUM", "HIGH", "CRITICAL"};
    private static final String[] EXCEPTION_STATUSES = {"OPEN", "ASSIGNED", "INVESTIGATING", "RESOLVED", "CLOSED"};
    private static final String[] EXCEPTION_PRIORITIES = {"LOW", "MEDIUM", "HIGH", "CRITICAL"};

    private final JdbcTemplate jdbc;
    private final Random random;

    public Customer360DatasetSeeder(JdbcTemplate jdbc, long seed) {
        this.jdbc = jdbc;
        this.random = new Random(seed);
    }

    /**
     * @return the id of the single "benchmark target" customer - the one the
     * seeding logic deliberately makes the heaviest transaction user in the
     * dataset, i.e. the customer for whom the old N+1 implementation was
     * slowest. This is the id the benchmark runner measures Customer 360
     * against.
     */
    public UUID seed(DatasetSize size) {
        log.info("Seeding {} dataset: {} customers, {} transactions, {} risk assessments, {} exceptions",
                size, size.customers(), size.transactions(), size.riskAssessments(), size.exceptions());

        UUID employeeRoleId = jdbc.queryForObject("SELECT id FROM users.roles WHERE name = 'EMPLOYEE'", UUID.class);

        List<UUID> customerIds = seedCustomers(size.customers());
        UUID assigneeId = seedAssigneeEmployee(employeeRoleId);
        List<UUID> accountIds = seedAccounts(customerIds);

        int heavyCount = Math.max(1, size.customers() / 20);
        UUID benchmarkTargetCustomerId = customerIds.get(0); // deterministically a heavy user

        List<UUID> transactionIds = seedTransactions(size.transactions(), customerIds, accountIds, heavyCount);
        seedRiskAssessments(size.riskAssessments(), transactionIds);
        seedExceptions(size.exceptions(), transactionIds, assigneeId);

        log.info("Seed complete. Benchmark target customer id = {}", benchmarkTargetCustomerId);
        return benchmarkTargetCustomerId;
    }

    private List<UUID> seedCustomers(int count) {
        List<UUID> ids = new ArrayList<>(count);
        List<Object[]> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = deterministicUuid();
            ids.add(id);
            Instant createdAt = Instant.now().minus(random.nextInt(700), ChronoUnit.DAYS);
            Timestamp ts = Timestamp.from(createdAt);
            rows.add(new Object[]{id, "bench-customer-" + i + "-" + id + "@example.test", "n/a",
                    "Bench" + i, "Customer", ts, ts});
        }
        jdbc.batchUpdate(
                "INSERT INTO users.users (id, email, password_hash, first_name, last_name, status, created_at, updated_at, version) " +
                        "VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, 0)",
                rows, BATCH_SIZE, (ps, row) -> {
                    ps.setObject(1, row[0]);
                    ps.setString(2, (String) row[1]);
                    ps.setString(3, (String) row[2]);
                    ps.setString(4, (String) row[3]);
                    ps.setString(5, (String) row[4]);
                    ps.setTimestamp(6, (Timestamp) row[5]);
                    ps.setTimestamp(7, (Timestamp) row[6]);
                });
        return ids;
    }

    private UUID seedAssigneeEmployee(UUID employeeRoleId) {
        UUID id = deterministicUuid();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("INSERT INTO users.users (id, email, password_hash, first_name, last_name, status, created_at, updated_at, version) " +
                        "VALUES (?, ?, 'n/a', 'Bench', 'Investigator', 'ACTIVE', ?, ?, 0)",
                id, "bench-investigator-" + id + "@example.test", now, now);
        jdbc.update("INSERT INTO users.user_roles (user_id, role_id) VALUES (?, ?)", id, employeeRoleId);
        return id;
    }

    private List<UUID> seedAccounts(List<UUID> customerIds) {
        List<UUID> accountIds = new ArrayList<>(customerIds.size());
        List<Object[]> rows = new ArrayList<>(customerIds.size());
        Timestamp createdAt = Timestamp.from(Instant.now().minus(700, ChronoUnit.DAYS));
        for (int i = 0; i < customerIds.size(); i++) {
            UUID accountId = deterministicUuid();
            accountIds.add(accountId);
            rows.add(new Object[]{accountId, customerIds.get(i), String.format("BENCH%014d", i),
                    BigDecimal.valueOf(1000 + random.nextInt(50_000)), createdAt});
        }
        jdbc.batchUpdate(
                "INSERT INTO accounts.accounts (id, user_id, account_number, account_type, balance, status, created_at, version) " +
                        "VALUES (?, ?, ?, 'CHECKING', ?, 'ACTIVE', ?, 0)",
                rows, BATCH_SIZE, (ps, row) -> {
                    ps.setObject(1, row[0]);
                    ps.setObject(2, row[1]);
                    ps.setString(3, (String) row[2]);
                    ps.setBigDecimal(4, (BigDecimal) row[3]);
                    ps.setTimestamp(5, (Timestamp) row[4]);
                });
        return accountIds;
    }

    private List<UUID> seedTransactions(int count, List<UUID> customerIds, List<UUID> accountIds, int heavyCount) {
        List<UUID> ids = new ArrayList<>(count);
        List<Object[]> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = deterministicUuid();
            ids.add(id);
            // 60% of transactions belong to the heavy-user cohort, 40% spread
            // uniformly across everyone else.
            int customerIndex = random.nextInt(100) < 60
                    ? random.nextInt(heavyCount)
                    : random.nextInt(customerIds.size());
            // Heavy users' transactions skew recent (within 90 days) so they land
            // in Customer 360's activity window; others spread over ~2 years.
            int maxAgeDays = customerIndex < heavyCount ? 90 : 730;
            Instant createdAt = Instant.now().minus(random.nextInt(maxAgeDays), ChronoUnit.DAYS)
                    .minus(random.nextInt(24), ChronoUnit.HOURS);
            rows.add(new Object[]{
                    id, accountIds.get(customerIndex), String.format("DEST%016d", random.nextInt(1_000_000)),
                    BigDecimal.valueOf(10 + random.nextInt(500_000) / 100.0),
                    STATUSES[random.nextInt(STATUSES.length)], "bench-" + id,
                    customerIds.get(customerIndex), Timestamp.from(createdAt)
            });
        }
        jdbc.batchUpdate(
                "INSERT INTO transactions.transactions " +
                        "(id, source_account_id, destination_account_number, amount, currency, status, type, " +
                        "idempotency_key, initiated_by_user_id, created_at) " +
                        "VALUES (?, ?, ?, ?, 'USD', ?, 'TRANSFER', ?, ?, ?)",
                rows, BATCH_SIZE, (ps, row) -> {
                    ps.setObject(1, row[0]);
                    ps.setObject(2, row[1]);
                    ps.setString(3, (String) row[2]);
                    ps.setBigDecimal(4, (BigDecimal) row[3]);
                    ps.setString(5, (String) row[4]);
                    ps.setString(6, (String) row[5]);
                    ps.setObject(7, row[6]);
                    ps.setTimestamp(8, (Timestamp) row[7]);
                });
        return ids;
    }

    private void seedRiskAssessments(int count, List<UUID> transactionIds) {
        List<Integer> indices = pickDistinctIndices(count, transactionIds.size());
        List<Object[]> rows = new ArrayList<>(indices.size());
        for (int idx : indices) {
            String level = RISK_LEVELS[random.nextInt(RISK_LEVELS.length)];
            rows.add(new Object[]{
                    deterministicUuid(), transactionIds.get(idx), level,
                    "CRITICAL".equals(level) && random.nextBoolean(),
                    Timestamp.from(Instant.now().minus(random.nextInt(90), ChronoUnit.DAYS))
            });
        }
        jdbc.batchUpdate(
                "INSERT INTO risk.risk_assessments (id, transaction_id, risk_level, reasons, blocked, assessed_at) " +
                        "VALUES (?, ?, ?, '[\"VELOCITY_LIMIT\", \"AMOUNT_THRESHOLD\"]'::jsonb, ?, ?)",
                rows, BATCH_SIZE, (ps, row) -> {
                    ps.setObject(1, row[0]);
                    ps.setObject(2, row[1]);
                    ps.setString(3, (String) row[2]);
                    ps.setBoolean(4, (Boolean) row[3]);
                    ps.setTimestamp(5, (Timestamp) row[4]);
                });
    }

    private void seedExceptions(int count, List<UUID> transactionIds, UUID assigneeId) {
        List<Integer> indices = pickDistinctIndices(count, transactionIds.size());
        List<Object[]> rows = new ArrayList<>(indices.size());
        for (int i = 0; i < indices.size(); i++) {
            Instant createdAt = Instant.now().minus(random.nextInt(90), ChronoUnit.DAYS);
            rows.add(new Object[]{
                    deterministicUuid(), transactionIds.get(indices.get(i)),
                    EXCEPTION_STATUSES[random.nextInt(EXCEPTION_STATUSES.length)],
                    EXCEPTION_PRIORITIES[random.nextInt(EXCEPTION_PRIORITIES.length)],
                    "Benchmark-generated exception " + i,
                    random.nextBoolean() ? assigneeId : null,
                    Timestamp.from(createdAt.plus(4, ChronoUnit.HOURS)),
                    Timestamp.from(createdAt)
            });
        }
        jdbc.batchUpdate(
                "INSERT INTO exceptions.transaction_exceptions " +
                        "(id, transaction_id, status, priority, reason, assigned_to_user_id, sla_due_at, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                rows, BATCH_SIZE, (ps, row) -> {
                    ps.setObject(1, row[0]);
                    ps.setObject(2, row[1]);
                    ps.setString(3, (String) row[2]);
                    ps.setString(4, (String) row[3]);
                    ps.setString(5, (String) row[4]);
                    if (row[5] != null) {
                        ps.setObject(6, row[5]);
                    } else {
                        ps.setNull(6, Types.OTHER);
                    }
                    ps.setTimestamp(7, (Timestamp) row[6]);
                    ps.setTimestamp(8, (Timestamp) row[7]);
                    ps.setTimestamp(9, (Timestamp) row[7]);
                });
    }

    private List<Integer> pickDistinctIndices(int count, int universeSize) {
        int n = Math.min(count, universeSize);
        int[] pool = new int[universeSize];
        for (int i = 0; i < universeSize; i++) pool[i] = i;
        List<Integer> picked = new ArrayList<>(n);
        int remaining = universeSize;
        for (int i = 0; i < n; i++) {
            int j = random.nextInt(remaining);
            picked.add(pool[j]);
            pool[j] = pool[remaining - 1];
            remaining--;
        }
        return picked;
    }

    private UUID deterministicUuid() {
        return new UUID(random.nextLong(), random.nextLong());
    }
}