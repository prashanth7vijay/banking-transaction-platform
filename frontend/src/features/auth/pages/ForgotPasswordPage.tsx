import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link } from 'react-router-dom';
import { forgotPassword } from '@/features/auth/api/authApi';
import { Button } from '@/shared/components/ui/Button';
import { Input } from '@/shared/components/ui/Input';
import { Alert } from '@/shared/components/ui/Alert';

const schema = z.object({
  email: z.string().email('Enter a valid email'),
});

type FormValues = z.infer<typeof schema>;

export function ForgotPasswordPage() {
  const [submitted, setSubmitted] = useState(false);
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (values: FormValues) => {
    await forgotPassword(values.email);
    setSubmitted(true);
  };

  if (submitted) {
    return (
      <div className="max-w-sm mx-auto">
        <Alert variant="success">
          If an account exists for that email, a reset link has been sent. Check MailHog at
          localhost:8025.
        </Alert>
        <Link to="/login" className="text-sm hover:underline">Back to login</Link>
      </div>
    );
  }

  return (
    <div className="max-w-sm mx-auto">
      <h1 className="text-2xl font-semibold mb-6">Forgot password</h1>
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <Input placeholder="Email" type="email" {...register('email')} />
          {errors.email && <p className="text-sm text-red-500 mt-1">{errors.email.message}</p>}
        </div>
        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? 'Sending...' : 'Send reset link'}
        </Button>
      </form>
    </div>
  );
}
