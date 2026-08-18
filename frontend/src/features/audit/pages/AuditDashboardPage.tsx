import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { searchAuditLogs } from '@/features/audit/api/auditApi';
import type { AuditLogFilters } from '@/features/audit/types';
import { Input } from '@/shared/components/ui/Input';
import { Button } from '@/shared/components/ui/Button';

const ACTIONS = [
  'LOGIN',
  'LOGOUT',
  'PASSWORD_RESET',
  'PASSWORD_CHANGED',
  'TRANSACTION_PENDING_APPROVAL',
  'TRANSACTION_COMPLETED',
  'TRANSACTION_REJECTED',
  'TRANSACTION_FAILED',
];

const ENTITY_TYPES = ['USER', 'TRANSACTION'];
const PAGE_SIZE = 25;

export function AuditDashboardPage() {
  const [filters, setFilters] = useState<AuditLogFilters>({});
  const [appliedFilters, setAppliedFilters] = useState<AuditLogFilters>({});
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['audit-logs', appliedFilters, page],
    queryFn: () => searchAuditLogs(appliedFilters, page, PAGE_SIZE),
  });

  const applyFilters = () => {
    setAppliedFilters(filters);
    setPage(0);
  };

  return (
    <div>
      <h1 className="text-2xl font-semibold mb-6">Audit Log</h1>

      <div className="flex flex-wrap gap-3 mb-6 items-end">
        <div>
          <label className="text-xs text-muted-foreground mb-1 block">Actor user ID</label>
          <Input
            placeholder="UUID"
            value={filters.actorUserId ?? ''}
            onChange={(e) => setFilters((f) => ({ ...f, actorUserId: e.target.value || undefined }))}
            className="w-64"
          />
        </div>
        <div>
          <label className="text-xs text-muted-foreground mb-1 block">Action</label>
          <select
            className="rounded-md border border-border bg-transparent px-3 py-2 text-sm"
            value={filters.action ?? ''}
            onChange={(e) => setFilters((f) => ({ ...f, action: e.target.value || undefined }))}
          >
            <option value="">All actions</option>
            {ACTIONS.map((a) => (
              <option key={a} value={a}>{a}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="text-xs text-muted-foreground mb-1 block">Entity type</label>
          <select
            className="rounded-md border border-border bg-transparent px-3 py-2 text-sm"
            value={filters.entityType ?? ''}
            onChange={(e) => setFilters((f) => ({ ...f, entityType: e.target.value || undefined }))}
          >
            <option value="">All entity types</option>
            {ENTITY_TYPES.map((t) => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
        </div>
        <Button onClick={applyFilters}>Apply filters</Button>
      </div>

      {isLoading && <p className="text-muted-foreground">Loading...</p>}

      <div className="border border-border rounded-lg overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="text-left text-muted-foreground border-b border-border">
            <tr>
              <th className="px-4 py-2">Time</th>
              <th className="px-4 py-2">Action</th>
              <th className="px-4 py-2">Entity</th>
              <th className="px-4 py-2">Actor</th>
              <th className="px-4 py-2">Metadata</th>
            </tr>
          </thead>
          <tbody>
            {data?.content.map((log) => (
              <tr key={log.id} className="border-b border-border last:border-0">
                <td className="px-4 py-2 whitespace-nowrap">{new Date(log.createdAt).toLocaleString()}</td>
                <td className="px-4 py-2">{log.action}</td>
                <td className="px-4 py-2">{log.entityType} {log.entityId && `#${log.entityId.slice(0, 8)}`}</td>
                <td className="px-4 py-2">{log.actorUserId?.slice(0, 8) ?? '—'}</td>
                <td className="px-4 py-2 text-muted-foreground font-mono text-xs">{log.metadata ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {data?.content.length === 0 && (
          <p className="text-muted-foreground text-sm px-4 py-6">No audit entries match these filters.</p>
        )}
      </div>

      {data && data.totalPages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <p className="text-sm text-muted-foreground">
            Page {data.page + 1} of {data.totalPages} · {data.totalElements} total entries
          </p>
          <div className="flex gap-2">
            <Button variant="outline" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
              Previous
            </Button>
            <Button
              variant="outline"
              disabled={page + 1 >= data.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
