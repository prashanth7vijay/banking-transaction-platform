import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate } from 'react-router-dom';
import { resetPassword } from '@/features/auth/api/authApi';
import { Button } from '@/shared/components/ui/Button';
import { Input } from '@/shared/components/ui/Input';
import { Alert } from '@/shared/components/ui/Alert';

const schema = z.object({
  token: z.string().min(1, 'Paste the token from the reset email'),
  newPassword: z
    .string()
    .min(8, 'At least 8 characters')
    .regex(/(?=.*[A-Za-z])(?=.*\d)/, 'Must include a letter and a digit'),
});

type FormValues = z.infer<typeof schema>;

export function ResetPasswordPage() {
  const navigate = useNavigate();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (values: FormValues) => {
    setServerError(null);
    try {
      await resetPassword(values);
      navigate('/login');
    } catch {
      setServerError('That reset token is invalid or has expired');
    }
  };

  return (
    <div className="max-w-sm mx-auto">
      <h1 className="text-2xl font-semibold mb-6">Reset password</h1>
      {serverError && <Alert>{serverError}</Alert>}
      <p className="text-sm text-muted-foreground mb-4">
        Check MailHog (localhost:8025) for your reset token.
      </p>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <Input placeholder="Reset token" {...register('token')} />
          {errors.token && <p className="text-sm text-red-500 mt-1">{errors.token.message}</p>}
        </div>
        <div>
          <Input placeholder="New password" type="password" {...register('newPassword')} />
          {errors.newPassword && <p className="text-sm text-red-500 mt-1">{errors.newPassword.message}</p>}
        </div>
        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? 'Resetting...' : 'Reset password'}
        </Button>
      </form>
    </div>
  );
}
