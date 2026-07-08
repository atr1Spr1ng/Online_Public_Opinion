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
        LocalDateTime createTime
) {
}
