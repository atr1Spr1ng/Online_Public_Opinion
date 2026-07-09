package com.bupt.publicopinion.config;

import com.bupt.publicopinion.content.entity.ArticleClean;
import com.bupt.publicopinion.event.entity.Event;
import com.bupt.publicopinion.search.service.SearchSyncService;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class})
})
public class EsSyncInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(EsSyncInterceptor.class);

    private final SearchSyncService searchSyncService;

    public EsSyncInterceptor(SearchSyncService searchSyncService) {
        this.searchSyncService = searchSyncService;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object result = invocation.proceed();
        try {
            MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
            SqlCommandType commandType = ms.getSqlCommandType();
            Object parameter = invocation.getArgs()[1];

            if (parameter instanceof ArticleClean article) {
                syncArticle(commandType, article, result);
            } else if (parameter instanceof Event event) {
                syncEvent(commandType, event, result);
            }
        } catch (Exception e) {
            log.warn("ES sync failed: {}", e.getMessage());
        }
        return result;
    }

    private void syncArticle(SqlCommandType type, ArticleClean article, Object result) {
        switch (type) {
            case INSERT, UPDATE -> searchSyncService.indexArticle(article);
            case DELETE -> searchSyncService.deleteArticle(article.getId());
        }
    }

    private void syncEvent(SqlCommandType type, Event event, Object result) {
        switch (type) {
            case INSERT, UPDATE -> searchSyncService.indexEvent(event);
            case DELETE -> {
                // Individual event deletion uses delete-by-id in ES
            }
        }
    }
}
