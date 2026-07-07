package com.bupt.publicopinion.analysis.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SentimentResult(
        Long id,
        Long cleanId,
        String sentiment,
        BigDecimal positiveScore,
        BigDecimal negativeScore,
        BigDecimal confidence,
        String detailsJson,
        LocalDateTime createTime
) {

    public static SentimentResult failed(Long cleanId, String error) {
        return new SentimentResult(null, cleanId, "FAILED", null, null, null, error, null);
    }
}
