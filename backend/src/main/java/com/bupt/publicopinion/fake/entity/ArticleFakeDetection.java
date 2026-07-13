package com.bupt.publicopinion.fake.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("article_fake_detection")
public class ArticleFakeDetection {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long cleanId;
    private BigDecimal fakeScore;
    private Integer isFake;
    private String detectionMethod;
    private String featuresJson;
    private String details;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getCleanId() { return cleanId; }
    public void setCleanId(Long cleanId) { this.cleanId = cleanId; }

    public BigDecimal getFakeScore() { return fakeScore; }
    public void setFakeScore(BigDecimal fakeScore) { this.fakeScore = fakeScore; }

    public Integer getIsFake() { return isFake; }
    public void setIsFake(Integer isFake) { this.isFake = isFake; }

    public String getDetectionMethod() { return detectionMethod; }
    public void setDetectionMethod(String detectionMethod) { this.detectionMethod = detectionMethod; }

    public String getFeaturesJson() { return featuresJson; }
    public void setFeaturesJson(String featuresJson) { this.featuresJson = featuresJson; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
