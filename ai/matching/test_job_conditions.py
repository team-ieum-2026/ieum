# 주인: AI-B
"""공고 조건(필수 기술 · 직무군) 규칙과 추천 정렬 · 필터 테스트. 가짜 임베딩 · 가짜 리랭커만 쓴다.

실행: cd ai && .venv/Scripts/python -m unittest matching.test_job_conditions -v
"""

import tempfile
import unittest
from pathlib import Path

from matching import job_conditions as jc
from matching.models import RecommendRequest
from matching.recommend import recommend_jobs
from matching.store import IndexStore
from matching.test_rerank import PROFILE, entry, fake_embed_query, fake_reranker, setUpModule, tearDownModule  # noqa: F401

MODEL = "fake-embedding"

# PROFILE: 백엔드 개발 · 서울 · 기술 Spring Boot, MySQL
FULL = "[공고] 백엔드 개발자\n[직무] 응용 소프트웨어 개발자\n[내용] 2. 자격요건\n· Spring Boot · MySQL 사용 가능자\n3. 우대사항\n· Kafka"
MISSING = "[공고] 백엔드 개발자 채용\n[직무] 웹 개발자\n[내용] 자격요건\n· Spring Boot · Redis 사용 가능자"
NEUTRAL = "[공고] 서버 개발자\n[직무] 응용 소프트웨어 개발자\n[내용] 결제 서버 개발"
FRONT = "[공고] 프론트엔드 개발자\n[직무] 웹 개발자\n[내용] 자격요건\n· React"
UNKNOWN = "[공고] 신입 개발자 모집\n[직무] 기타 컴퓨터 시스템 전문가\n[내용] 사내 시스템 개발"


class RequiredSkillsTest(unittest.TestCase):
    def test_only_requirement_section_is_read(self):
        self.assertEqual(jc.required_skills(FULL), ["Spring Boot", "MySQL"])  # 우대(Kafka)는 뺌

    def test_one_line_and_field_formats(self):
        self.assertEqual(jc.required_skills("[내용] 지원 자격 : Java · SQL 사용 가능자, 신입 지원 가능 우대 : Redis"), ["Java", "SQL"])
        self.assertEqual(jc.required_skills("[필요 역량] 문서작성 (워드프로세스 활용),기타(리액트, 타입스크립트 사용 경험)\n[우대 조건] Vue.js"),
                         ["TypeScript", "React"])

    def test_spring_boot_is_not_also_counted_as_spring(self):
        self.assertEqual(jc.required_skills("자격요건\n- Spring Boot"), ["Spring Boot"])

    def test_broad_posting_term_accepts_specific_student_skill_only_one_way(self):
        self.assertFalse(jc.missing_required("자격요건\n- Spring, SQL", ["Spring Boot", "MySQL"]))
        self.assertTrue(jc.missing_required("자격요건\n- Spring Boot", ["Spring"]))

    def test_no_required_skill_is_neutral(self):
        self.assertIsNone(jc.missing_required(NEUTRAL, ["Java"]))
        self.assertIsNone(jc.missing_required("[내용] 우대사항\n- Java", []))  # 우대에만 있으면 필수 아님

    def test_missing_one_skill(self):
        self.assertTrue(jc.missing_required(MISSING, ["Spring Boot", "MySQL"]))

    def test_html_one_line_body_is_read(self):
        html = ("[공고] 프론트엔드 개발자\n[내용] <p><b>■ 담당업무</b></p><p>- 화면 개발<br>- Java 배치 운영</p>"
                "<p><b>■ 자격요건</b></p><p>- Vue.js · JavaScript 사용 가능자<br>- 신입 지원 가능</p>"
                "<p><b>■ 우대사항</b></p><p>- React 경험</p>")
        self.assertCountEqual(jc.required_skills(html), ["JavaScript", "Vue.js"])  # 담당업무(Java) · 우대(React)는 뺌
        self.assertTrue(jc.missing_required(html, ["React", "TypeScript"]))  # Vue.js 가 없음
        self.assertEqual(jc.required_skills("<ul><li>자격요건</li><li>Python 사용 가능자</li></ul><div>우대: Docker</div>"),
                         ["Python"])

    def test_angular_is_a_required_skill(self):
        html = ("[공고] 프론트엔드 개발자 (Angular)\n[내용] <p><b>■ 자격요건</b></p>"
                "<p>- Angular · TypeScript 사용 가능자<br>- 신입 지원 가능</p><p><b>■ 우대사항</b></p><p>- RxJS</p>")
        self.assertCountEqual(jc.required_skills(html), ["Angular", "TypeScript"])
        self.assertTrue(jc.missing_required(html, ["React", "TypeScript"]))  # Angular 가 없어 뒤로
        self.assertFalse(jc.missing_required(html, ["Angular", "TypeScript"]))
        self.assertEqual(jc.required_skills("자격요건\n- AngularJS 유지보수 경험"), ["Angular"])

    def test_angular_korean_spelling(self):
        self.assertCountEqual(jc.required_skills("< 자격요건 >\nㆍ 앵귤러 · 타입스크립트 사용 가능자"), ["Angular", "TypeScript"])

    def test_added_ai_and_db_terms(self):
        self.assertCountEqual(jc.required_skills("자격요건\n- Python · 벡터 데이터베이스 사용 가능자"), ["Python", "Vector DB"])
        self.assertCountEqual(jc.required_skills("자격요건\n- Python, 사이킷런 등 머신러닝 라이브러리 사용 경험"), ["Python", "scikit-learn"])
        self.assertEqual(jc.required_skills("자격요건\n- 파이토치 · 판다스"), ["PyTorch", "Pandas"])
        self.assertEqual(jc.required_skills("자격요건\n- MS SQL 사용 가능자"), ["MS SQL"])  # "SQL" 로 또 세지 않음

    def test_gpt_api_is_openai_only(self):
        text = "자격요건\n- Python · GPT API 사용 가능자"
        self.assertCountEqual(jc.required_skills(text), ["Python", "OpenAI API"])
        self.assertFalse(jc.missing_required(text, ["Python", "OpenAI API"]))
        self.assertTrue(jc.missing_required(text, ["Python", "Gemini API"]))  # Gemini 는 다른 기술

    def test_react_native_is_not_react(self):
        text = "자격요건\n- React Native 앱 개발 경험"
        self.assertEqual(jc.required_skills(text), ["React Native"])
        self.assertTrue(jc.missing_required(text, ["React"]))

    def test_new_frameworks(self):
        self.assertCountEqual(jc.required_skills("자격요건\n- Express.js 또는 NestJS, Nuxt.js · SvelteKit"),
                              ["Express", "NestJS", "Nuxt", "SvelteKit"])
        self.assertCountEqual(jc.required_skills("지원 자격 : Laravel · Ruby on Rails · ASP.NET Core · Gin · Ktor"),
                              ["Laravel", "Ruby on Rails", "ASP.NET", "Gin", "Ktor"])

    def test_grpc_and_go(self):
        self.assertCountEqual(jc.required_skills("< 자격요건 >\nㆍ Go · gRPC · PostgreSQL 사용 가능자"), ["Go", "gRPC", "PostgreSQL"])
        self.assertEqual(jc.required_skills("자격요건\n- grpc 통신 경험"), ["gRPC"])
        for text in ("Go언어 사용 가능자", "Go 언어 경험", "Golang 개발", "Go+MySQL 기반 개발"):
            self.assertIn("Go", jc.required_skills("자격요건\n- " + text), text)
        self.assertTrue(jc.missing_required("자격요건\n- Go · gRPC · PostgreSQL", ["Java", "MySQL"]))  # PostgreSQL 만 인정

    def test_go_false_positives(self):
        for text in ("Google Cloud 사용 경험", "go 언어", "GO 문서", "Go to market 전략", "GTM(Go-To-Market) 수립",
                     "https://www.anyang.go.kr"):
            self.assertNotIn("Go", jc.required_skills("자격요건\n- " + text), text)

    def test_gap_check_terms(self):
        self.assertCountEqual(jc.required_skills("자격요건\n- Photoshop, Illustrator 활용 가능자\n- 프리미어 프로"),
                              ["Photoshop", "Illustrator", "Premiere Pro"])
        self.assertCountEqual(jc.required_skills("자격요건\n- C# 또는 .NET Framework, WPF · WinForms"), ["C#", ".NET", "WPF", "WinForms"])
        self.assertEqual(jc.required_skills("자격요건\n- ASP.NET Core"), ["ASP.NET"])  # .NET 으로 또 세지 않음
        self.assertCountEqual(jc.required_skills("자격요건\n- Linux(Ubuntu) · Windows · Unix"), ["Linux", "Ubuntu", "Windows", "Unix"])
        self.assertCountEqual(jc.required_skills("지원 자격 : PHP · Scala · Azure · Tableau · RxJS · OpenCV"),
                              ["PHP", "Scala", "Azure", "Tableau", "RxJS", "OpenCV"])

    def test_student_spelling_is_matched_to_dictionary_name(self):
        self.assertFalse(jc.missing_required("자격요건\n- scikit-learn", ["사이킷런"]))
        self.assertFalse(jc.missing_required("자격요건\n- Vue.js", ["Vue"]))
        self.assertFalse(jc.missing_required("자격요건\n- 벡터 데이터베이스", ["VectorDB"]))

    def test_relational_db_aliases_follow_rubric(self):
        self.assertFalse(jc.missing_required("자격요건\n- PostgreSQL", ["MySQL"]))
        self.assertFalse(jc.missing_required("자격요건\n- SQL", ["MyBatis"]))
        self.assertTrue(jc.missing_required("자격요건\n- MyBatis", ["MySQL"]))  # 매퍼 쪽으로는 인정하지 않음 (루브릭과 같게)

    def test_body_skills_only_when_no_requirement_section(self):
        body = "[공고] 병원 시스템 개발자\n[직무] 응용 소프트웨어 개발자\n[내용] *VB.NET 경험\n*MS SQL Server 활용한 개발경험\n*Windows FORMS 우대"
        self.assertCountEqual(jc.body_skills(body), ["MS SQL"])  # 우대 줄은 뺀다 (VB.NET 은 사전에 없음)
        self.assertEqual(jc.body_skills(FULL), [])  # 자격요건 구간이 있으면 읽지 않는다 (기존 동작 그대로)

    def test_lacks_body_skills_below_half(self):
        body = "[공고] Back End SW 개발자\n[직무] 응용 소프트웨어 개발자\n[내용] - Data Bricks, AWS 등 AI 관련 SW 개발 (Java, Python)"
        self.assertTrue(jc.lacks_body_skills(body, ["Java", "Spring Boot", "MySQL"]))  # Java · Python · AWS 중 1개
        self.assertFalse(jc.lacks_body_skills(body, ["Java", "Python"]))  # 3개 중 2개
        self.assertFalse(jc.lacks_body_skills(NEUTRAL, ["Java"]))  # 기술이 안 적혔으면 빼지 않는다
        self.assertFalse(jc.lacks_body_skills(MISSING, []))  # 자격요건 구간이 있는 공고는 이 규칙과 상관없음

    def test_plain_line_body_is_unchanged(self):
        plain = "[내용] < 담당업무 >\nㆍ API 개발\n< 자격요건 >\nㆍ Java · MySQL 사용 가능자\n< 우대사항 >\nㆍ Redis"
        self.assertEqual(jc.required_text(plain), "ㆍ Java · MySQL 사용 가능자")  # "< 자격요건 >" 은 태그로 보지 않음
        self.assertCountEqual(jc.required_skills(plain), ["Java", "MySQL"])
        self.assertEqual(jc.required_skills(FULL), ["Spring Boot", "MySQL"])


class FamilyTest(unittest.TestCase):
    def test_title_first_then_job_field(self):
        self.assertEqual(jc.posting_family("백엔드 개발자 (AI 상담 서비스팀) 신입", ""), "BE")
        self.assertEqual(jc.posting_family(None, "[공고] AI 학습 챗봇 개발\n[직무] 응용 소프트웨어 개발자"), "AI")
        self.assertEqual(jc.posting_family("신입 모집", "[직무] 웹 디자이너"), "DESIGN")

    def test_job_title_comes_before_domain_words(self):
        self.assertEqual(jc.posting_family("프론트엔드 개발자 (디자인 시스템)", ""), "FE")
        self.assertEqual(jc.posting_family("웹 퍼블리셔 · 프론트엔드 개발자 모집", ""), "FE")
        self.assertEqual(jc.posting_family("백엔드 개발자 (게임 서버)", ""), "BE")

    def test_front_and_back_together_is_fullstack(self):
        self.assertEqual(jc.posting_family("웹 서비스 개발 (프론트 · 백엔드)", ""), "FULLSTACK")
        self.assertEqual(jc.posting_family("풀스택 개발자", ""), "FULLSTACK")

    def test_preferred_parenthesis_is_ignored(self):
        self.assertIsNone(jc.posting_family("시뮬레이터 웹 대시보드 개발 (Unity 우대)", ""))
        self.assertEqual(jc.posting_family("게임 플랫폼 웹 백엔드(Unity 우대)", ""), "BE")
        self.assertEqual(jc.posting_family("모바일 앱 개발 [Flutter 우대]", ""), "MOBILE")
        self.assertEqual(jc.posting_family("Unity 게임 클라이언트 개발", ""), "GAME")  # 괄호 밖이면 그대로

    def test_job_field_name_is_mapped(self):
        self.assertEqual(jc.posting_family("소프트웨어 개발 직원 모집",
                                           "[직무] 산업특화 응용 소프트웨어 프로그래머(국방,항공,교통,에너지,금융,자동차 등)"), "INDUSTRIAL")
        self.assertEqual(jc.posting_family("헬프데스크 채용", "[직무] IT 기술지원 전문가"), "INFRA")
        self.assertEqual(jc.posting_family("보안 관제 요원", "[직무] 정보보안 전문가"), "INFRA")
        self.assertEqual(jc.posting_family("연구소 신입", "[직무] 펌웨어 및 임베디드 소프트웨어 프로그래머"), "EMBEDDED")
        self.assertIsNone(jc.posting_family("신입 개발자", "[직무] 응용 소프트웨어 개발자"))  # 넓은 분류는 판단 안 함
        self.assertFalse(jc.family_allowed("백엔드 개발", "소프트웨어 개발 직원 모집",
                                           "[직무] 산업특화 응용 소프트웨어 프로그래머(국방,항공,교통,에너지,금융,자동차 등)"))

    def test_title_still_comes_before_job_field_name(self):
        self.assertEqual(jc.posting_family("클라우드 엔지니어 (신입 가능)", "[직무] 시스템 소프트웨어 개발자"), "INFRA")
        self.assertEqual(jc.posting_family("백엔드 개발자", "[직무] IT 기술지원 전문가"), "BE")

    def test_react_native_is_mobile(self):
        self.assertEqual(jc.posting_family("React Native 앱 개발자 신입", ""), "MOBILE")
        self.assertEqual(jc.posting_family("모바일 앱 개발자(React Native)", ""), "MOBILE")
        self.assertEqual(jc.posting_family("React 웹 개발자", ""), "FE")
        self.assertEqual(jc.posting_family("프론트엔드 개발자 (React Native 우대)", ""), "FE")  # 직함 우선 · 우대 괄호 제외

    def test_server_equipment_is_infra(self):
        self.assertEqual(jc.posting_family("서버,스토리지(S/E) 등 전산장비 유지보수 및 기술지원", ""), "INFRA")
        self.assertEqual(jc.posting_family("API 서버 개발 및 유지보수", ""), "BE")  # 서버 개발은 BE 그대로
        self.assertEqual(jc.posting_family("결제 서버 개발", ""), "BE")

    def test_ai_followed_by_korean(self):
        self.assertEqual(jc.posting_family("(신입/경력) AI솔루션 개발자 채용", ""), "AI")
        self.assertEqual(jc.posting_family("AI서비스 개발", ""), "AI")
        self.assertIsNone(jc.posting_family("AI융합기술연구소 신입 및 경력", ""))  # 기관 이름은 직무가 아님
        self.assertEqual(jc.posting_family("AI융합기술연구소 신입 및 경력", "[직무] 펌웨어 및 임베디드 소프트웨어 프로그래머"),
                         "EMBEDDED")
        self.assertIsNone(jc.posting_family("Gmail솔루션 운영", ""))  # 앞에 영문이 붙으면 AI 가 아님

    def test_unknown_is_allowed(self):
        self.assertIsNone(jc.posting_family("신입 개발자 모집", UNKNOWN))
        self.assertTrue(jc.family_allowed("백엔드 개발", "신입 개발자 모집", UNKNOWN))
        self.assertTrue(jc.family_allowed("마케터", "프론트엔드 개발자", FRONT))  # 희망 직무를 모르면 통과

    def test_different_family_is_not_allowed(self):
        self.assertFalse(jc.family_allowed("백엔드 개발", "프론트엔드 개발자", FRONT))


class RecommendOrderTest(unittest.TestCase):
    def run_recommend(self, entries: dict, scores: dict, profile: dict = PROFILE) -> list[int]:
        store = IndexStore(Path(tempfile.mkdtemp()) / "index.json")
        store.save(entries)
        data = recommend_jobs(RecommendRequest(student_id=1, profile=profile), store,
                              embed_query=fake_embed_query, rerank_fn=fake_reranker(scores), model_name=MODEL)
        return [item.job_posting_id for item in data.items]

    def test_missing_skill_goes_after_full_and_neutral(self):
        ids = self.run_recommend(
            {"1": entry(MISSING), "2": entry(FULL), "3": entry(NEUTRAL)},
            {MISSING: 0.9, FULL: 0.6, NEUTRAL: 0.7},
        )
        self.assertEqual(ids, [3, 2, 1])  # 중립(0.7) · 전부 충족(0.6)은 점수순, 일부 빠짐(0.9)은 뒤로

    def test_neutral_is_not_penalized(self):
        ids = self.run_recommend({"1": entry(NEUTRAL), "2": entry(FULL)}, {NEUTRAL: 0.8, FULL: 0.5})
        self.assertEqual(ids, [1, 2])

    def test_other_family_is_filtered_but_unknown_passes(self):
        ids = self.run_recommend({"1": entry(FRONT), "2": entry(UNKNOWN), "3": entry(FULL)},
                                 {FRONT: 0.95, UNKNOWN: 0.4, FULL: 0.6})
        self.assertEqual(ids, [3, 2])

    def test_body_skill_filter_drops_posting(self):
        body = "[공고] 데이터 서버 개발\n[직무] 응용 소프트웨어 개발자\n[내용] Python · Django · Redis 로 서버 개발"
        ids = self.run_recommend({"1": entry(body), "2": entry(FULL)}, {body: 0.9, FULL: 0.6})
        self.assertEqual(ids, [2])  # 수료생(Spring Boot · MySQL)이 내용의 기술 3개 중 하나도 없음

    def test_empty_when_only_other_family(self):
        self.assertEqual(self.run_recommend({"1": entry(FRONT)}, {FRONT: 0.95}), [])

    def test_threshold_still_applies(self):
        self.assertEqual(self.run_recommend({"1": entry(FULL)}, {FULL: 0.01}), [])

    def test_region_order_still_comes_first(self):
        ids = self.run_recommend(
            {"1": entry(FULL, region="경기 성남시"), "2": entry(MISSING, region="서울 구로구")},
            {FULL: 0.9, MISSING: 0.5},
        )
        self.assertEqual(ids, [2, 1])  # 희망 시 · 도(서울) 우선은 그대로, 그 안에서 기술 정렬


if __name__ == "__main__":
    unittest.main()
