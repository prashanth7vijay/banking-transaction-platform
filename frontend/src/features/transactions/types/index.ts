export type TransactionStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'PROCESSING'
  | 'COMPLETED'
  | 'REJECTED'
  | 'FAILED'
  | 'CANCELLED';
export type TransactionType = 'TRANSFER';
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface Transaction {
  id: string;
  sourceAccountId: string;
  destinationAccountNumber: string;
  amount: string;
  currency: string;
  status: TransactionStatus;
  type: TransactionType;
  note: string | null;
  createdAt: string;
  approvedAt: string | null;
  riskLevel: RiskLevel | null;
}

export interface CreateTransferPayload {
  sourceAccountId: string;
  destinationAccountNumber: string;
  amount: number;
  idempotencyKey: string;
  note?: string;
}

export interface TransactionTimelineEntry {
  fromStatus: TransactionStatus | null;
  toStatus: TransactionStatus;
  actorUserId: string;
  occurredAt: string;
}

export interface TransactionParty {
  id: string;
  firstName: string | null;
  email: string;
}

export interface TransactionAccountInfo {
  id: string;
  accountNumber: string;
  balance: string;
  status: string;
  owner: TransactionParty | null;
}

export interface TransactionRiskInfo {
  riskLevel: RiskLevel;
  reasons: string[];
  blocked: boolean;
  assessedAt: string;
}

export interface TransactionApprovalInfo {
  approvedBy: TransactionParty | null;
  approvedAt: string;
  decision: 'APPROVED' | 'REJECTED' | null;
}

export interface TransactionLedgerLine {
  accountId: string | null;
  accountNumber: string | null;
  direction: 'DEBIT' | 'CREDIT';
  amount: string;
  currency: string;
  postedAt: string;
}

export interface Transaction360 {
  transaction: Transaction;
  customer: TransactionParty | null;
  sourceAccount: TransactionAccountInfo;
  destinationAccount: TransactionAccountInfo | null;
  risk: TransactionRiskInfo | null;
  approval: TransactionApprovalInfo | null;
  ledgerLines: TransactionLedgerLine[];
  timeline: TransactionTimelineEntry[];
}

export type SlaStatus = 'WITHIN' | 'AT_RISK' | 'BREACHED';

export interface ApprovalWorkbenchItem {
  transactionId: string;
  amount: string;
  currency: string;
  destinationAccountNumber: string;
  customer: TransactionParty | null;
  riskLevel: RiskLevel | null;
  riskReasons: string[];
  whyApprovalRequired: string[];
  customerAverageAmount: string | null;
  amountToAverageRatio: number | null;
  createdAt: string;
  slaDueAt: string | null;
  slaStatus: SlaStatus;
  note: string | null;
}

export interface ApprovalSlaSummary {
  within: number;
  atRisk: number;
  breached: number;
  total: number;
}
