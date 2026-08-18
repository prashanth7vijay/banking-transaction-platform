export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export interface RegisterPayload {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface ResetPasswordPayload {
  token: string;
  newPassword: string;
}
