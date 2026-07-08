package com.bupt.publicopinion.fake.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FakeDetectionResult(
        Long id,
        Long cleanId,
        BigDecimal fakeScore,
        Boolean isFake,
        String detectionMethod,
        String featuresJson,
        String details,
        LocalDateTime createTime
) {

    public static FakeDetectionResult failed(Long cleanId, String error) {
        return new FakeDetectionResult(null, cleanId, null, null, null, null, error, null);
    }
}
