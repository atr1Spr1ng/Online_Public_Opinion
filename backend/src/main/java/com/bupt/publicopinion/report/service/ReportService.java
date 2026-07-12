package com.bupt.publicopinion.report.service;

import com.bupt.publicopinion.common.vo.PageResult;
import com.bupt.publicopinion.report.dto.ReportGenerateRequest;
import com.bupt.publicopinion.report.dto.QaRequest;
import com.bupt.publicopinion.report.vo.QaResultVO;
import com.bupt.publicopinion.report.vo.ReportVO;

public interface ReportService {

    ReportVO generateReport(ReportGenerateRequest request);

    ReportVO getReport(Long id);

    PageResult<ReportVO> listReports(long pageNum, long pageSize);

    QaResultVO askQuestion(QaRequest request);

    void deleteReport(Long id);
}
