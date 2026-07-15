package com.bupt.publicopinion.propagation.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
        List<Map<String, Object>> edges,
        List<Map<String, Object>> phases,
        String method,
        LocalDateTime createTime
) {
}
