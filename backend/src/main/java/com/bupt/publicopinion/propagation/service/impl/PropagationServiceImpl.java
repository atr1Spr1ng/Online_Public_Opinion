package com.bupt.publicopinion.propagation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bupt.publicopinion.analysis.client.PythonIntelligenceClient;
import com.bupt.publicopinion.content.entity.ArticleClean;
import com.bupt.publicopinion.content.mapper.ArticleCleanMapper;
import com.bupt.publicopinion.event.entity.Event;
import com.bupt.publicopinion.event.entity.EventArticle;
import com.bupt.publicopinion.event.mapper.EventArticleMapper;
import com.bupt.publicopinion.event.mapper.EventMapper;
import com.bupt.publicopinion.propagation.dto.PropagationAnalysisRequest;
import com.bupt.publicopinion.propagation.entity.PropagationNode;
import com.bupt.publicopinion.propagation.entity.PropagationPath;
import com.bupt.publicopinion.propagation.exception.PropagationAnalysisException;
import com.bupt.publicopinion.propagation.mapper.PropagationNodeMapper;
import com.bupt.publicopinion.propagation.mapper.PropagationPathMapper;
import com.bupt.publicopinion.propagation.service.PropagationService;
import com.bupt.publicopinion.propagation.vo.PropagationNodeVO;
import com.bupt.publicopinion.propagation.vo.PropagationPathVO;
import com.bupt.publicopinion.propagation.vo.SourceTraceResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PropagationServiceImpl implements PropagationService {

    private static final DateTimeFormatter[] FORMATTERS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
    };

    private final PropagationPathMapper propagationPathMapper;
    private final PropagationNodeMapper propagationNodeMapper;
    private final EventMapper eventMapper;
    private final EventArticleMapper eventArticleMapper;
    private final ArticleCleanMapper articleCleanMapper;
    private final PythonIntelligenceClient pythonIntelligenceClient;

    public PropagationServiceImpl(
            PropagationPathMapper propagationPathMapper,
            PropagationNodeMapper propagationNodeMapper,
            EventMapper eventMapper,
            EventArticleMapper eventArticleMapper,
            ArticleCleanMapper articleCleanMapper,
            PythonIntelligenceClient pythonIntelligenceClient
    ) {
        this.propagationPathMapper = propagationPathMapper;
        this.propagationNodeMapper = propagationNodeMapper;
        this.eventMapper = eventMapper;
        this.eventArticleMapper = eventArticleMapper;
        this.articleCleanMapper = articleCleanMapper;
        this.pythonIntelligenceClient = pythonIntelligenceClient;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PropagationPathVO analyze(PropagationAnalysisRequest request) {
        Event event = eventMapper.selectById(request.eventId());
        if (event == null) {
            throw new PropagationAnalysisException("事件不存在: " + request.eventId());
        }

        LambdaQueryWrapper<EventArticle> relWrapper = new LambdaQueryWrapper<>();
        relWrapper.eq(EventArticle::getEventId, event.getId());
        List<EventArticle> relations = eventArticleMapper.selectList(relWrapper);
        if (relations.isEmpty()) {
            throw new PropagationAnalysisException("该事件没有关联文章");
        }

        List<Long> cleanIds = relations.stream().map(EventArticle::getCleanId).toList();
        List<ArticleClean> articles = articleCleanMapper.selectBatchIds(cleanIds);

        // 按时间排序
        List<ArticleClean> sortedArticles = articles.stream()
                .sorted(Comparator.comparing(a -> parseTime(effectiveTime(a))))
                .toList();
        ArticleClean sourceArticle = sortedArticles.get(0);

        // ── 尝试调用 Python 传播分析 ──
        Map<String, Object> pythonResult = null;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event_id", event.getId());
            payload.put("event_title", event.getTitle() != null ? event.getTitle() : "");

            List<Map<String, Object>> pyArticles = new ArrayList<>();
            for (ArticleClean a : articles) {
                Map<String, Object> pa = new HashMap<>();
                pa.put("id", a.getId());
                pa.put("title", a.getTitle() != null ? a.getTitle() : "");
                pa.put("content", a.getContent() != null ? a.getContent() : "");
                pa.put("source_name", a.getSourceName() != null ? a.getSourceName() : "");
                pa.put("published_at", effectiveTime(a));
                pyArticles.add(pa);
            }
            payload.put("articles", pyArticles);

            pythonResult = pythonIntelligenceClient.analyzePropagation(payload);
        } catch (Exception e) {
            System.err.println("[Propagation] Python 传播分析失败，降级为规则分析: " + e.getMessage());
            pythonResult = null;
        }

        // ── 保存到数据库 ──
        PropagationPath path = new PropagationPath();
        path.setEventId(event.getId());
        path.setSourceArticleId(sourceArticle.getId());
        path.setSourceName(sourceArticle.getSourceName());
        propagationPathMapper.insert(path);

        PropagationNode sourceNode = new PropagationNode();
        sourceNode.setPathId(path.getId());
        sourceNode.setCleanId(sourceArticle.getId());
        sourceNode.setSourceName(sourceArticle.getSourceName());
        sourceNode.setPublishedAt(effectiveTime(sourceArticle));
        sourceNode.setDepth(0);
        sourceNode.setIsSource(1);
        propagationNodeMapper.insert(sourceNode);

        List<PropagationNode> childNodes = new ArrayList<>();
        if (pythonResult != null) {
            // Python 成功：使用 Python 的 nodes/edges 结果
            List<Map<String, Object>> pyNodes = (List<Map<String, Object>>) pythonResult.getOrDefault("nodes", List.of());
            for (Map<String, Object> pn : pyNodes) {
                int cleanId = toInt(pn.get("cleanId"));
                if (cleanId == sourceArticle.getId().intValue()) continue;
                PropagationNode node = new PropagationNode();
                node.setPathId(path.getId());
                node.setCleanId((long) cleanId);
                node.setSourceName((String) pn.getOrDefault("sourceName", ""));
                node.setPublishedAt((String) pn.getOrDefault("publishedAt", ""));
                node.setDepth(toInt(pn.get("depth")));
                node.setParentNodeId(sourceNode.getId());
                node.setIsSource(0);
                propagationNodeMapper.insert(node);
                childNodes.add(node);
            }
        } else {
            // 降级：Java 规则构建
            childNodes = buildPropagationTree(sortedArticles, sourceArticle,
                    sourceNode.getId(), path.getId());
            for (PropagationNode node : childNodes) {
                propagationNodeMapper.insert(node);
            }
        }

        List<PropagationNode> allNodes = new ArrayList<>();
        allNodes.add(sourceNode);
        allNodes.addAll(childNodes);

        BigDecimal durationHours = calcDuration(sortedArticles);
        BigDecimal spreadSpeed = BigDecimal.valueOf(sortedArticles.size())
                .divide(durationHours.compareTo(BigDecimal.ZERO) > 0 ? durationHours : BigDecimal.ONE,
                        2, RoundingMode.HALF_UP);
        String pathJson = buildPathJson(allNodes);

        int spreadDepth;
        if (pythonResult != null) {
            spreadDepth = toInt(pythonResult.get("spread_depth"));
            durationHours = (BigDecimal) pythonResult.get("duration_hours");
            spreadSpeed = (BigDecimal) pythonResult.get("spread_speed");
        } else {
            spreadDepth = allNodes.stream().mapToInt(PropagationNode::getDepth).max().orElse(0);
        }

        path.setSpreadDepth(spreadDepth);
        path.setTotalNodes(allNodes.size());
        path.setDurationHours(durationHours);
        path.setSpreadSpeed(spreadSpeed);
        path.setPathJson(pathJson);
        propagationPathMapper.updateById(path);

        // ── 组装 VO ──
        List<PropagationNodeVO> nodeVOs;
        List<Map<String, Object>> edges = List.of();
        String method = "fallback";

        if (pythonResult != null) {
            method = "llm";
            List<Map<String, Object>> pyNodes = (List<Map<String, Object>>) pythonResult.getOrDefault("nodes", List.of());
            nodeVOs = pyNodes.stream().map(pn -> new PropagationNodeVO(
                    null,
                    (long) toInt(pn.get("cleanId")),
                    (String) pn.getOrDefault("articleTitle", ""),
                    (String) pn.getOrDefault("sourceName", ""),
                    (String) pn.getOrDefault("publishedAt", ""),
                    toInt(pn.get("depth")),
                    sourceNode.getId(),
                    Boolean.TRUE.equals(pn.get("isSource")),
                    Boolean.TRUE.equals(pn.get("isInfluencer")),
                    (String) pn.getOrDefault("nodeType", "commercial"),
                    null
            )).toList();
            edges = (List<Map<String, Object>>) pythonResult.getOrDefault("edges", List.of());
        } else {
            method = "fallback";
            nodeVOs = allNodes.stream().map(n -> new PropagationNodeVO(
                    n.getId(), n.getCleanId(),
                    findTitleById(articles, n.getCleanId()),
                    n.getSourceName(), n.getPublishedAt(),
                    n.getDepth(), n.getParentNodeId(),
                    n.getIsSource() != null && n.getIsSource() == 1,
                    false, "commercial",
                    n.getCreateTime()
            )).toList();
        }

        return new PropagationPathVO(
                path.getId(), path.getEventId(), path.getSourceArticleId(),
                path.getSourceName(), sourceArticle.getTitle(),
                spreadDepth, allNodes.size(),
                durationHours, spreadSpeed,
                path.getPathJson(), nodeVOs, edges, method, path.getCreateTime()
        );
    }

    @Override
    public PropagationPathVO getPath(Long id) {
        PropagationPath path = propagationPathMapper.selectById(id);
        if (path == null) {
            throw new PropagationAnalysisException("传播路径不存在: " + id);
        }

        LambdaQueryWrapper<PropagationNode> nodeWrapper = new LambdaQueryWrapper<>();
        nodeWrapper.eq(PropagationNode::getPathId, id);
        nodeWrapper.orderByAsc(PropagationNode::getDepth, PropagationNode::getPublishedAt);
        List<PropagationNode> nodes = propagationNodeMapper.selectList(nodeWrapper);

        // 获取文章标题
        List<Long> cleanIds = nodes.stream().map(PropagationNode::getCleanId).toList();
        List<ArticleClean> articles = articleCleanMapper.selectBatchIds(cleanIds);

        String sourceTitle = "";
        if (path.getSourceArticleId() != null) {
            ArticleClean source = articleCleanMapper.selectById(path.getSourceArticleId());
            if (source != null) sourceTitle = source.getTitle();
        }

        List<PropagationNodeVO> nodeVOs = nodes.stream().map(n -> new PropagationNodeVO(
                n.getId(), n.getCleanId(),
                findTitleById(articles, n.getCleanId()),
                n.getSourceName(), n.getPublishedAt(),
                n.getDepth(), n.getParentNodeId(),
                n.getIsSource() != null && n.getIsSource() == 1,
                false, "commercial",
                n.getCreateTime()
        )).toList();

        return new PropagationPathVO(
                path.getId(), path.getEventId(), path.getSourceArticleId(),
                path.getSourceName(), sourceTitle,
                path.getSpreadDepth(), path.getTotalNodes(),
                path.getDurationHours(), path.getSpreadSpeed(),
                path.getPathJson(), nodeVOs, List.of(), "fallback", path.getCreateTime()
        );
    }

    @Override
    public PropagationPathVO getPathByEvent(Long eventId) {
        LambdaQueryWrapper<PropagationPath> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PropagationPath::getEventId, eventId);
        wrapper.orderByDesc(PropagationPath::getCreateTime);
        wrapper.last("LIMIT 1");
        PropagationPath path = propagationPathMapper.selectOne(wrapper);
        if (path == null) {
            throw new PropagationAnalysisException("该事件尚未进行传播路径分析: " + eventId);
        }
        return getPath(path.getId());
    }

    @Override
    public SourceTraceResult traceSource(Long eventId) {
        Event event = eventMapper.selectById(eventId);
        if (event == null) {
            throw new PropagationAnalysisException("事件不存在: " + eventId);
        }

        // 找最早的关联文章
        LambdaQueryWrapper<EventArticle> relWrapper = new LambdaQueryWrapper<>();
        relWrapper.eq(EventArticle::getEventId, eventId);
        List<EventArticle> relations = eventArticleMapper.selectList(relWrapper);
        if (relations.isEmpty()) {
            throw new PropagationAnalysisException("该事件没有关联文章");
        }

        List<Long> cleanIds = relations.stream().map(EventArticle::getCleanId).toList();
        List<ArticleClean> articles = articleCleanMapper.selectBatchIds(cleanIds);

        ArticleClean sourceArticle = articles.stream()
                .min(Comparator.comparing(a -> parseTime(effectiveTime(a))))
                .orElseThrow(() -> new PropagationAnalysisException("没有可用的文章信息"));

        return new SourceTraceResult(
                event.getId(),
                event.getTitle(),
                sourceArticle.getId(),
                sourceArticle.getTitle(),
                sourceArticle.getSourceName(),
                sourceArticle.getPublishedAt(),
                sourceArticle.getSummary()
        );
    }

    // ── 传播树构建 ──────────────────────────────────────────────

    private List<PropagationNode> buildPropagationTree(List<ArticleClean> sortedArticles, ArticleClean source,
                                                         Long sourceNodeId, Long pathId) {
        List<PropagationNode> nodes = new ArrayList<>();

        // 按source_name分组，每组代表一个传播分支
        Map<String, List<ArticleClean>> groupedBySource = new LinkedHashMap<>();
        for (ArticleClean a : sortedArticles) {
            if (a.getId().equals(source.getId())) continue;
            String sourceName = a.getSourceName() != null ? a.getSourceName() : "未知来源";
            groupedBySource.computeIfAbsent(sourceName, k -> new ArrayList<>()).add(a);
        }

        // 按每组最先出现的时间排序（传播层级）
        List<Map.Entry<String, List<ArticleClean>>> sortedGroups = groupedBySource.entrySet().stream()
                .sorted(Comparator.comparing(e -> {
                    LocalDateTime t = parseTime(effectiveTime(e.getValue().get(0)));
                    return t != null ? t : LocalDateTime.MAX;
                }))
                .toList();

        // 最多5层深度
        int maxGroupsPerDepth = Math.max(1, (int) Math.ceil(Math.sqrt(sortedGroups.size())));
        int depth = 1;
        int groupIdx = 0;

        for (var group : sortedGroups) {
            if (groupIdx > 0 && groupIdx % maxGroupsPerDepth == 0) {
                depth++;
            }
            if (depth > 5) depth = 5; // 最大深度限制

            for (ArticleClean a : group.getValue()) {
                PropagationNode node = new PropagationNode();
                node.setPathId(pathId);
                node.setCleanId(a.getId());
                node.setSourceName(a.getSourceName());
                node.setPublishedAt(effectiveTime(a));
                node.setDepth(depth);
                node.setParentNodeId(sourceNodeId);
                node.setIsSource(0);
                nodes.add(node);
            }
            groupIdx++;
        }

        return nodes;
    }

    // ── 指标计算 ────────────────────────────────────────────────

    private BigDecimal calcDuration(List<ArticleClean> sortedArticles) {
        LocalDateTime first = parseTime(effectiveTime(sortedArticles.get(0)));
        LocalDateTime last = parseTime(effectiveTime(sortedArticles.get(sortedArticles.size() - 1)));
        if (first == null || last == null) return BigDecimal.ZERO;

        double hours = Duration.between(first, last).toSeconds() / 3600.0;
        return BigDecimal.valueOf(Math.max(hours, 0.1)).setScale(2, RoundingMode.HALF_UP);
    }

    private String buildPathJson(List<PropagationNode> nodes) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) sb.append(",");
            PropagationNode n = nodes.get(i);
            sb.append("{");
            sb.append("\"cleanId\":").append(n.getCleanId()).append(",");
            sb.append("\"sourceName\":\"").append(escapeJson(n.getSourceName())).append("\",");
            sb.append("\"publishedAt\":\"").append(escapeJson(n.getPublishedAt())).append("\",");
            sb.append("\"depth\":").append(n.getDepth()).append(",");
            sb.append("\"isSource\":").append(n.getIsSource() == 1);
            sb.append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    // ── 工具方法 ────────────────────────────────────────────────

    private String effectiveTime(ArticleClean article) {
        if (article.getPublishedAt() != null && !article.getPublishedAt().isBlank()) {
            return article.getPublishedAt();
        }
        return article.getCreateTime() != null ? article.getCreateTime().toString() : "";
    }

    private LocalDateTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) return null;
        String t = timeStr.trim();
        for (DateTimeFormatter fmt : FORMATTERS) {
            try {
                return LocalDateTime.parse(t.length() > 19 ? t.substring(0, 19) : t, fmt);
            } catch (Exception ignored) {}
        }
        // 尝试仅日期格式
        try {
            return LocalDateTime.parse(t.substring(0, 10) + "T00:00:00");
        } catch (Exception e) {
            return null;
        }
    }

    private String findTitleById(List<ArticleClean> articles, Long cleanId) {
        return articles.stream()
                .filter(a -> a.getId().equals(cleanId))
                .map(ArticleClean::getTitle)
                .findFirst()
                .orElse("");
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private int toInt(Object value) {
        if (value instanceof Number num) return num.intValue();
        return 0;
    }
}
