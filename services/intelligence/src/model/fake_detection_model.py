from pydantic import BaseModel, Field


class FakeDetectionRequest(BaseModel):
    title: str = ""
    content: str = ""
    language: str = "zh"
    mode: str | None = None


class FakeDetectionFeature(BaseModel):
    name: str = Field(description="特征名称")
    score: float = Field(description="特征得分 0-1")
    description: str = ""


class FakeDetectionResponse(BaseModel):
    fake_score: float = Field(default=0.0, description="虚假概率 0-1")
    is_fake: bool = Field(default=False, description="是否判定为虚假")
    detection_method: str = Field(default="rule", description="rule / llm / hybrid")
    features: list[FakeDetectionFeature] = []
    details: str = ""
