import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import {
  addExceptionNote,
  assignException,
  closeException,
  escalateException,
  getExceptionDetail,
  requestInformation,
  resolveException,
  retryException,
  startInvestigating,
} from '@/features/exceptions/api/exceptionsApi';
import { searchEmployees } from '@/features/employee/api/employeeApi';
import { ExceptionStatusBadge } from '@/features/exceptions/components/ExceptionStatusBadge';
import { PriorityBadge } from '@/features/exceptions/components/PriorityBadge';
import { SlaBadge } from '@/features/exceptions/components/SlaBadge';
import { Button } from '@/shared/components/ui/Button';
import { Alert } from '@/shared/components/ui/Alert';
import { Input } from '@/shared/components/ui/Input';
import { formatCurrency } from '@/shared/lib/format';
import { useAuth } from '@/features/auth/AuthContext';
import type { ExceptionNoteType } from '@/features/exceptions/types';

const noteTypeLabels: Record<ExceptionNoteType, string> = {
  NOTE: 'Note',
  STATUS_CHANGE: 'Status Change',
  ASSIGNMENT: 'Assignment',
  ACTION_TAKEN: 'Action Taken',
};

function extractError(err: unknown): string {
  if (axios.isAxiosError(err) && err.response?.data?.message) {
    return err.response.data.message;
  }
  if (err instanceof Error && err.message) {
    return err.message;
  }
  return 'Action failed';
}

export function ExceptionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [noteText, setNoteText] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);

  const { data, isLoading } = useQuery({
    queryKey: ['exceptions', id],
    queryFn: () => getExceptionDetail(id!),
    enabled: !!id,
  });

  const { data: employees } = useQuery({
    queryKey: ['employees', 'search'],
    queryFn: () => searchEmployees(),
  });

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['exceptions', id] });

  const withErrorHandling = (fn: () => Promise<unknown>) => async () => {
    setActionError(null);
    try {
      await fn();
      invalidate();
    } catch (err) {
      setActionError(extractError(err));
    }
  };

  const claimMutation = useMutation({
    mutationFn: () => assignException(id!, user!.id),
    onSuccess: invalidate,
  });
  const assignMutation = useMutation({
    mutationFn: (assigneeUserId: string) => assignException(id!, assigneeUserId),
    onSuccess: invalidate,
  });
  const startInvestigatingMutation = useMutation({
    mutationFn: () => startInvestigating(id!),
    onSuccess: invalidate,
  });
  const addNoteMutation = useMutation({
    mutationFn: (content: string) => addExceptionNote(id!, content),
    onSuccess: () => {
      setNoteText('');
      invalidate();
    },
  });
  const requestInfoMutation = useMutation({
    mutationFn: (content: string) => requestInformation(id!, content),
    onSuccess: invalidate,
  });
  const resolveMutation = useMutation({
    mutationFn: (content: string) => resolveException(id!, content),
    onSuccess: invalidate,
  });
  const closeMutation = useMutation({
    mutationFn: () => closeException(id!),
    onSuccess: invalidate,
  });
  const escalateMutation = useMutation({
    mutationFn: () => escalateException(id!),
    onSuccess: invalidate,
  });
  const retryMutation = useMutation({
    mutationFn: () => retryException(id!),
    onSuccess: invalidate,
  });

  const anyActionPending =
    claimMutation.isPending ||
    assignMutation.isPending ||
    startInvestigatingMutation.isPending ||
    requestInfoMutation.isPending ||
    resolveMutation.isPending ||
    closeMutation.isPending ||
    escalateMutation.isPending ||
    retryMutation.isPending;

  if (isLoading) return <p className="text-muted-foreground">Loading...</p>;
  if (!data) return <p className="text-red-500 text-sm">Could not load this exception.</p>;

  const { exception, notes, transaction } = data;
  const isAssignedToMe = exception.assignedTo?.id === user?.id;

  const handleRequestInfo = withErrorHandling(async () => {
    if (!noteText.trim()) throw new Error('A note is required');
    await requestInfoMutation.mutateAsync(noteText);
    setNoteText('');
  });

  const handleResolve = withErrorHandling(async () => {
    if (!noteText.trim()) throw new Error('A resolution note is required');
    await resolveMutation.mutateAsync(noteText);
    setNoteText('');
  });

  return (
    <div className="space-y-6">
      <div>
        <Link to="/exceptions" className="text-xs text-muted-foreground hover:underline">
          ← Back to exceptions
        </Link>
        <div className="flex items-center justify-between mt-1">
          <h1 className="text-2xl font-semibold">Exception Investigation</h1>
          <div className="flex items-center gap-2">
            <PriorityBadge priority={exception.priority} />
            <ExceptionStatusBadge status={exception.status} />
            <SlaBadge
              status={exception.slaStatus}
              slaDueAt={exception.slaDueAt}
              showCountdown={exception.status !== 'RESOLVED' && exception.status !== 'CLOSED'}
            />
          </div>
        </div>
      </div>

      {actionError && <Alert>{actionError}</Alert>}

      <div className="border border-border rounded-lg p-4 space-y-3">
        <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">Why this case exists</h2>
        <p className="text-sm">{exception.reason}</p>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 pt-2 border-t border-border">
          <div>
            <p className="text-xs text-muted-foreground">Transaction</p>
            <Link to={`/transactions/${transaction.id}`} className="text-sm font-medium hover:underline">
              View Transaction 360 →
            </Link>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Amount</p>
            <p className="text-sm font-medium">{formatCurrency(transaction.amount)}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Destination</p>
            <p className="text-sm font-medium">•••• {transaction.destinationAccountNumber.slice(-4)}</p>
          </div>
          <div>
            <p className="text-xs text-muted-foreground">Assigned To</p>
            <p className="text-sm font-medium">
              {exception.assignedTo ? (exception.assignedTo.firstName ?? exception.assignedTo.email) : 'Unassigned'}
            </p>
          </div>
        </div>
      </div>

      <div className="border border-border rounded-lg p-4 space-y-3">
        <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">Actions</h2>
        <div className="flex flex-wrap gap-2">
          {exception.status === 'OPEN' && (
            <Button disabled={anyActionPending} onClick={withErrorHandling(() => claimMutation.mutateAsync())}>
              Claim
            </Button>
          )}
          {exception.status !== 'CLOSED' && exception.status !== 'RESOLVED' && employees && employees.length > 0 && (
            <select
              className="rounded-md border border-border bg-transparent px-3 py-2 text-sm"
              disabled={anyActionPending}
              value=""
              onChange={(e) => {
                if (e.target.value) withErrorHandling(() => assignMutation.mutateAsync(e.target.value))();
              }}
            >
              <option value="">Reassign to...</option>
              {employees.map((emp) => (
                <option key={emp.id} value={emp.id}>
                  {emp.firstName} {emp.lastName}
                </option>
              ))}
            </select>
          )}
          {exception.status === 'ASSIGNED' && isAssignedToMe && (
            <Button disabled={anyActionPending} onClick={withErrorHandling(() => startInvestigatingMutation.mutateAsync())}>
              Start Investigating
            </Button>
          )}
          {(exception.status === 'INVESTIGATING' || exception.status === 'ACTION_REQUIRED') && (
            <Button variant="outline" disabled={anyActionPending} onClick={handleRequestInfo}>
              Request Information
            </Button>
          )}
          {(exception.status === 'INVESTIGATING' || exception.status === 'ACTION_REQUIRED') && (
            <Button disabled={anyActionPending} onClick={handleResolve}>
              Resolve
            </Button>
          )}
          {exception.status === 'RESOLVED' && (
            <Button disabled={anyActionPending} onClick={withErrorHandling(() => closeMutation.mutateAsync())}>
              Close
            </Button>
          )}
          {exception.status !== 'RESOLVED' && exception.status !== 'CLOSED' && (
            <Button variant="outline" disabled={anyActionPending} onClick={withErrorHandling(() => escalateMutation.mutateAsync())}>
              Escalate
            </Button>
          )}
          {exception.status !== 'RESOLVED' && exception.status !== 'CLOSED' && (
            <Button variant="outline" disabled={anyActionPending} onClick={withErrorHandling(() => retryMutation.mutateAsync())}>
              Retry Transfer
            </Button>
          )}
        </div>
        {(exception.status === 'INVESTIGATING' || exception.status === 'ACTION_REQUIRED') && (
          <p className="text-xs text-muted-foreground">
            "Request Information" and "Resolve" use the note field below as their reason.
          </p>
        )}
      </div>

      <div className="border border-border rounded-lg p-4 space-y-4">
        <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wide">Investigation Trail</h2>

        {exception.status !== 'CLOSED' && (
          <div className="flex gap-2">
            <Input
              placeholder="Add an investigation note..."
              value={noteText}
              onChange={(e) => setNoteText(e.target.value)}
            />
            <Button
              variant="outline"
              disabled={!noteText.trim() || addNoteMutation.isPending}
              onClick={withErrorHandling(() => addNoteMutation.mutateAsync(noteText))}
            >
              Add Note
            </Button>
          </div>
        )}

        <div className="space-y-3">
          {notes.map((note) => (
            <div key={note.id} className="flex items-start gap-3 text-sm border-b border-border pb-3 last:border-0">
              <span className="text-xs text-muted-foreground w-40 shrink-0">
                {new Date(note.createdAt).toLocaleString()}
              </span>
              <div className="flex-1">
                <p className="text-xs text-muted-foreground">
                  {noteTypeLabels[note.noteType]}
                  {note.author && ` · ${note.author.firstName ?? note.author.email}`}
                </p>
                <p>{note.content}</p>
              </div>
            </div>
          ))}
          {notes.length === 0 && <p className="text-sm text-muted-foreground">No investigation activity yet.</p>}
        </div>
      </div>
    </div>
  );
}
