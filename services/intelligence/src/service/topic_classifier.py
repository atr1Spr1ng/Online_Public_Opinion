import math
import re
from collections import defaultdict

import jieba
from sklearn.feature_extraction.text import CountVectorizer
from sklearn.decomposition import LatentDirichletAllocation

# 预设类别关键词词典（用于给 LDA 自动打标签，每类 12-25 个代表词）
CATEGORY_KEYWORDS = {
    "社会民生": [
        "住房", "房价", "就业", "失业", "社保", "养老", "医疗", "教育", "物价", "收入",
        "低保", "农民工", "拆迁", "物业", "社区", "交通", "环保", "污染", "食品安全",
        "婚姻", "生育", "儿童", "老人", "残疾人", "公益", "慈善"
    ],
    "科技经济": [
        "人工智能", "AI", "5G", "6G", "芯片", "半导体", "新能源", "光伏", "锂电", "电动车",
        "数字经济", "互联网", "电商", "区块链", "元宇宙", "量子", "航天", "卫星",
        "创新", "创业", "独角兽", "风投", "融资", "上市", "IPO", "股市", "基金",
        "GDP", "制造业", "进出口", "供应链", "消费"
    ],
    "教育文化": [
        "教育", "高考", "中考", "考研", "留学", "大学", "中小学", "考试", "双减",
        "教师", "学生", "家长", "培训", "在线教育", "文化", "艺术", "文学", "电影",
        "音乐", "博物馆", "非遗", "旅游", "传统", "历史", "考古", "出版", "版权"
    ],
    "医疗卫生": [
        "医疗", "医院", "医生", "护士", "患者", "药品", "疫苗", "疫情", "病毒",
        "疾控", "医保", "中医", "手术", "诊断", "心理健康", "公共卫生", "食品安全",
        "临床试验", "基因", "生物医药", "医疗器械"
    ],
    "政治法律": [
        "政策", "法规", "法律", "立法", "执法", "司法", "法院", "检察院", "公安",
        "反腐败", "纪检", "监察", "选举", "民主", "人权", "政府", "行政", "审批",
        "监管", "合规", "税收", "知识产权", "专利", "著作权"
    ],
    "生态环境": [
        "环保", "环境", "气候", "碳中和", "碳达峰", "排放", "污染", "生态", "绿化",
        "能源", "节能", "减排", "新能源", "水电", "风电", "核能", "废物", "垃圾",
        "垃圾分类", "水资源", "空气", "PM2.5", "生物多样性"
    ],
    "娱乐体育": [
        "体育", "足球", "篮球", "奥运", "世界杯", "电竞", "游戏", "明星", "演员",
        "歌手", "综艺", "真人秀", "演唱会", "粉丝", "追星", "偶像", "网红", "直播",
        "动漫", "二次元", "cosplay", "赛事"
    ],
    "国际时政": [
        "国际", "外交", "联合国", "制裁", "冲突", "战争", "和平", "地缘", "贸易战",
        "关税", "一带一路", "金砖", "G20", "APEC", "北约", "欧盟", "东盟", "中美",
        "中日", "中欧", "俄乌", "中东", "朝鲜", "南海", "台湾"
    ],
}

# 停用词表（高频但无意义的词）
STOP_WORDS = {
    "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
    "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
    "自己", "这", "他", "她", "它", "们", "那", "些", "所", "为", "所以", "因为",
    "但是", "然而", "虽然", "可以", "这个", "那个", "什么", "怎么", "如何", "为什么",
    "如果", "被", "把", "从", "对", "与", "及", "或", "等", "其", "中", "已", "已经",
    "将", "能", "能够", "可能", "应该", "需要", "经过", "通过", "根据", "按照",
    "进行", "使用", "利用", "其中", "该", "此", "之", "以", "以及", "甚至",
    "目前", "现在", "今天", "昨天", "近日", "近期", "今年", "去年",
    "报道", "据悉", "据了解", "消息", "新闻", "网", "记者", "编辑",
    "相关", "有关", "方面", "问题", "情况", "事件", "发生",
    "前", "后", "左右", "多", "来", "大", "小", "新", "老", "高", "低",
    "元", "万", "亿", "日", "月", "年", "第", "次", "个", "位", "名", "家",
    "发表", "显示", "表示", "称", "认为", "指出", "强调", "提出",
    "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
}


class TopicClassifier:
    """LDA 主题分类器 + 关键词词典自动命名"""

    def __init__(self, n_topics: int = 8, random_state: int = 42):
        self.n_topics = n_topics
        self.random_state = random_state

    def classify(self, events: list[dict]) -> list[dict]:
        """
        对事件列表进行主题分类。
        每个 event 需含字段: event_id, title, keywords (list[str])
        返回带 category 字段的 event 列表。
        """
        n = len(events)
        if n == 0:
            return events

        # 太少事件时退回纯关键词规则
        if n < 20:
            return self._classify_by_keyword_rules(events)

        return self._classify_by_lda(events)

    def _classify_by_lda(self, events: list[dict]) -> list[dict]:
        """LDA + 关键词词典混合分类"""
        n_events = len(events)
        # 主题数不超过事件数
        n_topics = min(self.n_topics, max(2, n_events // 2))

        # 构建文档：每个事件 = title + keywords 的组合文本
        raw_docs = []
        for e in events:
            title = e.get("title", "") or ""
            keywords = " ".join(e.get("keywords", []) or [])
            raw_docs.append(f"{title} {keywords}")

        # jieba 分词 + 去停用词
        docs = []
        for doc in raw_docs:
            words = jieba.lcut(doc)
            words = [w.strip() for w in words if w.strip() and w.strip() not in STOP_WORDS and len(w.strip()) > 1]
            docs.append(" ".join(words))

        # 过滤空文档
        valid_indices = [i for i, d in enumerate(docs) if d.strip()]
        if len(valid_indices) < 3:
            return self._classify_by_keyword_rules(events)

        valid_docs = [docs[i] for i in valid_indices]

        try:
            vectorizer = CountVectorizer(max_df=0.8, min_df=1, max_features=5000)
            dtm = vectorizer.fit_transform(valid_docs)
        except ValueError:
            return self._classify_by_keyword_rules(events)

        # 确保特征数足够
        n_features = dtm.shape[1]
        if n_features < 3:
            return self._classify_by_keyword_rules(events)

        lda = LatentDirichletAllocation(
            n_components=n_topics,
            random_state=self.random_state,
            max_iter=10,
            learning_method="online",
            batch_size=32,
        )
        lda.fit(dtm)

        # 提取每个 topic 的 top 词
        feature_names = vectorizer.get_feature_names_out()
        topic_labels = self._name_topics(lda, feature_names, n_topics)

        # 为每个有效 event 分配 category
        topic_dist = lda.transform(dtm)  # (n_docs, n_topics)
        topic_map = {}  # valid_idx -> category
        for i, idx in enumerate(valid_indices):
            top_topic = int(topic_dist[i].argmax())
            topic_map[idx] = topic_labels[top_topic]

        # 为所有 event 赋值（无效的给默认值）
        for i, event in enumerate(events):
            event["category"] = topic_map.get(i, "社会民生")

        return events

    def _classify_by_keyword_rules(self, events: list[dict]) -> list[dict]:
        """纯关键词规则匹配（降级方案）"""
        for event in events:
            keywords = event.get("keywords", []) or []
            title = event.get("title", "") or ""
            text = title + " " + " ".join(keywords)

            if not text.strip():
                event["category"] = "其他"
                continue

            words = set(jieba.lcut(text))
            best_cat = "其他"
            best_score = 0
            for cat, cat_keywords in CATEGORY_KEYWORDS.items():
                score = sum(1 for kw in cat_keywords if kw in words)
                if score > best_score:
                    best_score = score
                    best_cat = cat
            event["category"] = best_cat if best_score > 0 else "其他"

        return events

    def _name_topics(self, lda, feature_names, n_topics: int) -> list[str]:
        """用关键词词典自动给 LDA 主题命名"""
        labels = []
        for topic_idx in range(n_topics):
            top_indices = lda.components_[topic_idx].argsort()[::-1][:20]
            top_words = [feature_names[i] for i in top_indices]

            best_cat = "其他"
            best_score = 0
            for cat, cat_keywords in CATEGORY_KEYWORDS.items():
                score = sum(1 for w in top_words if w in cat_keywords)
                if score > best_score:
                    best_score = score
                    best_cat = cat

            labels.append(best_cat)

        return labels
