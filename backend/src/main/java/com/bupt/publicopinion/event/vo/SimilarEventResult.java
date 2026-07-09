package com.bupt.publicopinion.event.vo;

public class SimilarEventResult {

    private Long eventId;
    private String title;
    private String keywords;
    private Integer articleCount;
    private Float hotness;
    private String lifecycle;
    private String category;
    private Double similarity;

    public SimilarEventResult() {}

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }

    public Integer getArticleCount() { return articleCount; }
    public void setArticleCount(Integer articleCount) { this.articleCount = articleCount; }

    public Float getHotness() { return hotness; }
    public void setHotness(Float hotness) { this.hotness = hotness; }

    public String getLifecycle() { return lifecycle; }
    public void setLifecycle(String lifecycle) { this.lifecycle = lifecycle; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Double getSimilarity() { return similarity; }
    public void setSimilarity(Double similarity) { this.similarity = similarity; }

    public static SimilarEventResult from(com.bupt.publicopinion.search.document.EventDocument doc, double score) {
        SimilarEventResult r = new SimilarEventResult();
        r.setEventId(doc.getId());
        r.setTitle(doc.getTitle());
        r.setKeywords(doc.getKeywords());
        r.setArticleCount(doc.getArticleCount());
        r.setHotness(doc.getHotness());
        r.setLifecycle(doc.getLifecycle());
        r.setCategory(doc.getCategory());
        r.setSimilarity(score);
        return r;
    }
}
