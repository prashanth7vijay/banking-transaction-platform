import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import {
  createCustomer,
  openAccount,
  searchCustomersPaged,
} from '@/features/employee/api/employeeApi';
import { Input } from '@/shared/components/ui/Input';
import { Button } from '@/shared/components/ui/Button';
import { Alert } from '@/shared/components/ui/Alert';
import { useDebouncedValue } from '@/shared/hooks/useDebouncedValue';

const schema = z.object({
  firstName: z.string().min(1, 'Required'),
  lastName: z.string().min(1, 'Required'),
  email: z.string().email('Enter a valid email'),
  password: z
    .string()
    .min(8, 'At least 8 characters')
    .regex(/(?=.*[A-Za-z])(?=.*\d)/, 'Must include a letter and a digit'),
  accountType: z.enum(['CHECKING', 'SAVINGS']),
  openingBalance: z.coerce.number().min(0, 'Cannot be negative'),
});

type FormValues = z.infer<typeof schema>;

function extractError(err: unknown): string {
  if (axios.isAxiosError(err) && err.response?.data?.message) {
    return err.response.data.message;
  }
  return 'Something went wrong';
}

function AddCustomerForm({ onDone }: { onDone: () => void }) {
  const [error, setError] = useState<string | null>(null);
  const [partialFailureCustomerId, setPartialFailureCustomerId] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { accountType: 'CHECKING', openingBalance: 0 },
  });

  const onSubmit = async (values: FormValues) => {
    setError(null);
    setPartialFailureCustomerId(null);
    try {
      const customer = await createCustomer({
        email: values.email,
        password: values.password,
        firstName: values.firstName,
        lastName: values.lastName,
      });
      try {
        await openAccount({
          customerUserId: customer.id,
          accountType: values.accountType,
          openingBalance: values.openingBalance,
        });
        onDone();
      } catch (accountErr) {
        // The customer now exists but has no account yet - don't hide that.
        // Retrying "Add Customer" would fail on the duplicate email, so the
        // honest recovery path is opening the account separately, which the
        // customer's own row (once found via search) will support next.
        setPartialFailureCustomerId(customer.id);
        setError(
          `Customer was created, but opening the account failed: ${extractError(accountErr)}. ` +
            `Search for ${values.firstName} ${values.lastName} below and open an account for them from their profile.`
        );
      }
    } catch (customerErr) {
      setError(extractError(customerErr));
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="border border-border rounded-lg p-4 space-y-4 mb-6">
      <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">Add Customer</h2>
      {error && <Alert>{error}</Alert>}

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1.5">First name</label>
          <Input {...register('firstName')} disabled={!!partialFailureCustomerId} />
          {errors.firstName && <p className="text-sm text-red-500 mt-1">{errors.firstName.message}</p>}
        </div>
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1.5">Last name</label>
          <Input {...register('lastName')} disabled={!!partialFailureCustomerId} />
          {errors.lastName && <p className="text-sm text-red-500 mt-1">{errors.lastName.message}</p>}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1.5">Email</label>
          <Input type="email" {...register('email')} disabled={!!partialFailureCustomerId} />
          {errors.email && <p className="text-sm text-red-500 mt-1">{errors.email.message}</p>}
        </div>
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1.5">Temporary password</label>
          <Input type="text" {...register('password')} disabled={!!partialFailureCustomerId} />
          {errors.password && <p className="text-sm text-red-500 mt-1">{errors.password.message}</p>}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1.5">Account type</label>
          <select
            {...register('accountType')}
            disabled={!!partialFailureCustomerId}
            className="w-full rounded-md border border-border bg-transparent px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-primary"
          >
            <option value="CHECKING">Checking</option>
            <option value="SAVINGS">Savings</option>
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-muted-foreground mb-1.5">Opening balance</label>
          <Input type="number" step="0.01" min="0" {...register('openingBalance')} disabled={!!partialFailureCustomerId} />
          {errors.openingBalance && <p className="text-sm text-red-500 mt-1">{errors.openingBalance.message}</p>}
        </div>
      </div>

      <p className="text-xs text-muted-foreground">
        The temporary password is handed to the customer directly — they can change it from their
        profile after logging in for the first time.
      </p>

      <div className="flex gap-2">
        <Button type="submit" disabled={isSubmitting || !!partialFailureCustomerId}>
          {isSubmitting ? 'Creating...' : 'Create Customer & Open Account'}
        </Button>
        <Button type="button" variant="outline" onClick={onDone}>
          Cancel
        </Button>
      </div>
    </form>
  );
}

export function CustomerSearchPage() {
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [showAddForm, setShowAddForm] = useState(false);
  const debouncedSearch = useDebouncedValue(search, 300);
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ['employee', 'customers', 'paged', debouncedSearch, page],
    queryFn: () => searchCustomersPaged(debouncedSearch || undefined, page),
  });

  const handleAddDone = () => {
    setShowAddForm(false);
    setPage(0);
    queryClient.invalidateQueries({ queryKey: ['employee', 'customers'] });
  };

  return (
    <div className="max-w-3xl">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold">Customers</h1>
        {!showAddForm && <Button onClick={() => setShowAddForm(true)}>Add Customer</Button>}
      </div>

      {showAddForm && <AddCustomerForm onDone={handleAddDone} />}

      <Input
        placeholder="Search by name or email"
        value={search}
        onChange={(e) => {
          setSearch(e.target.value);
          setPage(0);
        }}
        className="max-w-sm mb-3"
      />

      {isLoading && <p className="text-muted-foreground text-sm">Loading...</p>}

      <div className="border border-border rounded-lg divide-y divide-border">
        {data?.content.map((c) => (
          <Link key={c.id} to={`/customers/${c.id}`} className="flex items-center justify-between px-4 py-3 hover:bg-muted transition-colors">
            <div>
              <p className="font-medium">{c.firstName} {c.lastName}</p>
              <p className="text-sm text-muted-foreground">{c.email}</p>
            </div>
            <span className="text-xs rounded-full px-2 py-0.5 bg-muted text-muted-foreground">{c.status}</span>
          </Link>
        ))}
        {data?.content.length === 0 && (
          <p className="text-muted-foreground text-sm px-4 py-6">No customers match this search.</p>
        )}
      </div>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <p className="text-sm text-muted-foreground">
            Page {data.page + 1} of {data.totalPages} · {data.totalElements} total customers
          </p>
          <div className="flex gap-2">
            <Button variant="outline" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <Button variant="outline" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}