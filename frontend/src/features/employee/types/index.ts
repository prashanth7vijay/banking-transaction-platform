export interface OpenAccountPayload {
  customerUserId: string;
  accountType: 'CHECKING' | 'SAVINGS';
  openingBalance: number;
}

export interface CreateCustomerPayload {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}