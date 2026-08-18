export interface CustomerProfile {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  status: string;
  customerSince: string;
}

export interface CustomerAccount {
  id: string;
  accountNumber: string;
  balance: string;
  status: string;
}

export interface CustomerTransactionItem {
  id: string;
  amount: string;
  currency: string;
  status: string;
  destinationAccountNumber: string;
  createdAt: string;
}

export interface CustomerTransactionActivity {
  recentTransactions: CustomerTransactionItem[];
  last30DayCount: number;
  last30DayValue: string;
  averageTransactionAmount: string | null;
  pendingCount: number;
  failedCount: number;
  highRiskCount: number;
}

export interface CustomerRiskProfile {
  mostRecentRiskLevel: string | null;
  assessmentCountByLevel: Record<string, number>;
  distinctRiskReasons: string[];
}

export interface CustomerExceptionItem {
  id: string;
  transactionId: string;
  status: string;
  priority: string;
  reason: string;
  assignedToName: string | null;
  createdAt: string;
}

export interface CustomerTimelineEntry {
  occurredAt: string;
  description: string;
}

export interface Customer360 {
  profile: CustomerProfile;
  accounts: CustomerAccount[];
  transactionActivity: CustomerTransactionActivity;
  risk: CustomerRiskProfile;
  openExceptions: CustomerExceptionItem[];
  timeline: CustomerTimelineEntry[];
}
