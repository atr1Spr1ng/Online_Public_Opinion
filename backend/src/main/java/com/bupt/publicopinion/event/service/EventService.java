package com.bupt.publicopinion.event.service;

import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.event.vo.EventDetailVO;
import com.bupt.publicopinion.event.vo.EventVO;
import com.bupt.publicopinion.event.vo.SimilarEventResult;

import java.util.List;
import java.util.Map;

public interface EventService {

    Map<String, Object> clusterAndSave(double threshold);

    PageResult<EventVO> listEvents(long pageNum, long pageSize);

    EventDetailVO getEvent(Long id);

    PageResult<EventVO> searchEvents(String keyword, long pageNum, long pageSize);

    List<SimilarEventResult> findSimilarEvents(String keywords, int topK);
}
