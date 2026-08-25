import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ChevronDown, LogOut, User as UserIcon } from 'lucide-react';
import { clsx } from 'clsx';
import { useAuth } from '@/features/auth/AuthContext';

const roleStyles: Record<string, string> = {
  CUSTOMER: 'bg-blue-500/10 text-blue-600',
  EMPLOYEE: 'bg-indigo-500/10 text-indigo-600',
  ADMIN: 'bg-red-500/10 text-red-500',
};

function initials(firstName?: string, lastName?: string): string {
  return `${firstName?.[0] ?? ''}${lastName?.[0] ?? ''}`.toUpperCase() || '?';
}

export function UserMenu() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    }
    function handleEscape(e: KeyboardEvent) {
      if (e.key === 'Escape') setOpen(false);
    }
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleEscape);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleEscape);
    };
  }, []);

  if (!user) return null;

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div className="relative" ref={containerRef}>
      <button
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-2 rounded-full pl-1 pr-2 py-1 hover:bg-muted transition-colors"
        aria-expanded={open}
        aria-haspopup="true"
      >
        <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-primary text-primary-foreground text-xs font-semibold">
          {initials(user.firstName, user.lastName)}
        </span>
        <ChevronDown size={14} className={clsx('text-muted-foreground transition-transform', open && 'rotate-180')} />
      </button>

      {open && (
        <div className="absolute right-0 mt-2 w-64 rounded-lg border border-border bg-background shadow-lg py-1.5 z-50">
          <div className="px-3 py-2.5 border-b border-border">
            <p className="text-sm font-medium truncate">{user.firstName} {user.lastName}</p>
            <p className="text-xs text-muted-foreground truncate mt-0.5">{user.email}</p>
            <div className="flex flex-wrap gap-1 mt-2">
              {user.roles.map((role) => (
                <span
                  key={role}
                  className={clsx('text-[10px] font-medium uppercase tracking-wide rounded-full px-2 py-0.5', roleStyles[role] ?? 'bg-muted text-muted-foreground')}
                >
                  {role}
                </span>
              ))}
            </div>
          </div>
          <Link
            to="/profile"
            onClick={() => setOpen(false)}
            className="flex items-center gap-2 px-3 py-2 text-sm hover:bg-muted transition-colors"
          >
            <UserIcon size={14} /> Profile
          </Link>
          <button
            onClick={handleLogout}
            className="w-full flex items-center gap-2 px-3 py-2 text-sm text-left text-red-500 hover:bg-muted transition-colors"
          >
            <LogOut size={14} /> Log out
          </button>
        </div>
      )}
    </div>
  );
}