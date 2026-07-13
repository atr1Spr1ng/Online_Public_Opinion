import math
import json
import re
import logging
from datetime import datetime, timedelta
from collections import defaultdict

from src.model.clustering_model import ArticleItem, EventCluster, ClusterResponse
from src.config import EventSummaryConfig, EVENT_NAMING_SYSTEM_PROMPT

logger = logging.getLogger(__name__)


class ClusteringService:

    def __init__(self):
        self._llm_client = None

    @property
    def llm_client(self):
        if self._llm_client is None and EventSummaryConfig.enabled():
            try:
                from openai import OpenAI
                self._llm_client = OpenAI(
                    api_key=EventSummaryConfig.api_key,
                    base_url=EventSummaryConfig.base_url,
                )
                logger.info("Clustering LLM client initialized")
            except Exception as e:
                logger.warning("Failed to init Clustering LLM client: %s", e)
        return self._llm_client

    def cluster(self, articles: list[ArticleItem], threshold: float = 0.25) -> ClusterResponse:
        if not articles:
            return ClusterResponse(events=[], total_articles=0, clustered_articles=0, unclustered_articles=0)

        # 将关键词字符串转为集合
        keyword_sets: dict[int, set[str]] = {}
        for a in articles:
            kw = a.keywords or ""
            keyword_sets[a.id] = set(k.strip() for k in kw.split(",") if k.strip())

        # SinglePass 聚类
        clusters: list[dict] = []  # [{"ids": set, "keyword_union": set, "articles": list}]
        no_keyword_count = 0

        for article in articles:
            kw_set = keyword_sets.get(article.id, set())
            if not kw_set:
                no_keyword_count += 1
                continue

            best_idx = -1
            best_sim = 0.0
            for i, cluster in enumerate(clusters):
                sim = self._jaccard(kw_set, cluster["keyword_union"])
                if sim > best_sim:
                    best_sim = sim
                    best_idx = i

            if best_idx >= 0 and best_sim >= threshold:
                clusters[best_idx]["ids"].add(article.id)
                clusters[best_idx]["keyword_union"] |= kw_set
                clusters[best_idx]["articles"].append(article)
            else:
                clusters.append({
                    "ids": {article.id},
                    "keyword_union": set(kw_set),
                    "articles": [article]
                })

        # 去除单篇文章的簇（不构成"事件"）
        valid_clusters = [c for c in clusters if len(c["ids"]) >= 2]
        noise_count = sum(len(c["ids"]) for c in clusters if len(c["ids"]) < 2) + no_keyword_count

        events: list[EventCluster] = []
        now = datetime.now()

        for i, cluster in enumerate(valid_clusters):
            event_id = i + 1
            arts = cluster["articles"]

            # LLM 生成标题和关键词，失败时降级为词频
            title, top_keywords = self._generate_event_name(arts, keyword_sets)

            article_count = len(arts)
            hotness = self._calc_hotness(arts, now)
            lifecycle = self._calc_lifecycle(arts, now)

            # 时间范围
            times = [a.published_at for a in arts if a.published_at]
            times.sort()
            start_time = times[0] if times else ""
            end_time = times[-1] if times else ""

            events.append(EventCluster(
                event_id=event_id,
                title=title,
                keywords=top_keywords,
                article_ids=list(cluster["ids"]),
                article_count=article_count,
                hotness=round(hotness, 2),
                lifecycle=lifecycle,
                start_time=start_time,
                end_time=end_time,
            ))

        events.sort(key=lambda e: e.hotness, reverse=True)

        return ClusterResponse(
            events=events,
            total_articles=len(articles),
            clustered_articles=sum(e.article_count for e in events),
            unclustered_articles=noise_count,
        )

    def _generate_event_name(self, arts: list[ArticleItem], keyword_sets: dict[int, set[str]]) -> tuple[str, list[str]]:
        """生成事件标题和关键词：LLM 优先，词频降级"""
        if EventSummaryConfig.enabled() and self.llm_client:
            llm_result = self._generate_by_llm(arts)
            if llm_result is not None:
                return llm_result

        return self._generate_by_frequency(arts, keyword_sets)

    def _generate_by_llm(self, arts: list[ArticleItem]) -> tuple[str, list[str]] | None:
        """DeepSeek LLM 生成事件标题和关键词"""
        try:
            articles_text = ""
            for j, a in enumerate(arts[:15]):  # 最多15篇防止token超限
                title = a.title or "无标题"
                keywords = a.keywords or ""
                articles_text += f"[{j + 1}] 标题：{title}\n    关键词：{keywords}\n\n"

            user_content = (
                f"以下是一个舆情事件的相关报道：\n\n{articles_text}"
                f"请根据以上报道生成事件标题（15字以内，不以\"事件\"结尾）和5-10个核心关键词。严格按 JSON 格式回复。"
            )

            response = self.llm_client.chat.completions.create(
                model=EventSummaryConfig.model,
                messages=[
                    {"role": "system", "content": EVENT_NAMING_SYSTEM_PROMPT},
                    {"role": "user", "content": user_content}
                ],
                temperature=0.3,
                max_tokens=300,
            )
            content = response.choices[0].message.content
            return self._parse_naming_response(content)

        except Exception as e:
            logger.error("Event naming LLM call failed: %s", e)
            return None

    def _parse_naming_response(self, content: str) -> tuple[str, list[str]] | None:
        """解析 LLM 返回的 JSON"""
        try:
            json_match = re.search(r'\{[\s\S]*\}', content)
            if json_match:
                data = json.loads(json_match.group())
                title = data.get("title", "").strip()
                keywords = data.get("keywords", [])
                if title and keywords:
                    return (title, keywords)
        except (json.JSONDecodeError, KeyError) as e:
            logger.warning("Failed to parse naming LLM JSON: %s", e)
        return None

    def _generate_by_frequency(self, arts: list[ArticleItem], keyword_sets: dict[int, set[str]]) -> tuple[str, list[str]]:
        """词频降级：最高频关键词 + 事件"""
        keyword_freq: dict[str, int] = defaultdict(int)
        for a in arts:
            for kw in keyword_sets.get(a.id, set()):
                keyword_freq[kw] += 1

        top_keywords = sorted(keyword_freq, key=keyword_freq.get, reverse=True)[:10]
        title_kw = top_keywords[0] if top_keywords else "未命名"
        title = f"{title_kw}事件"
        return (title, top_keywords)

    def _jaccard(self, set1: set[str], set2: set[str]) -> float:
        if not set1 or not set2:
            return 0.0
        intersection = len(set1 & set2)
        union = len(set1 | set2)
        return intersection / union if union > 0 else 0.0

    def _calc_hotness(self, articles: list[ArticleItem], now: datetime) -> float:
        """热度 = 文章数 × 时间衰减"""
        decay_factor = 0.1
        article_count = len(articles)

        # 找最近的发布时间
        latest = None
        for a in articles:
            t = self._parse_time(a.published_at)
            if t and (latest is None or t > latest):
                latest = t

        if latest:
            days = (now - latest).total_seconds() / 86400
        else:
            days = 7  # 默认7天

        time_decay = math.exp(-decay_factor * max(days, 0))
        return article_count * time_decay

    def _calc_lifecycle(self, articles: list[ArticleItem], now: datetime) -> str:
        """根据文章时间分布判断生命周期阶段"""
        if len(articles) < 2:
            return "潜伏期"

        times = []
        for a in articles:
            t = self._parse_time(a.published_at)
            if t:
                times.append(t)
        if not times:
            return "潜伏期"

        times.sort()
        total_span = (times[-1] - times[0]).total_seconds() / 3600  # hours
        if total_span <= 0:
            return "潜伏期"

        # 分段计数：前1/3、中1/3、后1/3
        third = total_span / 3
        t0 = times[0]
        first_count = sum(1 for t in times if (t - t0).total_seconds() / 3600 < third)
        mid_count = sum(1 for t in times if third <= (t - t0).total_seconds() / 3600 < 2 * third)
        last_count = len(times) - first_count - mid_count

        # 随时间衰减 → 衰退期
        if last_count > mid_count or (mid_count > first_count and first_count > 0):
            return "成长期"
        elif mid_count >= last_count and mid_count >= first_count:
            return "高潮期"
        elif first_count > mid_count and last_count < mid_count:
            return "衰退期"

        return "成长期"

    def _parse_time(self, time_str: str) -> datetime | None:
        if not time_str:
            return None
        formats = [
            "%Y-%m-%d %H:%M:%S",
            "%Y-%m-%dT%H:%M:%S",
            "%Y-%m-%d",
        ]
        for fmt in formats:
            try:
                return datetime.strptime(time_str[:19], fmt)
            except ValueError:
                continue
        return None
