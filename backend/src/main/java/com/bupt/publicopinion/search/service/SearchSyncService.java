package com.bupt.publicopinion.search.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Like;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.bupt.publicopinion.content.entity.ArticleClean;
import com.bupt.publicopinion.event.entity.Event;
import com.bupt.publicopinion.search.document.ArticleDocument;
import com.bupt.publicopinion.search.document.EventDocument;
import com.bupt.publicopinion.search.document.EventSimilarHit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SearchSyncService {

    private final ElasticsearchOperations elasticsearchOperations;

    public SearchSyncService(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    /**
     * 将清洗后的文章同步到 ES
     */
    public void indexArticle(ArticleClean article) {
        ArticleDocument doc = toDocument(article);
        elasticsearchOperations.save(doc);
    }

    /**
     * 批量同步
     */
    public void indexArticles(List<ArticleClean> articles) {
        List<ArticleDocument> docs = articles.stream().map(this::toDocument).toList();
        elasticsearchOperations.save(docs);
    }

    /**
     * 从 ES 删除
     */
    public void deleteArticle(Long id) {
        elasticsearchOperations.delete(String.valueOf(id), ArticleDocument.class);
    }

    /**
     * 分页获取全部文章
     */
    public Page<ArticleDocument> findAll(int pageNum, int pageSize) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.matchAll(m -> m))
                .withPageable(PageRequest.of(pageNum - 1, pageSize))
                .build();

        SearchHits<ArticleDocument> hits = elasticsearchOperations.search(query, ArticleDocument.class);
        List<ArticleDocument> results = hits.getSearchHits().stream()
                .map(h -> h.getContent())
                .toList();
        return new org.springframework.data.domain.PageImpl<>(
                results, PageRequest.of(pageNum - 1, pageSize), hits.getTotalHits()
        );
    }

    /**
     * 按关键词搜索文章（ES multi_match + IK 分词 + 字段加权 + 最低相关度）
     */
    public Page<ArticleDocument> searchByKeyword(String keyword, int pageNum, int pageSize) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                        .multiMatch(mm -> mm
                                .fields("title^3", "keywords^2", "content")
                                .query(keyword)
                                .type(TextQueryType.BestFields)
                        )
                )
                .withPageable(PageRequest.of(pageNum - 1, pageSize))
                .build();

        SearchHits<ArticleDocument> hits = elasticsearchOperations.search(query, ArticleDocument.class);

        // 动态阈值：只保留分数 >= 最高分 30% 的结果，过滤仅顺带提及的噪音文章
        float maxScore = hits.getMaxScore();
        float minScore = maxScore * 0.5f;

        List<ArticleDocument> results = hits.getSearchHits().stream()
                .filter(h -> h.getScore() >= minScore)
                .map(h -> h.getContent())
                .toList();
        return new org.springframework.data.domain.PageImpl<>(
                results,
                PageRequest.of(pageNum - 1, pageSize),
                results.size()
        );
    }

    /**
     * 按多个关键词过滤（任意命中）
     */
    public Page<ArticleDocument> searchByKeywords(List<String> keywords, int pageNum, int pageSize) {
        String queryStr = String.join(" ", keywords);
        return searchByKeyword(queryStr, pageNum, pageSize);
    }

    /**
     * 按关键词 + 来源过滤
     */
    public Page<ArticleDocument> searchByKeywordAndSource(String keyword, String sourceName,
                                                           int pageNum, int pageSize) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> {
                            b.must(m -> m.multiMatch(mm -> mm
                                    .fields("title^3", "keywords^2", "content")
                                    .query(keyword)
                                    .type(TextQueryType.BestFields)
                            ));
                            if (sourceName != null && !sourceName.isBlank()) {
                                b.filter(f -> f.term(t -> t.field("sourceName.keyword").value(sourceName)));
                            }
                            return b;
                        })
                )
                .withPageable(PageRequest.of(pageNum - 1, pageSize))
                                .build();

        SearchHits<ArticleDocument> hits = elasticsearchOperations.search(query, ArticleDocument.class);

        float maxScore = hits.getMaxScore();
        float minScore = maxScore * 0.5f;

        List<ArticleDocument> results = hits.getSearchHits().stream()
                .filter(h -> h.getScore() >= minScore)
                .map(h -> h.getContent())
                .toList();
        return new org.springframework.data.domain.PageImpl<>(
                results, PageRequest.of(pageNum - 1, pageSize), results.size()
        );
    }

    /**
     * 用 ES more_like_this 查找相似文章（内容去重）
     */
    public List<ArticleDocument> findSimilar(String title, String content, int maxResults) {
        String text = (title != null ? title : "") + " " + (content != null ? content : "");
        if (text.isBlank()) return List.of();

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                        .moreLikeThis(mlt -> mlt
                                .fields("title", "content")
                                .like(List.of(Like.of(l -> l.text(text))))
                                .minTermFreq(1)
                                .minDocFreq(1)
                                .maxQueryTerms(12)
                        )
                )
                .withMaxResults(maxResults)
                .build();

        SearchHits<ArticleDocument> hits = elasticsearchOperations.search(query, ArticleDocument.class);
        return hits.getSearchHits().stream()
                .map(h -> h.getContent())
                .toList();
    }

    /**
     * 清空并全量重建索引（从 MySQL 同步）
     */
    public void rebuildIndex(List<ArticleClean> allArticles) {
        elasticsearchOperations.indexOps(ArticleDocument.class).delete();
        elasticsearchOperations.indexOps(ArticleDocument.class).create();
        elasticsearchOperations.indexOps(ArticleDocument.class).refresh();
        List<ArticleDocument> docs = allArticles.stream().map(this::toDocument).toList();
        if (!docs.isEmpty()) {
            elasticsearchOperations.save(docs);
        }
    }

    public void syncAllArticles(List<ArticleClean> allArticles) {
        List<ArticleDocument> docs = allArticles.stream().map(this::toDocument).toList();
        if (!docs.isEmpty()) {
            elasticsearchOperations.save(docs);
        }
    }

    // ==================== 事件索引方法 ====================

    /**
     * 将单个事件同步到 ES
     */
    public void indexEvent(Event event) {
        EventDocument doc = toEventDocument(event);
        elasticsearchOperations.save(doc);
    }

    /**
     * 批量同步事件到 ES
     */
    public void indexEvents(List<Event> events) {
        List<EventDocument> docs = events.stream().map(this::toEventDocument).toList();
        elasticsearchOperations.save(docs);
    }

    /**
     * 按关键词检索历史事件（ES multi_match）
     */
    public Page<EventDocument> searchEvents(String keyword, int pageNum, int pageSize) {
        if (keyword == null || keyword.isBlank()) {
            NativeQuery query = NativeQuery.builder()
                    .withQuery(q -> q.matchAll(m -> m))
                    .withPageable(PageRequest.of(pageNum - 1, pageSize))
                    .build();
            SearchHits<EventDocument> hits = elasticsearchOperations.search(query, EventDocument.class);
            List<EventDocument> results = hits.getSearchHits().stream()
                    .map(h -> h.getContent())
                    .toList();
            return new org.springframework.data.domain.PageImpl<>(
                    results, PageRequest.of(pageNum - 1, pageSize), hits.getTotalHits()
            );
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                        .multiMatch(mm -> mm
                                .fields("title^3", "keywords^2")
                                .query(keyword)
                                .type(TextQueryType.BestFields)
                        )
                )
                .withPageable(PageRequest.of(pageNum - 1, pageSize))
                .build();

        SearchHits<EventDocument> hits = elasticsearchOperations.search(query, EventDocument.class);

        float maxScore = hits.getMaxScore();
        float minScore = maxScore * 0.3f;

        List<EventDocument> results = hits.getSearchHits().stream()
                .filter(h -> h.getScore() >= minScore)
                .map(h -> h.getContent())
                .toList();
        return new org.springframework.data.domain.PageImpl<>(
                results, PageRequest.of(pageNum - 1, pageSize), results.size()
        );
    }

    /**
     * 用 ES more_like_this 查找相似事件，返回文档 + 相似度分数
     */
    public List<EventSimilarHit> findSimilarEvents(String keywords, int topK) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                        .moreLikeThis(mlt -> mlt
                                .fields("title", "keywords")
                                .like(List.of(Like.of(l -> l.text(keywords))))
                                .minTermFreq(1)
                                .minDocFreq(1)
                                .maxQueryTerms(12)
                        )
                )
                .withMaxResults(topK)
                .build();

        SearchHits<EventDocument> hits = elasticsearchOperations.search(query, EventDocument.class);

        // more_like_this 的 _score 不是 0-1 范围，归一化到 0-1
        float maxScore = hits.getMaxScore();
        return hits.getSearchHits().stream()
                .map(h -> new EventSimilarHit(h.getContent(),
                        maxScore > 0 ? (double) (h.getScore() / maxScore) : 0.0))
                .toList();
    }

    /**
     * 删除所有事件文档（聚类重建前调用，避免 ES 孤儿文档）
     */
    public void deleteAllEvents() {
        elasticsearchOperations.delete(
                DeleteQuery.builder(
                        NativeQuery.builder()
                                .withQuery(q -> q.matchAll(m -> m))
                                .build()
                ).build(),
                EventDocument.class
        );
    }

    private EventDocument toEventDocument(Event event) {
        EventDocument doc = new EventDocument();
        doc.setId(event.getId());
        doc.setTitle(event.getTitle());
        doc.setKeywords(event.getKeywords());
        doc.setArticleCount(event.getArticleCount());
        doc.setHotness(event.getHotness() != null ? event.getHotness().floatValue() : null);
        doc.setLifecycle(event.getLifecycle());
        doc.setCategory(event.getCategory());
        doc.setStartTime(event.getStartTime());
        doc.setEndTime(event.getEndTime());
        doc.setCreateTime(event.getCreateTime());
        return doc;
    }

    // ==================== 文章索引方法 ====================

    private ArticleDocument toDocument(ArticleClean article) {
        ArticleDocument doc = new ArticleDocument();
        doc.setId(article.getId());
        doc.setRawId(article.getRawId());
        doc.setTitle(article.getTitle());
        doc.setContent(article.getContent());
        doc.setKeywords(article.getKeywords());
        doc.setSummary(article.getSummary());
        doc.setSourceName(article.getSourceName());
        doc.setPublishedAt(article.getPublishedAt());
        doc.setLanguage(article.getLanguage());
        doc.setStatus(article.getStatus());
        doc.setCreateTime(article.getCreateTime());
        return doc;
    }
}
