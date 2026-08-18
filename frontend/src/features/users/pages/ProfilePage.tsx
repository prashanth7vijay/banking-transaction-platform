import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useAuth } from '@/features/auth/AuthContext';
import { updateMe, changePassword } from '@/features/users/api/usersApi';
import { Button } from '@/shared/components/ui/Button';
import { Input } from '@/shared/components/ui/Input';
import { Alert } from '@/shared/components/ui/Alert';

const profileSchema = z.object({
  firstName: z.string().min(1, 'Required'),
  lastName: z.string().min(1, 'Required'),
});
type ProfileFormValues = z.infer<typeof profileSchema>;

const passwordSchema = z.object({
  currentPassword: z.string().min(1, 'Required'),
  newPassword: z
    .string()
    .min(8, 'At least 8 characters')
    .regex(/(?=.*[A-Za-z])(?=.*\d)/, 'Must include a letter and a digit'),
});
type PasswordFormValues = z.infer<typeof passwordSchema>;

export function ProfilePage() {
  const { user, refreshUser } = useAuth();
  const [profileMessage, setProfileMessage] = useState<string | null>(null);
  const [passwordMessage, setPasswordMessage] = useState<{ type: 'error' | 'success'; text: string } | null>(null);

  const profileForm = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { firstName: user?.firstName ?? '', lastName: user?.lastName ?? '' },
  });

  const passwordForm = useForm<PasswordFormValues>({ resolver: zodResolver(passwordSchema) });

  if (!user) return null;

  const onSaveProfile = async (values: ProfileFormValues) => {
    setProfileMessage(null);
    await updateMe(values);
    await refreshUser();
    setProfileMessage('Profile updated');
  };

  const onChangePassword = async (values: PasswordFormValues) => {
    setPasswordMessage(null);
    try {
      await changePassword(values);
      passwordForm.reset();
      setPasswordMessage({ type: 'success', text: 'Password changed' });
    } catch {
      setPasswordMessage({ type: 'error', text: 'Current password is incorrect' });
    }
  };

  return (
    <div className="max-w-lg space-y-8">
      <div>
        <h1 className="text-2xl font-semibold">Profile</h1>
        <p className="text-sm text-muted-foreground">
          {user.email} · {user.roles.join(', ')}
        </p>
      </div>

      <section>
        <h2 className="text-lg font-medium mb-3">Details</h2>
        {profileMessage && <Alert variant="success">{profileMessage}</Alert>}
        <form onSubmit={profileForm.handleSubmit(onSaveProfile)} className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <Input placeholder="First name" {...profileForm.register('firstName')} />
            <Input placeholder="Last name" {...profileForm.register('lastName')} />
          </div>
          <Button type="submit" disabled={profileForm.formState.isSubmitting}>
            Save changes
          </Button>
        </form>
      </section>

      <section>
        <h2 className="text-lg font-medium mb-3">Change password</h2>
        {passwordMessage && <Alert variant={passwordMessage.type}>{passwordMessage.text}</Alert>}
        <form onSubmit={passwordForm.handleSubmit(onChangePassword)} className="space-y-3">
          <Input placeholder="Current password" type="password" {...passwordForm.register('currentPassword')} />
          <Input placeholder="New password" type="password" {...passwordForm.register('newPassword')} />
          {passwordForm.formState.errors.newPassword && (
            <p className="text-sm text-red-500">{passwordForm.formState.errors.newPassword.message}</p>
          )}
          <Button type="submit" disabled={passwordForm.formState.isSubmitting}>
            Update password
          </Button>
        </form>
      </section>
    </div>
  );
}
