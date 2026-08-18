import { apiClient } from '@/shared/lib/apiClient';
import type { AdminMetrics, AdminUser, ReconciliationCheckResult } from '@/features/admin/types';

export async function searchUsers(search?: string): Promise<AdminUser[]> {
  const { data } = await apiClient.get<AdminUser[]>('/admin/users', {
    params: search ? { search } : undefined,
  });
  return data;
}

export async function updateUserStatus(id: string, status: string): Promise<AdminUser> {
  const { data } = await apiClient.patch<AdminUser>(`/admin/users/${id}/status`, { status });
  return data;
}

export async function updateUserRoles(id: string, roles: string[]): Promise<AdminUser> {
  const { data } = await apiClient.patch<AdminUser>(`/admin/users/${id}/roles`, { roles });
  return data;
}

export async function getAdminMetrics(): Promise<AdminMetrics> {
  const { data } = await apiClient.get<AdminMetrics>('/admin/metrics');
  return data;
}

export async function getReconciliationCheck(): Promise<ReconciliationCheckResult> {
  const { data } = await apiClient.get<ReconciliationCheckResult>('/admin/ledger/reconciliation-check');
  return data;
}
