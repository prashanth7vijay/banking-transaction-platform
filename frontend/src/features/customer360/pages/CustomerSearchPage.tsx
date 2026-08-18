import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { searchCustomers } from '@/features/employee/api/employeeApi';
import { Input } from '@/shared/components/ui/Input';

export function CustomerSearchPage() {
  const [search, setSearch] = useState('');

  const { data: customers, isLoading } = useQuery({
    queryKey: ['employee', 'customers', search],
    queryFn: () => searchCustomers(search || undefined),
  });

  return (
    <div className="max-w-2xl">
      <h1 className="text-2xl font-semibold mb-6">Customers</h1>
      <Input
        placeholder="Search by name or email"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        className="max-w-sm mb-3"
      />

      {isLoading && <p className="text-muted-foreground text-sm">Loading...</p>}

      <div className="border border-border rounded-lg divide-y divide-border">
        {customers?.map((c) => (
          <Link key={c.id} to={`/customers/${c.id}`} className="block px-4 py-3 hover:bg-muted transition-colors">
            <p className="font-medium">{c.firstName} {c.lastName}</p>
            <p className="text-sm text-muted-foreground">{c.email}</p>
          </Link>
        ))}
        {customers?.length === 0 && (
          <p className="text-muted-foreground text-sm px-4 py-6">No customers match this search.</p>
        )}
      </div>
    </div>
  );
}
