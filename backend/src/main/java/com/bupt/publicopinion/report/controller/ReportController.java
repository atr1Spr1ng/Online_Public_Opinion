package com.bupt.publicopinion.report.controller;

import com.bupt.publicopinion.common.result.ApiResult;
import com.bupt.publicopinion.report.dto.QaRequest;
import com.bupt.publicopinion.report.dto.ReportGenerateRequest;
import com.bupt.publicopinion.report.service.ReportService;
import com.bupt.publicopinion.report.vo.QaResultVO;
import com.bupt.publicopinion.report.vo.ReportVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/report")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/health")
    public ApiResult<String> health() {
        return ApiResult.success("report service ok");
    }

    @PostMapping("/generate")
    public ApiResult<ReportVO> generateReport(@Valid @RequestBody ReportGenerateRequest request) {
        return ApiResult.success(reportService.generateReport(request));
    }

    @GetMapping("/{id}")
    public ApiResult<ReportVO> getReport(@PathVariable Long id) {
        return ApiResult.success(reportService.getReport(id));
    }

    @GetMapping
    public ApiResult<List<ReportVO>> listReports(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize
    ) {
        return ApiResult.success(reportService.listReports(pageNum, pageSize));
    }

    @PostMapping("/qa")
    public ApiResult<QaResultVO> askQuestion(@Valid @RequestBody QaRequest request) {
        return ApiResult.success(reportService.askQuestion(request));
    }
}
