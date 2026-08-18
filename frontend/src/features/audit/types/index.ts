export interface AuditLog {
  id: number;
  correlationId: string | null;
  actorUserId: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  metadata: string | null;
  createdAt: string;
}

export interface AuditLogFilters {
  actorUserId?: string;
  action?: string;
  entityType?: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
