import { useRef, useState } from 'react';
import { Bell } from 'lucide-react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getUnreadCount,
  listMyNotifications,
  markNotificationAsRead,
} from '@/features/notifications/api/notificationsApi';
import { useOnClickOutside } from '@/shared/hooks/useOnClickOutside';

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const queryClient = useQueryClient();

  useOnClickOutside(containerRef, () => setOpen(false));

  const { data: unreadCount } = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: getUnreadCount,
    refetchInterval: 15_000,
  });

  const { data: notifications } = useQuery({
    queryKey: ['notifications', 'mine'],
    queryFn: listMyNotifications,
    enabled: open,
  });

  const markReadMutation = useMutation({
    mutationFn: markNotificationAsRead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });

  return (
    <div className="relative" ref={containerRef}>
      <button
        onClick={() => setOpen((o) => !o)}
        aria-label="Notifications"
        className="relative p-2 rounded-md hover:bg-muted"
      >
        <Bell size={18} />
        {!!unreadCount && unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-red-500 text-[10px] text-white">
            {unreadCount > 9 ? '9+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 mt-2 w-80 max-h-96 overflow-y-auto rounded-md border border-border bg-background shadow-lg z-10">
          {notifications?.length === 0 && (
            <p className="text-sm text-muted-foreground px-4 py-6 text-center">No notifications yet.</p>
          )}
          {notifications?.map((n) => (
            <button
              key={n.id}
              onClick={() => !n.read && markReadMutation.mutate(n.id)}
              className={`w-full text-left px-4 py-3 border-b border-border last:border-0 hover:bg-muted ${
                n.read ? 'opacity-60' : ''
              }`}
            >
              <p className="text-sm font-medium">{n.title}</p>
              <p className="text-xs text-muted-foreground mt-0.5">{n.message}</p>
              <p className="text-xs text-muted-foreground mt-1">{new Date(n.createdAt).toLocaleString()}</p>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
