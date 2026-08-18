import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import { searchUsers, updateUserRoles, updateUserStatus } from '@/features/admin/api/adminApi';
import type { AdminUser } from '@/features/admin/types';
import { Input } from '@/shared/components/ui/Input';
import { Alert } from '@/shared/components/ui/Alert';

const ALL_ROLES = ['CUSTOMER', 'EMPLOYEE', 'ADMIN'];
const ALL_STATUSES = ['ACTIVE', 'LOCKED', 'DISABLED'] as const;

export function UserManagementPage() {
  const [search, setSearch] = useState('');
  const [error, setError] = useState<string | null>(null);
  const queryClient = useQueryClient();

  const { data: users, isLoading } = useQuery({
    queryKey: ['admin', 'users', search],
    queryFn: () => searchUsers(search || undefined),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) => updateUserStatus(id, status),
    onSuccess: invalidate,
  });

  const rolesMutation = useMutation({
    mutationFn: ({ id, roles }: { id: string; roles: string[] }) => updateUserRoles(id, roles),
    onSuccess: invalidate,
  });

  const handleStatusChange = async (user: AdminUser, status: string) => {
    setError(null);
    try {
      await statusMutation.mutateAsync({ id: user.id, status });
    } catch (err) {
      setError(extractError(err));
    }
  };

  const handleToggleRole = async (user: AdminUser, role: string) => {
    setError(null);
    const nextRoles = user.roles.includes(role)
      ? user.roles.filter((r) => r !== role)
      : [...user.roles, role];

    if (nextRoles.length === 0) {
      setError('A user must have at least one role');
      return;
    }

    try {
      await rolesMutation.mutateAsync({ id: user.id, roles: nextRoles });
    } catch (err) {
      setError(extractError(err));
    }
  };

  return (
    <div>
      <h1 className="text-2xl font-semibold mb-6">User Management</h1>

      {error && <Alert>{error}</Alert>}

      <Input
        placeholder="Search by name or email"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        className="max-w-sm mb-6"
      />

      {isLoading && <p className="text-muted-foreground">Loading...</p>}

      <div className="border border-border rounded-lg overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="text-left text-muted-foreground border-b border-border">
            <tr>
              <th className="px-4 py-2">Name</th>
              <th className="px-4 py-2">Email</th>
              <th className="px-4 py-2">Roles</th>
              <th className="px-4 py-2">Status</th>
            </tr>
          </thead>
          <tbody>
            {users?.map((u) => (
              <tr key={u.id} className="border-b border-border last:border-0">
                <td className="px-4 py-2 whitespace-nowrap">{u.firstName} {u.lastName}</td>
                <td className="px-4 py-2">{u.email}</td>
                <td className="px-4 py-2">
                  <div className="flex gap-1 flex-wrap">
                    {ALL_ROLES.map((role) => (
                      <button
                        key={role}
                        onClick={() => handleToggleRole(u, role)}
                        className={`text-xs rounded-full px-2 py-0.5 border ${
                          u.roles.includes(role)
                            ? 'bg-primary text-primary-foreground border-primary'
                            : 'border-border text-muted-foreground'
                        }`}
                      >
                        {role}
                      </button>
                    ))}
                  </div>
                </td>
                <td className="px-4 py-2">
                  <select
                    className="rounded-md border border-border bg-transparent px-2 py-1 text-xs"
                    value={u.status}
                    onChange={(e) => handleStatusChange(u, e.target.value)}
                  >
                    {ALL_STATUSES.map((s) => (
                      <option key={s} value={s}>{s}</option>
                    ))}
                  </select>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {users?.length === 0 && (
          <p className="text-muted-foreground text-sm px-4 py-6">No users match this search.</p>
        )}
      </div>
    </div>
  );
}

function extractError(err: unknown): string {
  if (axios.isAxiosError(err) && err.response?.data?.message) {
    return err.response.data.message;
  }
  return 'Action failed';
}
