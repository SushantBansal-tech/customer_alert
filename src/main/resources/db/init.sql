
CREATE SCHEMA IF NOT EXISTS alert_system;


CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255),
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_user_role CHECK (role IN ('DEVELOPER', 'OPERATIONS', 'ADMIN'))
);


CREATE TABLE IF NOT EXISTS user_activity (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    activity_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    response_time BIGINT NOT NULL,
    severity VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    details JSONB,
    CONSTRAINT fk_activity_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX idx_user_activity_timestamp ON user_activity(user_id, timestamp);
CREATE INDEX idx_activity_status_timestamp ON user_activity(status, timestamp);


CREATE TABLE IF NOT EXISTS issues (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    issue_type VARCHAR(100) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    detected_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    description TEXT,
    affected_users_count INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT fk_issue_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT fk_issue_status CHECK (status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED'))
);

CREATE INDEX idx_issue_user_status ON issues(user_id, status);
CREATE INDEX idx_issue_detected_at ON issues(detected_at);


CREATE TABLE IF NOT EXISTS alerts (
    id UUID PRIMARY KEY,
    issue_id UUID UNIQUE NOT NULL REFERENCES issues(id),
    created_at TIMESTAMP NOT NULL,
    last_notified_at TIMESTAMP,
    next_recheck_at TIMESTAMP,
    notification_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL,
    CONSTRAINT fk_alert_status CHECK (status IN ('PENDING', 'SENT', 'ACKNOWLEDGED'))
);

CREATE INDEX idx_alert_issue_id ON alerts(issue_id);
CREATE INDEX idx_alert_status ON alerts(status);


CREATE TABLE IF NOT EXISTS issue_assignments (
    id UUID PRIMARY KEY,
    issue_id UUID NOT NULL REFERENCES issues(id),
    assigned_to_user_id UUID NOT NULL REFERENCES users(id),
    assigned_at TIMESTAMP NOT NULL,
    assignment_type VARCHAR(50) NOT NULL
);


CREATE TABLE IF NOT EXISTS issue_history (
    id UUID PRIMARY KEY,
    issue_id UUID NOT NULL REFERENCES issues(id),
    old_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    changed_by_user_id UUID REFERENCES users(id),
    changed_at TIMESTAMP NOT NULL,
    notes TEXT
);

CREATE INDEX idx_history_issue_id ON issue_history(issue_id);
CREATE INDEX idx_history_changed_at ON issue_history(changed_at);


CREATE TABLE IF NOT EXISTS notification_log (
    id UUID PRIMARY KEY,
    alert_id UUID REFERENCES alerts(id),
    recipient_user_id UUID NOT NULL REFERENCES users(id),
    notification_type VARCHAR(50) NOT NULL,
    sent_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    message TEXT
);


INSERT INTO users (id, username, email, role, created_at) VALUES
    ('550e8400-e29b-41d4-a716-446655440000', 'dev1', 'dev1@company.com', 'DEVELOPER', NOW()),
    ('550e8400-e29b-41d4-a716-446655440001', 'ops_team', 'ops@company.com', 'OPERATIONS', NOW()),
    ('550e8400-e29b-41d4-a716-446655440002', 'admin', 'admin@company.com', 'ADMIN', NOW())
ON CONFLICT DO NOTHING;