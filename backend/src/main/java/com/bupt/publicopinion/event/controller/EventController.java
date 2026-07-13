package com.bupt.publicopinion.event.controller;

import com.bupt.publicopinion.common.context.UserContext;
import com.bupt.publicopinion.common.result.ApiResult;
import com.bupt.publicopinion.event.dto.EventClusterRequest;
import com.bupt.publicopinion.event.dto.SimilarEventRequest;
import com.bupt.publicopinion.event.service.EventService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping("/cluster")
    public ApiResult<Map<String, Object>> cluster(@RequestBody @Valid EventClusterRequest request) {
        return ApiResult.success(eventService.clusterAndSave(request.threshold()));
    }

    @GetMapping
    public ApiResult<?> list(@RequestParam(defaultValue = "1") long pageNum,
                              @RequestParam(defaultValue = "10") long pageSize,
                              @RequestParam(required = false) String category) {
        return ApiResult.success(eventService.listEvents(pageNum, pageSize, category));
    }

    @GetMapping("/search")
    public ApiResult<?> search(@RequestParam(defaultValue = "") String keyword,
                                @RequestParam(defaultValue = "1") long pageNum,
                                @RequestParam(defaultValue = "10") long pageSize) {
        return ApiResult.success(eventService.searchEvents(keyword, pageNum, pageSize));
    }

    @PostMapping("/similar")
    public ApiResult<?> findSimilar(@RequestBody @Valid SimilarEventRequest request) {
        return ApiResult.success(eventService.findSimilarEvents(request.keywords(), request.topK()));
    }

    @GetMapping("/{id}")
    public ApiResult<?> detail(@PathVariable Long id) {
        return ApiResult.success(eventService.getEvent(id));
    }

    @GetMapping("/{id}/trend")
    public ApiResult<?> trend(@PathVariable Long id,
                               @RequestParam(defaultValue = "7") int periods) {
        return ApiResult.success(eventService.forecastTrend(id, periods));
    }

    @GetMapping("/{id}/full-report")
    public ApiResult<?> fullReport(@PathVariable Long id) {
        return ApiResult.success(eventService.fullReport(id));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteEvent(@PathVariable Long id) {
        eventService.deleteEvent(id);
        return ApiResult.success();
    }

    @GetMapping("/my-feed")
    public ApiResult<?> myFeed() {
        UserContext.UserContextInfo user = UserContext.get();
        if (user == null || user.userId() == null) {
            return ApiResult.success(List.of());
        }
        return ApiResult.success(eventService.getMyFeedEvents(user.userId()));
    }
}
