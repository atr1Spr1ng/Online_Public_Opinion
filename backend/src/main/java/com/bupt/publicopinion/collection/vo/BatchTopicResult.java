package com.bupt.publicopinion.collection.vo;

import com.bupt.publicopinion.collection.entity.ArticleRaw;

import java.util.ArrayList;
import java.util.List;

public class BatchTopicResult {
    private String keyword;
    private int totalSources;
    private int totalSuccess;
    private int totalFailed;
    private final List<SourceTopicResult> sourceResults = new ArrayList<>();
    private final List<ArticleRaw> savedArticles = new ArrayList<>();

    public static BatchTopicResult empty(String keyword, int totalSources) {
        BatchTopicResult r = new BatchTopicResult();
        r.keyword = keyword;
        r.totalSources = totalSources;
        return r;
    }

    public void addResult(String sourceName, int found, int success, int failed) {
        sourceResults.add(new SourceTopicResult(sourceName, found, success, failed));
        totalSuccess += success;
        totalFailed += failed;
    }

    public void addArticle(ArticleRaw article) {
        savedArticles.add(article);
    }

    // getters / setters
    public String getKeyword() { return keyword; }
    public int getTotalSources() { return totalSources; }
    public int getTotalSuccess() { return totalSuccess; }
    public int getTotalFailed() { return totalFailed; }
    public List<SourceTopicResult> getSourceResults() { return sourceResults; }
    public List<ArticleRaw> getSavedArticles() { return savedArticles; }

    public record SourceTopicResult(String sourceName, int found, int success, int failed) {}
}
