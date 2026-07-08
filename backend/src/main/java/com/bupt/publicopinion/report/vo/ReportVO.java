package com.bupt.publicopinion.report.vo;

import java.time.LocalDateTime;

public record ReportVO(
        Long id,
        Long eventId,
        String title,
        String contentJson,
        LocalDateTime createTime
) {
}
