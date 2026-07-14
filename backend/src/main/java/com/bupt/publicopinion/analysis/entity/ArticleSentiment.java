package com.bupt.publicopinion.analysis.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("article_sentiment")
public class ArticleSentiment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long cleanId;
    private String sentiment;
    private BigDecimal positiveScore;
    private BigDecimal negativeScore;
    private BigDecimal confidence;
    private String detailsJson;
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String title;
    @TableField(exist = false)
    private String originalUrl;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getCleanId() { return cleanId; }
    public void setCleanId(Long cleanId) { this.cleanId = cleanId; }

    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }

    public BigDecimal getPositiveScore() { return positiveScore; }
    public void setPositiveScore(BigDecimal positiveScore) { this.positiveScore = positiveScore; }

    public BigDecimal getNegativeScore() { return negativeScore; }
    public void setNegativeScore(BigDecimal negativeScore) { this.negativeScore = negativeScore; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getOriginalUrl() { return originalUrl; }
    public void setOriginalUrl(String originalUrl) { this.originalUrl = originalUrl; }
}
