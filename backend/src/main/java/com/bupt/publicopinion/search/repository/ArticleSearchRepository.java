package com.bupt.publicopinion.search.repository;

import com.bupt.publicopinion.search.document.ArticleDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ArticleSearchRepository extends ElasticsearchRepository<ArticleDocument, Long> {
}
