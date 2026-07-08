package com.bupt.publicopinion.search.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Like;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.bupt.publicopinion.content.entity.ArticleClean;
import com.bupt.publicopinion.search.document.ArticleDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
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
     * 按关键词搜索文章（ES multi_match + IK 分词）
     */
    public Page<ArticleDocument> searchByKeyword(String keyword, int pageNum, int pageSize) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                        .multiMatch(mm -> mm
                                .fields("title", "content", "keywords")
                                .query(keyword)
                                .type(TextQueryType.BestFields)
                        )
                )
                .withPageable(PageRequest.of(pageNum - 1, pageSize))
                .build();

        SearchHits<ArticleDocument> hits = elasticsearchOperations.search(query, ArticleDocument.class);
        List<ArticleDocument> results = hits.getSearchHits().stream()
                .map(h -> {
                    ArticleDocument doc = h.getContent();
                    return doc;
                })
                .toList();
        return new org.springframework.data.domain.PageImpl<>(
                results,
                PageRequest.of(pageNum - 1, pageSize),
                hits.getTotalHits()
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
                                    .fields("title", "content", "keywords")
                                    .query(keyword)
                                    .type(TextQueryType.BestFields)
                            ));
                            if (sourceName != null && !sourceName.isBlank()) {
                                b.filter(f -> f.term(t -> t.field("sourceName").value(sourceName)));
                            }
                            return b;
                        })
                )
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
        // 删除旧索引
        elasticsearchOperations.indexOps(ArticleDocument.class).delete();
        // Spring Data ES 会在保存时自动创建索引
        List<ArticleDocument> docs = allArticles.stream().map(this::toDocument).toList();
        if (!docs.isEmpty()) {
            elasticsearchOperations.save(docs);
        }
    }

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
