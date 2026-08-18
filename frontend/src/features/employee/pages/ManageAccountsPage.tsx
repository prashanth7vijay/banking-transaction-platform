import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import { openAccount, searchCustomers } from '@/features/employee/api/employeeApi';
import type { UserProfile } from '@/features/users/types';
import { Input } from '@/shared/components/ui/Input';
import { Button } from '@/shared/components/ui/Button';
import { Alert } from '@/shared/components/ui/Alert';
import { Link } from 'react-router-dom';

const schema = z.object({
  accountType: z.enum(['CHECKING', 'SAVINGS']),
  openingBalance: z.coerce.number().min(0, 'Cannot be negative'),
});

type FormValues = z.infer<typeof schema>;

export function ManageAccountsPage() {
  const [search, setSearch] = useState('');
  const [selectedCustomer, setSelectedCustomer] = useState<UserProfile | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const queryClient = useQueryClient();

  const { data: customers, isLoading } = useQuery({
    queryKey: ['employee', 'customers', search],
    queryFn: () => searchCustomers(search || undefined),
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { accountType: 'CHECKING', openingBalance: 0 },
  });

  const openAccountMutation = useMutation({
    mutationFn: openAccount,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'metrics'] });
    },
  });

  const onSubmit = async (values: FormValues) => {
    if (!selectedCustomer) {
      setError('Select a customer first');
      return;
    }
    setError(null);
    setSuccess(null);
    try {
      const account = await openAccountMutation.mutateAsync({
        customerUserId: selectedCustomer.id,
        ...values,
      });
      setSuccess(`Opened ${account.accountType} account •••• ${account.accountNumber.slice(-4)} for ${selectedCustomer.firstName} ${selectedCustomer.lastName}`);
      reset({ accountType: 'CHECKING', openingBalance: 0 });
    } catch (err) {
      if (axios.isAxiosError(err) && err.response?.data?.message) {
        setError(err.response.data.message);
      } else {
        setError('Could not open account');
      }
    }
  };

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-semibold mb-6">Manage Accounts</h1>

      <section className="mb-8">
        <h2 className="text-lg font-medium mb-3">1. Find a customer</h2>
        <Input
          placeholder="Search by name or email"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="max-w-sm mb-3"
        />

        {isLoading && <p className="text-muted-foreground text-sm">Loading...</p>}

        <div className="border border-border rounded-lg divide-y divide-border">
          {customers?.map((c) => (
            <div key={c.id} className={`flex items-center justify-between px-4 py-3 ${selectedCustomer?.id === c.id ? 'bg-muted' : ''}`}>
              <button
                onClick={() => {
                  setSelectedCustomer(c);
                  setError(null);
                  setSuccess(null);
                }}
                className="text-left flex-1"
              >
                <p className="font-medium">{c.firstName} {c.lastName}</p>
                <p className="text-sm text-muted-foreground">{c.email}</p>
              </button>
              <Link to={`/customers/${c.id}`} className="text-xs text-muted-foreground hover:underline shrink-0 ml-3">
                View 360 →
              </Link>
            </div>
          ))}
          {customers?.length === 0 && (
            <p className="text-muted-foreground text-sm px-4 py-6">No customers match this search.</p>
          )}
        </div>
      </section>

      <section>
        <h2 className="text-lg font-medium mb-3">2. Open an account</h2>

        {!selectedCustomer && (
          <p className="text-sm text-muted-foreground mb-3">Select a customer above first.</p>
        )}
        {selectedCustomer && (
          <p className="text-sm mb-3">
            Opening an account for <span className="font-medium">{selectedCustomer.firstName} {selectedCustomer.lastName}</span> ({selectedCustomer.email})
          </p>
        )}

        {error && <Alert>{error}</Alert>}
        {success && <Alert variant="success">{success}</Alert>}

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <label className="text-sm text-muted-foreground mb-1 block">Account type</label>
            <select
              className="w-full rounded-md border border-border bg-transparent px-3 py-2 text-sm"
              {...register('accountType')}
            >
              <option value="CHECKING">Checking</option>
              <option value="SAVINGS">Savings</option>
            </select>
          </div>
          <div>
            <label className="text-sm text-muted-foreground mb-1 block">Opening balance</label>
            <Input type="number" step="0.01" min="0" {...register('openingBalance')} />
            {errors.openingBalance && (
              <p className="text-sm text-red-500 mt-1">{errors.openingBalance.message}</p>
            )}
          </div>
          <Button type="submit" disabled={isSubmitting || !selectedCustomer}>
            {isSubmitting ? 'Opening...' : 'Open account'}
          </Button>
        </form>
      </section>
    </div>
  );
}
