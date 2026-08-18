import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import { tokenStore } from '@/shared/lib/tokenStore';
import * as authApi from '@/features/auth/api/authApi';
import * as usersApi from '@/features/users/api/usersApi';
import type { UserProfile } from '@/features/users/types';

interface AuthContextValue {
  user: UserProfile | null;
  isLoading: boolean;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const refreshUser = useCallback(async () => {
    const profile = await usersApi.getMe();
    setUser(profile);
  }, []);

  // On app load there is no access token in memory yet (a hard refresh clears it),
  // but the httpOnly refresh cookie may still be valid - attempt a silent refresh
  // once so an existing session survives a page reload.
  useEffect(() => {
    (async () => {
      try {
        const tokenResponse = await authApi.refresh();
        tokenStore.set(tokenResponse.accessToken);
        await refreshUser();
      } catch {
        tokenStore.set(null);
        setUser(null);
      } finally {
        setIsLoading(false);
      }
    })();
  }, [refreshUser]);

  const login = useCallback(
    async (email: string, password: string) => {
      const tokenResponse = await authApi.login({ email, password });
      tokenStore.set(tokenResponse.accessToken);
      await refreshUser();
    },
    [refreshUser]
  );

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      tokenStore.set(null);
      setUser(null);
    }
  }, []);

  return (
    <AuthContext.Provider
      value={{ user, isLoading, isAuthenticated: user !== null, login, logout, refreshUser }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
