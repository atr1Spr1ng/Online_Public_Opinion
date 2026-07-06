from dataclasses import dataclass


@dataclass(frozen=True)
class CrawlerSettings:
    request_timeout_seconds: float = 10.0
    max_response_bytes: int = 5 * 1024 * 1024
    user_agent: str = (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/126.0 Safari/537.36 "
        "OnlinePublicOpinionResearch/0.1"
    )


settings = CrawlerSettings()
