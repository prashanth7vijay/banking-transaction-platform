export interface TransactionMetrics {
  totalToday: number;
  totalValueToday: string;
  completedToday: number;
  failedToday: number;
  pendingApprovalCount: number;
  highRiskPendingCount: number;
  successRateToday: number | null;
  averageApprovalMinutes: number | null;
  slaComplianceRate: number | null;
  slaWithinCount: number;
  slaAtRiskCount: number;
  slaBreachedCount: number;
}

export interface ExceptionMetrics {
  openCount: number;
  slaWithinCount: number;
  slaAtRiskCount: number;
  slaBreachedCount: number;
  averageResolutionMinutes: number | null;
}

export interface CommandCenterSummary {
  transactions: TransactionMetrics;
  exceptions: ExceptionMetrics;
}
