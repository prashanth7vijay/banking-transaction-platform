import { apiClient } from '@/shared/lib/apiClient';
import type { AuditLog, AuditLogFilters, PageResponse } from '@/features/audit/types';

export async function searchAuditLogs(
  filters: AuditLogFilters,
  page: number,
  size: number
): Promise<PageResponse<AuditLog>> {
  const params: Record<string, string | number> = { page, size };
  if (filters.actorUserId) params.actorUserId = filters.actorUserId;
  if (filters.action) params.action = filters.action;
  if (filters.entityType) params.entityType = filters.entityType;

  const { data } = await apiClient.get<PageResponse<AuditLog>>('/audit/logs', { params });
  return data;
}
