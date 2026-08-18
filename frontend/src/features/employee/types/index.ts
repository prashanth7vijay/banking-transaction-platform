export interface OpenAccountPayload {
  customerUserId: string;
  accountType: 'CHECKING' | 'SAVINGS';
  openingBalance: number;
}
