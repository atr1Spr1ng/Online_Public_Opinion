package com.bupt.publicopinion.propagation.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bupt.publicopinion.analysis.client.PythonIntelligenceClient;
import com.bupt.publicopinion.collection.entity.ArticleRaw;
import com.bupt.publicopinion.collection.mapper.ArticleRawMapper;
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
import java.util.stream.Collectors;

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
    private final ArticleRawMapper articleRawMapper;
    private final PythonIntelligenceClient pythonIntelligenceClient;

    public PropagationServiceImpl(
            PropagationPathMapper propagationPathMapper,
            PropagationNodeMapper propagationNodeMapper,
            EventMapper eventMapper,
            EventArticleMapper eventArticleMapper,
            ArticleCleanMapper articleCleanMapper,
            ArticleRawMapper articleRawMapper,
            PythonIntelligenceClient pythonIntelligenceClient
    ) {
        this.propagationPathMapper = propagationPathMapper;
        this.propagationNodeMapper = propagationNodeMapper;
        this.eventMapper = eventMapper;
        this.eventArticleMapper = eventArticleMapper;
        this.articleCleanMapper = articleCleanMapper;
        this.articleRawMapper = articleRawMapper;
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
        Map<Long, String> sourceMap = buildSourceMap(articles);

        // 按时间排序
        List<ArticleClean> sortedArticles = articles.stream()
                .sorted(Comparator.comparing(a -> parseTime(effectiveTime(a))))
                .toList();

        // 时间间隙分组：>90天间隙视为不同活跃期，指标基于近期集群
        List<ArticleClean> mainCluster = extractMainCluster(sortedArticles);
        if (mainCluster.size() < sortedArticles.size()) {
            System.out.println("[Propagation] 检测到 " + (sortedArticles.size() - mainCluster.size())
                    + " 篇历史文章（>90天间隙），指标基于近期 " + mainCluster.size() + " 篇计算");
        }

        ArticleClean sourceArticle = mainCluster.get(0);

        // ── 尝试调用 Python 传播分析 ──
        Map<String, Object> pythonResult = null;
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event_id", event.getId());
            payload.put("event_title", event.getTitle() != null ? event.getTitle() : "");

            // 只传主力集群给 Python，避免历史文章抢占源头角色
            List<Map<String, Object>> pyArticles = new ArrayList<>();
            for (ArticleClean a : mainCluster) {
                Map<String, Object> pa = new HashMap<>();
                pa.put("id", a.getId());
                pa.put("title", a.getTitle() != null ? a.getTitle() : "");
                pa.put("content", a.getContent() != null ? a.getContent() : "");
                pa.put("source_name", resolveSourceName(a, sourceMap));
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
        path.setSourceName(resolveSourceName(sourceArticle, sourceMap));
        propagationPathMapper.insert(path);

        PropagationNode sourceNode = new PropagationNode();
        sourceNode.setPathId(path.getId());
        sourceNode.setCleanId(sourceArticle.getId());
        sourceNode.setSourceName(resolveSourceName(sourceArticle, sourceMap));
        sourceNode.setPublishedAt(effectiveTime(sourceArticle));
        sourceNode.setDepth(0);
        sourceNode.setIsSource(1);
        propagationNodeMapper.insert(sourceNode);

        List<PropagationNode> childNodes = new ArrayList<>();
        if (pythonResult != null) {
            // Python 成功：使用 PythonIntelligenceClient 已翻译的 nodes/edges
            List<Map<String, Object>> pyNodes = (List<Map<String, Object>>) pythonResult.getOrDefault("nodes", List.of());
            for (Map<String, Object> pn : pyNodes) {
                int cleanId = toInt(pn.get("cleanId"));
                if (cleanId == sourceArticle.getId().intValue()) continue;
                PropagationNode node = new PropagationNode();
                node.setPathId(path.getId());
                node.setCleanId((long) cleanId);
                ArticleClean matched = findArticleById(articles, (long) cleanId);
                node.setSourceName(resolveSourceName(matched, sourceMap, (String) pn.getOrDefault("sourceName", "")));
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
                    sourceNode.getId(), path.getId(), sourceMap);
            for (PropagationNode node : childNodes) {
                propagationNodeMapper.insert(node);
            }
        }

        List<PropagationNode> allNodes = new ArrayList<>();
        allNodes.add(sourceNode);
        allNodes.addAll(childNodes);

        // 追加历史文章节点到 DB 和 allNodes
        for (ArticleClean a : sortedArticles) {
            if (!mainCluster.contains(a)) {
                PropagationNode hn = new PropagationNode();
                hn.setPathId(path.getId());
                hn.setCleanId(a.getId());
                hn.setSourceName(resolveSourceName(a, sourceMap));
                hn.setPublishedAt(effectiveTime(a));
                hn.setDepth(1);
                hn.setParentNodeId(sourceNode.getId());
                hn.setIsSource(0);
                propagationNodeMapper.insert(hn);
                allNodes.add(hn);
            }
        }

        BigDecimal durationHours = calcDuration(mainCluster);
        BigDecimal spreadSpeed = BigDecimal.valueOf(mainCluster.size())
                .divide(durationHours.compareTo(BigDecimal.ZERO) > 0 ? durationHours : BigDecimal.ONE,
                        4, RoundingMode.HALF_UP);
        String pathJson = buildPathJson(allNodes);

        int spreadDepth;
        if (pythonResult != null) {
            spreadDepth = toInt(pythonResult.get("spread_depth"));
            // durationHours 和 spreadSpeed 以 Java mainCluster 为准，不被 Python 覆盖
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
        Set<Long> clusterIds = mainCluster.stream().map(ArticleClean::getId).collect(Collectors.toSet());
        Map<Long, Integer> clusterOrder = buildClusterOrder(mainCluster);
        List<PropagationNodeVO> nodeVOs = new ArrayList<>();
        List<Map<String, Object>> edges = List.of();
        String method = "fallback";

        if (pythonResult != null) {
            method = "llm";
            List<Map<String, Object>> pyNodes = (List<Map<String, Object>>) pythonResult.getOrDefault("nodes", List.of());
            for (Map<String, Object> pn : pyNodes) {
                long cid = toInt(pn.get("cleanId"));
                ArticleClean matched = findArticleById(articles, cid);
                nodeVOs.add(buildNodeVO(
                        null, cid,
                        (String) pn.getOrDefault("articleTitle", ""),
                        resolveSourceName(matched, sourceMap, (String) pn.getOrDefault("sourceName", "")),
                        (String) pn.getOrDefault("publishedAt", ""),
                        toInt(pn.get("depth")),
                        sourceNode.getId(),
                        Boolean.TRUE.equals(pn.get("isSource")),
                        Boolean.TRUE.equals(pn.get("isInfluencer")),
                        (String) pn.getOrDefault("nodeType", "commercial"),
                        !clusterIds.contains(cid),  // isHistorical
                        null,
                        clusterOrder, mainCluster.size()
                ));
            }
            edges = (List<Map<String, Object>>) pythonResult.getOrDefault("edges", List.of());
        } else {
            method = "fallback";
            for (PropagationNode n : allNodes) {
                nodeVOs.add(buildNodeVO(
                        n.getId(), n.getCleanId(),
                        findTitleById(articles, n.getCleanId()),
                        resolveSourceName(findArticleById(articles, n.getCleanId()), sourceMap, n.getSourceName()), n.getPublishedAt(),
                        n.getDepth(), n.getParentNodeId(),
                        n.getIsSource() != null && n.getIsSource() == 1,
                        false, "commercial",
                        !clusterIds.contains(n.getCleanId()),
                        n.getCreateTime(),
                        clusterOrder, mainCluster.size()
                ));
            }
        }

        // 追加历史文章到 VO（这些不在 Python 输出中，因为只传了主力集群给 Python）
        for (ArticleClean a : sortedArticles) {
            if (!clusterIds.contains(a.getId())) {
                nodeVOs.add(buildNodeVO(
                        null, a.getId(),
                        a.getTitle(), resolveSourceName(a, sourceMap), effectiveTime(a),
                        1, sourceNode.getId(),
                        false, false, "commercial",
                        true, null,
                        clusterOrder, mainCluster.size()
                ));
            }
        }

        List<Map<String, Object>> phases = buildPhaseSummaries(nodeVOs);

        return new PropagationPathVO(
                path.getId(), path.getEventId(), path.getSourceArticleId(),
                path.getSourceName(), sourceArticle.getTitle(),
                spreadDepth, allNodes.size(),
                durationHours, spreadSpeed,
                path.getPathJson(), nodeVOs, edges, phases, method, path.getCreateTime()
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
        Map<Long, String> sourceMap = buildSourceMap(articles);

        String sourceTitle = "";
        if (path.getSourceArticleId() != null) {
            ArticleClean source = articleCleanMapper.selectById(path.getSourceArticleId());
            if (source != null) sourceTitle = source.getTitle();
        }

        List<ArticleClean> sortedNodeArticles = articles.stream()
                .sorted(Comparator.comparing(a -> parseTime(effectiveTime(a))))
                .toList();
        Map<Long, Integer> clusterOrder = buildClusterOrder(sortedNodeArticles);

        List<PropagationNodeVO> nodeVOs = nodes.stream().map(n -> buildNodeVO(
                n.getId(), n.getCleanId(),
                findTitleById(articles, n.getCleanId()),
                resolveSourceName(findArticleById(articles, n.getCleanId()), sourceMap, n.getSourceName()), n.getPublishedAt(),
                n.getDepth(), n.getParentNodeId(),
                n.getIsSource() != null && n.getIsSource() == 1,
                false, "commercial",
                false,   // isHistorical not tracked for historical paths
                n.getCreateTime(),
                clusterOrder, sortedNodeArticles.size()
        )).toList();

        List<Map<String, Object>> phases = buildPhaseSummaries(nodeVOs);

        return new PropagationPathVO(
                path.getId(), path.getEventId(), path.getSourceArticleId(),
                resolveSourceName(findArticleById(articles, path.getSourceArticleId()), sourceMap, path.getSourceName()), sourceTitle,
                path.getSpreadDepth(), path.getTotalNodes(),
                path.getDurationHours(), path.getSpreadSpeed(),
                path.getPathJson(), nodeVOs, List.of(), phases, "fallback", path.getCreateTime()
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

        // 找最早的关联文章（过滤时间离群点后）
        LambdaQueryWrapper<EventArticle> relWrapper = new LambdaQueryWrapper<>();
        relWrapper.eq(EventArticle::getEventId, eventId);
        List<EventArticle> relations = eventArticleMapper.selectList(relWrapper);
        if (relations.isEmpty()) {
            throw new PropagationAnalysisException("该事件没有关联文章");
        }

        List<Long> cleanIds = relations.stream().map(EventArticle::getCleanId).toList();
        List<ArticleClean> articles = articleCleanMapper.selectBatchIds(cleanIds);
        Map<Long, String> sourceMap = buildSourceMap(articles);

        ArticleClean sourceArticle = articles.stream()
                .min(Comparator.comparing(a -> parseTime(effectiveTime(a))))
                .orElseThrow(() -> new PropagationAnalysisException("没有可用的文章信息"));

        return new SourceTraceResult(
                event.getId(),
                event.getTitle(),
                sourceArticle.getId(),
                sourceArticle.getTitle(),
                resolveSourceName(sourceArticle, sourceMap),
                sourceArticle.getPublishedAt(),
                sourceArticle.getSummary()
        );
    }

    // ── 传播树构建 ──────────────────────────────────────────────

    private List<PropagationNode> buildPropagationTree(List<ArticleClean> sortedArticles, ArticleClean source,
                                                         Long sourceNodeId, Long pathId, Map<Long, String> sourceMap) {
        List<PropagationNode> nodes = new ArrayList<>();

        // 按source_name分组，每组代表一个传播分支
        Map<String, List<ArticleClean>> groupedBySource = new LinkedHashMap<>();
        for (ArticleClean a : sortedArticles) {
            if (a.getId().equals(source.getId())) continue;
            String sourceName = resolveSourceName(a, sourceMap);
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
                node.setSourceName(resolveSourceName(a, sourceMap));
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

    // ── 阶段化展示字段 ──────────────────────────────────────────

    private PropagationNodeVO buildNodeVO(
            Long id,
            Long cleanId,
            String articleTitle,
            String sourceName,
            String publishedAt,
            Integer depth,
            Long parentNodeId,
            Boolean isSource,
            Boolean isInfluencer,
            String nodeType,
            Boolean isHistorical,
            LocalDateTime createTime,
            Map<Long, Integer> clusterOrder,
            int clusterSize
    ) {
        StageInfo stage = stageInfo(cleanId, isSource, isInfluencer, isHistorical, nodeType, clusterOrder, clusterSize);
        return new PropagationNodeVO(
                id, cleanId, articleTitle, sourceName, publishedAt, depth, parentNodeId,
                isSource, isInfluencer, nodeType, isHistorical,
                stage.phase(), stage.phaseIndex(), stage.role(), stage.reason(), stage.representative(),
                createTime
        );
    }

    private Map<Long, Integer> buildClusterOrder(List<ArticleClean> sortedArticles) {
        Map<Long, Integer> order = new HashMap<>();
        for (int i = 0; i < sortedArticles.size(); i++) {
            order.put(sortedArticles.get(i).getId(), i);
        }
        return order;
    }

    private StageInfo stageInfo(
            Long cleanId,
            Boolean isSource,
            Boolean isInfluencer,
            Boolean isHistorical,
            String nodeType,
            Map<Long, Integer> clusterOrder,
            int clusterSize
    ) {
        if (Boolean.TRUE.equals(isHistorical)) {
            return new StageInfo("历史参考", -1, "历史文章",
                    "与当前活跃期存在较大时间间隔，仅作为背景参考", false);
        }

        int order = clusterOrder.getOrDefault(cleanId, 0);
        int phaseIndex = phaseIndex(order, clusterSize);
        String phase = switch (phaseIndex) {
            case 0 -> "首发期";
            case 1 -> "扩散期";
            case 2 -> "升温期";
            default -> "后续期";
        };

        String role;
        String reason;
        boolean representative = false;
        if (Boolean.TRUE.equals(isSource) || order == 0) {
            role = "源头文章";
            reason = "当前事件活跃期内最早出现的关联报道";
            representative = true;
            phaseIndex = 0;
            phase = "首发期";
        } else if (Boolean.TRUE.equals(isInfluencer)) {
            role = "关键放大";
            reason = "传播链推断中被标记为关键节点";
            representative = true;
        } else if ("official".equals(nodeType)) {
            role = "权威跟进";
            reason = "来源或正文特征匹配官方媒体";
            representative = order <= Math.max(2, clusterSize / 3);
        } else {
            role = "普通报道";
            reason = "按发布时间归入对应传播阶段";
        }

        return new StageInfo(phase, phaseIndex, role, reason, representative);
    }

    private int phaseIndex(int order, int total) {
        if (total <= 1 || order <= 0) return 0;
        double ratio = order / (double) Math.max(total - 1, 1);
        if (ratio <= 0.25) return 0;
        if (ratio <= 0.55) return 1;
        if (ratio <= 0.80) return 2;
        return 3;
    }

    private List<Map<String, Object>> buildPhaseSummaries(List<PropagationNodeVO> nodes) {
        Map<Integer, List<PropagationNodeVO>> grouped = nodes.stream()
                .collect(Collectors.groupingBy(
                        n -> n.phaseIndex() != null ? n.phaseIndex() : 0,
                        TreeMap::new,
                        Collectors.toList()
                ));

        List<Map<String, Object>> phases = new ArrayList<>();
        for (Map.Entry<Integer, List<PropagationNodeVO>> entry : grouped.entrySet()) {
            List<PropagationNodeVO> phaseNodes = entry.getValue().stream()
                    .sorted(Comparator.comparing(n -> parseTime(n.publishedAt()), Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            if (phaseNodes.isEmpty()) continue;

            List<Map<String, Object>> representatives = phaseNodes.stream()
                    .filter(n -> Boolean.TRUE.equals(n.representative()))
                    .limit(3)
                    .map(this::toRepresentativeMap)
                    .collect(Collectors.toCollection(ArrayList::new));

            if (representatives.isEmpty()) {
                representatives.add(toRepresentativeMap(phaseNodes.get(0)));
            }

            Map<String, Object> phase = new LinkedHashMap<>();
            phase.put("name", phaseNodes.get(0).phase());
            phase.put("phaseIndex", entry.getKey());
            phase.put("articleCount", phaseNodes.size());
            phase.put("sourceCount", phaseNodes.stream()
                    .map(PropagationNodeVO::sourceName)
                    .filter(s -> !isUnknownSource(s))
                    .collect(Collectors.toSet())
                    .size());
            phase.put("startTime", phaseNodes.get(0).publishedAt());
            phase.put("endTime", phaseNodes.get(phaseNodes.size() - 1).publishedAt());
            phase.put("representatives", representatives);
            phases.add(phase);
        }
        return phases;
    }

    private Map<String, Object> toRepresentativeMap(PropagationNodeVO node) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("cleanId", node.cleanId());
        item.put("title", node.articleTitle());
        item.put("sourceName", node.sourceName());
        item.put("publishedAt", node.publishedAt());
        item.put("role", node.role());
        item.put("reason", node.reason());
        return item;
    }

    private record StageInfo(
            String phase,
            Integer phaseIndex,
            String role,
            String reason,
            Boolean representative
    ) {}

    // ── 工具方法 ────────────────────────────────────────────────

    /**
     * 从最新文章向前扫描，>90天间隙处断开，返回最近的连贯集群。
     * 历史文章保留在全量数据中（传给 Python 构图），但指标只基于此集群计算。
     */
    private List<ArticleClean> extractMainCluster(List<ArticleClean> sortedArticles) {
        if (sortedArticles.size() <= 1) return sortedArticles;

        // 从最新文章向前扫描
        List<ArticleClean> cluster = new ArrayList<>();
        LocalDateTime prevTime = null;

        for (int i = sortedArticles.size() - 1; i >= 0; i--) {
            ArticleClean a = sortedArticles.get(i);
            LocalDateTime t = parseTime(effectiveTime(a));
            if (t == null) {
                cluster.add(a);
                continue;
            }

            if (prevTime == null) {
                cluster.add(a);
                prevTime = t;
                continue;
            }

            long gapDays = Math.abs(Duration.between(prevTime, t).toDays());
            if (gapDays > 90) break; // 间隙过大，前面的归为历史文章

            cluster.add(a);
            prevTime = t;
        }

        // cluster 是逆序的，翻转回时间升序
        Collections.reverse(cluster);
        return cluster;
    }

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

    private ArticleClean findArticleById(List<ArticleClean> articles, Long cleanId) {
        if (cleanId == null) return null;
        return articles.stream()
                .filter(a -> cleanId.equals(a.getId()))
                .findFirst()
                .orElse(null);
    }

    private Map<Long, String> buildSourceMap(List<ArticleClean> articles) {
        Map<Long, String> sourceMap = new HashMap<>();
        List<Long> rawIds = articles.stream()
                .map(ArticleClean::getRawId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> rawSourceMap = new HashMap<>();
        if (!rawIds.isEmpty()) {
            List<ArticleRaw> rawArticles = articleRawMapper.selectList(
                    new LambdaQueryWrapper<ArticleRaw>()
                            .select(ArticleRaw::getId, ArticleRaw::getSourceName)
                            .in(ArticleRaw::getId, rawIds)
            );
            for (ArticleRaw raw : rawArticles) {
                if (!isUnknownSource(raw.getSourceName())) {
                    rawSourceMap.put(raw.getId(), raw.getSourceName());
                }
            }
        }
        for (ArticleClean article : articles) {
            String source = !isUnknownSource(article.getSourceName())
                    ? article.getSourceName()
                    : rawSourceMap.get(article.getRawId());
            if (!isUnknownSource(source)) {
                sourceMap.put(article.getId(), source);
            }
        }
        return sourceMap;
    }

    private String resolveSourceName(ArticleClean article, Map<Long, String> sourceMap) {
        return resolveSourceName(article, sourceMap, null);
    }

    private String resolveSourceName(ArticleClean article, Map<Long, String> sourceMap, String fallback) {
        if (article != null) {
            String mapped = sourceMap.get(article.getId());
            if (!isUnknownSource(mapped)) {
                return mapped;
            }
            if (!isUnknownSource(article.getSourceName())) {
                return article.getSourceName();
            }
        }
        if (!isUnknownSource(fallback)) {
            return fallback;
        }
        return "未知来源";
    }

    private boolean isUnknownSource(String sourceName) {
        return sourceName == null
                || sourceName.isBlank()
                || "未知".equals(sourceName)
                || "未知来源".equals(sourceName)
                || "未知新闻源".equals(sourceName);
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
