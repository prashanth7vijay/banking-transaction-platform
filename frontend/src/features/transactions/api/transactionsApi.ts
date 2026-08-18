import { apiClient } from '@/shared/lib/apiClient';
import type {
  ApprovalSlaSummary,
  ApprovalWorkbenchItem,
  CreateTransferPayload,
  Transaction,
  Transaction360,
} from '@/features/transactions/types';

export async function createTransfer(payload: CreateTransferPayload): Promise<Transaction> {
  const { data } = await apiClient.post<Transaction>('/transactions/transfer', payload);
  return data;
}

export async function listMyTransactions(): Promise<Transaction[]> {
  const { data } = await apiClient.get<Transaction[]>('/transactions/mine');
  return data;
}

export async function listPendingTransactions(): Promise<Transaction[]> {
  const { data } = await apiClient.get<Transaction[]>('/transactions/pending');
  return data;
}

export async function listApprovalWorkbench(): Promise<ApprovalWorkbenchItem[]> {
  const { data } = await apiClient.get<ApprovalWorkbenchItem[]>('/transactions/pending/workbench');
  return data;
}

export async function getApprovalSlaSummary(): Promise<ApprovalSlaSummary> {
  const { data } = await apiClient.get<ApprovalSlaSummary>('/transactions/pending/sla-summary');
  return data;
}

export async function listTransactionsByStatus(status: string): Promise<Transaction[]> {
  const { data } = await apiClient.get<Transaction[]>('/transactions/by-status', { params: { status } });
  return data;
}

export async function approveTransaction(id: string): Promise<Transaction> {
  const { data } = await apiClient.post<Transaction>(`/transactions/${id}/approve`);
  return data;
}

export async function rejectTransaction(id: string, reason?: string): Promise<Transaction> {
  const { data } = await apiClient.post<Transaction>(`/transactions/${id}/reject`, { reason });
  return data;
}

export async function getTransaction360(id: string): Promise<Transaction360> {
  const { data } = await apiClient.get<Transaction360>(`/transactions/${id}/360`);
  return data;
}
