package com.bupt.publicopinion.fake.service;

import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.fake.dto.FakeDetectionRequest;
import com.bupt.publicopinion.fake.vo.FakeDetectionResult;

import java.util.List;

public interface FakeDetectionService {

    FakeDetectionResult detect(FakeDetectionRequest request);

    List<FakeDetectionResult> batchDetect(List<Long> cleanIds);

    FakeDetectionResult getResult(Long id);

    PageResult<FakeDetectionResult> listResults(long pageNum, long pageSize, Boolean isFake);

    void deleteFakeResult(Long id);
}
