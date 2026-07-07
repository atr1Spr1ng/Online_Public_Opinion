package com.bupt.publicopinion.propagation.service;

import com.bupt.publicopinion.propagation.dto.PropagationAnalysisRequest;
import com.bupt.publicopinion.propagation.vo.PropagationPathVO;
import com.bupt.publicopinion.propagation.vo.SourceTraceResult;

import java.util.List;

public interface PropagationService {

    PropagationPathVO analyze(PropagationAnalysisRequest request);

    PropagationPathVO getPath(Long id);

    PropagationPathVO getPathByEvent(Long eventId);

    SourceTraceResult traceSource(Long eventId);
}
