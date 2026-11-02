-- BE-B · 기존 DB에 1회 적용. 재실행해도 원본·이력·선택값을 초기화하지 않는다.
-- schema.sql 재실행 대신 이 파일을 사용한다. 적용 후 BE를 재시작한다.
-- 연결된 DB를 대상으로 실행하며 USE 문이나 DROP/TRUNCATE/DELETE를 사용하지 않는다.

SET @ieum_help_type_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
         AND table_name='help_request' AND column_name='help_type'),
  'SELECT 1', 'ALTER TABLE help_request ADD COLUMN help_type VARCHAR(32) NULL AFTER recommendation_item_id');
PREPARE ieum_migration FROM @ieum_help_type_sql;
EXECUTE ieum_migration;
DEALLOCATE PREPARE ieum_migration;

SET @ieum_close_reason_sql = IF(
  EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE()
         AND table_name='help_request' AND column_name='close_reason'),
  'SELECT 1', 'ALTER TABLE help_request ADD COLUMN close_reason VARCHAR(32) NULL AFTER closed_at');
PREPARE ieum_migration FROM @ieum_close_reason_sql;
EXECUTE ieum_migration;
DEALLOCATE PREPARE ieum_migration;

CREATE TABLE IF NOT EXISTS student_intent_history (
  id BIGINT NOT NULL AUTO_INCREMENT,
  student_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL COMMENT 'EMPLOYED | SEEKING | RESTING',
  selected_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_intent_student_time (student_id, selected_at, id),
  CONSTRAINT fk_intent_student FOREIGN KEY (student_id) REFERENCES student (id) ON DELETE CASCADE,
  CONSTRAINT ck_intent_status CHECK (status IN ('EMPLOYED', 'SEEKING', 'RESTING'))
) COMMENT '수료생 본인 상태 선택 이력';

-- 같은 초 안의 상태 선택과 새 지원도 실제 저장 시각으로 비교한다.
ALTER TABLE recommendation_item MODIFY COLUMN responded_at DATETIME(6) NULL;
ALTER TABLE student MODIFY COLUMN last_response_at DATETIME(6) NULL
  COMMENT '마지막 응답·현재 의사 선택 시각. 1주 무응답 판정에 사용';

-- 기존 help_type=NULL은 미분류다. 임의로 OTHER로 채우지 않는다.
-- 기존 student_status는 원문을 보존하며 다음 응답/상태선택/일일 재계산에 갱신된다.
-- 이전 NEED_HELP 행은 읽기 API에서 SEEKING/WAITING으로 호환 표시한다.
