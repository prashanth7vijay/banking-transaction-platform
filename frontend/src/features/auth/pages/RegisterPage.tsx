import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Link, useNavigate } from 'react-router-dom';
import { register as registerApi } from '@/features/auth/api/authApi';
import { Button } from '@/shared/components/ui/Button';
import { Input } from '@/shared/components/ui/Input';
import { Alert } from '@/shared/components/ui/Alert';
import axios from 'axios';

const schema = z.object({
  firstName: z.string().min(1, 'Required'),
  lastName: z.string().min(1, 'Required'),
  email: z.string().email('Enter a valid email'),
  password: z
    .string()
    .min(8, 'At least 8 characters')
    .regex(/(?=.*[A-Za-z])(?=.*\d)/, 'Must include a letter and a digit'),
});

type FormValues = z.infer<typeof schema>;

export function RegisterPage() {
  const navigate = useNavigate();
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register: registerField,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const onSubmit = async (values: FormValues) => {
    setServerError(null);
    try {
      await registerApi(values);
      navigate('/login');
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 409) {
        setServerError('An account with this email already exists');
      } else {
        setServerError('Registration failed. Please try again.');
      }
    }
  };

  return (
    <div className="max-w-sm mx-auto">
      <h1 className="text-2xl font-semibold mb-6">Create account</h1>
      {serverError && <Alert>{serverError}</Alert>}
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div className="grid grid-cols-2 gap-3">
          <div>
            <Input placeholder="First name" {...registerField('firstName')} />
            {errors.firstName && <p className="text-sm text-red-500 mt-1">{errors.firstName.message}</p>}
          </div>
          <div>
            <Input placeholder="Last name" {...registerField('lastName')} />
            {errors.lastName && <p className="text-sm text-red-500 mt-1">{errors.lastName.message}</p>}
          </div>
        </div>
        <div>
          <Input placeholder="Email" type="email" {...registerField('email')} />
          {errors.email && <p className="text-sm text-red-500 mt-1">{errors.email.message}</p>}
        </div>
        <div>
          <Input placeholder="Password" type="password" {...registerField('password')} />
          {errors.password && <p className="text-sm text-red-500 mt-1">{errors.password.message}</p>}
        </div>
        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? 'Creating account...' : 'Create account'}
        </Button>
      </form>
      <p className="mt-4 text-sm text-muted-foreground">
        Already have an account? <Link to="/login" className="hover:underline">Log in</Link>
      </p>
    </div>
  );
}
