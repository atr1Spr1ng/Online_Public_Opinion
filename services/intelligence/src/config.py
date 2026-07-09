import os


class FakeDetectionConfig:
    api_key: str = os.getenv("DEEPSEEK_API_KEY", "")
    base_url: str = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
    model: str = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

    @classmethod
    def enabled(cls) -> bool:
        return bool(cls.api_key)


class EventSummaryConfig:
    api_key: str = os.getenv("DEEPSEEK_API_KEY", "")
    base_url: str = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
    model: str = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

    @classmethod
    def enabled(cls) -> bool:
        return bool(cls.api_key)


EVENT_SUMMARY_SYSTEM_PROMPT = """你是一个专业的网络舆情分析师。请根据提供的事件标题、关键词和相关报道，提取事件的完整概述。

请严格按以下 JSON 格式回复，每个字段用中文描述：
{
  "summary": "事件的一段话综合概述（200字以内）",
  "time": "事件发生时间或时间范围",
  "location": "事件发生地点",
  "cause": "事件的起因",
  "persons": "涉事人物或机构",
  "key_steps": "事件的关键节点和发展步骤",
  "important_info": "其他重要补充信息"
}

要求：
1. 每个字段必须填写，如果无法从材料中确定，请填写"暂无信息"
2. 不要编造材料中没有的事实
3. 概述部分精炼准确，不超过200字"""


FAKE_DETECTION_SYSTEM_PROMPT = """你是一个专业的虚假信息检测助手。请分析给定文本是否为虚假新闻/虚假信息。

判断标准：
1. 缺乏可验证的信息来源（如未引用官方机构、媒体报道等）
2. 过度使用煽情、夸张、情绪化语言
3. 叙事逻辑矛盾或违反常识
4. 属于典型的谣言传播模式

请严格按以下 JSON 格式回复：
{"fake_score": 0.0-1.0的浮点数, "reason": "简要说明判断理由"}

其中 fake_score >= 0.5 表示倾向于虚假信息，< 0.5 表示倾向于可信信息。"""
