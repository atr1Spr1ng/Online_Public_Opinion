package com.bupt.publicopinion.propagation.controller;

import com.bupt.publicopinion.common.result.ApiResult;
import com.bupt.publicopinion.propagation.dto.PropagationAnalysisRequest;
import com.bupt.publicopinion.propagation.service.PropagationService;
import com.bupt.publicopinion.propagation.vo.PropagationPathVO;
import com.bupt.publicopinion.propagation.vo.SourceTraceResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/propagation")
public class PropagationController {

    private final PropagationService propagationService;

    public PropagationController(PropagationService propagationService) {
        this.propagationService = propagationService;
    }

    @GetMapping("/health")
    public ApiResult<String> health() {
        return ApiResult.success("propagation service ok");
    }

    @PostMapping("/analyze")
    public ApiResult<PropagationPathVO> analyze(@Valid @RequestBody PropagationAnalysisRequest request) {
        return ApiResult.success(propagationService.analyze(request));
    }

    @GetMapping("/{id}")
    public ApiResult<PropagationPathVO> getPath(@PathVariable Long id) {
        return ApiResult.success(propagationService.getPath(id));
    }

    @GetMapping("/event/{eventId}")
    public ApiResult<PropagationPathVO> getPathByEvent(@PathVariable Long eventId) {
        return ApiResult.success(propagationService.getPathByEvent(eventId));
    }

    @GetMapping("/source/{eventId}")
    public ApiResult<SourceTraceResult> traceSource(@PathVariable Long eventId) {
        return ApiResult.success(propagationService.traceSource(eventId));
    }
}
