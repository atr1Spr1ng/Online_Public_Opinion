package com.bupt.publicopinion.event.service;

import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.event.vo.EventDetailVO;
import com.bupt.publicopinion.event.vo.EventVO;
import com.bupt.publicopinion.event.vo.SimilarEventResult;

import java.util.List;
import java.util.Map;

public interface EventService {

    Map<String, Object> clusterAndSave(double threshold);

    PageResult<EventVO> listEvents(long pageNum, long pageSize, String category, String sortBy, String sortOrder);

    EventDetailVO getEvent(Long id);

    PageResult<EventVO> searchEvents(String keyword, long pageNum, long pageSize);

    List<SimilarEventResult> findSimilarEvents(String keywords, int topK);

    Map<String, Object> forecastTrend(Long eventId, int periods);

    Map<String, Object> fullReport(Long eventId);

    List<EventVO> getMyFeedEvents(Long userId);

    void deleteEvent(Long id);
}
