ALTER TABLE notifications
  ADD COLUMN read_at TIMESTAMP NULL;

CREATE INDEX idx_notifications_member_id_id ON notifications(member_id, id);
