import os


class FakeDetectionConfig:
    api_key: str = os.getenv("DEEPSEEK_API_KEY", "")
    base_url: str = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
    model: str = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

    @classmethod
    def enabled(cls) -> bool:
        return bool(cls.api_key)


FAKE_DETECTION_SYSTEM_PROMPT = """你是一个专业的虚假信息检测助手。请分析给定文本是否为虚假新闻/虚假信息。

判断标准：
1. 缺乏可验证的信息来源（如未引用官方机构、媒体报道等）
2. 过度使用煽情、夸张、情绪化语言
3. 叙事逻辑矛盾或违反常识
4. 属于典型的谣言传播模式

请严格按以下 JSON 格式回复：
{"fake_score": 0.0-1.0的浮点数, "reason": "简要说明判断理由"}

其中 fake_score >= 0.5 表示倾向于虚假信息，< 0.5 表示倾向于可信信息。"""
