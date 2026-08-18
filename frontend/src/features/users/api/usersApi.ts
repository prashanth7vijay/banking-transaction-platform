import { apiClient } from '@/shared/lib/apiClient';
import type { ChangePasswordPayload, UpdateProfilePayload, UserProfile } from '@/features/users/types';

export async function getMe(): Promise<UserProfile> {
  const { data } = await apiClient.get<UserProfile>('/users/me');
  return data;
}

export async function updateMe(payload: UpdateProfilePayload): Promise<UserProfile> {
  const { data } = await apiClient.patch<UserProfile>('/users/me', payload);
  return data;
}

export async function changePassword(payload: ChangePasswordPayload): Promise<void> {
  await apiClient.post('/users/me/change-password', payload);
}
