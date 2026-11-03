-- BE-B · 기존 DB에 1회 적용. 재실행해도 기존 도움 요청 행 · 상태 · 이력을 바꾸지 않는다.
-- 도움 요청 처리 단계 추가: IN_PROGRESS(처리 중) · CONSULTATION_SCHEDULED(상담 예정) + 상담 일시 컬럼.
-- schema.sql 재실행 대신 이 파일을 사용한다. 적용 후 BE를 재시작한다.
-- 연결된 DB를 대상으로 실행하며 USE 문이나 DROP/TRUNCATE/DELETE를 사용하지 않는다.

SET @ieum_consultation_at_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
         AND table_name='help_request' AND column_name='consultation_at'),
  'SELECT 1',
  'ALTER TABLE help_request ADD COLUMN consultation_at DATETIME NULL COMMENT ''상담 예정 일시 (CONSULTATION_SCHEDULED 때 코디네이터 입력, 이후 상태에서도 이력으로 유지)'' AFTER close_reason');
PREPARE ieum_migration FROM @ieum_consultation_at_sql;
EXECUTE ieum_migration;
DEALLOCATE PREPARE ieum_migration;

-- 상태값 설명을 바꾸고 길이를 늘린다 (CONSULTATION_SCHEDULED 22자 > 기존 20). 값 · 기본값 그대로, 늘리기만 하므로 기존 값 보존
ALTER TABLE help_request MODIFY COLUMN status VARCHAR(32) NOT NULL DEFAULT 'OPEN'
  COMMENT 'OPEN | IN_PROGRESS | CONSULTATION_SCHEDULED | CONTACTED | CLOSED';
