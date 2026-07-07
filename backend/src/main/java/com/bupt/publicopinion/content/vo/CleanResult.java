package com.bupt.publicopinion.content.vo;

public record CleanResult(
        String title,
        String content,
        String keywords,
        String summary,
        String language,
        String status,
        String error
) {

    public static CleanResult failed(Long rawId, String error) {
        return new CleanResult(null, null, null, null, null, "FAILED", error);
    }
}
