package com.bupt.publicopinion.event.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record EventClusterRequest(
        @DecimalMin("0.01") @DecimalMax("0.99")
        double threshold,
        Integer days,
        @Min(2) @Max(20)
        Integer minClusterSize
) {
}
