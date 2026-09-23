package com.platform.customer360.benchmark;

import com.platform.customer360.service.Customer360Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

@Slf4j
@Component
@Profile("benchmark")
@RequiredArgsConstructor
public class Customer360BenchmarkRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final Customer360Service customer360Service;
    private final EntityManagerFactory entityManagerFactory;

    @Value("${benchmark.seed-data:false}")
    private boolean seedData;
    @Value("${benchmark.dataset-size:SMALL}")
    private DatasetSize datasetSize;
    @Value("${benchmark.seed:42}")
    private long seed;
    @Value("${benchmark.target-customer-id:}")
    private String targetCustomerIdOverride;
    @Value("${benchmark.warmup-iterations:20}")
    private int warmupIterations;
    @Value("${benchmark.iterations:200}")
    private int iterations;
    @Value("${benchmark.label:unlabeled}")
    private String label;
    @Value("${benchmark.output-file:performance/result.json}")
    private String outputFile;

    @Override
    public void run(String... args) throws Exception {
        UUID targetCustomerId;
        if (seedData) {
            targetCustomerId = new Customer360DatasetSeeder(jdbcTemplate, seed).seed(datasetSize);
        } else if (!targetCustomerIdOverride.isBlank()) {
            targetCustomerId = UUID.fromString(targetCustomerIdOverride);
        } else {
            throw new IllegalStateException(
                    "Either set benchmark.seed-data=true (to seed and use the new benchmark customer) " +
                            "or benchmark.target-customer-id=<uuid> (to reuse a previously seeded one). " +
                            "The seeder logs the target customer id on every seed run - reuse that id for the " +
                            "second (optimized) measurement so both runs hit the exact same data.");
        }

        log.info("Benchmark target customer: {}", targetCustomerId);

        int errors = 0;
        // Warm up JIT/connection pool/query plan cache before measuring.
        for (int i = 0; i < warmupIterations; i++) {
            try {
                customer360Service.getSummary(targetCustomerId);
            } catch (Exception e) {
                log.warn("Warmup iteration failed", e);
            }
        }

        long[] latenciesMillis = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            try {
                customer360Service.getSummary(targetCustomerId);
            } catch (Exception e) {
                errors++;
                log.warn("Measured iteration failed", e);
            }
            latenciesMillis[i] = (System.nanoTime() - start) / 1_000_000;
        }

        // One additional, separately-measured call purely to capture the
        // Hibernate statement count for a single Customer 360 request -
        // see docs/customer360-performance.md for why wall-clock latency
        // alone isn't a reliable proxy for query count.
        Statistics statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        customer360Service.getSummary(targetCustomerId);
        long dbOperationsForOneRequest = statistics.getQueryExecutionCount();
        statistics.setStatisticsEnabled(wasEnabled);

        writeResult(targetCustomerId, latenciesMillis, errors, dbOperationsForOneRequest);
    }

    private void writeResult(UUID targetCustomerId, long[] latenciesMillis, int errors, long dbOperationsForOneRequest)
            throws IOException {
        long[] sorted = latenciesMillis.clone();
        Arrays.sort(sorted);
        double totalSeconds = Arrays.stream(latenciesMillis).sum() / 1000.0;
        double throughput = totalSeconds > 0 ? iterations / totalSeconds : 0;

        String json = """
                {
                  "label": "%s",
                  "generatedAt": "%s",
                  "datasetSize": "%s",
                  "seed": %d,
                  "targetCustomerId": "%s",
                  "iterations": %d,
                  "warmupIterations": %d,
                  "errors": %d,
                  "errorRatePercent": %.4f,
                  "dbOperationsPerRequest": %d,
                  "latencyMillis": {
                    "p50": %d,
                    "p95": %d,
                    "p99": %d,
                    "min": %d,
                    "max": %d
                  },
                  "throughputRequestsPerSecond": %.2f
                }
                """.formatted(
                label, Instant.now(), seedData ? datasetSize.name() : "REUSED_EXISTING", seed, targetCustomerId,
                iterations, warmupIterations, errors, 100.0 * errors / iterations, dbOperationsForOneRequest,
                percentile(sorted, 50), percentile(sorted, 95), percentile(sorted, 99),
                sorted.length == 0 ? 0 : sorted[0], sorted.length == 0 ? 0 : sorted[sorted.length - 1],
                throughput
        );

        Path path = Path.of(outputFile);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, json);
        log.info("Wrote benchmark result to {}\n{}", path.toAbsolutePath(), json);
    }

    private long percentile(long[] sortedLatencies, int pct) {
        if (sortedLatencies.length == 0) return 0;
        int index = (int) Math.ceil(pct / 100.0 * sortedLatencies.length) - 1;
        index = Math.max(0, Math.min(index, sortedLatencies.length - 1));
        return sortedLatencies[index];
    }
}