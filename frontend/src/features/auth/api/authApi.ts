import axios from 'axios';
import { apiClient } from '@/shared/lib/apiClient';
import type { LoginPayload, RegisterPayload, ResetPasswordPayload, TokenResponse } from '@/features/auth/types';

export async function register(payload: RegisterPayload): Promise<void> {
  await apiClient.post('/auth/register', payload);
}

export async function login(payload: LoginPayload): Promise<TokenResponse> {
  const { data } = await apiClient.post<TokenResponse>('/auth/login', payload);
  return data;
}

// Uses a bare axios call (not apiClient) so it never triggers the 401-refresh
// interceptor loop - this IS the refresh call.
export async function refresh(): Promise<TokenResponse> {
  const { data } = await axios.post<TokenResponse>('/api/v1/auth/refresh', {}, { withCredentials: true });
  return data;
}

export async function logout(): Promise<void> {
  await apiClient.post('/auth/logout');
}

export async function forgotPassword(email: string): Promise<void> {
  await apiClient.post('/auth/forgot-password', { email });
}

export async function resetPassword(payload: ResetPasswordPayload): Promise<void> {
  await apiClient.post('/auth/reset-password', payload);
}
