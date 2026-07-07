package com.bupt.publicopinion.propagation.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record PropagationPathVO(
        Long id,
        Long eventId,
        Long sourceArticleId,
        String sourceName,
        String sourceTitle,
        Integer spreadDepth,
        Integer totalNodes,
        BigDecimal durationHours,
        BigDecimal spreadSpeed,
        String pathJson,
        List<PropagationNodeVO> nodes,
        LocalDateTime createTime
) {
}
