import math
from datetime import datetime

import jieba
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from src.model.propagation_model import (
    PropagationArticle,
    PropagationEdge,
    PropagationNode,
    PropagationRequest,
    PropagationResponse,
)


class PropagationService:

    OFFICIAL_KEYWORDS = [
        "新华", "人民", "央视", "央广", "中新", "光明",
        "政府", "法院", "公安", "检察院", "外交部", "国防部",
        "xinhuanet", "people", "cctv", "china daily",
    ]
    SOCIAL_KEYWORDS = [
        "微博", "微信公众号", "抖音", "快手", "小红书",
        "知乎", "豆瓣", "贴吧", "B站", "bilibili",
        "头条号", "百家号", "搜狐号", "一点号",
    ]

    MIN_SIMILARITY = 0.15
    MAX_CONTENT_LEN = 2000

    def analyze(self, request: PropagationRequest) -> PropagationResponse:
        articles = sorted(request.articles, key=lambda a: a.published_at)

        # 第1层：节点分类
        nodes = [self._classify_node(a, idx) for idx, a in enumerate(articles)]

        # 第2层：传播链推理（TF-IDF 相似度）
        edges = self._build_edges(articles, nodes)

        # 第3层：关键节点标注
        self._mark_key_nodes(nodes, edges, articles[0].id if articles else 0)

        # 指标计算
        return self._build_response(nodes, edges, articles)

    def _classify_node(self, a: PropagationArticle, idx: int) -> PropagationNode:
        source = a.source_name or ""
        content = a.content or ""
        combined = source + content[:500]

        if any(kw in combined for kw in self.SOCIAL_KEYWORDS):
            node_type = "social"
        elif any(kw in combined for kw in self.OFFICIAL_KEYWORDS):
            node_type = "official"
        else:
            node_type = "commercial"

        return PropagationNode(
            id=a.id,
            title=a.title,
            source_name=source,
            published_at=a.published_at,
            node_type=node_type,
            is_source=(idx == 0),
            is_influencer=False,
            depth=idx,
        )

    def _build_edges(
        self,
        articles: list[PropagationArticle],
        nodes: list[PropagationNode],
    ) -> list[PropagationEdge]:
        if len(articles) <= 1:
            return []

        # TF-IDF 向量化
        texts = []
        for a in articles:
            content = a.content or a.title or ""
            if len(content) > self.MAX_CONTENT_LEN:
                content = content[:self.MAX_CONTENT_LEN]
            words = " ".join(jieba.cut(content))
            texts.append(words)

        vec = TfidfVectorizer(max_features=500)
        try:
            tfidf = vec.fit_transform(texts)
        except ValueError:
            return []

        edges: list[PropagationEdge] = []
        source_id = articles[0].id

        for i in range(1, len(articles)):
            # 计算与前序所有文章的相似度，找最相似的前驱
            sims = cosine_similarity(tfidf[i:i + 1], tfidf[:i])[0]
            best_j = int(sims.argmax())
            best_sim = float(sims[best_j])

            if best_sim >= self.MIN_SIMILARITY:
                edges.append(PropagationEdge(
                    source=articles[best_j].id,
                    target=articles[i].id,
                    similarity=round(best_sim, 4),
                ))
                # 更新 depth = 前驱 depth + 1
                parent_depth = next(
                    (n.depth for n in nodes if n.id == articles[best_j].id), 0
                )
                target_node = next(n for n in nodes if n.id == articles[i].id)
                target_node.depth = parent_depth + 1
            else:
                # 相似度不够，挂到源头
                edges.append(PropagationEdge(
                    source=source_id,
                    target=articles[i].id,
                    similarity=round(best_sim, 4),
                ))
                target_node = next(n for n in nodes if n.id == articles[i].id)
                target_node.depth = 1

        return edges

    def _mark_key_nodes(
        self,
        nodes: list[PropagationNode],
        edges: list[PropagationEdge],
        source_id: int,
    ):
        # 源头
        for n in nodes:
            if n.id == source_id:
                n.is_source = True
                break

        # 首个官方媒体节点
        for n in nodes:
            if n.node_type == "official" and n.id != source_id:
                n.is_influencer = True
                break

        # 被引用最多的中间节点（传播桥）
        indegree: dict[int, int] = {}
        for e in edges:
            indegree[e.target] = indegree.get(e.target, 0) + 1

        if indegree:
            max_target = max(indegree, key=indegree.get)
            if indegree[max_target] >= 2:
                for n in nodes:
                    if n.id == max_target and n.id != source_id and not n.is_influencer:
                        n.is_influencer = True
                        break

    def _build_response(
        self,
        nodes: list[PropagationNode],
        edges: list[PropagationEdge],
        articles: list[PropagationArticle],
    ) -> PropagationResponse:
        max_depth = max((n.depth for n in nodes), default=0)
        total_nodes = len(nodes)

        duration_hours = 0.0
        if len(articles) >= 2:
            try:
                t1 = self._parse_time(articles[0].published_at)
                t2 = self._parse_time(articles[-1].published_at)
                if t1 and t2:
                    duration_hours = max((t2 - t1).total_seconds() / 3600, 0.1)
            except Exception:
                pass

        spread_speed = round(total_nodes / max(duration_hours, 0.1), 4)

        return PropagationResponse(
            spread_depth=max_depth,
            total_nodes=total_nodes,
            duration_hours=round(duration_hours, 2),
            spread_speed=spread_speed,
            nodes=nodes,
            edges=edges,
            method="llm",
        )

    def _parse_time(self, s: str) -> datetime | None:
        if not s:
            return None
        for fmt in (
            "%Y-%m-%d %H:%M:%S",
            "%Y-%m-%dT%H:%M:%S",
            "%Y-%m-%d",
        ):
            try:
                return datetime.strptime(s[:19] if len(s) >= 19 else s, fmt)
            except ValueError:
                continue
        return None
