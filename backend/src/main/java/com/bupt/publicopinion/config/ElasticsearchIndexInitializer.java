package com.bupt.publicopinion.config;

import com.bupt.publicopinion.search.document.ArticleDocument;
import com.bupt.publicopinion.search.document.EventDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexInitializer.class);

    private final ElasticsearchOperations elasticsearchOperations;

    public ElasticsearchIndexInitializer(ElasticsearchOperations elasticsearchOperations) {
        this.elasticsearchOperations = elasticsearchOperations;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureIndex(ArticleDocument.class, "article_clean");
        ensureIndex(EventDocument.class, "events");
    }

    private void ensureIndex(Class<?> documentClass, String indexName) {
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(documentClass);
            if (!indexOps.exists()) {
                indexOps.create();
                indexOps.refresh();
                log.info("Created Elasticsearch index: {}", indexName);
            } else {
                log.info("Elasticsearch index already exists: {}", indexName);
            }
        } catch (Exception e) {
            log.warn("Failed to ensure Elasticsearch index '{}' exists: {}", indexName, e.getMessage());
            log.info("Index '{}' may need to be created manually via: PUT /{}", indexName, indexName);
        }
    }
}
