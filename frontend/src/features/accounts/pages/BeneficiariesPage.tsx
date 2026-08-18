import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Trash2 } from 'lucide-react';
import { addBeneficiary, listBeneficiaries, removeBeneficiary } from '@/features/accounts/api/accountsApi';
import { Button } from '@/shared/components/ui/Button';
import { Input } from '@/shared/components/ui/Input';
import { Alert } from '@/shared/components/ui/Alert';
import axios from 'axios';

const schema = z.object({
  beneficiaryAccountNumber: z.string().regex(/^\d{10}$/, 'Must be exactly 10 digits'),
  nickname: z.string().min(1, 'Required').max(100),
});

type FormValues = z.infer<typeof schema>;

export function BeneficiariesPage() {
  const queryClient = useQueryClient();
  const [serverError, setServerError] = useState<string | null>(null);

  const { data: beneficiaries, isLoading } = useQuery({
    queryKey: ['beneficiaries'],
    queryFn: listBeneficiaries,
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const addMutation = useMutation({
    mutationFn: addBeneficiary,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['beneficiaries'] });
      reset();
    },
  });

  const removeMutation = useMutation({
    mutationFn: removeBeneficiary,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['beneficiaries'] }),
  });

  const onSubmit = async (values: FormValues) => {
    setServerError(null);
    try {
      await addMutation.mutateAsync(values);
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.status === 409) {
        setServerError('This account is already saved as a beneficiary');
      } else {
        setServerError('Could not add beneficiary');
      }
    }
  };

  return (
    <div className="max-w-lg">
      <Link to="/dashboard" className="text-sm text-muted-foreground hover:underline">
        ← Back to accounts
      </Link>
      <h1 className="text-2xl font-semibold mt-3 mb-6">Beneficiaries</h1>

      {serverError && <Alert>{serverError}</Alert>}

      <form onSubmit={handleSubmit(onSubmit)} className="flex gap-2 mb-6 items-start">
        <div className="flex-1">
          <Input placeholder="10-digit account number" {...register('beneficiaryAccountNumber')} />
          {errors.beneficiaryAccountNumber && (
            <p className="text-sm text-red-500 mt-1">{errors.beneficiaryAccountNumber.message}</p>
          )}
        </div>
        <div className="flex-1">
          <Input placeholder="Nickname" {...register('nickname')} />
          {errors.nickname && <p className="text-sm text-red-500 mt-1">{errors.nickname.message}</p>}
        </div>
        <Button type="submit" disabled={isSubmitting}>
          Add
        </Button>
      </form>

      {isLoading && <p className="text-muted-foreground">Loading...</p>}

      <div className="space-y-2">
        {beneficiaries?.map((b) => (
          <div key={b.id} className="flex items-center justify-between rounded-md border border-border px-4 py-3">
            <div>
              <p className="font-medium">{b.nickname}</p>
              <p className="text-sm text-muted-foreground">{b.beneficiaryAccountNumber}</p>
            </div>
            <Button
              variant="ghost"
              aria-label={`Remove ${b.nickname}`}
              onClick={() => removeMutation.mutate(b.id)}
            >
              <Trash2 size={16} className="text-red-500" />
            </Button>
          </div>
        ))}
        {beneficiaries?.length === 0 && (
          <p className="text-muted-foreground text-sm">No beneficiaries saved yet.</p>
        )}
      </div>
    </div>
  );
}
