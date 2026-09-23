package com.platform.customer360.benchmark;

public enum DatasetSize {
    SMALL(1_000, 20_000, 16_000, 2_000),
    MEDIUM(20_000, 300_000, 240_000, 30_000),
    LARGE(100_000, 1_000_000, 1_000_000, 100_000);

    private final int customers;
    private final int transactions;
    private final int riskAssessments;
    private final int exceptions;

    DatasetSize(int customers, int transactions, int riskAssessments, int exceptions) {
        this.customers = customers;
        this.transactions = transactions;
        this.riskAssessments = riskAssessments;
        this.exceptions = exceptions;
    }

    public int customers() { return customers; }
    public int transactions() { return transactions; }
    public int riskAssessments() { return riskAssessments; }
    public int exceptions() { return exceptions; }
}
