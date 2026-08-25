export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface ResetPasswordPayload {
  token: string;
  newPassword: string;
}
