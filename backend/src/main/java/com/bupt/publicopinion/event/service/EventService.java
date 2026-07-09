package com.bupt.publicopinion.event.service;

import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.event.vo.EventDetailVO;
import com.bupt.publicopinion.event.vo.EventVO;

import java.util.Map;

public interface EventService {

    Map<String, Object> clusterAndSave(double threshold);

    PageResult<EventVO> listEvents(long pageNum, long pageSize, String category);

    EventDetailVO getEvent(Long id);
}
