package com.platform.risk.config;

import com.platform.risk.domain.LimitPolicy;
import com.platform.risk.domain.LimitScope;
import com.platform.risk.domain.LimitType;
import com.platform.risk.repository.LimitPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Not `@Profile("dev")` like DataSeeder - these are real system configuration, not
 * demo data, so a fresh deployment in any profile gets sane, generous defaults
 * rather than either zero limits (nothing evaluated at all) or an empty policy
 * table silently blocking nothing. Deliberately permissive: this phase exists to
 * prove the risk-check machinery works end to end without changing what any
 * existing transfer flow can already do - an admin tightens these later via the
 * CRUD endpoints, this seeder never overwrites an existing policy.
 */
@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class RiskPolicySeeder implements CommandLineRunner {

    private final LimitPolicyRepository limitPolicyRepository;

    @Override
    public void run(String... args) {
        if (limitPolicyRepository.count() > 0) {
            return;
        }

        seed("Default per-transaction limit", LimitType.PER_TRANSACTION, new BigDecimal("50000.00"), null, null);
        seed("Default daily cumulative limit", LimitType.DAILY_CUMULATIVE, new BigDecimal("100000.00"), null, null);
        seed("Default velocity check", LimitType.VELOCITY_COUNT, null, 100, 10);

        log.info("Seeded default GLOBAL risk limit policies");
    }

    private void seed(String name, LimitType type, BigDecimal maxAmount, Integer maxCount, Integer windowMinutes) {
        LimitPolicy policy = new LimitPolicy();
        policy.setName(name);
        policy.setScope(LimitScope.GLOBAL);
        policy.setLimitType(type);
        policy.setMaxAmount(maxAmount);
        policy.setMaxCount(maxCount);
        policy.setWindowMinutes(windowMinutes);
        policy.setActive(true);
        limitPolicyRepository.save(policy);
    }
}
