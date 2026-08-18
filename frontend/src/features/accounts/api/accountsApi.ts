import { apiClient } from '@/shared/lib/apiClient';
import type { Account, Beneficiary, CreateBeneficiaryPayload } from '@/features/accounts/types';

export async function getMyAccounts(): Promise<Account[]> {
  const { data } = await apiClient.get<Account[]>('/accounts');
  return data;
}

export async function getAccount(id: string): Promise<Account> {
  const { data } = await apiClient.get<Account>(`/accounts/${id}`);
  return data;
}

export async function listBeneficiaries(): Promise<Beneficiary[]> {
  const { data } = await apiClient.get<Beneficiary[]>('/beneficiaries');
  return data;
}

export async function addBeneficiary(payload: CreateBeneficiaryPayload): Promise<Beneficiary> {
  const { data } = await apiClient.post<Beneficiary>('/beneficiaries', payload);
  return data;
}

export async function removeBeneficiary(id: string): Promise<void> {
  await apiClient.delete(`/beneficiaries/${id}`);
}
