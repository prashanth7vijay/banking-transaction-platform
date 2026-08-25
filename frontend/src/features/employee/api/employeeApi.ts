import { apiClient } from '@/shared/lib/apiClient';
import type { UserProfile } from '@/features/users/types';
import type { Account } from '@/features/accounts/types';
import type { CreateCustomerPayload, OpenAccountPayload, PageResponse } from '@/features/employee/types';

export async function searchCustomers(search?: string): Promise<UserProfile[]> {
  const { data } = await apiClient.get<UserProfile[]>('/employee/customers', {
    params: search ? { search } : undefined,
  });
  return data;
}

export async function searchCustomersPaged(search: string | undefined, page: number, size = 10): Promise<PageResponse<UserProfile>> {
  const { data } = await apiClient.get<PageResponse<UserProfile>>('/employee/customers', {
    params: { search: search || undefined, page, size },
  });
  return data;
}

export async function createCustomer(payload: CreateCustomerPayload): Promise<UserProfile> {
  const { data } = await apiClient.post<UserProfile>('/employee/customers', payload);
  return data;
}

export async function searchEmployees(search?: string): Promise<UserProfile[]> {
  const { data } = await apiClient.get<UserProfile[]>('/employee/employees', {
    params: search ? { search } : undefined,
  });
  return data;
}

export async function openAccount(payload: OpenAccountPayload): Promise<Account> {
  const { data } = await apiClient.post<Account>('/employee/accounts', payload);
  return data;
}