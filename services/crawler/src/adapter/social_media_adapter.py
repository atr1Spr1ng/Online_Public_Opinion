from datetime import datetime, timezone

import httpx

from src.config.settings import CrawlerSettings, settings
from src.exception.crawler_exception import CrawlerException
from src.model.social_hot_item import SocialHotItem, SocialHotResult


class SocialMediaAdapter:
    """社交平台爬虫适配器，调用公开热搜/热榜 API 获取实时热点"""

    _BROWSER_HEADERS = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/126.0.0.0 Safari/537.36"
        ),
        "Accept": "application/json, text/plain, */*",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    }

    _WEIBO_HEADERS = {
        **_BROWSER_HEADERS,
        "Referer": "https://weibo.com/",
        "X-Requested-With": "XMLHttpRequest",
    }

    _BAIDU_HEADERS = {
        **_BROWSER_HEADERS,
        "Referer": "https://top.baidu.com/board?tab=realtime",
    }

    def __init__(
        self,
        client: httpx.Client | None = None,
        crawler_settings: CrawlerSettings = settings,
    ) -> None:
        self._settings = crawler_settings
        self._client = client or httpx.Client(
            follow_redirects=True,
            timeout=crawler_settings.request_timeout_seconds,
        )

    def fetch_weibo_hot(self) -> SocialHotResult:
        """获取微博实时热搜榜（无需登录）"""
        url = "https://weibo.com/ajax/side/hotSearch"
        try:
            response = self._client.get(url, headers=self._WEIBO_HEADERS)
            response.raise_for_status()
            data = response.json()
        except httpx.TimeoutException as exc:
            raise CrawlerException("微博热搜请求超时") from exc
        except httpx.HTTPStatusError as exc:
            raise CrawlerException(
                f"微博热搜接口返回异常状态码: {exc.response.status_code}"
            ) from exc
        except httpx.RequestError as exc:
            raise CrawlerException(f"无法访问微博热搜接口: {exc}") from exc

        items: list[SocialHotItem] = []
        realtime = data.get("data", {}).get("realtime", [])

        for idx, entry in enumerate(realtime[:50], start=1):
            raw_score = (entry.get("raw_hot") or entry.get("num") or 0)
            word = entry.get("word", "")
            scheme = entry.get("word_scheme", "")
            url = f"https://s.weibo.com/weibo?q={word}&t=31" if word else scheme

            items.append(
                SocialHotItem(
                    rank=idx,
                    title=word,
                    hot_score=raw_score,
                    url=url,
                )
            )

        return SocialHotResult(
            platform="weibo",
            items=items,
            fetched_at=datetime.now(timezone.utc),
        )

    def fetch_baidu_hot(self) -> SocialHotResult:
        """获取百度实时热搜榜（无需登录）"""
        url = "https://top.baidu.com/api/board?platform=pc&tab=realtime"
        try:
            response = self._client.get(url, headers=self._BAIDU_HEADERS)
            response.raise_for_status()
            data = response.json()
        except httpx.TimeoutException as exc:
            raise CrawlerException("百度热搜请求超时") from exc
        except httpx.HTTPStatusError as exc:
            raise CrawlerException(
                f"百度热搜接口返回异常状态码: {exc.response.status_code}"
            ) from exc
        except httpx.RequestError as exc:
            raise CrawlerException(f"无法访问百度热搜接口: {exc}") from exc

        items: list[SocialHotItem] = []
        for card in data.get("data", {}).get("cards", []):
            for idx, entry in enumerate(card.get("content", []), start=1):
                title = entry.get("query") or entry.get("word", "")
                hot_score = entry.get("hotScore", 0) or 0
                url = entry.get("url", "")
                desc = entry.get("desc") or ""

                items.append(
                    SocialHotItem(
                        rank=idx,
                        title=title,
                        hot_score=hot_score,
                        url=url,
                        summary=desc,
                    )
                )

        return SocialHotResult(
            platform="baidu",
            items=items,
            fetched_at=datetime.now(timezone.utc),
        )
