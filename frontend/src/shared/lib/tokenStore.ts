/**
 * Access tokens live in memory only (never localStorage) - the refresh token is an
 * httpOnly cookie the browser manages automatically. This module is a plain
 * singleton (not React state) so the Axios interceptor can read/write it without
 * needing to be inside a component.
 */
let currentAccessToken: string | null = null;

export const tokenStore = {
  get(): string | null {
    return currentAccessToken;
  },
  set(token: string | null): void {
    currentAccessToken = token;
  },
};
