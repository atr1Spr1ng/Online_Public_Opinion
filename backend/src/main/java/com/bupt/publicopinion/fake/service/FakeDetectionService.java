package com.bupt.publicopinion.fake.service;

import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.fake.dto.FakeDetectionRequest;
import com.bupt.publicopinion.fake.vo.FakeDetectionResult;
import com.bupt.publicopinion.task.entity.ProcessingTask;

import java.util.List;

public interface FakeDetectionService {

    FakeDetectionResult detect(FakeDetectionRequest request);

    ProcessingTask batchDetect(List<Long> cleanIds);

    ProcessingTask batchDetect(List<Long> cleanIds, String mode);

    FakeDetectionResult getResult(Long id);

    PageResult<FakeDetectionResult> listResults(long pageNum, long pageSize, Boolean isFake);

    void deleteFakeResult(Long id);
}
