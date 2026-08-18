import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import { getMyAccounts } from '@/features/accounts/api/accountsApi';
import { createTransfer } from '@/features/transactions/api/transactionsApi';
import { Button } from '@/shared/components/ui/Button';
import { Input } from '@/shared/components/ui/Input';
import { Alert } from '@/shared/components/ui/Alert';
import { formatCurrency } from '@/shared/lib/format';

const schema = z.object({
  sourceAccountId: z.string().min(1, 'Select an account'),
  destinationAccountNumber: z.string().regex(/^\d{10}$/, 'Must be exactly 10 digits'),
  amount: z.coerce.number().positive('Amount must be greater than zero'),
  note: z.string().max(255).optional(),
});

type FormValues = z.infer<typeof schema>;

export function TransferPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [serverError, setServerError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const { data: accounts } = useQuery({ queryKey: ['accounts'], queryFn: getMyAccounts });

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const transferMutation = useMutation({
    mutationFn: createTransfer,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      setSuccess(true);
    },
  });

  const onSubmit = async (values: FormValues) => {
    setServerError(null);
    setSuccess(false);
    try {
      await transferMutation.mutateAsync({
        ...values,
        idempotencyKey: crypto.randomUUID(),
      });
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.data?.message) {
        setServerError(err.response.data.message);
      } else {
        setServerError('Could not submit transfer');
      }
    }
  };

  return (
    <div className="max-w-md">
      <h1 className="text-2xl font-semibold mb-6">Transfer money</h1>

      {serverError && <Alert>{serverError}</Alert>}
      {success && (
        <Alert variant="success">
          Transfer submitted and is awaiting employee approval.{' '}
          <button className="underline" onClick={() => navigate('/transactions')}>
            View history
          </button>
        </Alert>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div>
          <label className="text-sm text-muted-foreground mb-1 block">From account</label>
          <select
            className="w-full rounded-md border border-border bg-transparent px-3 py-2 text-sm"
            {...register('sourceAccountId')}
          >
            <option value="">Select an account</option>
            {accounts?.map((a) => (
              <option key={a.id} value={a.id}>
                {a.accountType} •••• {a.accountNumber.slice(-4)} — {formatCurrency(a.balance)}
              </option>
            ))}
          </select>
          {errors.sourceAccountId && (
            <p className="text-sm text-red-500 mt-1">{errors.sourceAccountId.message}</p>
          )}
        </div>

        <div>
          <Input placeholder="Destination account number (10 digits)" {...register('destinationAccountNumber')} />
          {errors.destinationAccountNumber && (
            <p className="text-sm text-red-500 mt-1">{errors.destinationAccountNumber.message}</p>
          )}
        </div>

        <div>
          <Input placeholder="Amount" type="number" step="0.01" {...register('amount')} />
          {errors.amount && <p className="text-sm text-red-500 mt-1">{errors.amount.message}</p>}
        </div>

        <div>
          <Input placeholder="Note (optional)" {...register('note')} />
        </div>

        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? 'Submitting...' : 'Submit transfer'}
        </Button>
      </form>
    </div>
  );
}
