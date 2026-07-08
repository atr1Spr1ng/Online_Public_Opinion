package com.bupt.publicopinion.propagation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("propagation_path")
public class PropagationPath {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long eventId;
    private Long sourceArticleId;
    private String sourceName;
    private Integer spreadDepth;
    private Integer totalNodes;
    private BigDecimal durationHours;
    private BigDecimal spreadSpeed;
    private String pathJson;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public Long getSourceArticleId() { return sourceArticleId; }
    public void setSourceArticleId(Long sourceArticleId) { this.sourceArticleId = sourceArticleId; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public Integer getSpreadDepth() { return spreadDepth; }
    public void setSpreadDepth(Integer spreadDepth) { this.spreadDepth = spreadDepth; }

    public Integer getTotalNodes() { return totalNodes; }
    public void setTotalNodes(Integer totalNodes) { this.totalNodes = totalNodes; }

    public BigDecimal getDurationHours() { return durationHours; }
    public void setDurationHours(BigDecimal durationHours) { this.durationHours = durationHours; }

    public BigDecimal getSpreadSpeed() { return spreadSpeed; }
    public void setSpreadSpeed(BigDecimal spreadSpeed) { this.spreadSpeed = spreadSpeed; }

    public String getPathJson() { return pathJson; }
    public void setPathJson(String pathJson) { this.pathJson = pathJson; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
