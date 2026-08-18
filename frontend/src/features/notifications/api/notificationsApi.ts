import { apiClient } from '@/shared/lib/apiClient';
import type { Notification } from '@/features/notifications/types';

export async function listMyNotifications(): Promise<Notification[]> {
  const { data } = await apiClient.get<Notification[]>('/notifications');
  return data;
}

export async function getUnreadCount(): Promise<number> {
  const { data } = await apiClient.get<{ count: number }>('/notifications/unread-count');
  return data.count;
}

export async function markNotificationAsRead(id: string): Promise<void> {
  await apiClient.post(`/notifications/${id}/read`);
}
