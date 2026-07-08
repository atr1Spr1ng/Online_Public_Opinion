import math
from datetime import datetime, timedelta
from collections import defaultdict

from src.model.clustering_model import ArticleItem, EventCluster, ClusterResponse


class ClusteringService:

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
            keyword_freq: dict[str, int] = defaultdict(int)
            for a in arts:
                for kw in keyword_sets.get(a.id, set()):
                    keyword_freq[kw] += 1

            # 取频率最高的关键词作为事件标签
            top_keywords = sorted(keyword_freq, key=keyword_freq.get, reverse=True)[:10]

            # 事件标题：频率最高的关键词 + "事件"
            title_kw = top_keywords[0] if top_keywords else "未命名"
            title = f"{title_kw}事件"

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
