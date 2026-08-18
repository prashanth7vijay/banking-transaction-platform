export interface AdminUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  status: 'ACTIVE' | 'LOCKED' | 'DISABLED';
  roles: string[];
  createdAt: string;
}

export interface AdminMetrics {
  usersByRole: Record<string, number>;
  totalAccounts: number;
  totalBalance: string;
  transactionsByStatus: Record<string, number>;
}

export interface BalanceMismatch {
  accountId: string;
  ledgerBalance: string;
  actualBalance: string;
  difference: string;
}

export interface ReconciliationCheckResult {
  accountsChecked: number;
  mismatches: BalanceMismatch[];
}
