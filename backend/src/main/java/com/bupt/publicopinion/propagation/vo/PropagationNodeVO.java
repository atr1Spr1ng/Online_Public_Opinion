package com.bupt.publicopinion.propagation.vo;

import java.time.LocalDateTime;

public record PropagationNodeVO(
        Long id,
        Long cleanId,
        String articleTitle,
        String sourceName,
        String publishedAt,
        Integer depth,
        Long parentNodeId,
        Boolean isSource,
        Boolean isInfluencer,
        String nodeType,
        Boolean isHistorical,
        String phase,
        Integer phaseIndex,
        String role,
        String reason,
        Boolean representative,
        LocalDateTime createTime
) {
}
