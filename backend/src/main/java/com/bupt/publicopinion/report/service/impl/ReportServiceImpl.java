package com.bupt.publicopinion.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bupt.publicopinion.analysis.entity.ArticleSentiment;
import com.bupt.publicopinion.analysis.mapper.ArticleSentimentMapper;
import com.bupt.publicopinion.content.entity.ArticleClean;
import com.bupt.publicopinion.content.mapper.ArticleCleanMapper;
import com.bupt.publicopinion.event.entity.Event;
import com.bupt.publicopinion.event.entity.EventArticle;
import com.bupt.publicopinion.event.mapper.EventArticleMapper;
import com.bupt.publicopinion.event.mapper.EventMapper;
import com.bupt.publicopinion.report.client.PythonReportClient;
import com.bupt.publicopinion.report.dto.QaRequest;
import com.bupt.publicopinion.report.dto.ReportGenerateRequest;
import com.bupt.publicopinion.report.entity.Report;
import com.bupt.publicopinion.report.exception.ReportServiceException;
import com.bupt.publicopinion.report.mapper.ReportMapper;
import com.bupt.publicopinion.report.service.ReportService;
import com.bupt.publicopinion.report.vo.QaResultVO;
import com.bupt.publicopinion.report.vo.ReportVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final EventMapper eventMapper;
    private final EventArticleMapper eventArticleMapper;
    private final ArticleCleanMapper articleCleanMapper;
    private final ArticleSentimentMapper articleSentimentMapper;
    private final PythonReportClient pythonReportClient;

    public ReportServiceImpl(
            ReportMapper reportMapper,
            EventMapper eventMapper,
            EventArticleMapper eventArticleMapper,
            ArticleCleanMapper articleCleanMapper,
            ArticleSentimentMapper articleSentimentMapper,
            PythonReportClient pythonReportClient
    ) {
        this.reportMapper = reportMapper;
        this.eventMapper = eventMapper;
        this.eventArticleMapper = eventArticleMapper;
        this.articleCleanMapper = articleCleanMapper;
        this.articleSentimentMapper = articleSentimentMapper;
        this.pythonReportClient = pythonReportClient;
    }

    @Override
    public ReportVO generateReport(ReportGenerateRequest request) {
        Event event = eventMapper.selectById(request.eventId());
        if (event == null) {
            throw new ReportServiceException("事件不存在: " + request.eventId());
        }

        // 获取关联文章
        LambdaQueryWrapper<EventArticle> relWrapper = new LambdaQueryWrapper<>();
        relWrapper.eq(EventArticle::getEventId, event.getId());
        List<EventArticle> relations = eventArticleMapper.selectList(relWrapper);
        List<Long> cleanIds = relations.stream().map(EventArticle::getCleanId).toList();

        if (cleanIds.isEmpty()) {
            throw new ReportServiceException("该事件没有关联文章");
        }

        List<ArticleClean> articles = articleCleanMapper.selectBatchIds(cleanIds);

        // 情感统计
        LambdaQueryWrapper<ArticleSentiment> sentimentWrapper = new LambdaQueryWrapper<>();
        sentimentWrapper.in(ArticleSentiment::getCleanId, cleanIds);
        List<ArticleSentiment> sentiments = articleSentimentMapper.selectList(sentimentWrapper);

        int posCount = 0, negCount = 0, neuCount = 0;
        for (ArticleSentiment s : sentiments) {
            switch (s.getSentiment()) {
                case "POSITIVE" -> posCount++;
                case "NEGATIVE" -> negCount++;
                default -> neuCount++;
            }
        }
        int total = Math.max(sentiments.size(), 1);
        BigDecimal posRatio = BigDecimal.valueOf(posCount).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        BigDecimal negRatio = BigDecimal.valueOf(negCount).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        BigDecimal neuRatio = BigDecimal.valueOf(neuCount).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);

        // 时间线
        Map<LocalDate, Long> timeline = articles.stream()
                .filter(a -> a.getPublishedAt() != null)
                .collect(Collectors.groupingBy(
                        a -> {
                            try { return LocalDate.parse(a.getPublishedAt().substring(0, 10)); }
                            catch (Exception e) { return LocalDate.now(); }
                        },
                        TreeMap::new,
                        Collectors.counting()
                ));

        // 组装 JSON
        StringBuilder json = new StringBuilder("{");
        json.append("\"eventId\":").append(event.getId()).append(",");
        json.append("\"title\":\"").append(escapeJson(event.getTitle())).append("\",");
        json.append("\"keywords\":\"").append(escapeJson(event.getKeywords())).append("\",");
        json.append("\"lifecycle\":\"").append(escapeJson(event.getLifecycle())).append("\",");
        json.append("\"hotness\":").append(event.getHotness()).append(",");
        json.append("\"articleCount\":").append(event.getArticleCount()).append(",");
        json.append("\"sentiment\":{");
        json.append("\"positive\":").append(posRatio).append(",");
        json.append("\"negative\":").append(negRatio).append(",");
        json.append("\"neutral\":").append(neuRatio);
        json.append("},");
        json.append("\"timeline\":[");
        boolean first = true;
        for (var entry : timeline.entrySet()) {
            if (!first) json.append(",");
            json.append("{\"date\":\"").append(entry.getKey()).append("\",\"count\":").append(entry.getValue()).append("}");
            first = false;
        }
        json.append("],");
        json.append("\"articles\":[");
        for (int i = 0; i < articles.size(); i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(escapeJson(articles.get(i).getTitle())).append("\"");
        }
        json.append("]}");

        Report report = new Report();
        report.setEventId(event.getId());
        report.setTitle(event.getTitle());
        report.setContentJson(json.toString());
        reportMapper.insert(report);

        return new ReportVO(report.getId(), report.getEventId(), report.getTitle(),
                report.getContentJson(), report.getCreateTime());
    }

    @Override
    public ReportVO getReport(Long id) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new ReportServiceException("报告不存在: " + id);
        }
        return new ReportVO(report.getId(), report.getEventId(), report.getTitle(),
                report.getContentJson(), report.getCreateTime());
    }

    @Override
    public List<ReportVO> listReports(long pageNum, long pageSize) {
        Page<Report> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Report::getCreateTime);
        Page<Report> result = reportMapper.selectPage(page, wrapper);
        return result.getRecords().stream()
                .map(r -> new ReportVO(r.getId(), r.getEventId(), r.getTitle(),
                        r.getContentJson(), r.getCreateTime()))
                .collect(Collectors.toList());
    }

    @Override
    public QaResultVO askQuestion(QaRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("question", request.question());

        if (request.reportId() != null) {
            Report report = reportMapper.selectById(request.reportId());
            if (report != null) {
                body.put("report", report.getContentJson());
            }
        }

        PythonReportClient.QaResult result = pythonReportClient.ask(body);

        // 如果 report.json 是字符串，需要特殊处理
        return new QaResultVO(result.answer(), result.source());
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
