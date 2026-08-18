export interface UserProfile {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  status: 'ACTIVE' | 'LOCKED' | 'DISABLED';
  roles: string[];
  createdAt: string;
}

export interface UpdateProfilePayload {
  firstName: string;
  lastName: string;
}

export interface ChangePasswordPayload {
  currentPassword: string;
  newPassword: string;
}
