import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '@/features/auth/AuthContext';
import { Button } from '@/shared/components/ui/Button';
import { Input } from '@/shared/components/ui/Input';
import { Alert } from '@/shared/components/ui/Alert';

const schema = z.object({
  email: z.string().email('Enter a valid email'),
  password: z.string().min(1, 'Password is required'),
});

type FormValues = z.infer<typeof schema>;

export function LoginPage() {
  const { login } = useAuth();
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
      await login(values.email, values.password);
      navigate('/dashboard');
    } catch {
      setServerError('Invalid email or password');
    }
  };

  return (
    <div className="max-w-sm mx-auto">
      <h1 className="text-2xl font-semibold mb-6">Log in</h1>
      {serverError && <Alert>{serverError}</Alert>}
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <Input placeholder="Email" type="email" {...register('email')} />
          {errors.email && <p className="text-sm text-red-500 mt-1">{errors.email.message}</p>}
        </div>
        <div>
          <Input placeholder="Password" type="password" {...register('password')} />
          {errors.password && <p className="text-sm text-red-500 mt-1">{errors.password.message}</p>}
        </div>
        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? 'Logging in...' : 'Log in'}
        </Button>
      </form>
      <div className="flex justify-between mt-4 text-sm text-muted-foreground">
        <Link to="/forgot-password" className="hover:underline">Forgot password?</Link>
        <Link to="/register" className="hover:underline">Create account</Link>
      </div>
    </div>
  );
}
