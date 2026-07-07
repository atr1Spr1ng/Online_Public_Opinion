package com.bupt.publicopinion.propagation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("propagation_node")
public class PropagationNode {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pathId;
    private Long cleanId;
    private String sourceName;
    private String publishedAt;
    private Integer depth;
    private Long parentNodeId;
    private Integer isSource;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPathId() { return pathId; }
    public void setPathId(Long pathId) { this.pathId = pathId; }

    public Long getCleanId() { return cleanId; }
    public void setCleanId(Long cleanId) { this.cleanId = cleanId; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public String getPublishedAt() { return publishedAt; }
    public void setPublishedAt(String publishedAt) { this.publishedAt = publishedAt; }

    public Integer getDepth() { return depth; }
    public void setDepth(Integer depth) { this.depth = depth; }

    public Long getParentNodeId() { return parentNodeId; }
    public void setParentNodeId(Long parentNodeId) { this.parentNodeId = parentNodeId; }

    public Integer getIsSource() { return isSource; }
    public void setIsSource(Integer isSource) { this.isSource = isSource; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
