import { apiClient } from '@/shared/lib/apiClient';
import type { ExceptionDetail, ExceptionNote, ExceptionStatus, TransactionException } from '@/features/exceptions/types';

export async function listExceptions(status?: ExceptionStatus[]): Promise<TransactionException[]> {
  const { data } = await apiClient.get<TransactionException[]>('/exceptions', {
    params: status && status.length > 0 ? { status: status.join(',') } : undefined,
  });
  return data;
}

export async function listMyExceptions(): Promise<TransactionException[]> {
  const { data } = await apiClient.get<TransactionException[]>('/exceptions/mine');
  return data;
}

export async function getExceptionByTransaction(transactionId: string): Promise<TransactionException | null> {
  const { data } = await apiClient.get<TransactionException | null>(`/exceptions/by-transaction/${transactionId}`);
  return data;
}

export async function getExceptionDetail(id: string): Promise<ExceptionDetail> {
  const { data } = await apiClient.get<ExceptionDetail>(`/exceptions/${id}`);
  return data;
}

export async function assignException(id: string, assigneeUserId: string): Promise<TransactionException> {
  const { data } = await apiClient.post<TransactionException>(`/exceptions/${id}/assign`, { assigneeUserId });
  return data;
}

export async function startInvestigating(id: string): Promise<TransactionException> {
  const { data } = await apiClient.post<TransactionException>(`/exceptions/${id}/start-investigating`);
  return data;
}

export async function requestInformation(id: string, content: string): Promise<TransactionException> {
  const { data } = await apiClient.post<TransactionException>(`/exceptions/${id}/request-information`, { content });
  return data;
}

export async function resolveException(id: string, content: string): Promise<TransactionException> {
  const { data } = await apiClient.post<TransactionException>(`/exceptions/${id}/resolve`, { content });
  return data;
}

export async function closeException(id: string, content?: string): Promise<TransactionException> {
  const { data } = await apiClient.post<TransactionException>(`/exceptions/${id}/close`, content ? { content } : undefined);
  return data;
}

export async function escalateException(id: string, content?: string): Promise<TransactionException> {
  const { data } = await apiClient.post<TransactionException>(`/exceptions/${id}/escalate`, content ? { content } : undefined);
  return data;
}

export async function retryException(id: string): Promise<TransactionException> {
  const { data } = await apiClient.post<TransactionException>(`/exceptions/${id}/retry`);
  return data;
}

export async function addExceptionNote(id: string, content: string): Promise<ExceptionNote> {
  const { data } = await apiClient.post<ExceptionNote>(`/exceptions/${id}/notes`, { content });
  return data;
}
