import { apiClient } from '@/shared/lib/apiClient';
import type { CommandCenterSummary } from '@/features/dashboard/types';

export async function getCommandCenterSummary(): Promise<CommandCenterSummary> {
  const { data } = await apiClient.get<CommandCenterSummary>('/dashboard/command-center');
  return data;
}
