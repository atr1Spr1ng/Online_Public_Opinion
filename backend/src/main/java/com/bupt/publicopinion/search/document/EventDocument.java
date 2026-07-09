package com.bupt.publicopinion.search.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

@Document(indexName = "events", createIndex = false)
public class EventDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String title;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String keywords;

    @Field(type = FieldType.Integer)
    private Integer articleCount;

    @Field(type = FieldType.Float)
    private Float hotness;

    @Field(type = FieldType.Keyword)
    private String lifecycle;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Date)
    private LocalDateTime startTime;

    @Field(type = FieldType.Date)
    private LocalDateTime endTime;

    @Field(type = FieldType.Date)
    private LocalDateTime createTime;

    public EventDocument() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
