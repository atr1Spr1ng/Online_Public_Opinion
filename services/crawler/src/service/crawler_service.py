import json as _json
import jieba.posseg as pseg
from concurrent.futures import ThreadPoolExecutor, as_completed
from threading import Lock

from openai import OpenAI

from adapter.news_link_discoverer import NewsLinkDiscoverer
from adapter.news_please_adapter import NewsPleaseAdapter
from adapter.search_adapter import TopicSearchAdapter
from config.settings import KEYWORD_SPLIT_PROMPT, KeywordSplitConfig
from exception.crawler_exception import CrawlerException
from model.crawl_result import CrawlResult
from model.news_collect_result import FailedNewsCrawl, NewsCollectResult
from model.news_discover_result import NewsDiscoverResult
from model.topic_search_result import TopicSearchResult


class CrawlerService:
    def __init__(
        self,
        news_adapter: NewsPleaseAdapter | None = None,
        link_discoverer: NewsLinkDiscoverer | None = None,
        search_adapter: TopicSearchAdapter | None = None,
    ) -> None:
        self._news_adapter = news_adapter or NewsPleaseAdapter()
        self._link_discoverer = link_discoverer or NewsLinkDiscoverer()
        self._search_adapter = search_adapter or TopicSearchAdapter()
        self._llm_client: OpenAI | None = None

    def _get_llm_client(self) -> OpenAI | None:
        if self._llm_client is None and KeywordSplitConfig.enabled():
            self._llm_client = OpenAI(
                api_key=KeywordSplitConfig.api_key,
                base_url=KeywordSplitConfig.base_url,
            )
        return self._llm_client

    def crawl_news(self, url: str) -> CrawlResult:
        return self._news_adapter.crawl(url)

    def discover_news_links(self, url: str, limit: int) -> NewsDiscoverResult:
        return self._link_discoverer.discover(url, limit)

    def collect_news(self, url: str, limit: int) -> NewsCollectResult:
        discovered = self.discover_news_links(url, limit)
        articles: list[CrawlResult] = []
        failures: list[FailedNewsCrawl] = []

        def _crawl_one(link):
            try:
                article = self.crawl_news(link.url)
            except CrawlerException as exc:
                return ("fail", FailedNewsCrawl(url=link.url, title=link.title, reason=str(exc)))
            if article.extract_status == "SUCCESS":
                return ("ok", article)
            else:
                return ("fail", FailedNewsCrawl(url=link.url, title=link.title, reason=article.message))

        with ThreadPoolExecutor(max_workers=5) as pool:
            futures = [pool.submit(_crawl_one, link) for link in discovered.links]
            for future in as_completed(futures):
                try:
                    status, result = future.result()
                except Exception:
                    continue
                if status == "ok":
                    articles.append(result)
                else:
                    failures.append(result)

        return NewsCollectResult(
            source_url=discovered.source_url,
            final_url=discovered.final_url,
            source_name=discovered.source_name,
            source_type=discovered.source_type,
            total_discovered=discovered.total_found,
            total_success=len(articles),
            total_failed=len(failures),
            articles=articles,
            failures=failures,
        )

    def search_by_topic(self, keyword: str, source_url: str, limit: int) -> TopicSearchResult:
        profile = self._search_adapter.match_profile(source_url)
        if profile is None:
            raise CrawlerException(f"当前不支持该新闻源的搜索: {source_url}")

        # 1. Split long keyword → parallel search with each group → collect + dedup links
        search_queries = self._split_keywords(keyword)
        all_links: list = []
        seen_urls: set[str] = set()
        seen_lock = Lock()

        with ThreadPoolExecutor(max_workers=min(len(search_queries), 3)) as pool:
            futures = {pool.submit(self._search_adapter.search, q, profile, limit): q for q in search_queries}
            for future in as_completed(futures):
                try:
                    links = future.result()
                except Exception:
                    continue
                with seen_lock:
                    for link in links:
                        if link.url not in seen_urls:
                            seen_urls.add(link.url)
                            all_links.append(link)

        # 2. Sina feed supplement: pull recent articles from roll API (cross-day coverage)
        if profile.name == "新浪新闻":
            try:
                feed_links = self._search_adapter.search_sina_feed_all(limit * 3)
                for link in feed_links:
                    if link.url not in seen_urls:
                        seen_urls.add(link.url)
                        all_links.append(link)
            except Exception:
                pass

        # 3. Extract entity words for relevance filter
        entity_words = self._extract_nouns(keyword)

        # 4. Parallel crawl each link + filter by relevance
        articles: list[CrawlResult] = []
        failures: list[FailedNewsCrawl] = []

        def _crawl_one(link):
            try:
                article = self._news_adapter.crawl(link.url)
            except CrawlerException as exc:
                return ("fail", FailedNewsCrawl(url=link.url, title=link.title, reason=str(exc)))
            if article.extract_status != "SUCCESS":
                return ("fail", FailedNewsCrawl(url=link.url, title=link.title, reason=article.message))
            if not self._is_relevant(article.title or "", article.content or "", entity_words):
                return ("skip", None)
            return ("ok", article)

        with ThreadPoolExecutor(max_workers=5) as pool:
            futures = [pool.submit(_crawl_one, link) for link in all_links]
            for future in as_completed(futures):
                try:
                    status, result = future.result()
                except Exception:
                    continue
                if status == "ok":
                    articles.append(result)
                elif status == "fail":
                    failures.append(result)

        return TopicSearchResult(
            keyword=keyword,
            source_name=profile.name,
            source_type=profile.source_type,
            source_url=source_url,
            total_found=len(all_links),
            total_success=len(articles),
            total_failed=len(failures),
            articles=articles,
            failures=failures,
        )

    @staticmethod
    def _extract_nouns(keyword: str) -> list[str]:
        """Extract multi-character nouns from keyword using jieba POS tagging."""
        words = pseg.cut(keyword)
        return [w.word for w in words if w.flag.startswith("n") and len(w.word) >= 2]

    def _split_keywords(self, keyword: str) -> list[str]:
        """Split a long search keyword into 2-3 short keyword groups.

        Tries LLM-based extraction first (understands semantic meaning);
        falls back to jieba POS-based hardcoded grouping on failure.
        Short keywords (≤5 chars or ≤2 nouns) skip LLM — no splitting needed.
        """
        # Short keywords don't benefit from splitting
        nouns = self._extract_nouns(keyword)
        if len(nouns) <= 2:
            return [keyword]

        # Try LLM-based keyword extraction
        llm_result = self._split_keywords_llm(keyword)
        if llm_result:
            return llm_result

        # Fallback: jieba-based hardcoded grouping
        core = nouns[0]
        others = nouns[1:]

        groups: list[str] = []
        if others:
            groups.append(f"{core} {' '.join(others[:2])}")
            if len(others) > 2:
                groups.append(f"{core} {' '.join(others[2:4])}")
        groups.append(core)
        return groups[:3]

    def _split_keywords_llm(self, keyword: str) -> list[str] | None:
        """Use LLM to extract search-friendly keyword groups. Returns None on failure."""
        client = self._get_llm_client()
        if client is None:
            return None
        try:
            response = client.chat.completions.create(
                model=KeywordSplitConfig.model,
                messages=[
                    {"role": "system", "content": KEYWORD_SPLIT_PROMPT},
                    {"role": "user", "content": keyword},
                ],
                temperature=0.1,
                max_tokens=100,
            )
            text = response.choices[0].message.content.strip()
            groups = _json.loads(text)
            if isinstance(groups, list) and len(groups) > 0:
                return groups[:3]
        except Exception:
            pass
        return None

    @staticmethod
    def _is_relevant(title: str, content: str, entity_words: list[str]) -> bool:
        """Filter: article must contain at least the most specific entity word."""
        if not entity_words:
            return True
        text = f"{title} {content}"
        return entity_words[0] in text
