import { apiClient } from '@/shared/lib/apiClient';
import type { Customer360 } from '@/features/customer360/types';

export async function getCustomer360(customerId: string): Promise<Customer360> {
  const { data } = await apiClient.get<Customer360>(`/employee/customers/${customerId}/360`);
  return data;
}
