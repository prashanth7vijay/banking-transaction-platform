export type AccountType = 'CHECKING' | 'SAVINGS';
export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';

export interface Account {
  id: string;
  accountNumber: string;
  accountType: AccountType;
  balance: string;
  status: AccountStatus;
  createdAt: string;
}

export interface Beneficiary {
  id: string;
  beneficiaryAccountNumber: string;
  nickname: string;
  createdAt: string;
}

export interface CreateBeneficiaryPayload {
  beneficiaryAccountNumber: string;
  nickname: string;
}
