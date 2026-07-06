package com.bupt.publicopinion.common.vo;

import java.time.OffsetDateTime;

public record ErrorResponse(
        int status,
        String message,
        OffsetDateTime timestamp
) {
}
