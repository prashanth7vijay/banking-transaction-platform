package com.platform.exceptions.port;

import java.util.List;
import java.util.UUID;

/**
 * The `exceptions` module's public read contract for other modules - so far
 * `dashboard` (via {@link ExceptionMetricsPort}) and now `customer360`, which
 * needs a customer's open cases without depending on
 * TransactionExceptionRepository directly.
 */
public interface ExceptionLookupPort {
    List<ExceptionSummary> listForTransactionIds(List<UUID> transactionIds);
}
