package com.bupt.publicopinion.event.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record EventClusterRequest(
        @DecimalMin("0.01") @DecimalMax("0.99")
        double threshold
) {
}
