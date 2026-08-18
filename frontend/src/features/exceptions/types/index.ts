export type ExceptionStatus = 'OPEN' | 'ASSIGNED' | 'INVESTIGATING' | 'ACTION_REQUIRED' | 'RESOLVED' | 'CLOSED';
export type ExceptionPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type ExceptionNoteType = 'NOTE' | 'STATUS_CHANGE' | 'ASSIGNMENT' | 'ACTION_TAKEN';
export type SlaStatus = 'WITHIN' | 'AT_RISK' | 'BREACHED' | 'MET';

export interface ExceptionParty {
  id: string;
  firstName: string | null;
  email: string;
}

export interface TransactionException {
  id: string;
  transactionId: string;
  amount: string;
  currency: string;
  status: ExceptionStatus;
  priority: ExceptionPriority;
  reason: string;
  assignedTo: ExceptionParty | null;
  createdAt: string;
  updatedAt: string;
  resolvedAt: string | null;
  slaDueAt: string;
  slaStatus: SlaStatus;
}

export interface ExceptionNote {
  id: string;
  author: ExceptionParty | null;
  noteType: ExceptionNoteType;
  content: string;
  createdAt: string;
}

export interface LinkedTransaction {
  id: string;
  amount: string;
  currency: string;
  status: string;
  destinationAccountNumber: string;
  createdAt: string;
}

export interface ExceptionDetail {
  exception: TransactionException;
  notes: ExceptionNote[];
  transaction: LinkedTransaction;
}
