-- Story 1.1 인증 전환을 위해 members 스키마를 확장한다.
-- 기존 스캐폴딩과의 호환을 위해 member_no 컬럼은 유지한다.

ALTER TABLE members
  ADD COLUMN password_hash VARCHAR(255) NOT NULL DEFAULT '__LEGACY__';

ALTER TABLE members
  ADD COLUMN name VARCHAR(100) NOT NULL DEFAULT 'UNKNOWN';

ALTER TABLE members
  ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER';

-- 기존 데이터가 있는 환경에서 표시 이름이 비어 보이지 않도록 member_no를 기본 이름으로 채운다.
UPDATE members
SET name = member_no
WHERE name = 'UNKNOWN' OR name IS NULL;

CREATE UNIQUE INDEX uk_members_email ON members(email);
