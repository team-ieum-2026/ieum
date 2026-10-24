# 주인: AI-A
"""모든 스키마의 부모. 파이썬에서는 snake_case, JSON 입출력은 camelCase.

사용: Model.model_validate(json_dict) / model.model_dump(by_alias=True)
"""

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class CamelModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True, extra="ignore")
