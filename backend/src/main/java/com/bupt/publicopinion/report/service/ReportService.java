package com.bupt.publicopinion.report.service;

import com.bupt.publicopinion.report.dto.ReportGenerateRequest;
import com.bupt.publicopinion.report.dto.QaRequest;
import com.bupt.publicopinion.report.vo.QaResultVO;
import com.bupt.publicopinion.report.vo.ReportVO;

import java.util.List;

public interface ReportService {

    ReportVO generateReport(ReportGenerateRequest request);

    ReportVO getReport(Long id);

    List<ReportVO> listReports(long pageNum, long pageSize);

    QaResultVO askQuestion(QaRequest request);
}
