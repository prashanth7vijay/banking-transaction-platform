import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { tokenStore } from '@/shared/lib/tokenStore';

/**
 * Shared Axios instance for all authenticated feature calls. Attaches the in-memory
 * access token on every request and, on a single 401, attempts one silent refresh
 * (using the httpOnly refresh cookie sent automatically via withCredentials) before
 * retrying the original request once. If refresh also fails, the caller's normal
 * error handling takes over (AuthContext redirects to /login).
 */
export const apiClient = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
});

apiClient.interceptors.request.use((config) => {
  const token = tokenStore.get();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

interface RetriableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean;
}

let refreshPromise: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = axios
      .post<{ accessToken: string }>('/api/v1/auth/refresh', {}, { withCredentials: true })
      .then((res) => {
        tokenStore.set(res.data.accessToken);
        return res.data.accessToken;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as RetriableConfig | undefined;

    if (error.response?.status === 401 && originalRequest && !originalRequest._retried) {
      originalRequest._retried = true;
      try {
        const newToken = await refreshAccessToken();
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        tokenStore.set(null);
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);
