import os
from dataclasses import dataclass


@dataclass(frozen=True)
class CrawlerSettings:
    request_timeout_seconds: float = 20.0
    max_response_bytes: int = 5 * 1024 * 1024
    user_agent: str = (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/126.0 Safari/537.36 "
        "OnlinePublicOpinionResearch/0.1"
    )


@dataclass(frozen=True)
class KeywordSplitConfig:
    api_key: str = os.getenv("DEEPSEEK_API_KEY", "")
    base_url: str = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
    model: str = os.getenv("DEEPSEEK_MODEL", "deepseek-chat")

    @classmethod
    def enabled(cls) -> bool:
        return bool(cls.api_key)


KEYWORD_SPLIT_PROMPT = """你是一个搜索关键词提取助手。给定一个新闻话题，提取2-3组适合在中文新闻网站搜索的关键词组合。

规则：
1. 每组关键词应简短（2-5个词），适合搜索引擎查询
2. 第一组应包含核心实体词+最重要的修饰词
3. 后续组可以用同义词或不同角度改写，扩大搜索覆盖面
4. 如果话题本身已经很简短（5个字以内），直接返回包含原词的单元素数组

请只回复一个 JSON 数组，不要加任何其他文字：
["关键词组1", "关键词组2", "关键词组3"]"""


settings = CrawlerSettings()
