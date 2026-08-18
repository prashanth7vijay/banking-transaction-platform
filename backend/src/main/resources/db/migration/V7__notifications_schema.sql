CREATE TABLE notifications.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    type VARCHAR(64) NOT NULL,
    title VARCHAR(150) NOT NULL,
    message VARCHAR(500) NOT NULL,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user_id ON notifications.notifications (user_id);
CREATE INDEX idx_notifications_user_unread ON notifications.notifications (user_id, read);
