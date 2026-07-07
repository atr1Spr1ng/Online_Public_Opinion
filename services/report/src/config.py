import os


class LLMConfig:
    api_key: str = os.getenv("DEEPSEEK_API_KEY", "")
    base_url: str = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
    model: str = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

    @classmethod
    def enabled(cls) -> bool:
        return bool(cls.api_key)


SYSTEM_PROMPT = """你是一个专业的网络舆情分析师。用户会就某个舆情事件向你提问，请基于提供的事件报告数据给出专业、准确的分析回答。

要求：
1. 回答使用中文
2. 回答简洁专业，控制在 200 字以内
3. 如果报告数据不足以回答该问题，如实说明
4. 不要编造数据中没有的信息"""
