-- BE-B · 동일한 초 안의 패스와 연락 기록도 실제 시각으로 비교한다.
-- 기존 행을 삭제/초기화하지 않는다. 여러 번 적용해도 기존 시각의 의미는 보존된다.
ALTER TABLE contact_log MODIFY COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
