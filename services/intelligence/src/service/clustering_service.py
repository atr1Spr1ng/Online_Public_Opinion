import math
import json
import re
import logging
from datetime import datetime, timedelta
from collections import defaultdict

import numpy as np

from src.model.clustering_model import ArticleItem, EventCluster, ClusterResponse
from src.config import EventSummaryConfig, EVENT_NAMING_SYSTEM_PROMPT
from src.service.embedding_service import EmbeddingService

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

        # 构建关键词集合（用于降级方案和命名）
        keyword_sets: dict[int, set[str]] = {}
        for a in articles:
            kw = a.keywords or ""
            keyword_sets[a.id] = set(k.strip() for k in kw.split(",") if k.strip())

        # 1. 稠密向量 + HDBSCAN 聚类（主力）
        clusters, noise_count = self._cluster_by_hdbscan(articles)
        if clusters is not None:
            logger.info("Clustering: HDBSCAN grouped %d articles into %d clusters", len(articles), len(clusters))
        else:
            # 2. HDBSCAN 不可用，降级为 SinglePass + Jaccard
            clusters, noise_count = self._cluster_by_jaccard(articles, keyword_sets, threshold)
            logger.info("Clustering: Jaccard fallback grouped %d articles into %d clusters", len(articles), len(clusters))

        # 后处理：簇内一致性剪枝，剔除与簇中心余弦距离过远的离群文章
        clusters, pruned_count = self._prune_cluster_outliers(clusters)
        if pruned_count > 0:
            noise_count += pruned_count
            logger.info("Clustering: pruned %d outlier articles from clusters", pruned_count)

        # 后处理：合并语义高度相似的簇（簇中心余弦相似度 ≥ 0.92），防 HDBSCAN 把同一事件拆散
        merged_count = len(clusters)
        clusters = self._merge_similar_clusters(clusters, threshold=0.92)
        merged_count = merged_count - len(clusters)
        if merged_count > 0:
            logger.info("Clustering: merged %d similar clusters, now %d total", merged_count, len(clusters))

        # 排除噪声簇（HDBSCAN 标记的 _noise），这些文章已在 noise_count 中
        valid_clusters = [c for c in clusters if len(c["ids"]) >= 2 and not c.get("_noise")]
        # noise_count 已包含 HDBSCAN/Jaccard 返回的噪声数，这里只补充单篇非噪声簇
        for c in clusters:
            if len(c["ids"]) < 2 and not c.get("_noise"):
                noise_count += len(c["ids"])

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

    def _cluster_by_hdbscan(self, articles: list[ArticleItem]) -> tuple[list[dict] | None, int]:
        """稠密向量 + HDBSCAN 聚类，失败返回 (None, 0)。

        返回的每个 cluster dict 附带 _vectors 字段（np.ndarray），
        供后续 _prune_cluster_outliers 使用，避免重复编码。
        """
        try:
            embed_service = EmbeddingService()
            if not embed_service.available:
                logger.warning("EmbeddingService not available, falling back to Jaccard")
                return None, 0

            # 构造文本：标题 + 摘要（比纯关键词语义信息更丰富）
            texts = []
            for a in articles:
                title = a.title or ""
                summary = a.summary or ""
                texts.append(f"{title} {summary}"[:512])

            vectors = embed_service.encode(texts)
            if vectors.shape[0] == 0:
                return None, 0

            # HDBSCAN 聚类
            import hdbscan
            clusterer = hdbscan.HDBSCAN(
                min_cluster_size=2,
                min_samples=1,
                metric='euclidean',
                cluster_selection_epsilon=0.08,
            )
            labels = clusterer.fit_predict(vectors)
            logger.info("HDBSCAN: %d clusters found, noise=%d",
                        len(set(labels)) - (1 if -1 in labels else 0),
                        (labels == -1).sum())

            # 转换为 clusters 结构（保留 per-article vectors）
            clusters: list[dict] = []
            noise_articles: list[ArticleItem] = []

            unique_labels = set(labels)
            for label in unique_labels:
                indices = np.where(labels == label)[0]
                group_articles = [articles[i] for i in indices]
                group_ids = {a.id for a in group_articles}
                group_vectors = vectors[indices]
                if label == -1:
                    noise_articles.extend(group_articles)
                else:
                    clusters.append({
                        "ids": group_ids,
                        "articles": group_articles,
                        "_vectors": group_vectors,
                    })

            # 噪声文章收集到 _noise 簇
            if noise_articles:
                clusters.append({
                    "ids": {a.id for a in noise_articles},
                    "articles": noise_articles,
                    "_noise": True,
                })

            noise_count = len(noise_articles)
            return clusters, noise_count

        except Exception as e:
            logger.error("HDBSCAN clustering failed: %s", e)
            return None, 0

    def _cluster_by_jaccard(self, articles: list[ArticleItem], keyword_sets: dict[int, set[str]], threshold: float) -> tuple[list[dict], int]:
        """SinglePass + Jaccard 关键词聚类（原有降级方案）"""
        clusters: list[dict] = []
        no_keyword_count = 0

        for article in articles:
            kw_set = keyword_sets.get(article.id, set())
            if not kw_set:
                no_keyword_count += 1
                continue

            best_idx = -1
            best_sim = 0.0
            for i, cluster in enumerate(clusters):
                sim = self._jaccard(kw_set, cluster.get("keyword_union", set()))
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

        return clusters, no_keyword_count

    def _prune_cluster_outliers(self, clusters: list[dict], min_sigma: float = 2.0) -> tuple[list[dict], int]:
        """簇内一致性剪枝：自适应阈值剔除统计离群文章。

        对每个非噪声簇计算中心向量及各文章到中心的余弦相似度分布，
        剔除低于 μ - Nσ 的文章（默认 N=2，即剔除下尾 2σ 外的离群点）。
        自适应阈值让紧簇和松簇各有自己的标准，避免固定阈值一刀切。

        返回 (clusters, total_pruned)。
        """
        if not clusters:
            return clusters, 0

        total_pruned = 0
        noise_ids: set[int] = set()
        noise_articles: list[ArticleItem] = []

        # 先收集已有噪声簇的文章
        for c in clusters:
            if c.get("_noise"):
                noise_ids.update(c["ids"])
                noise_articles.extend(c["articles"])

        pruned_clusters: list[dict] = []
        for c in clusters:
            if c.get("_noise"):
                pruned_clusters.append(c)
                continue

            vectors = c.pop("_vectors", None)
            if vectors is None or vectors.shape[0] < 2:
                pruned_clusters.append(c)
                continue

            # 簇中心向量（L2 归一化向量的均值近似中心）
            centroid = np.mean(vectors, axis=0)
            centroid_norm = np.linalg.norm(centroid)
            if centroid_norm > 0:
                centroid = centroid / centroid_norm

            # 各文章与中心的余弦相似度
            cos_sims = np.dot(vectors, centroid)  # 已归一化 → 点积即余弦

            mean_sim = float(np.mean(cos_sims))
            std_sim = float(np.std(cos_sims))
            # 自适应阈值：μ - Nσ，不低于 0.55 的绝对下限
            adaptive_threshold = max(mean_sim - min_sigma * std_sim, 0.55)
            logger.info("Pruning: cluster size=%d, cos_sim μ=%.4f σ=%.4f, adaptive_threshold=%.4f",
                        len(c["articles"]), mean_sim, std_sim, adaptive_threshold)

            keep_mask = cos_sims >= adaptive_threshold
            prune_mask = ~keep_mask

            pruned_in_cluster = int(prune_mask.sum())
            if pruned_in_cluster > 0:
                pruned_cos = cos_sims[prune_mask]
                logger.info("Pruning: removing %d articles from cluster (pruned cos_sims: %s)",
                            pruned_in_cluster, ", ".join(f"{v:.4f}" for v in pruned_cos))
            if pruned_in_cluster == 0:
                pruned_clusters.append(c)
                continue

            # 保留的文章
            keep_indices = np.where(keep_mask)[0]
            keep_articles = [c["articles"][int(i)] for i in keep_indices]
            keep_ids = {a.id for a in keep_articles}
            keep_vectors = vectors[keep_indices]

            # 剔除的文章归入噪声
            prune_indices = np.where(prune_mask)[0]
            for i in prune_indices:
                idx = int(i)
                art = c["articles"][idx]
                if art.id not in noise_ids:
                    noise_ids.add(art.id)
                    noise_articles.append(art)

            total_pruned += pruned_in_cluster

            if len(keep_articles) >= 2:
                pruned_clusters.append({
                    "ids": keep_ids,
                    "articles": keep_articles,
                    "_vectors": keep_vectors,
                })
            else:
                # 剪枝后不足 2 篇，整簇降级为噪声
                for art in c["articles"]:
                    if art.id not in noise_ids:
                        noise_ids.add(art.id)
                        noise_articles.append(art)
                total_pruned += len(c["articles"]) - pruned_in_cluster

        # 重建噪声簇
        if noise_articles:
            pruned_clusters = [c for c in pruned_clusters if not c.get("_noise")]
            pruned_clusters.append({
                "ids": noise_ids,
                "articles": noise_articles,
                "_noise": True,
            })

        return pruned_clusters, total_pruned

    def _merge_similar_clusters(self, clusters: list[dict], threshold: float = 0.85) -> list[dict]:
        """后处理：基于簇中心向量的余弦相似度合并语义高度相似的簇。

        与 HDBSCAN 使用同一语义空间（BGE 向量），替代原来的关键词 Jaccard 方案，
        消除"关键词偶然重叠导致传递链合并"的问题。

        阈值 threshold 为余弦相似度，默认 0.85（即向量夹角 < 31.8°），
        只合并语义几乎相同的簇。
        """
        if len(clusters) <= 1:
            return clusters

        embed_service = EmbeddingService()
        if not embed_service.available:
            logger.warning("EmbeddingService unavailable, skip merge")
            return clusters

        # 为每个簇计算中心向量（簇内所有文章向量的均值）
        centroids: list[np.ndarray | None] = []
        for c in clusters:
            texts = []
            for a in c["articles"]:
                title = a.title or ""
                summary = a.summary or ""
                texts.append(f"{title} {summary}"[:512])
            if not texts:
                centroids.append(None)
                continue
            vecs = embed_service.encode(texts)
            centroids.append(np.mean(vecs, axis=0))

        n = len(clusters)
        parent = list(range(n))

        def find(x: int) -> int:
            while parent[x] != x:
                parent[x] = parent[parent[x]]
                x = parent[x]
            return x

        def union(x: int, y: int):
            px, py = find(x), find(y)
            if px != py:
                parent[px] = py

        for i in range(n):
            for j in range(i + 1, n):
                ci, cj = centroids[i], centroids[j]
                if ci is None or cj is None:
                    continue
                # L2 归一化向量，余弦相似度 = 点积
                cos_sim = float(np.dot(ci, cj))
                if cos_sim >= threshold:
                    union(i, j)

        # 追踪 _noise 标记：记录每个原始簇是否噪声
        original_noise = [bool(c.get("_noise")) for c in clusters]

        # 合并到新的 groups
        groups: dict[int, dict] = {}
        for i in range(n):
            root = find(i)
            if root not in groups:
                groups[root] = {"ids": set(clusters[i]["ids"]), "articles": list(clusters[i]["articles"])}
            else:
                groups[root]["ids"] |= clusters[i]["ids"]
                groups[root]["articles"].extend(clusters[i]["articles"])

        merged = list(groups.values())

        # 正确恢复 _noise 标记：若合并组中任一原始簇是噪声，结果也是噪声
        for root_idx, g in zip(groups.keys(), merged):
            for i in range(n):
                if find(i) == root_idx and original_noise[i]:
                    g["_noise"] = True
                    break

        return merged

    def _generate_event_name(self, arts: list[ArticleItem], keyword_sets: dict[int, set[str]]) -> tuple[str, list[str]]:
        """生成事件标题和关键词：LLM 优先，词频降级"""
        if EventSummaryConfig.enabled() and self.llm_client:
            llm_result = self._generate_by_llm(arts)
            if llm_result is not None:
                return llm_result

        return self._generate_by_frequency(arts, keyword_sets)

    @staticmethod
    def _sanitize(text: str) -> str:
        """清理文本中的孤立代理字符"""
        if not text:
            return ""
        return text.encode("utf-8", errors="surrogateescape").decode("utf-8", errors="replace")

    def _generate_by_llm(self, arts: list[ArticleItem]) -> tuple[str, list[str]] | None:
        """DeepSeek LLM 生成事件标题和关键词"""
        try:
            articles_text = ""
            for j, a in enumerate(arts[:15]):  # 最多15篇防止token超限
                title = self._sanitize(a.title or "无标题")
                keywords = self._sanitize(a.keywords or "")
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
