# 주인: AI-A
"""역량 프로필 (docs/API.md 3-1, DB competency_profile.profile)"""

from schemas.base import CamelModel
from schemas.evidence import EvidenceRef


class ProfileSkill(CamelModel):
    name: str
    evidence: list[EvidenceRef] = []


class ProfileAchievement(CamelModel):
    text: str
    evidence: list[EvidenceRef] = []


class CompetencyProfile(CamelModel):
    roles: list[str] = []
    skills: list[ProfileSkill] = []
    achievements: list[ProfileAchievement] = []
    certificates: list[str] = []
    desired_job: str | None = None
    preferred_region: str | None = None
