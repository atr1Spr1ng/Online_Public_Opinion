package com.bupt.publicopinion.fake.controller;

import com.bupt.publicopinion.common.result.ApiResult;
import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.fake.dto.FakeDetectionRequest;
import com.bupt.publicopinion.fake.service.FakeDetectionService;
import com.bupt.publicopinion.fake.vo.FakeDetectionResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/fake")
public class FakeDetectionController {

    private final FakeDetectionService fakeDetectionService;

    public FakeDetectionController(FakeDetectionService fakeDetectionService) {
        this.fakeDetectionService = fakeDetectionService;
    }

    @GetMapping("/health")
    public ApiResult<String> health() {
        return ApiResult.success("fake detection service ok");
    }

    @PostMapping("/detect")
    public ApiResult<FakeDetectionResult> detect(@Valid @RequestBody FakeDetectionRequest request) {
        return ApiResult.success(fakeDetectionService.detect(request));
    }

    @PostMapping("/batch-detect")
    public ApiResult<List<FakeDetectionResult>> batchDetect(@RequestBody List<Long> cleanIds) {
        return ApiResult.success(fakeDetectionService.batchDetect(cleanIds));
    }

    @GetMapping("/{id}")
    public ApiResult<FakeDetectionResult> getResult(@PathVariable Long id) {
        return ApiResult.success(fakeDetectionService.getResult(id));
    }

    @GetMapping
    public ApiResult<PageResult<FakeDetectionResult>> listResults(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) Boolean isFake
    ) {
        return ApiResult.success(fakeDetectionService.listResults(pageNum, pageSize, isFake));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteFakeResult(@PathVariable Long id) {
        fakeDetectionService.deleteFakeResult(id);
        return ApiResult.success();
    }
}
