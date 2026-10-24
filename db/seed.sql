-- =====================================================================
-- 이음 개발 · 시연용 시드 (PM 작성, 주인 BE-B)
-- 실행: mysql -u <계정> -p ieum < db/seed.sql   (schema.sql 실행 직후, 빈 DB에)
-- 수료생 50명: 평가용 20 (id 1~20, 등록 원문 있음 · id 1은 통합 기간에 시연 주인공 자료로 교체) · 명단용 30 (id 21~50, 역량 프로필 + 응답 이력)
-- 공고: 실제 공고는 BE-A 배치(서울 열린데이터광장)로 받는다. 여기에는
--   - 501~503: 배치 전 개발용 임시 공고 (source DEV_SEED). 첫 배치 후 BE-A가 is_active = 0 으로 끈다
--   - 601~612: 명단용 수료생의 지난 응답 이력에 필요한 마감 공고 (source DEV_SEED, is_active = 0)
-- 시각은 실행 시점 기준 상대값(N일 전)이라 언제 넣어도 상태 판정 결과가 같다.
-- 모든 이름 · 회사 · 연락처 · 수치는 모의 데이터.
-- =====================================================================
USE ieum;
SET NAMES utf8mb4;

INSERT INTO course (id, name, cohort, campus, started_on, ended_on, curriculum_skills) VALUES
 (1, '청년취업사관학교 AI Agent 과정', '4기', '동대문캠퍼스', '2026-03-02', '2026-08-29', JSON_ARRAY('Java','Spring Boot','MySQL','React','FastAPI','LLM API')),
 (2, '청년취업사관학교 클라우드 풀스택 과정', '3기', '동대문캠퍼스', '2026-02-02', '2026-07-31', JSON_ARRAY('Java','Spring Boot','React','AWS','Docker'));

INSERT INTO student (id, course_id, name, email, github_url, desired_job, preferred_region, military_status, skills, completed_on, last_response_at) VALUES
 (1, 1, '김민준', 'minjun.dev@example.com', 'github.com/minjun-dev', '백엔드 개발', '서울', NULL, '["Java", "Spring Boot", "MySQL", "FastAPI"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 18 HOUR), INTERVAL 1 DAY)),
 (2, 1, '이서준', 'student02@example.com', 'github.com/ieum-seed-02', '백엔드 개발', '서울', 'SERVED', '["Spring Boot", "MySQL", "JPA", "React", "Java", "Servlet"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 1 DAY)),
 (3, 2, '정하은', 'student03@example.com', 'github.com/ieum-seed-03', '프론트엔드 개발', '서울', NULL, '["React", "TypeScript", "Tailwind CSS", "Zustand"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 10 HOUR), INTERVAL 2 DAY)),
 (4, 1, '최지훈', 'student04@example.com', 'github.com/ieum-seed-04', 'AI 서비스 개발', '서울, 경기', 'SERVED', '["Python", "FastAPI", "LangChain", "ChromaDB", "OpenAI API", "scikit-learn"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 11 HOUR), INTERVAL 3 DAY)),
 (5, 2, '한예린', 'student05@example.com', 'github.com/ieum-seed-05', '풀스택 개발', '서울', NULL, '["Spring Boot", "React", "MySQL", "AWS", "Docker"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 12 HOUR), INTERVAL 4 DAY)),
 (6, 1, '윤도현', 'student06@example.com', 'github.com/ieum-seed-06', '백엔드 개발', '서울', 'SERVED', '["Spring Boot", "MySQL"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 13 HOUR), INTERVAL 5 DAY)),
 (7, 2, '강수아', 'student07@example.com', 'github.com/ieum-seed-07', '프론트엔드 개발', '경기', NULL, '["Vue.js", "JavaScript"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 14 HOUR), INTERVAL 1 DAY)),
 (8, 1, '오민석', 'student08@example.com', 'github.com/ieum-seed-08', 'AI 서비스 개발', '서울', 'SERVED', '["Python", "FastAPI", "Gemini API", "Whisper", "React"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 15 HOUR), INTERVAL 2 DAY)),
 (9, 2, '신유진', 'student09@example.com', 'github.com/ieum-seed-09', '백엔드 개발', '서울', NULL, '["Spring Boot", "MyBatis", "MySQL", "Redis"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 16 HOUR), INTERVAL 3 DAY)),
 (10, 1, '임건우', 'student10@example.com', 'github.com/ieum-seed-10', '게임 클라이언트 개발', '서울', 'SERVED', '["Unity", "C#"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 17 HOUR), INTERVAL 4 DAY)),
 (11, 2, '배소윤', 'student11@example.com', 'github.com/ieum-seed-11', '프론트엔드 개발', '서울', NULL, '["Next.js", "TypeScript", "WebSocket", "Canvas"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 5 DAY)),
 (12, 1, '박서연', 'student12@example.com', 'github.com/ieum-seed-12', '데이터 엔지니어', '서울', NULL, '["Python", "Airflow", "PostgreSQL", "Streamlit"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 10 HOUR), INTERVAL 1 DAY)),
 (13, 2, '조현우', 'student13@example.com', 'github.com/ieum-seed-13', '백엔드 개발', '부산', 'SERVED', '["Spring Boot", "MySQL"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 11 HOUR), INTERVAL 2 DAY)),
 (14, 1, '황지민', 'student14@example.com', 'github.com/ieum-seed-14', 'AI 서비스 개발', '서울', NULL, '["Python", "Streamlit", "OpenAI API"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 12 HOUR), INTERVAL 3 DAY)),
 (15, 2, '남궁태오', 'student15@example.com', 'github.com/ieum-seed-15', '풀스택 개발', '서울', 'SERVED', '["Spring Boot", "WebSocket", "React", "Redis", "AWS", "Next.js"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 13 HOUR), INTERVAL 4 DAY)),
 (16, 1, '문채원', 'student16@example.com', 'github.com/ieum-seed-16', '프론트엔드 개발', '서울', NULL, '["React", "TypeScript", "React Query", "Gemini API"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 14 HOUR), INTERVAL 5 DAY)),
 (17, 2, '서지호', 'student17@example.com', 'github.com/ieum-seed-17', '임베디드 개발', '경기', 'SERVED', '["Arduino", "C", "Spring Boot"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 15 HOUR), INTERVAL 1 DAY)),
 (18, 1, '권나연', 'student18@example.com', 'github.com/ieum-seed-18', '백엔드 개발', '서울', NULL, '["Spring Boot", "MyBatis", "MySQL", "FastAPI", "OpenAI API"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 16 HOUR), INTERVAL 2 DAY)),
 (19, 2, '유하람', 'student19@example.com', 'github.com/ieum-seed-19', '풀스택 개발', '서울', NULL, '["React", "Node.js"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 17 HOUR), INTERVAL 3 DAY)),
 (20, 1, '장우진', 'student20@example.com', 'github.com/ieum-seed-20', 'AI 서비스 개발', '서울', 'EXEMPT', '["Python", "FastAPI", "Gemini API", "Qdrant", "Docker"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 4 DAY)),
 (21, 1, '최유나', 'student21@example.com', NULL, '프론트엔드 개발', '서울', NULL, '["React", "TypeScript", "Tailwind CSS"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 10 HOUR), INTERVAL 10 DAY)),
 (22, 2, '김태윤', 'student22@example.com', NULL, 'AI 서비스 개발', '서울', NULL, '["Python", "FastAPI", "LLM API"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 11 HOUR), INTERVAL 15 DAY)),
 (23, 1, '이수빈', 'student23@example.com', NULL, '풀스택 개발', '서울', NULL, '["Spring Boot", "React", "AWS"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 12 HOUR), INTERVAL 4 DAY)),
 (24, 2, '박준영', 'student24@example.com', NULL, '데이터 엔지니어', '서울', NULL, '["Python", "SQL", "Airflow"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 13 HOUR), INTERVAL 5 DAY)),
 (25, 1, '정다은', 'student25@example.com', NULL, '백엔드 개발', '서울', NULL, '["Java", "Spring Boot", "MySQL"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 14 HOUR), INTERVAL 9 DAY)),
 (26, 2, '강민혁', 'student26@example.com', NULL, '프론트엔드 개발', '서울', NULL, '["React", "TypeScript", "Tailwind CSS"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 15 HOUR), INTERVAL 8 DAY)),
 (27, 1, '조은서', 'student27@example.com', NULL, 'AI 서비스 개발', '서울', NULL, '["Python", "FastAPI", "LLM API"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 16 HOUR), INTERVAL 3 DAY)),
 (28, 2, '윤재원', 'student28@example.com', NULL, '풀스택 개발', '서울', NULL, '["Spring Boot", "React", "AWS"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 17 HOUR), INTERVAL 3 DAY)),
 (29, 1, '장서현', 'student29@example.com', NULL, '데이터 엔지니어', '서울', NULL, '["Python", "SQL", "Airflow"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 18 HOUR), INTERVAL 5 DAY)),
 (30, 2, '임현수', 'student30@example.com', NULL, '백엔드 개발', '서울', NULL, '["Java", "Spring Boot", "MySQL"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 14 DAY)),
 (31, 1, '한지우', 'student31@example.com', NULL, '프론트엔드 개발', '서울', NULL, '["React", "TypeScript", "Tailwind CSS"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 10 HOUR), INTERVAL 21 DAY)),
 (32, 2, '오세린', 'student32@example.com', NULL, 'AI 서비스 개발', '서울', NULL, '["Python", "FastAPI", "LLM API"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 11 HOUR), INTERVAL 3 DAY)),
 (33, 1, '서민재', 'student33@example.com', NULL, '풀스택 개발', '서울', NULL, '["Spring Boot", "React", "AWS"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 12 HOUR), INTERVAL 4 DAY)),
 (34, 2, '신동하', 'student34@example.com', NULL, '데이터 엔지니어', '서울', NULL, '["Python", "SQL", "Airflow"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 13 HOUR), INTERVAL 5 DAY)),
 (35, 1, '권예은', 'student35@example.com', NULL, '백엔드 개발', '서울', NULL, '["Java", "Spring Boot", "MySQL"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 14 HOUR), INTERVAL 21 DAY)),
 (36, 2, '황민규', 'student36@example.com', NULL, '프론트엔드 개발', '서울', NULL, '["React", "TypeScript", "Tailwind CSS"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 15 HOUR), INTERVAL 9 DAY)),
 (37, 1, '안소희', 'student37@example.com', NULL, 'AI 서비스 개발', '서울', NULL, '["Python", "FastAPI", "LLM API"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 16 HOUR), INTERVAL 3 DAY)),
 (38, 2, '송준호', 'student38@example.com', NULL, '풀스택 개발', '서울', NULL, '["Spring Boot", "React", "AWS"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 17 HOUR), INTERVAL 15 DAY)),
 (39, 1, '전가은', 'student39@example.com', NULL, '데이터 엔지니어', '서울', NULL, '["Python", "SQL", "Airflow"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 18 HOUR), INTERVAL 5 DAY)),
 (40, 2, '이도윤', 'student40@example.com', NULL, '백엔드 개발', '서울', NULL, '["Java", "Spring Boot", "MySQL"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 3 DAY)),
 (41, 1, '홍성민', 'student41@example.com', NULL, '프론트엔드 개발', '서울', NULL, '["React", "TypeScript", "Tailwind CSS"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 10 HOUR), INTERVAL 21 DAY)),
 (42, 2, '고유나', 'student42@example.com', NULL, 'AI 서비스 개발', '서울', NULL, '["Python", "FastAPI", "LLM API"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 11 HOUR), INTERVAL 3 DAY)),
 (43, 1, '양승현', 'student43@example.com', NULL, '풀스택 개발', '서울', NULL, '["Spring Boot", "React", "AWS"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 12 HOUR), INTERVAL 21 DAY)),
 (44, 2, '백지원', 'student44@example.com', NULL, '데이터 엔지니어', '서울', NULL, '["Python", "SQL", "Airflow"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 13 HOUR), INTERVAL 5 DAY)),
 (45, 1, '허준서', 'student45@example.com', NULL, '백엔드 개발', '서울', NULL, '["Java", "Spring Boot", "MySQL"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 14 HOUR), INTERVAL 12 DAY)),
 (46, 2, '노하린', 'student46@example.com', NULL, '프론트엔드 개발', '서울', NULL, '["React", "TypeScript", "Tailwind CSS"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 15 HOUR), INTERVAL 15 DAY)),
 (47, 1, '구본재', 'student47@example.com', NULL, 'AI 서비스 개발', '서울', NULL, '["Python", "FastAPI", "LLM API"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 16 HOUR), INTERVAL 3 DAY)),
 (48, 2, '문지아', 'student48@example.com', NULL, '풀스택 개발', '서울', NULL, '["Spring Boot", "React", "AWS"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 17 HOUR), INTERVAL 4 DAY)),
 (49, 1, '류시우', 'student49@example.com', NULL, '데이터 엔지니어', '서울', NULL, '["Python", "SQL", "Airflow"]', '2026-08-29', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 18 HOUR), INTERVAL 9 DAY)),
 (50, 2, '차은비', 'student50@example.com', NULL, '백엔드 개발', '서울', NULL, '["Java", "Spring Boot", "MySQL"]', '2026-07-31', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 17 DAY));

INSERT INTO student_education (student_id, school_name, major, degree, status, graduated_on) VALUES
 (1, '○○대학교', '경영학과', 'BACHELOR', 'GRADUATED', '2025-02-28'),
 (2, '○○대학교', '컴퓨터공학과', 'BACHELOR', 'GRADUATED', '2025-02-28'),
 (3, '△△여자대학교', '시각디자인학과', 'BACHELOR', 'GRADUATED', '2024-02-20'),
 (4, '□□대학교', '통계학과', 'BACHELOR', 'GRADUATED', '2025-08-22'),
 (5, '◇◇대학교', '경영정보학과', 'BACHELOR', 'GRADUATED', '2025-02-21'),
 (6, '☆☆전문대학', '전자과', 'ASSOCIATE', 'GRADUATED', '2021-02-15'),
 (7, '▽▽대학교', '국어국문학과', 'BACHELOR', 'GRADUATED', '2023-02-17'),
 (8, '○○대학교', '산업공학과', 'BACHELOR', 'GRADUATED', '2025-02-28'),
 (9, '△△대학교', '수학과', 'BACHELOR', 'GRADUATED', '2024-08-23'),
 (10, '□□대학교', '게임공학과', 'BACHELOR', 'GRADUATED', '2025-02-28'),
 (11, '◇◇대학교', '컴퓨터공학과', 'BACHELOR', 'EXPECTED', '2027-02-20'),
 (12, '○○대학교', '정보통계학과', 'BACHELOR', 'GRADUATED', '2025-02-28'),
 (13, '△△대학교', '기계공학과', 'BACHELOR', 'GRADUATED', '2023-02-17'),
 (14, '▽▽대학교', '심리학과', 'BACHELOR', 'GRADUATED', '2024-02-16'),
 (15, '☆☆대학교', '컴퓨터공학과', 'BACHELOR', 'GRADUATED', '2026-02-20'),
 (16, '○○대학교', '미디어커뮤니케이션학과', 'BACHELOR', 'GRADUATED', '2024-08-23'),
 (17, '△△대학교', '전자공학과', 'BACHELOR', 'GRADUATED', '2025-02-21'),
 (18, '◇◇대학교', '소프트웨어학과', 'BACHELOR', 'GRADUATED', '2025-08-22'),
 (19, '▽▽대학교', '경제학과', 'BACHELOR', 'GRADUATED', '2024-02-16'),
 (20, '○○대학교', '컴퓨터공학과', 'MASTER', 'GRADUATED', '2025-08-22');

INSERT INTO student_certificate (student_id, name, issuer, acquired_on) VALUES
 (1, '정보처리기사', '한국산업인력공단', '2026-03-20'),
 (2, '정보처리기사', '한국산업인력공단', '2025-06-13'),
 (2, 'SQLD', '한국데이터산업진흥원', '2026-04-04'),
 (3, '웹디자인개발기능사', '한국산업인력공단', '2025-09-12'),
 (4, 'ADsP', '한국데이터산업진흥원', '2025-11-15'),
 (5, '정보처리기사', '한국산업인력공단', '2026-03-20'),
 (5, 'AWS Certified Cloud Practitioner', 'Amazon Web Services', '2026-07-10'),
 (8, '빅데이터분석기사', '한국데이터산업진흥원', '2026-06-27'),
 (9, 'SQLD', '한국데이터산업진흥원', '2025-12-06'),
 (11, '정보처리기사', '한국산업인력공단', '2026-06-12'),
 (12, 'SQLD', '한국데이터산업진흥원', '2025-09-06'),
 (12, 'ADsP', '한국데이터산업진흥원', '2026-03-07'),
 (13, '정보처리기사', '한국산업인력공단', '2026-03-20'),
 (15, '정보처리기사', '한국산업인력공단', '2026-06-12'),
 (17, '전자기사', '한국산업인력공단', '2024-11-29'),
 (18, '정보처리기사', '한국산업인력공단', '2025-09-12'),
 (20, '정보처리기사', '한국산업인력공단', '2023-06-16');

INSERT INTO student_project (id, student_id, title, period_text, team_size, role_note, readme, tech_stack, portfolio_file_url, include_in_apply) VALUES
 (11, 1, '모아요 · 스터디 모집 웹서비스', '6주', 4, '백엔드 담당. 모집글 CRUD API와 로그인, 목록 조회 성능 개선을 맡음', '# 모아요
스터디 모집 웹서비스.
## 내가 한 일
- 모집글 CRUD 등 REST API 12개 설계 · 구현
- 목록 API에서 N+1 쿼리 발생 → fetch join 적용. 평균 응답 1.8s → 0.4s', '["Spring Boot", "MySQL", "React"]', '모아요_포트폴리오.pdf', 1),
 (12, 1, 'AI 면접 코치', '4주', 3, '백엔드와 AI 연동 담당', '# AI 면접 코치
직무별 면접 질문을 만들고 답변에 피드백을 주는 서비스.
## 내가 한 일
- Gemini API로 질문 생성 · 답변 피드백 기능 구현
- FastAPI 서버 구성', '["FastAPI", "Gemini API"]', NULL, 0),
 (101, 2, '가계부 공유 서비스 같이쓰자', '2026.06 – 2026.07 (6주)', 4, '백엔드 리드. 인증(JWT)과 정산 API, 배포 담당. ERD 설계를 주도함', '# 같이쓰자 · 공유 가계부
룸메이트나 커플이 함께 쓰는 공유 가계부입니다. 지출을 등록하면 정산 금액을 자동으로 계산해 줍니다.

## 주요 기능
- 그룹 생성과 초대 링크
- 지출 등록, 카테고리별 통계
- 월말 정산: 누가 누구에게 얼마를 보내야 하는지 계산

## 내가 한 일
- JWT 기반 로그인 · 토큰 재발급 구현
- 정산 알고리즘 구현: 송금 횟수를 최소화하도록 채권 · 채무를 정리
- 통계 API에서 월별 집계 쿼리를 인덱스로 개선해 응답 1.2초 → 0.3초
- AWS EC2 · RDS 배포, GitHub Actions로 자동 배포 구성

## 트러블슈팅
동시에 같은 지출을 수정할 때 금액이 덮어써지는 문제가 있어 낙관적 락(@Version)을 적용했습니다.', '["Spring Boot", "MySQL", "JPA", "React"]', '가계부_포트폴리오.pdf', 1),
 (102, 2, '도서관 좌석 예약', '2026.04 (2주)', 3, '좌석 예약 · 취소 기능 담당', '# 도서관 좌석 예약
JSP/Servlet으로 만든 미니 프로젝트. 좌석 배치도에서 빈 좌석을 골라 2시간 단위로 예약합니다.
- 예약 중복을 막기 위해 DB 유니크 제약 사용
- 예약 만료 스케줄러 작성', '["Java", "Servlet", "Oracle"]', NULL, 0),
 (103, 3, '반려식물 일지 플랜토리', '2026.06 – 2026.07 (5주)', 4, '프론트엔드 담당. 디자인 시스템 컴포넌트 12개와 일지 작성 화면, 반응형 레이아웃', '# 플랜토리 · 반려식물 일지
물 준 날짜와 사진을 기록하면 다음 물 주기를 알려 주는 서비스입니다.

## 내가 한 일
- Figma 디자인을 바탕으로 버튼 · 카드 · 모달 등 공통 컴포넌트 12개 제작, Storybook으로 문서화
- 사진 업로드 시 브라우저에서 이미지를 압축해 업로드 시간을 평균 60% 단축
- 캘린더 화면 렌더링이 느려 memo와 가상 스크롤 적용, 스크롤 끊김 해결
- Lighthouse 접근성 점수 72 → 95 (대체 텍스트, 포커스 순서, 대비 개선)

## 배운 점
디자이너 출신이라 화면을 그리는 건 빨랐지만, 상태 관리가 복잡해지면서 Zustand로 전역 상태를 나누는 기준을 세웠습니다.', '["React", "TypeScript", "Tailwind CSS", "Zustand"]', '반려식물_포트폴리오.pdf', 1),
 (104, 4, '법률 상담 RAG 챗봇 로봇변호사', '2026.07 – 2026.08 (6주)', 5, 'AI 파트. 문서 청킹 전략과 검색 품질 평가를 맡음. 리랭커 도입 실험 진행', '# 로봇변호사 · 생활 법률 RAG 챗봇
임대차 · 근로 관련 법령과 판례를 검색해 근거와 함께 답하는 챗봇입니다.

## 구조
- 법령 · 판례 PDF 2,300건을 조항 단위로 청킹해 벡터 DB에 저장
- 질문 → 하이브리드 검색(BM25 + 임베딩) → 리랭커로 상위 5개 → LLM 답변 + 출처 표시

## 내가 한 일
- 청킹 단위 실험: 고정 길이 대비 조항 단위가 정답 포함률 61% → 78%
- 크로스 인코더 리랭커 도입으로 Top-5 정답 포함률 78% → 86%
- 평가셋 120문항을 직접 만들고 자동 채점 스크립트 작성
- 답변에 근거가 없으면 "확인할 수 없음"으로 답하도록 프롬프트와 후처리 구성', '["Python", "FastAPI", "LangChain", "ChromaDB", "OpenAI API"]', '법률_포트폴리오.pdf', 1),
 (105, 4, '중고차 가격 예측', '2026.05 (2주)', 2, '데이터 전처리와 모델링', '# 중고차 가격 예측
공공 데이터 4만 건으로 중고차 시세를 예측했습니다. 이상치 제거와 범주형 인코딩 후 LightGBM으로 MAPE 9.8%.', '["Python", "scikit-learn", "pandas"]', NULL, 0),
 (106, 5, '동네 소모임 플랫폼 모여라', '2026.06 – 2026.07 (6주)', 4, '풀스택. 모임 생성 · 참여 기능 전체(화면+API), Docker 배포', '# 모여라 · 동네 소모임
지역 기반으로 소모임을 만들고 참여하는 서비스입니다.

## 내가 한 일
- 모임 생성 · 검색 · 참여 신청 화면(React)과 API(Spring Boot)를 함께 개발
- 위치 기반 검색: 위경도 범위 쿼리 후 거리 계산
- Docker Compose로 개발 환경 통일, EC2에 배포
- 참여 인원 초과 문제를 비관적 락으로 해결', '["Spring Boot", "React", "MySQL", "AWS", "Docker"]', '동네_포트폴리오.pdf', 1),
 (107, 6, '팀 프로젝트 쇼핑몰', '2026.07 (4주)', 5, '백엔드 일부 참여', '# 쇼핑몰 프로젝트
상품, 장바구니, 주문 기능이 있는 쇼핑몰입니다. Spring Boot로 만들었습니다.', '["Spring Boot", "MySQL"]', NULL, 1),
 (108, 7, '영화 리뷰 사이트', '2026.05 – 2026.06 (3주)', 3, '화면 몇 개 만듦', '# 무비톡
영화 리뷰를 남기는 사이트. 영화 API를 연동했습니다. 별점과 댓글 기능이 있습니다.', '["Vue.js", "JavaScript"]', NULL, 1),
 (109, 8, '회의록 요약 에이전트 미팅노트', '2026.07 – 2026.08 (6주)', 4, 'AI 파이프라인 전체. 음성 인식 → 화자 분리 → 요약 → 할 일 추출. 프롬프트 버전 관리', '# 미팅노트 · 회의록 요약 에이전트
회의 녹음을 올리면 요약과 담당자별 할 일을 뽑아 줍니다.

## 파이프라인
1. Whisper로 음성 인식
2. 화자 분리 후 발화 정리
3. LLM으로 요약 · 결정 사항 · 할 일 추출 (JSON 출력 강제)

## 내가 한 일
- 긴 회의(90분 이상)는 구간별 요약 후 합치는 map-reduce 방식 적용
- 출력 JSON 스키마를 pydantic으로 검증, 실패 시 1회 재요청
- 할 일 추출 정확도를 팀원 3명이 수기 채점: 71% → 88%
- 프롬프트를 파일로 버전 관리하고 결과를 비교하는 스크립트 작성', '["Python", "FastAPI", "Gemini API", "Whisper", "React"]', '회의록_포트폴리오.pdf', 1),
 (110, 9, '병원 예약 시스템 닥터콜', '2026.06 – 2026.07 (6주)', 4, '예약 API와 대기열. Redis로 동시 예약 제어', '# 닥터콜 · 병원 예약
진료과와 시간대를 골라 예약하는 서비스입니다.

## 내가 한 일
- 예약 API 설계(MyBatis 동적 쿼리)
- 같은 시간대에 예약이 몰릴 때 Redis 분산 락으로 중복 예약 방지
- 예약 1시간 전 알림 배치 작성
- JMeter 부하 테스트: 동시 200명에서 중복 예약 0건 확인', '["Spring Boot", "MyBatis", "MySQL", "Redis"]', NULL, 1),
 (111, 10, '2D 로그라이크 던전런', '2026.07 – 2026.08 (5주)', 3, '전투 시스템과 몬스터 AI 담당', '# 던전런 · 2D 로그라이크
Unity로 만든 로그라이크 게임입니다. 방을 무작위로 생성하고 몬스터와 싸웁니다.
- 상태 머신 기반 몬스터 AI
- 오브젝트 풀링으로 투사체 생성 비용 감소', '["Unity", "C#"]', NULL, 1),
 (112, 11, '실시간 협업 화이트보드 그려봐', '2026.06 – 2026.07 (6주)', 3, '프론트 전체와 실시간 동기화. 커서 공유, 되돌리기 구현', '# 그려봐 · 실시간 화이트보드
여러 명이 같은 보드에 그림을 그리는 서비스입니다.

## 내가 한 일
- Canvas 렌더링과 도형 선택 · 이동
- WebSocket으로 도형 변경을 실시간 동기화, 충돌 시 마지막 수정 우선
- 되돌리기/다시하기를 명령 패턴으로 구현
- 도형 1,000개 이상에서 프레임 저하 → 보이는 영역만 다시 그리도록 개선', '["Next.js", "TypeScript", "WebSocket", "Canvas"]', NULL, 1),
 (113, 12, '서울 따릉이 수요 대시보드', '2026.06 – 2026.07 (5주)', 3, '데이터 파이프라인. 공공 API 수집 → 정제 → 적재 자동화', '# 따릉이 수요 대시보드
서울 공공자전거 대여 데이터를 매시간 수집해 대여소별 수요를 보여 줍니다.

## 내가 한 일
- Airflow DAG로 수집 · 정제 · 적재 자동화 (매시간)
- 결측과 중복 처리 규칙 정리, 데이터 품질 체크 태스크 추가
- 시간대 · 날씨별 수요 집계 테이블 설계
- Streamlit 대시보드로 대여소 지도와 시간대 그래프 제공', '["Python", "Airflow", "PostgreSQL", "Streamlit"]', '서울_포트폴리오.pdf', 1),
 (114, 13, '물류 재고 관리', '2026.07 (4주)', 4, '입출고 API', '# 재고 관리 시스템
창고 입출고를 기록하고 재고를 계산합니다. 입출고 API를 맡았습니다.', '["Spring Boot", "MySQL"]', NULL, 1),
 (115, 14, '감정 일기 챗봇', '2026.07 – 2026.08 (5주)', 4, 'AI 쪽 일부', '# 감정 일기 챗봇
일기를 쓰면 AI가 공감하는 답장을 줍니다. 프롬프트를 여러 번 바꿔 가며 답장 말투를 다듬었습니다.', '["Python", "Streamlit", "OpenAI API"]', NULL, 1),
 (116, 15, '중고 거래 채팅 마켓톡', '2026.06 – 2026.07 (6주)', 4, '채팅 서버와 채팅 화면. 읽음 처리, 안 읽은 메시지 수', '# 마켓톡 · 중고 거래 채팅
중고 거래 게시글에서 바로 1:1 채팅을 하는 서비스입니다.

## 내가 한 일
- STOMP over WebSocket 채팅 서버, Redis Pub/Sub로 서버 2대 간 메시지 전달
- 읽음 처리와 안 읽은 메시지 수 계산
- 채팅 화면(React): 무한 스크롤로 이전 메시지 불러오기
- 메시지 저장을 비동기로 바꿔 전송 지연 감소', '["Spring Boot", "WebSocket", "React", "Redis", "AWS"]', '중고_포트폴리오.pdf', 1),
 (117, 15, '개인 블로그', '2026.05 (1주)', 1, '혼자 제작', '# 개인 블로그
Next.js 정적 생성으로 만든 기술 블로그. 배운 내용을 30편 정리했습니다.', '["Next.js", "MDX"]', NULL, 0),
 (118, 16, 'AI 여행 일정 추천 트립메이트', '2026.07 – 2026.08 (6주)', 5, '프론트엔드 리드. 일정 편집 드래그 앤 드롭, 지도 연동, API 상태 관리', '# 트립메이트 · AI 여행 일정
여행지와 기간을 입력하면 AI가 일정을 짜 주고, 사용자가 드래그로 고칩니다.

## 내가 한 일
- 날짜별 일정 카드를 드래그 앤 드롭으로 옮기는 편집 화면
- 지도 SDK 연동, 일정 순서대로 경로 표시
- React Query로 서버 상태와 화면 상태 분리, 로딩 · 에러 화면 통일
- AI 응답이 늦을 때 단계별 진행 표시로 이탈률 감소', '["React", "TypeScript", "React Query", "Gemini API"]', 'AI_포트폴리오.pdf', 1),
 (119, 17, '스마트 화분 IoT', '2026.06 (3주)', 2, '센서 펌웨어와 서버 연동', '# 스마트 화분
토양 습도 센서 값을 서버로 보내고, 기준 이하면 펌프를 켭니다. 펌웨어는 C, 서버는 Spring Boot.', '["Arduino", "C", "Spring Boot"]', NULL, 1),
 (120, 18, 'AI 면접 연습 인터뷰온', '2026.07 – 2026.08 (6주)', 4, '백엔드 리드. Spring Boot와 AI 서버 연동, 비동기 처리, API 문서화', '# 인터뷰온 · AI 면접 연습
직무를 고르면 AI가 질문하고, 답변을 녹음하면 피드백을 줍니다.

## 내가 한 일
- Spring Boot 메인 서버와 FastAPI AI 서버를 나누고 연동 규격 정의
- 피드백 생성이 20초 이상 걸려 비동기 처리 + 상태 조회 API로 변경
- Swagger로 API 문서화, 프론트와 목업 응답을 먼저 맞춘 뒤 병렬 개발
- AI 서버 장애 시 재시도와 실패 상태 저장', '["Spring Boot", "MyBatis", "MySQL", "FastAPI", "OpenAI API"]', 'AI_포트폴리오.pdf', 1),
 (121, 19, '가게 예약 웹', '2026.06 (4주)', 3, '이것저것 맡음', '# 가게 예약
가게를 검색하고 예약하는 웹사이트입니다.', '["React", "Node.js"]', NULL, 1),
 (122, 20, '사내 문서 검색 에이전트 찾아줘', '2026.07 – 2026.08 (6주)', 4, 'AI 리드. 에이전트 도구 설계(검색 · 요약 · 표 추출), 평가 체계', '# 찾아줘 · 사내 문서 검색 에이전트
규정 · 매뉴얼 문서를 검색하고, 필요하면 표를 뽑아 비교해 주는 에이전트입니다.

## 내가 한 일
- 도구 3종(문서 검색, 요약, 표 추출)을 LLM이 골라 쓰는 에이전트 구조
- 문서 2,000건 임베딩 색인, 바뀐 문서만 다시 색인
- 답변마다 출처 문단을 붙이고, 출처와 답이 맞는지 자동 검사
- 평가셋 80문항: 정답률 82%, 근거 일치율 93%', '["Python", "FastAPI", "Gemini API", "Qdrant", "Docker"]', '사내_포트폴리오.pdf', 1),
 (123, 23, 'AI 상담 챗봇', '5주', 4, '화면 · API 함께 구현 담당', '# AI 상담 챗봇
팀 프로젝트. 화면 · API 함께 구현을(를) 맡았습니다.', '["Spring Boot", "React", "AWS"]', NULL, 1),
 (124, 29, '사내 예약 시스템', '5주', 4, '데이터 수집 · 정제 담당', '# 사내 예약 시스템
팀 프로젝트. 데이터 수집 · 정제을(를) 맡았습니다.', '["Python", "SQL", "Airflow"]', NULL, 1),
 (125, 37, '학습 관리 서비스', '5주', 4, 'LLM 기능 연동 담당', '# 학습 관리 서비스
팀 프로젝트. LLM 기능 연동을(를) 맡았습니다.', '["Python", "FastAPI", "LLM API"]', NULL, 1),
 (126, 44, '사내 예약 시스템', '5주', 4, '데이터 수집 · 정제 담당', '# 사내 예약 시스템
팀 프로젝트. 데이터 수집 · 정제을(를) 맡았습니다.', '["Python", "SQL", "Airflow"]', NULL, 1);

INSERT INTO student_cover_letter (student_id, content) VALUES
 (1, '저는 맡은 일을 끝까지 확인하는 개발자입니다. 모아요 프로젝트에서 마감 전날까지 직접 테스트하며 버그를 잡았습니다. 목록 조회가 느리다는 피드백을 받고 원인을 찾아 응답 시간을 크게 줄였습니다. AI 면접 코치 프로젝트에서는 LLM API를 서비스에 붙이며 예외 상황을 다루는 법을 배웠습니다.'),
 (2, '저는 데이터가 틀리지 않는 서비스를 만드는 백엔드 개발자가 되고 싶습니다. 공유 가계부 프로젝트에서 정산 금액이 한 번이라도 틀리면 사용자가 서비스를 떠난다는 걸 느꼈고, 동시 수정 문제를 직접 재현해 낙관적 락으로 해결했습니다. 통계 API가 느리다는 피드백에는 실행 계획을 보고 인덱스를 추가해 응답 시간을 4분의 1로 줄였습니다.'),
 (3, '시각디자인을 전공하고 개발로 전향했습니다. 디자인 의도를 정확하게 화면으로 옮기는 것이 제 강점입니다. 플랜토리 프로젝트에서 공통 컴포넌트를 먼저 만들어 팀원들이 같은 모양의 화면을 빠르게 만들 수 있게 했고, 접근성 점수를 95점까지 끌어올렸습니다.'),
 (4, '통계학을 전공하며 숫자로 증명하는 습관을 들였습니다. 법률 RAG 챗봇을 만들 때 감으로 프롬프트를 고치지 않고 평가셋 120문항을 먼저 만들어, 청킹과 리랭커 실험을 수치로 비교했습니다. 근거 없는 답을 하지 않는 AI 서비스를 만들고 싶습니다.'),
 (5, '화면부터 서버, 배포까지 한 기능을 끝까지 책임져 본 경험이 있습니다. 모여라 프로젝트에서 모임 참여 기능을 혼자 맡아 화면과 API를 함께 만들었고, 인원 초과 버그를 락으로 해결했습니다.'),
 (6, '성실하게 배우는 자세로 회사에 기여하겠습니다. 팀 프로젝트에서 맡은 일을 끝까지 했습니다.'),
 (7, '글을 다루던 경험을 살려 사용자가 읽기 쉬운 화면을 만들고 싶습니다.'),
 (8, 'LLM이 그럴듯한 결과를 내는 것보다, 매번 같은 형식으로 믿을 수 있는 결과를 내는 데 관심이 많습니다. 미팅노트에서 출력 스키마 검증과 재요청 로직을 넣어 형식 오류를 없앴고, 수기 채점으로 정확도를 88%까지 올렸습니다.'),
 (9, '수학을 전공해 논리적으로 문제를 쪼개는 데 익숙합니다. 닥터콜 프로젝트에서 동시 예약 문제를 재현하는 테스트부터 만들고 Redis 락으로 해결해 중복 예약 0건을 확인했습니다.'),
 (10, '게임 클라이언트 개발자가 되고 싶습니다. 교육 과정에서 웹을 배웠지만 Unity로 게임을 만들며 가장 즐거웠습니다.'),
 (11, '눈에 보이는 결과를 빠르게 만드는 걸 좋아하지만, 그 뒤의 구조를 더 고민합니다. 그려봐에서 되돌리기 기능을 명령 패턴으로 설계해 기능 추가가 쉬워졌습니다.'),
 (12, '데이터가 매일 끊기지 않고 들어오게 만드는 일에 보람을 느낍니다. 따릉이 프로젝트에서 수집부터 대시보드까지 파이프라인을 자동화했습니다.'),
 (13, '부산에서 근무하며 백엔드 개발자로 성장하고 싶습니다. 기계공학을 전공해 제조 · 물류 도메인을 이해하고 있습니다.'),
 (14, '사람의 마음을 이해하는 AI 서비스를 만들고 싶습니다.'),
 (15, '실시간 서비스의 어려움을 직접 겪어 봤습니다. 마켓톡에서 서버를 두 대로 늘리자 메시지가 사라지는 문제를 Redis Pub/Sub로 해결했습니다.'),
 (16, 'AI 기능을 사용자가 편하게 쓰도록 화면을 설계하는 데 관심이 있습니다. 트립메이트에서 AI 응답 대기 화면을 단계별로 보여 줘 사용자 이탈을 줄였습니다.'),
 (17, '하드웨어와 소프트웨어를 잇는 임베디드 개발자가 되고 싶습니다.'),
 (18, '서버 여러 개를 연결하는 구조에서 일해 보고 싶습니다. 인터뷰온에서 AI 서버 응답이 느린 문제를 비동기 처리와 상태 조회로 풀었고, 목업 응답을 먼저 정해 팀이 기다리지 않게 했습니다.'),
 (19, '여러 일을 해 보며 풀스택 개발자가 되고 싶다고 생각했습니다.'),
 (20, '에이전트가 틀린 답을 자신 있게 말하는 문제를 줄이고 싶습니다. 찾아줘에서 답변과 출처가 맞는지 자동으로 검사하는 단계를 넣어 근거 일치율 93%를 만들었습니다.');

INSERT INTO competency_profile (student_id, profile) VALUES
 (21, '{"roles": ["웹 화면 구현"], "skills": [{"name": "React", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "TypeScript", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "Tailwind CSS", "evidence": [{"refType": "COURSE", "refId": 1}]}], "achievements": [], "certificates": [], "desiredJob": "프론트엔드 개발", "preferredRegion": "서울"}'),
 (22, '{"roles": ["LLM 기능 연동"], "skills": [{"name": "Python", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "FastAPI", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "LLM API", "evidence": [{"refType": "COURSE", "refId": 2}]}], "achievements": [], "certificates": [], "desiredJob": "AI 서비스 개발", "preferredRegion": "서울"}'),
 (23, '{"roles": ["화면 · API 함께 구현"], "skills": [{"name": "Spring Boot", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "React", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "AWS", "evidence": [{"refType": "COURSE", "refId": 1}]}], "achievements": [], "certificates": [], "desiredJob": "풀스택 개발", "preferredRegion": "서울"}'),
 (24, '{"roles": ["데이터 수집 · 정제"], "skills": [{"name": "Python", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "SQL", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "Airflow", "evidence": [{"refType": "COURSE", "refId": 2}]}], "achievements": [], "certificates": [], "desiredJob": "데이터 엔지니어", "preferredRegion": "서울"}'),
 (25, '{"roles": ["백엔드 API 구현"], "skills": [{"name": "Java", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "Spring Boot", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "MySQL", "evidence": [{"refType": "COURSE", "refId": 1}]}], "achievements": [], "certificates": [], "desiredJob": "백엔드 개발", "preferredRegion": "서울"}'),
 (26, '{"roles": ["웹 화면 구현"], "skills": [{"name": "React", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "TypeScript", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "Tailwind CSS", "evidence": [{"refType": "COURSE", "refId": 2}]}], "achievements": [], "certificates": [], "desiredJob": "프론트엔드 개발", "preferredRegion": "서울"}'),
 (27, '{"roles": ["LLM 기능 연동"], "skills": [{"name": "Python", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "FastAPI", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "LLM API", "evidence": [{"refType": "COURSE", "refId": 1}]}], "achievements": [], "certificates": [], "desiredJob": "AI 서비스 개발", "preferredRegion": "서울"}'),
 (28, '{"roles": ["화면 · API 함께 구현"], "skills": [{"name": "Spring Boot", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "React", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "AWS", "evidence": [{"refType": "COURSE", "refId": 2}]}], "achievements": [], "certificates": [], "desiredJob": "풀스택 개발", "preferredRegion": "서울"}'),
 (29, '{"roles": ["데이터 수집 · 정제"], "skills": [{"name": "Python", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "SQL", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "Airflow", "evidence": [{"refType": "COURSE", "refId": 1}]}], "achievements": [], "certificates": [], "desiredJob": "데이터 엔지니어", "preferredRegion": "서울"}'),
 (30, '{"roles": ["백엔드 API 구현"], "skills": [{"name": "Java", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "Spring Boot", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "MySQL", "evidence": [{"refType": "COURSE", "refId": 2}]}], "achievements": [], "certificates": [], "desiredJob": "백엔드 개발", "preferredRegion": "서울"}'),
 (31, '{"roles": ["웹 화면 구현"], "skills": [{"name": "React", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "TypeScript", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "Tailwind CSS", "evidence": [{"refType": "COURSE", "refId": 1}]}], "achievements": [], "certificates": [], "desiredJob": "프론트엔드 개발", "preferredRegion": "서울"}'),
 (32, '{"roles": ["LLM 기능 연동"], "skills": [{"name": "Python", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "FastAPI", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "LLM API", "evidence": [{"refType": "COURSE", "refId": 2}]}], "achievements": [], "certificates": [], "desiredJob": "AI 서비스 개발", "preferredRegion": "서울"}'),
 (33, '{"roles": ["화면 · API 함께 구현"], "skills": [{"name": "Spring Boot", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "React", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "AWS", "evidence": [{"refType": "COURSE", "refId": 1}]}], "achievements": [], "certificates": [], "desiredJob": "풀스택 개발", "preferredRegion": "서울"}'),
 (34, '{"roles": ["데이터 수집 · 정제"], "skills": [{"name": "Python", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "SQL", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "Airflow", "evidence": [{"refType": "COURSE", "refId": 2}]}], "achievements": [], "certificates": [], "desiredJob": "데이터 엔지니어", "preferredRegion": "서울"}'),
 (35, '{"roles": ["백엔드 API 구현"], "skills": [{"name": "Java", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "Spring Boot", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "MySQL", "evidence": [{"refType": "COURSE", "refId": 1}]}], "achievements": [], "certificates": [], "desiredJob": "백엔드 개발", "preferredRegion": "서울"}'),
 (36, '{"roles": ["웹 화면 구현"], "skills": [{"name": "React", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "TypeScript", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "Tailwind CSS", "evidence": [{"refType": "COURSE", "refId": 2}]}], "achievements": [], "certificates": [], "desiredJob": "프론트엔드 개발", "preferredRegion": "서울"}'),
 (37, '{"roles": ["LLM 기능 연동"], "skills": [{"name": "Python", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "FastAPI", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "LLM API", "evidence": [{"refType": "COURSE", "refId": 1}]}], "achievements": [], "certificates": [], "desiredJob": "AI 서비스 개발", "preferredRegion": "서울"}'),
 (38, '{"roles": ["화면 · API 함께 구현"], "skills": [{"name": "Spring Boot", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "React", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "AWS", "evidence": [{"refType": "COURSE", "refId": 2}]}], "achievements": [], "certificates": [], "desiredJob": "풀스택 개발", "preferredRegion": "서울"}'),
 (39, '{"roles": ["데이터 수집 · 정제"], "skills": [{"name": "Python", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "SQL", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "Airflow", "evidence": [{"refType": "COURSE", "refId": 1}]}], "achievements": [], "certificates": [], "desiredJob": "데이터 엔지니어", "preferredRegion": "서울"}'),
 (40, '{"roles": ["백엔드 API 구현"], "skills": [{"name": "Java", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "Spring Boot", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "MySQL", "evidence": [{"refType": "COURSE", "refId": 2}]}], "achievements": [], "certificates": [], "desiredJob": "백엔드 개발", "preferredRegion": "서울"}'),
 (41, '{"roles": ["웹 화면 구현"], "skills": [{"name": "React", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "TypeScript", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "Tailwind CSS", "evidence": [{"refType": "COURSE", "refId": 1}]}], "achievements": [], "certificates": [], "desiredJob": "프론트엔드 개발", "preferredRegion": "서울"}'),
 (42, '{"roles": ["LLM 기능 연동"], "skills": [{"name": "Python", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "FastAPI", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "LLM API", "evidence": [{"refType": "COURSE", "refId": 2}]}], "achievements": [], "certificates": [], "desiredJob": "AI 서비스 개발", "preferredRegion": "서울"}'),
 (43, '{"roles": ["화면 · API 함께 구현"], "skills": [{"name": "Spring Boot", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "React", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "AWS", "evidence": [{"refType": "COURSE", "refId": 1}]}], "achievements": [], "certificates": [], "desiredJob": "풀스택 개발", "preferredRegion": "서울"}'),
 (44, '{"roles": ["데이터 수집 · 정제"], "skills": [{"name": "Python", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "SQL", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "Airflow", "evidence": [{"refType": "COURSE", "refId": 2}]}], "achievements": [], "certificates": [], "desiredJob": "데이터 엔지니어", "preferredRegion": "서울"}'),
 (45, '{"roles": ["백엔드 API 구현"], "skills": [{"name": "Java", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "Spring Boot", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "MySQL", "evidence": [{"refType": "COURSE", "refId": 1}]}], "achievements": [], "certificates": [], "desiredJob": "백엔드 개발", "preferredRegion": "서울"}'),
 (46, '{"roles": ["웹 화면 구현"], "skills": [{"name": "React", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "TypeScript", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "Tailwind CSS", "evidence": [{"refType": "COURSE", "refId": 2}]}], "achievements": [], "certificates": [], "desiredJob": "프론트엔드 개발", "preferredRegion": "서울"}'),
 (47, '{"roles": ["LLM 기능 연동"], "skills": [{"name": "Python", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "FastAPI", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "LLM API", "evidence": [{"refType": "COURSE", "refId": 1}]}], "achievements": [], "certificates": [], "desiredJob": "AI 서비스 개발", "preferredRegion": "서울"}'),
 (48, '{"roles": ["화면 · API 함께 구현"], "skills": [{"name": "Spring Boot", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "React", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "AWS", "evidence": [{"refType": "COURSE", "refId": 2}]}], "achievements": [], "certificates": [], "desiredJob": "풀스택 개발", "preferredRegion": "서울"}'),
 (49, '{"roles": ["데이터 수집 · 정제"], "skills": [{"name": "Python", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "SQL", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"name": "Airflow", "evidence": [{"refType": "COURSE", "refId": 1}]}], "achievements": [], "certificates": [], "desiredJob": "데이터 엔지니어", "preferredRegion": "서울"}'),
 (50, '{"roles": ["백엔드 API 구현"], "skills": [{"name": "Java", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "Spring Boot", "evidence": [{"refType": "COURSE", "refId": 2}]}, {"name": "MySQL", "evidence": [{"refType": "COURSE", "refId": 2}]}], "achievements": [], "certificates": [], "desiredJob": "백엔드 개발", "preferredRegion": "서울"}');

-- 개발용 임시 공고 (배치 전). 첫 배치 후: UPDATE job_posting SET is_active = 0 WHERE source = 'DEV_SEED';
INSERT INTO job_posting (id, source, external_id, company_name, title, job_code, job_category, location, employment_type, career, education, salary_text, description, deadline_on, until_filled, posted_on, is_active) VALUES
 (501, 'DEV_SEED', 'dev-501', '(주)온다랩', '백엔드 인턴 (AI 서비스팀)', '133202', '응용 소프트웨어 개발자', '서울 성동구', '인턴', '신입', '학력무관', '월 250만원', '[직무] 응용 소프트웨어 개발자
[내용] AI 상담 서비스 백엔드 API 개발, LLM 연동 기능 유지보수
[필요 역량] Java · Spring Boot 사용 경험
[우대 조건] LLM API 연동 경험', DATE_ADD(CURDATE(), INTERVAL 7 DAY), 0, DATE_SUB(CURDATE(), INTERVAL 4 DAY), 1),
 (502, 'DEV_SEED', 'dev-502', '데이터웨이브(주)', '주니어 데이터 엔지니어', '133300', '데이터 엔지니어', '서울 강남구', '정규직', '신입', '대졸', '회사 내규', '[직무] 데이터 엔지니어
[내용] 데이터 수집 파이프라인 운영, SQL 기반 리포트 작성
[필요 역량] SQL 활용 능력
[우대 조건] SQLD, Python', DATE_ADD(CURDATE(), INTERVAL 14 DAY), 1, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 1),
 (503, 'DEV_SEED', 'dev-503', '(주)서울에이아이랩', 'AI 서비스 기획 보조', '133900', '서비스 기획자', '서울 마포구', '계약직', '신입', '학력무관', '월 230만원', '[직무] 서비스 기획자
[내용] LLM 기반 서비스 기획 보조, 사용자 피드백 정리
[우대 조건] LLM 서비스 개발 · 사용 경험', DATE_ADD(CURDATE(), INTERVAL 22 DAY), 0, DATE_SUB(CURDATE(), INTERVAL 1 DAY), 1),
 (601, 'DEV_SEED', 'dev-601', '(주)브릿지웍스', '주니어 백엔드 개발자', '133202', '응용 소프트웨어 개발자', '서울 영등포구', '정규직', '신입', '학력무관', '회사 내규', '[직무] 응용 소프트웨어 개발자
[내용] Java · Spring Boot 기반 사내 시스템 API 개발', DATE_SUB(CURDATE(), INTERVAL 11 DAY), 0, DATE_SUB(CURDATE(), INTERVAL 47 DAY), 0),
 (602, 'DEV_SEED', 'dev-602', '코드너리(주)', '프론트엔드 개발자 (신입)', '133201', '웹 개발자', '서울 강남구', '정규직', '신입', '학력무관', '회사 내규', '[직무] 웹 개발자
[내용] React · TypeScript로 B2B 관리 화면 개발', DATE_SUB(CURDATE(), INTERVAL 5 DAY), 0, DATE_SUB(CURDATE(), INTERVAL 48 DAY), 0),
 (603, 'DEV_SEED', 'dev-603', '(주)데이터숲', '데이터 엔지니어 인턴', '133300', '데이터 엔지니어', '서울 마포구', '인턴', '신입', '학력무관', '회사 내규', '[직무] 데이터 엔지니어
[내용] ETL 파이프라인 운영 보조, SQL 리포트 작성', DATE_SUB(CURDATE(), INTERVAL 6 DAY), 0, DATE_SUB(CURDATE(), INTERVAL 40 DAY), 0),
 (604, 'DEV_SEED', 'dev-604', '(주)한빛솔루션', '웹 풀스택 개발자', '133201', '웹 개발자', '서울 구로구', '정규직', '관계없음', '학력무관', '회사 내규', '[직무] 웹 개발자
[내용] Spring Boot · Vue 기반 고객사 웹 구축', DATE_SUB(CURDATE(), INTERVAL 7 DAY), 0, DATE_SUB(CURDATE(), INTERVAL 41 DAY), 0),
 (605, 'DEV_SEED', 'dev-605', '에이아이웨이브(주)', 'LLM 서비스 개발 (주니어)', '133202', '응용 소프트웨어 개발자', '서울 성동구', '계약직', '신입', '학력무관', '회사 내규', '[직무] 응용 소프트웨어 개발자
[내용] LLM API 연동, 프롬프트 · 평가 파이프라인 개발', DATE_SUB(CURDATE(), INTERVAL 8 DAY), 0, DATE_SUB(CURDATE(), INTERVAL 42 DAY), 0),
 (606, 'DEV_SEED', 'dev-606', '(주)모바일팩토리', '앱 서버 개발자', '133202', '응용 소프트웨어 개발자', '경기 성남시', '정규직', '경력', '학력무관', '회사 내규', '[직무] 응용 소프트웨어 개발자
[내용] 모바일 앱 백엔드 API, 결제 연동', DATE_SUB(CURDATE(), INTERVAL 9 DAY), 0, DATE_SUB(CURDATE(), INTERVAL 43 DAY), 0),
 (607, 'DEV_SEED', 'dev-607', '(주)스마트물류', '물류 시스템 운영 개발', '133202', '응용 소프트웨어 개발자', '경기 이천시', '정규직', '신입', '학력무관', '회사 내규', '[직무] 응용 소프트웨어 개발자
[내용] WMS 유지보수, 입출고 API 개발', DATE_SUB(CURDATE(), INTERVAL 10 DAY), 0, DATE_SUB(CURDATE(), INTERVAL 44 DAY), 0),
 (608, 'DEV_SEED', 'dev-608', '그린IT(주)', '사내 정보시스템 운영', '133400', '정보시스템 운영자', '서울 중구', '계약직', '관계없음', '학력무관', '회사 내규', '[직무] 정보시스템 운영자
[내용] 사내 그룹웨어 · 서버 운영, 장애 대응', DATE_SUB(CURDATE(), INTERVAL 11 DAY), 0, DATE_SUB(CURDATE(), INTERVAL 45 DAY), 0),
 (609, 'DEV_SEED', 'dev-609', '(주)픽셀랩', '웹 퍼블리셔 · 프론트엔드', '133201', '웹 개발자', '서울 마포구', '정규직', '신입', '학력무관', '회사 내규', '[직무] 웹 개발자
[내용] 반응형 웹 퍼블리싱, 접근성 개선', DATE_SUB(CURDATE(), INTERVAL 5 DAY), 0, DATE_SUB(CURDATE(), INTERVAL 46 DAY), 0),
 (610, 'DEV_SEED', 'dev-610', '(주)클라우드원', '클라우드 엔지니어 (신입)', '133400', '정보시스템 운영자', '서울 강서구', '정규직', '신입', '학력무관', '회사 내규', '[직무] 정보시스템 운영자
[내용] AWS 인프라 운영, CI/CD 구성', DATE_SUB(CURDATE(), INTERVAL 6 DAY), 0, DATE_SUB(CURDATE(), INTERVAL 47 DAY), 0),
 (611, 'DEV_SEED', 'dev-611', '(주)에듀테크랩', '교육 플랫폼 백엔드', '133202', '응용 소프트웨어 개발자', '서울 서초구', '정규직', '신입', '학력무관', '회사 내규', '[직무] 응용 소프트웨어 개발자
[내용] 강의 · 결제 API, 동영상 처리 배치', DATE_SUB(CURDATE(), INTERVAL 7 DAY), 0, DATE_SUB(CURDATE(), INTERVAL 48 DAY), 0),
 (612, 'DEV_SEED', 'dev-612', '(주)헬스노트', 'AI 데이터 분석 보조', '133300', '데이터 분석가', '서울 송파구', '인턴', '신입', '학력무관', '회사 내규', '[직무] 데이터 분석가
[내용] 건강 데이터 전처리, 분석 리포트 작성', DATE_SUB(CURDATE(), INTERVAL 8 DAY), 0, DATE_SUB(CURDATE(), INTERVAL 40 DAY), 0);

INSERT INTO recommendation (id, student_id, trigger_type, created_at) VALUES
 (101, 21, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 13 DAY)),
 (102, 22, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 16 DAY)),
 (103, 23, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 5 DAY)),
 (104, 24, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 6 DAY)),
 (105, 25, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 10 DAY)),
 (106, 26, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 11 DAY)),
 (107, 27, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 4 DAY)),
 (108, 28, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 4 DAY)),
 (109, 29, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 6 DAY)),
 (110, 30, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 17 DAY)),
 (111, 31, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 22 DAY)),
 (112, 32, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 4 DAY)),
 (113, 33, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 5 DAY)),
 (114, 34, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 6 DAY)),
 (115, 35, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 22 DAY)),
 (116, 36, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 12 DAY)),
 (117, 37, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 4 DAY)),
 (118, 38, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 16 DAY)),
 (119, 39, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 6 DAY)),
 (120, 40, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 4 DAY)),
 (121, 41, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 24 DAY)),
 (122, 42, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 4 DAY)),
 (123, 43, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 22 DAY)),
 (124, 44, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 6 DAY)),
 (125, 45, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 15 DAY)),
 (126, 46, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 16 DAY)),
 (127, 47, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 4 DAY)),
 (128, 48, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 5 DAY)),
 (129, 49, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 10 DAY)),
 (130, 50, 'BATCH', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 20 DAY)),
 (131, 12, 'VISIT', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 1 DAY));

INSERT INTO recommendation_item (id, recommendation_id, job_posting_id, rank_no, reasons, response, pass_reason, pass_note, responded_at) VALUES
 (1001, 101, 610, 1, '[{"text": "프론트엔드 개발 희망 직무와 ''정보시스템 운영자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "React 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1002, 101, 611, 2, '[{"text": "프론트엔드 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "React 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1003, 101, 612, 3, '[{"text": "프론트엔드 개발 희망 직무와 ''데이터 분석가'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "React 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1004, 102, 611, 1, '[{"text": "AI 서비스 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'ALREADY_EMPLOYED', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 11 HOUR), INTERVAL 15 DAY)),
 (1005, 102, 612, 2, '[{"text": "AI 서비스 개발 희망 직무와 ''데이터 분석가'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1006, 102, 601, 3, '[{"text": "AI 서비스 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1007, 103, 612, 1, '[{"text": "풀스택 개발 희망 직무와 ''데이터 분석가'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Spring Boot 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'APPLY', NULL, NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 12 HOUR), INTERVAL 4 DAY)),
 (1008, 103, 601, 2, '[{"text": "풀스택 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Spring Boot 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'JOB_MISMATCH', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 8 HOUR), INTERVAL 4 DAY)),
 (1009, 103, 602, 3, '[{"text": "풀스택 개발 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Spring Boot 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1010, 104, 601, 1, '[{"text": "데이터 엔지니어 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'HELP', NULL, NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 13 HOUR), INTERVAL 5 DAY)),
 (1011, 104, 602, 2, '[{"text": "데이터 엔지니어 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'SKILL_GAP', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 8 HOUR), INTERVAL 5 DAY)),
 (1012, 104, 603, 3, '[{"text": "데이터 엔지니어 희망 직무와 ''데이터 엔지니어'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1013, 105, 602, 1, '[{"text": "백엔드 개발 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Java 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'ALREADY_EMPLOYED', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 14 HOUR), INTERVAL 9 DAY)),
 (1014, 105, 603, 2, '[{"text": "백엔드 개발 희망 직무와 ''데이터 엔지니어'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Java 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1015, 105, 604, 3, '[{"text": "백엔드 개발 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Java 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1016, 106, 603, 1, '[{"text": "프론트엔드 개발 희망 직무와 ''데이터 엔지니어'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "React 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1017, 106, 604, 2, '[{"text": "프론트엔드 개발 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "React 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1018, 106, 605, 3, '[{"text": "프론트엔드 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "React 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1019, 107, 604, 1, '[{"text": "AI 서비스 개발 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'OTHER', '대학원 진학도 같이 고민하고 있어요', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 16 HOUR), INTERVAL 3 DAY)),
 (1020, 107, 605, 2, '[{"text": "AI 서비스 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'CONDITION_MISMATCH', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 4 DAY)),
 (1021, 107, 606, 3, '[{"text": "AI 서비스 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1022, 108, 605, 1, '[{"text": "풀스택 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Spring Boot 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'ALREADY_EMPLOYED', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 17 HOUR), INTERVAL 3 DAY)),
 (1023, 108, 606, 2, '[{"text": "풀스택 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Spring Boot 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1024, 108, 607, 3, '[{"text": "풀스택 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Spring Boot 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1025, 109, 606, 1, '[{"text": "데이터 엔지니어 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'APPLY', NULL, NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 18 HOUR), INTERVAL 5 DAY)),
 (1026, 109, 607, 2, '[{"text": "데이터 엔지니어 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'JOB_MISMATCH', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 8 HOUR), INTERVAL 5 DAY)),
 (1027, 109, 608, 3, '[{"text": "데이터 엔지니어 희망 직무와 ''정보시스템 운영자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1028, 110, 607, 1, '[{"text": "백엔드 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Java 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1029, 110, 608, 2, '[{"text": "백엔드 개발 희망 직무와 ''정보시스템 운영자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Java 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1030, 110, 609, 3, '[{"text": "백엔드 개발 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Java 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1031, 111, 608, 1, '[{"text": "프론트엔드 개발 희망 직무와 ''정보시스템 운영자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "React 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'ALREADY_EMPLOYED', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 10 HOUR), INTERVAL 21 DAY)),
 (1032, 111, 609, 2, '[{"text": "프론트엔드 개발 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "React 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1033, 111, 610, 3, '[{"text": "프론트엔드 개발 희망 직무와 ''정보시스템 운영자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "React 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1034, 112, 609, 1, '[{"text": "AI 서비스 개발 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'TAKING_BREAK', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 11 HOUR), INTERVAL 3 DAY)),
 (1035, 112, 610, 2, '[{"text": "AI 서비스 개발 희망 직무와 ''정보시스템 운영자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1036, 112, 611, 3, '[{"text": "AI 서비스 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1037, 113, 610, 1, '[{"text": "풀스택 개발 희망 직무와 ''정보시스템 운영자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Spring Boot 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'OTHER', '부트캠프 하나 더 들을까 고민 중', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 12 HOUR), INTERVAL 4 DAY)),
 (1038, 113, 611, 2, '[{"text": "풀스택 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Spring Boot 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'CONDITION_MISMATCH', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 5 DAY)),
 (1039, 113, 612, 3, '[{"text": "풀스택 개발 희망 직무와 ''데이터 분석가'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Spring Boot 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1040, 114, 611, 1, '[{"text": "데이터 엔지니어 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'HELP', NULL, NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 13 HOUR), INTERVAL 5 DAY)),
 (1041, 114, 612, 2, '[{"text": "데이터 엔지니어 희망 직무와 ''데이터 분석가'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'SKILL_GAP', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 8 HOUR), INTERVAL 5 DAY)),
 (1042, 114, 601, 3, '[{"text": "데이터 엔지니어 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1043, 115, 612, 1, '[{"text": "백엔드 개발 희망 직무와 ''데이터 분석가'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Java 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'ALREADY_EMPLOYED', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 14 HOUR), INTERVAL 21 DAY)),
 (1044, 115, 601, 2, '[{"text": "백엔드 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Java 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1045, 115, 602, 3, '[{"text": "백엔드 개발 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Java 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1046, 116, 601, 1, '[{"text": "프론트엔드 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "React 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1047, 116, 602, 2, '[{"text": "프론트엔드 개발 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "React 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1048, 116, 603, 3, '[{"text": "프론트엔드 개발 희망 직무와 ''데이터 엔지니어'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "React 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1049, 117, 602, 1, '[{"text": "AI 서비스 개발 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'APPLY', NULL, NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 16 HOUR), INTERVAL 3 DAY)),
 (1050, 117, 603, 2, '[{"text": "AI 서비스 개발 희망 직무와 ''데이터 엔지니어'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'JOB_MISMATCH', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 8 HOUR), INTERVAL 3 DAY)),
 (1051, 117, 604, 3, '[{"text": "AI 서비스 개발 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1052, 118, 603, 1, '[{"text": "풀스택 개발 희망 직무와 ''데이터 엔지니어'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Spring Boot 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'ALREADY_EMPLOYED', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 17 HOUR), INTERVAL 15 DAY)),
 (1053, 118, 604, 2, '[{"text": "풀스택 개발 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Spring Boot 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1054, 118, 605, 3, '[{"text": "풀스택 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Spring Boot 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1055, 119, 604, 1, '[{"text": "데이터 엔지니어 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'CONDITION_MISMATCH', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 18 HOUR), INTERVAL 5 DAY)),
 (1056, 119, 605, 2, '[{"text": "데이터 엔지니어 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1057, 119, 606, 3, '[{"text": "데이터 엔지니어 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1058, 120, 605, 1, '[{"text": "백엔드 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Java 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'ALREADY_EMPLOYED', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 3 DAY)),
 (1059, 120, 606, 2, '[{"text": "백엔드 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Java 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1060, 120, 607, 3, '[{"text": "백엔드 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Java 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1061, 121, 606, 1, '[{"text": "프론트엔드 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "React 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1062, 121, 607, 2, '[{"text": "프론트엔드 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "React 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1063, 121, 608, 3, '[{"text": "프론트엔드 개발 희망 직무와 ''정보시스템 운영자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "React 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1064, 122, 607, 1, '[{"text": "AI 서비스 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'OTHER', '프리랜서로 일해 볼까 생각 중이에요', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 11 HOUR), INTERVAL 3 DAY)),
 (1065, 122, 608, 2, '[{"text": "AI 서비스 개발 희망 직무와 ''정보시스템 운영자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'CONDITION_MISMATCH', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 4 DAY)),
 (1066, 122, 609, 3, '[{"text": "AI 서비스 개발 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1067, 123, 608, 1, '[{"text": "풀스택 개발 희망 직무와 ''정보시스템 운영자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Spring Boot 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'ALREADY_EMPLOYED', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 12 HOUR), INTERVAL 21 DAY)),
 (1068, 123, 609, 2, '[{"text": "풀스택 개발 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Spring Boot 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1069, 123, 610, 3, '[{"text": "풀스택 개발 희망 직무와 ''정보시스템 운영자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Spring Boot 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1070, 124, 609, 1, '[{"text": "데이터 엔지니어 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'APPLY', NULL, NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 13 HOUR), INTERVAL 5 DAY)),
 (1071, 124, 610, 2, '[{"text": "데이터 엔지니어 희망 직무와 ''정보시스템 운영자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'JOB_MISMATCH', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 8 HOUR), INTERVAL 5 DAY)),
 (1072, 124, 611, 3, '[{"text": "데이터 엔지니어 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1073, 125, 610, 1, '[{"text": "백엔드 개발 희망 직무와 ''정보시스템 운영자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Java 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1074, 125, 611, 2, '[{"text": "백엔드 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Java 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1075, 125, 612, 3, '[{"text": "백엔드 개발 희망 직무와 ''데이터 분석가'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Java 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1076, 126, 611, 1, '[{"text": "프론트엔드 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "React 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'ALREADY_EMPLOYED', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 15 HOUR), INTERVAL 15 DAY)),
 (1077, 126, 612, 2, '[{"text": "프론트엔드 개발 희망 직무와 ''데이터 분석가'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "React 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1078, 126, 601, 3, '[{"text": "프론트엔드 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "React 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1079, 127, 612, 1, '[{"text": "AI 서비스 개발 희망 직무와 ''데이터 분석가'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'HELP', NULL, NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 16 HOUR), INTERVAL 3 DAY)),
 (1080, 127, 601, 2, '[{"text": "AI 서비스 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'SKILL_GAP', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 8 HOUR), INTERVAL 3 DAY)),
 (1081, 127, 602, 3, '[{"text": "AI 서비스 개발 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1082, 128, 601, 1, '[{"text": "풀스택 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Spring Boot 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'CONDITION_MISMATCH', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 17 HOUR), INTERVAL 4 DAY)),
 (1083, 128, 602, 2, '[{"text": "풀스택 개발 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Spring Boot 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1084, 128, 603, 3, '[{"text": "풀스택 개발 희망 직무와 ''데이터 엔지니어'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Spring Boot 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1085, 129, 602, 1, '[{"text": "데이터 엔지니어 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'PASS', 'ALREADY_EMPLOYED', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 18 HOUR), INTERVAL 9 DAY)),
 (1086, 129, 603, 2, '[{"text": "데이터 엔지니어 희망 직무와 ''데이터 엔지니어'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1087, 129, 604, 3, '[{"text": "데이터 엔지니어 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Python 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1088, 130, 603, 1, '[{"text": "백엔드 개발 희망 직무와 ''데이터 엔지니어'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Java 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1089, 130, 604, 2, '[{"text": "백엔드 개발 희망 직무와 ''웹 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Java 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1090, 130, 605, 3, '[{"text": "백엔드 개발 희망 직무와 ''응용 소프트웨어 개발자'' 직종이 맞아요", "evidence": [{"refType": "COURSE", "refId": 1}]}, {"text": "Java 교육 이수 내용이 주요 업무와 관련 있어요", "evidence": [{"refType": "COURSE", "refId": 1}]}]', 'NONE', NULL, NULL, NULL),
 (1091, 131, 603, 1, '[{"text": "데이터 파이프라인 프로젝트가 주요 업무와 맞아요", "evidence": [{"refType": "PROJECT", "refId": 113}]}]', 'HELP', NULL, NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 1 DAY)),
 (1092, 131, 612, 2, '[{"text": "데이터 파이프라인 프로젝트가 주요 업무와 맞아요", "evidence": [{"refType": "PROJECT", "refId": 113}]}]', 'NONE', NULL, NULL, NULL),
 (1093, 131, 601, 3, '[{"text": "데이터 파이프라인 프로젝트가 주요 업무와 맞아요", "evidence": [{"refType": "PROJECT", "refId": 113}]}]', 'NONE', NULL, NULL, NULL);

INSERT INTO help_request (id, student_id, recommendation_item_id, contact_method, memo, created_at) VALUES
 (101, 24, 1010, 'SMS', '자기소개서를 어떻게 써야 할지 모르겠어요', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 13 HOUR), INTERVAL 5 DAY)),
 (102, 34, 1040, NULL, NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 13 HOUR), INTERVAL 5 DAY)),
 (103, 47, 1079, 'SMS', '면접 준비를 도와주셨으면 해요', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 16 HOUR), INTERVAL 3 DAY)),
 (104, 12, 1091, 'SMS', NULL, DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 9 HOUR), INTERVAL 1 DAY));

INSERT INTO application (id, student_id, recommendation_item_id, job_posting_id, status, submitted_at, reviewed_at, delivered_at) VALUES
 (101, 23, 1007, 612, 'SUBMITTED', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 12 HOUR), INTERVAL 4 DAY), NULL, NULL),
 (102, 29, 1025, 606, 'SUBMITTED', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 18 HOUR), INTERVAL 5 DAY), NULL, NULL),
 (103, 37, 1049, 602, 'APPROVED', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 16 HOUR), INTERVAL 3 DAY), DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 15 HOUR), INTERVAL 2 DAY), NULL),
 (104, 44, 1070, 609, 'DELIVERED', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 13 HOUR), INTERVAL 5 DAY), DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 15 HOUR), INTERVAL 4 DAY), DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 17 HOUR), INTERVAL 4 DAY));

INSERT INTO application_document (application_id, document) VALUES
 (101, '{"companyInfoStatus": "NOT_FOUND", "companyInfoNote": "(주)헬스노트의 인재상 · 최근 소식을 찾지 못해 공고 내용만 반영했어요", "excludedSentenceCount": 1, "sections": [{"section": "BASIC", "source": "REGISTERED", "fields": {"name": "이수빈", "email": "student23@example.com", "githubUrl": "github.com/ieum-seed-23", "desiredJob": "풀스택 개발"}}, {"section": "EDUCATION", "items": []}, {"section": "TRAINING", "items": [{"itemId": "trn-1", "source": "REGISTERED", "name": "청년취업사관학교 AI Agent 과정", "campus": "동대문캠퍼스", "startedOn": "2026-03-02", "endedOn": "2026-08-29"}]}, {"section": "CAREER", "items": []}, {"section": "MILITARY", "value": null}, {"section": "CERTIFICATES", "items": []}, {"section": "SKILLS", "source": "AI", "summary": [{"sentenceId": "sk-1", "text": "Spring Boot · React · AWS로 팀 프로젝트 기능을 구현", "source": "AI", "evidence": [{"refType": "PROJECT", "refId": 123, "label": "AI 상담 챗봇 역할 기록", "quote": "맡은 기능의 API와 화면을 직접 구현", "url": null, "collectedAt": null}]}], "tags": ["Spring Boot", "React", "AWS"]}, {"section": "PROJECTS", "items": [{"itemId": "prj-123", "title": "AI 상담 챗봇", "periodText": "5주", "teamSize": 4, "role": "풀스택 개발", "bullets": [{"sentenceId": "pj-1", "text": "맡은 기능의 API와 화면을 직접 설계 · 구현", "source": "AI", "evidence": [{"refType": "PROJECT", "refId": 123, "label": "AI 상담 챗봇 역할 기록", "quote": "맡은 기능의 API와 화면을 직접 구현", "url": null, "collectedAt": null}]}]}]}, {"section": "PORTFOLIO", "items": []}, {"section": "COVER_LETTER", "sentences": [{"sentenceId": "cl-1", "text": "AI 상담 챗봇에서 맡은 기능을 끝까지 구현하며 Spring Boot 실무 감각을 키웠습니다.", "source": "AI", "evidence": [{"refType": "PROJECT", "refId": 123, "label": "AI 상담 챗봇 역할 기록", "quote": "맡은 기능의 API와 화면을 직접 구현", "url": null, "collectedAt": null}]}, {"sentenceId": "cl-2", "text": "(주)헬스노트에서 배운 것을 바로 쓰며 성장하겠습니다.", "source": "USER", "evidence": []}]}]}'),
 (102, '{"companyInfoStatus": "NOT_FOUND", "companyInfoNote": "(주)모바일팩토리의 인재상 · 최근 소식을 찾지 못해 공고 내용만 반영했어요", "excludedSentenceCount": 1, "sections": [{"section": "BASIC", "source": "REGISTERED", "fields": {"name": "장서현", "email": "student29@example.com", "githubUrl": "github.com/ieum-seed-29", "desiredJob": "데이터 엔지니어"}}, {"section": "EDUCATION", "items": []}, {"section": "TRAINING", "items": [{"itemId": "trn-1", "source": "REGISTERED", "name": "청년취업사관학교 AI Agent 과정", "campus": "동대문캠퍼스", "startedOn": "2026-03-02", "endedOn": "2026-08-29"}]}, {"section": "CAREER", "items": []}, {"section": "MILITARY", "value": null}, {"section": "CERTIFICATES", "items": []}, {"section": "SKILLS", "source": "AI", "summary": [{"sentenceId": "sk-1", "text": "Python · SQL · Airflow로 팀 프로젝트 기능을 구현", "source": "AI", "evidence": [{"refType": "PROJECT", "refId": 124, "label": "사내 예약 시스템 역할 기록", "quote": "맡은 기능의 API와 화면을 직접 구현", "url": null, "collectedAt": null}]}], "tags": ["Python", "SQL", "Airflow"]}, {"section": "PROJECTS", "items": [{"itemId": "prj-124", "title": "사내 예약 시스템", "periodText": "5주", "teamSize": 4, "role": "데이터 엔지니어", "bullets": [{"sentenceId": "pj-1", "text": "맡은 기능의 API와 화면을 직접 설계 · 구현", "source": "AI", "evidence": [{"refType": "PROJECT", "refId": 124, "label": "사내 예약 시스템 역할 기록", "quote": "맡은 기능의 API와 화면을 직접 구현", "url": null, "collectedAt": null}]}]}]}, {"section": "PORTFOLIO", "items": []}, {"section": "COVER_LETTER", "sentences": [{"sentenceId": "cl-1", "text": "사내 예약 시스템에서 맡은 기능을 끝까지 구현하며 Python 실무 감각을 키웠습니다.", "source": "AI", "evidence": [{"refType": "PROJECT", "refId": 124, "label": "사내 예약 시스템 역할 기록", "quote": "맡은 기능의 API와 화면을 직접 구현", "url": null, "collectedAt": null}]}, {"sentenceId": "cl-2", "text": "(주)모바일팩토리에서 배운 것을 바로 쓰며 성장하겠습니다.", "source": "USER", "evidence": []}]}]}'),
 (103, '{"companyInfoStatus": "NOT_FOUND", "companyInfoNote": "코드너리(주)의 인재상 · 최근 소식을 찾지 못해 공고 내용만 반영했어요", "excludedSentenceCount": 1, "sections": [{"section": "BASIC", "source": "REGISTERED", "fields": {"name": "안소희", "email": "student37@example.com", "githubUrl": "github.com/ieum-seed-37", "desiredJob": "AI 서비스 개발"}}, {"section": "EDUCATION", "items": []}, {"section": "TRAINING", "items": [{"itemId": "trn-1", "source": "REGISTERED", "name": "청년취업사관학교 AI Agent 과정", "campus": "동대문캠퍼스", "startedOn": "2026-03-02", "endedOn": "2026-08-29"}]}, {"section": "CAREER", "items": []}, {"section": "MILITARY", "value": null}, {"section": "CERTIFICATES", "items": []}, {"section": "SKILLS", "source": "AI", "summary": [{"sentenceId": "sk-1", "text": "Python · FastAPI · LLM API로 팀 프로젝트 기능을 구현", "source": "AI", "evidence": [{"refType": "PROJECT", "refId": 125, "label": "학습 관리 서비스 역할 기록", "quote": "맡은 기능의 API와 화면을 직접 구현", "url": null, "collectedAt": null}]}], "tags": ["Python", "FastAPI", "LLM API"]}, {"section": "PROJECTS", "items": [{"itemId": "prj-125", "title": "학습 관리 서비스", "periodText": "5주", "teamSize": 4, "role": "AI 서비스 개발", "bullets": [{"sentenceId": "pj-1", "text": "맡은 기능의 API와 화면을 직접 설계 · 구현", "source": "AI", "evidence": [{"refType": "PROJECT", "refId": 125, "label": "학습 관리 서비스 역할 기록", "quote": "맡은 기능의 API와 화면을 직접 구현", "url": null, "collectedAt": null}]}]}]}, {"section": "PORTFOLIO", "items": []}, {"section": "COVER_LETTER", "sentences": [{"sentenceId": "cl-1", "text": "학습 관리 서비스에서 맡은 기능을 끝까지 구현하며 Python 실무 감각을 키웠습니다.", "source": "AI", "evidence": [{"refType": "PROJECT", "refId": 125, "label": "학습 관리 서비스 역할 기록", "quote": "맡은 기능의 API와 화면을 직접 구현", "url": null, "collectedAt": null}]}, {"sentenceId": "cl-2", "text": "코드너리(주)에서 배운 것을 바로 쓰며 성장하겠습니다.", "source": "USER", "evidence": []}]}]}'),
 (104, '{"companyInfoStatus": "NOT_FOUND", "companyInfoNote": "(주)픽셀랩의 인재상 · 최근 소식을 찾지 못해 공고 내용만 반영했어요", "excludedSentenceCount": 1, "sections": [{"section": "BASIC", "source": "REGISTERED", "fields": {"name": "백지원", "email": "student44@example.com", "githubUrl": "github.com/ieum-seed-44", "desiredJob": "데이터 엔지니어"}}, {"section": "EDUCATION", "items": []}, {"section": "TRAINING", "items": [{"itemId": "trn-1", "source": "REGISTERED", "name": "청년취업사관학교 클라우드 풀스택 과정", "campus": "동대문캠퍼스", "startedOn": "2026-02-02", "endedOn": "2026-07-31"}]}, {"section": "CAREER", "items": []}, {"section": "MILITARY", "value": null}, {"section": "CERTIFICATES", "items": []}, {"section": "SKILLS", "source": "AI", "summary": [{"sentenceId": "sk-1", "text": "Python · SQL · Airflow로 팀 프로젝트 기능을 구현", "source": "AI", "evidence": [{"refType": "PROJECT", "refId": 126, "label": "사내 예약 시스템 역할 기록", "quote": "맡은 기능의 API와 화면을 직접 구현", "url": null, "collectedAt": null}]}], "tags": ["Python", "SQL", "Airflow"]}, {"section": "PROJECTS", "items": [{"itemId": "prj-126", "title": "사내 예약 시스템", "periodText": "5주", "teamSize": 4, "role": "데이터 엔지니어", "bullets": [{"sentenceId": "pj-1", "text": "맡은 기능의 API와 화면을 직접 설계 · 구현", "source": "AI", "evidence": [{"refType": "PROJECT", "refId": 126, "label": "사내 예약 시스템 역할 기록", "quote": "맡은 기능의 API와 화면을 직접 구현", "url": null, "collectedAt": null}]}]}]}, {"section": "PORTFOLIO", "items": []}, {"section": "COVER_LETTER", "sentences": [{"sentenceId": "cl-1", "text": "사내 예약 시스템에서 맡은 기능을 끝까지 구현하며 Python 실무 감각을 키웠습니다.", "source": "AI", "evidence": [{"refType": "PROJECT", "refId": 126, "label": "사내 예약 시스템 역할 기록", "quote": "맡은 기능의 API와 화면을 직접 구현", "url": null, "collectedAt": null}]}, {"sentenceId": "cl-2", "text": "(주)픽셀랩에서 배운 것을 바로 쓰며 성장하겠습니다.", "source": "USER", "evidence": []}]}]}');

INSERT INTO contact_log (student_id, contact_type, note, created_at) VALUES
 (21, 'PHONE', '수료 직후 근황 통화. 구직 중이라고 함', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 14 HOUR), INTERVAL 15 DAY)),
 (26, 'PHONE', '수료 직후 근황 통화. 구직 중이라고 함', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 14 HOUR), INTERVAL 13 DAY)),
 (41, 'PHONE', '수료 직후 근황 통화. 구직 중이라고 함', DATE_SUB(DATE_ADD(CURDATE(), INTERVAL 14 HOUR), INTERVAL 26 DAY));

INSERT INTO student_status (student_id, status, sub_status, reason) VALUES
 (1, 'SEEKING', 'WAITING', '시드 초기값 · 응답 이력 없음'),
 (2, 'SEEKING', 'WAITING', '평가용 · 응답 이력 없음'),
 (3, 'SEEKING', 'WAITING', '평가용 · 응답 이력 없음'),
 (4, 'SEEKING', 'WAITING', '평가용 · 응답 이력 없음'),
 (5, 'SEEKING', 'WAITING', '평가용 · 응답 이력 없음'),
 (6, 'SEEKING', 'WAITING', '평가용 · 응답 이력 없음'),
 (7, 'SEEKING', 'WAITING', '평가용 · 응답 이력 없음'),
 (8, 'SEEKING', 'WAITING', '평가용 · 응답 이력 없음'),
 (9, 'SEEKING', 'WAITING', '평가용 · 응답 이력 없음'),
 (10, 'SEEKING', 'WAITING', '평가용 · 응답 이력 없음'),
 (11, 'SEEKING', 'WAITING', '평가용 · 응답 이력 없음'),
 (12, 'NEED_HELP', NULL, '열린 도움 요청 있음'),
 (13, 'SEEKING', 'WAITING', '평가용 · 응답 이력 없음'),
 (14, 'SEEKING', 'WAITING', '평가용 · 응답 이력 없음'),
 (15, 'SEEKING', 'WAITING', '평가용 · 응답 이력 없음'),
 (16, 'SEEKING', 'WAITING', '평가용 · 응답 이력 없음'),
 (17, 'SEEKING', 'WAITING', '평가용 · 응답 이력 없음'),
 (18, 'SEEKING', 'WAITING', '평가용 · 응답 이력 없음'),
 (19, 'SEEKING', 'WAITING', '평가용 · 응답 이력 없음'),
 (20, 'SEEKING', 'WAITING', '평가용 · 응답 이력 없음'),
 (21, 'CHECK_REQUIRED', NULL, '마지막 응답 후 7일 이상 지남'),
 (22, 'EMPLOYED', NULL, '패스 사유 ALREADY_EMPLOYED'),
 (23, 'SEEKING', 'APPLYING', '제출된 지원서 있음'),
 (24, 'NEED_HELP', NULL, '열린 도움 요청 있음'),
 (25, 'EMPLOYED', NULL, '패스 사유 ALREADY_EMPLOYED'),
 (26, 'CHECK_REQUIRED', NULL, '마지막 응답 후 7일 이상 지남'),
 (27, 'SEEKING', 'OTHER', '최근 패스 사유 기타'),
 (28, 'EMPLOYED', NULL, '패스 사유 ALREADY_EMPLOYED'),
 (29, 'SEEKING', 'APPLYING', '제출된 지원서 있음'),
 (30, 'CHECK_REQUIRED', NULL, '마지막 응답 후 7일 이상 지남'),
 (31, 'EMPLOYED', NULL, '패스 사유 ALREADY_EMPLOYED'),
 (32, 'SEEKING', 'WAITING', '패스 후 대기'),
 (33, 'SEEKING', 'OTHER', '최근 패스 사유 기타'),
 (34, 'NEED_HELP', NULL, '열린 도움 요청 있음'),
 (35, 'EMPLOYED', NULL, '패스 사유 ALREADY_EMPLOYED'),
 (36, 'CHECK_REQUIRED', NULL, '마지막 응답 후 7일 이상 지남'),
 (37, 'SEEKING', 'APPLYING', '승인된 지원서 있음'),
 (38, 'EMPLOYED', NULL, '패스 사유 ALREADY_EMPLOYED'),
 (39, 'SEEKING', 'WAITING', '패스 후 대기'),
 (40, 'EMPLOYED', NULL, '패스 사유 ALREADY_EMPLOYED'),
 (41, 'CHECK_REQUIRED', NULL, '마지막 응답 후 7일 이상 지남'),
 (42, 'SEEKING', 'OTHER', '최근 패스 사유 기타'),
 (43, 'EMPLOYED', NULL, '패스 사유 ALREADY_EMPLOYED'),
 (44, 'SEEKING', 'APPLYING', '기관 추천 전달 완료'),
 (45, 'CHECK_REQUIRED', NULL, '마지막 응답 후 7일 이상 지남'),
 (46, 'EMPLOYED', NULL, '패스 사유 ALREADY_EMPLOYED'),
 (47, 'NEED_HELP', NULL, '열린 도움 요청 있음'),
 (48, 'SEEKING', 'WAITING', '패스 후 대기'),
 (49, 'EMPLOYED', NULL, '패스 사유 ALREADY_EMPLOYED'),
 (50, 'CHECK_REQUIRED', NULL, '마지막 응답 후 7일 이상 지남');
