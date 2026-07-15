import json
import os
import re
import logging
import jieba

from src.model.sentiment_model import SentimentRequest, SentimentResponse, SentimentWord
from src.config import SENTIMENT_ANALYSIS_SYSTEM_PROMPT

logger = logging.getLogger(__name__)


# ── 中文情感词典（内嵌） ──────────────────────────────────────────────

POSITIVE_WORDS: set[str] = {
    "好", "优秀", "出色", "棒", "赞", "完美", "精彩", "杰出", "卓越",
    "高兴", "快乐", "幸福", "满意", "喜欢", "热爱", "喜悦", "开心", "愉快",
    "成功", "胜利", "进步", "发展", "增长", "提升", "突破", "创新",
    "积极", "正面", "乐观", "向上", "进取", "奋发", "昂扬",
    "强大", "稳定", "安全", "可靠", "优质", "高效", "便捷", "舒适",
    "领先", "先进", "第一", "前列", "一流", "顶尖", "顶级",
    "和谐", "美好", "美丽", "繁荣", "昌盛", "兴旺", "辉煌",
    "公正", "公平", "公开", "透明", "廉洁", "诚信", "正直",
    "团结", "合作", "共赢", "友好", "和平", "协作", "互助",
    "关爱", "温暖", "感动", "感恩", "珍惜", "尊重", "理解",
    "支持", "拥护", "赞成", "认可", "赞赏", "赞扬", "表扬",
    "改善", "优化", "完善", "增强", "加强", "推进", "促进",
    "顺利", "圆满", "理想", "达标", "实现", "达成", "完成",
    "增长", "上涨", "回升", "复苏", "利好", "收益", "盈利",
    "丰富", "充足", "充沛", "雄厚", "坚实", "稳固",
    "清晰", "明确", "规范", "有序", "科学", "合理", "务实",
    "健康", "绿色", "环保", "可持续", "生态", "节能",
    "智能", "数字", "信息化", "现代化", "高科技", "自动化",
    "普惠", "便民", "惠民", "利民", "亲民", "为民",
    "光荣", "荣誉", "骄傲", "自豪", "尊严", "荣耀",
}

NEGATIVE_WORDS: set[str] = {
    "差", "坏", "劣", "糟", "烂", "恶", "惨", "糟糕", "恶劣", "败坏",
    "失败", "损失", "下滑", "下降", "衰退", "萎缩", "倒退", "落后",
    "消极", "负面", "悲观", "失望", "绝望", "沮丧", "消沉", "低迷",
    "愤怒", "怨恨", "憎恨", "仇恨", "厌恶", "反感", "不满", "抱怨",
    "恐惧", "害怕", "恐慌", "焦虑", "担忧", "紧张", "不安",
    "困难", "艰难", "困境", "危机", "风险", "威胁", "挑战",
    "腐败", "贪污", "受贿", "滥用", "违规", "违法", "犯罪",
    "欺骗", "欺诈", "虚假", "伪造", "造假", "谎言", "骗局",
    "暴力", "冲突", "战争", "攻击", "破坏", "摧毁", "恐怖",
    "污染", "破坏", "损害", "危害", "威胁", "恶化", "毒害",
    "不公平", "不公正", "歧视", "偏见", "压迫", "剥削",
    "冷漠", "无情", "残酷", "残忍", "狠心", "恶劣",
    "混乱", "无序", "杂乱", "繁杂", "繁琐", "麻烦",
    "低效", "缓慢", "滞后", "拖延", "延误", "耽搁",
    "缺乏", "不足", "短缺", "匮乏", "紧缺", "稀缺",
    "浪费", "挥霍", "奢侈", "过度", "过分", "极端",
    "虚假", "浮夸", "夸大", "炒作", "造假", "欺骗",
    "漏洞", "缺陷", "隐患", "弊端", "毛病", "短板",
    "事故", "灾难", "灾祸", "灾害", "危机", "危急",
    "投诉", "举报", "曝光", "谴责", "抗议", "抵制",
    "失业", "下岗", "裁员", "减薪", "贫困", "困难",
    "疾病", "疫情", "病毒", "传染", "感染", "死亡",
    "事故", "爆炸", "火灾", "倒塌", "泄漏", "坠毁",
    "暴跌", "崩盘", "危机", "泡沫", "亏损", "债务",
    "侵权", "盗版", "抄袭", "剽窃", "山寨", "仿冒",
    "丑闻", "风波", "争议", "纠纷", "矛盾", "冲突",
    "分裂", "对立", "对抗", "割裂", "撕裂", "分化",
    "冷漠", "麻木", "无视", "忽视", "漠视", "轻视",
    "尴尬", "难堪", "丢脸", "耻辱", "羞耻", "可耻",
    "荒唐", "荒谬", "可笑", "幼稚", "愚蠢", "愚昧",
}

# 否定词
NEGATION_WORDS: set[str] = {
    "不", "没", "无", "非", "未", "莫", "勿", "休", "别",
    "没有", "并非", "绝不", "决不", "从不", "毫无",
}

# 程度副词（带权重）
DEGREE_ADVERBS: dict[str, float] = {
    # 极量 (2.5x)
    "极其": 2.5, "极度": 2.5, "最为": 2.5,
    "非常": 2.5, "异常": 2.5, "十分": 2.5, "万分": 2.5,
    # 高量 (2.0x)
    "很": 2.0, "太": 2.0, "多么": 2.0,
    "特别": 2.0, "尤其": 2.0, "格外": 2.0, "相当": 2.0,
    "颇为": 2.0, "挺": 2.0, "真": 2.0, "够": 2.0,
    "蛮": 2.0, "老": 2.0,
    # 中量 (1.5x)
    "比较": 1.5, "较": 1.5, "较为": 1.5,
    "还": 1.5, "更": 1.5, "更加": 1.5, "更为": 1.5,
    "越发": 1.5, "愈加": 1.5, "愈发": 1.5,
    # 低量 (0.5x)
    "稍微": 0.5, "稍稍": 0.5, "略微": 0.5, "略": 0.5,
    "有点": 0.5, "有些": 0.5, "一点儿": 0.5, "不大": 0.5,
}

# 否定窗口大小（否定词影响范围内）
NEGATION_WINDOW = 3
LOW_CONFIDENCE_LLM_THRESHOLD = 0.45


class SentimentService:
    """情感分析：LLM → 字典 两级降级"""

    def __init__(self):
        self._llm_client = None

    @property
    def _llm(self):
        if self._llm_client is None:
            api_key = os.getenv("DEEPSEEK_API_KEY", "")
            if api_key:
                try:
                    from openai import OpenAI
                    self._llm_client = OpenAI(
                        api_key=api_key,
                        base_url=os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com"),
                    )
                    logger.info("SentimentService LLM client initialized")
                except Exception as e:
                    logger.warning("Failed to init SentimentService LLM client: %s", e)
        return self._llm_client

    def analyze(self, request: SentimentRequest) -> SentimentResponse:
        text = f"{request.title} {request.content}".strip()
        if not text:
            return SentimentResponse(
                sentiment="NEUTRAL",
                positive_score=0.0,
                negative_score=0.0,
                confidence=0.0,
                details="empty text"
            )

        mode = (request.mode or "").lower()

        # 自动模式：先快速字典判断，低置信度再调用 LLM 兜底。
        if mode == "auto":
            dictionary_result = self._analyze_by_dictionary(text)
            if dictionary_result.confidence < LOW_CONFIDENCE_LLM_THRESHOLD:
                llm_result = self._analyze_by_llm(text)
                if llm_result is not None:
                    llm_result.details = self._merge_details(
                        llm_result.details,
                        f"自动模式低置信度LLM兜底；字典置信度={dictionary_result.confidence:.4f}"
                    )
                    return llm_result
                dictionary_result.details = self._merge_details(
                    dictionary_result.details,
                    f"自动模式低置信度，但LLM不可用，使用字典结果；置信度={dictionary_result.confidence:.4f}"
                )
            return dictionary_result

        # 精准模式：LLM 情感分析优先，失败后降级字典。
        if mode != "fast":
            llm_result = self._analyze_by_llm(text)
            if llm_result is not None:
                return llm_result

        # 快速模式 / 精准模式降级：字典匹配
        logger.info("Sentiment: using dictionary mode")
        return self._analyze_by_dictionary(text)

    def _merge_details(self, original: str, note: str) -> str:
        if not original:
            return note
        return f"{original} | {note}"

    def _analyze_by_llm(self, text: str) -> SentimentResponse | None:
        """LLM 零样本情感分析，失败返回 None"""
        if not self._llm:
            return None

        try:
            truncated = text[:2000] if len(text) > 2000 else text
            response = self._llm.chat.completions.create(
                model=os.getenv("DEEPSEEK_MODEL", "deepseek-chat"),
                messages=[
                    {"role": "system", "content": SENTIMENT_ANALYSIS_SYSTEM_PROMPT},
                    {"role": "user", "content": truncated}
                ],
                temperature=0.2,
                max_tokens=200,
            )
            content = response.choices[0].message.content

            json_match = re.search(r'\{[\s\S]*\}', content)
            if json_match:
                data = json.loads(json_match.group())
                sentiment = data.get("sentiment", "NEUTRAL")
                if sentiment not in ("POSITIVE", "NEGATIVE", "NEUTRAL"):
                    sentiment = "NEUTRAL"
                return SentimentResponse(
                    sentiment=sentiment,
                    positive_score=float(data.get("positive_score", 0)),
                    negative_score=float(data.get("negative_score", 0)),
                    confidence=float(data.get("confidence", 0)),
                    details=data.get("reason", ""),
                )

        except Exception as e:
            logger.error("LLM sentiment analysis failed: %s", e)
        return None

    def _analyze_by_dictionary(self, text: str) -> SentimentResponse:

        words = list(jieba.cut(text))
        words = [w.strip() for w in words if w.strip()]

        positive_hits: list[SentimentWord] = []
        negative_hits: list[SentimentWord] = []
        pos_score = 0.0
        neg_score = 0.0

        n = len(words)
        i = 0
        while i < n:
            word = words[i]

            if word in NEGATION_WORDS:
                # 在窗口内查找情感词并翻转极性
                window_end = min(i + 1 + NEGATION_WINDOW, n)
                negation_found = False
                for j in range(i + 1, window_end):
                    candidate = words[j]
                    degree_weight = 1.0
                    # 跳过程度副词
                    if candidate in DEGREE_ADVERBS:
                        degree_weight = DEGREE_ADVERBS[candidate]
                        continue
                    if candidate in POSITIVE_WORDS:
                        neg_score += 1.0 * degree_weight
                        negative_hits.append(SentimentWord(word=candidate, sentiment="NEGATIVE", weight=degree_weight))
                        negation_found = True
                        break
                    elif candidate in NEGATIVE_WORDS:
                        pos_score += 1.0 * degree_weight
                        positive_hits.append(SentimentWord(word=candidate, sentiment="POSITIVE", weight=degree_weight))
                        negation_found = True
                        break
                i += 1
                continue

            if word in DEGREE_ADVERBS:
                degree_weight = DEGREE_ADVERBS[word]
                # 查找下一个情感词
                for j in range(i + 1, min(i + 3, n)):
                    nxt = words[j]
                    if nxt in POSITIVE_WORDS:
                        # 检查前面是否有否定词
                        negated = self._has_negation_before(words, i)
                        if negated:
                            neg_score += 1.0 * degree_weight
                            negative_hits.append(SentimentWord(word=nxt, sentiment="NEGATIVE", weight=degree_weight))
                        else:
                            pos_score += 1.0 * degree_weight
                            positive_hits.append(SentimentWord(word=nxt, sentiment="POSITIVE", weight=degree_weight))
                        i = j + 1
                        break
                    elif nxt in NEGATIVE_WORDS:
                        negated = self._has_negation_before(words, i)
                        if negated:
                            pos_score += 1.0 * degree_weight
                            positive_hits.append(SentimentWord(word=nxt, sentiment="POSITIVE", weight=degree_weight))
                        else:
                            neg_score += 1.0 * degree_weight
                            negative_hits.append(SentimentWord(word=nxt, sentiment="NEGATIVE", weight=degree_weight))
                        i = j + 1
                        break
                else:
                    i += 1
                continue

            if word in POSITIVE_WORDS:
                negated = self._has_negation_before(words, i)
                if negated:
                    neg_score += 1.0
                    negative_hits.append(SentimentWord(word=word, sentiment="NEGATIVE", weight=1.0))
                else:
                    pos_score += 1.0
                    positive_hits.append(SentimentWord(word=word, sentiment="POSITIVE", weight=1.0))
                i += 1
                continue

            if word in NEGATIVE_WORDS:
                negated = self._has_negation_before(words, i)
                if negated:
                    pos_score += 1.0
                    positive_hits.append(SentimentWord(word=word, sentiment="POSITIVE", weight=1.0))
                else:
                    neg_score += 1.0
                    negative_hits.append(SentimentWord(word=word, sentiment="NEGATIVE", weight=1.0))
                i += 1
                continue

            i += 1

        return self._build_response(pos_score, neg_score, positive_hits, negative_hits)

    def _has_negation_before(self, words: list[str], idx: int) -> bool:
        """检查 idx 位置前 NEGATION_WINDOW 内是否有否定词"""
        start = max(0, idx - NEGATION_WINDOW)
        for k in range(start, idx):
            if words[k] in NEGATION_WORDS:
                return True
        return False

    def _build_response(
        self,
        pos_score: float,
        neg_score: float,
        positive_hits: list[SentimentWord],
        negative_hits: list[SentimentWord],
    ) -> SentimentResponse:
        total = pos_score + neg_score
        if total == 0:
            return SentimentResponse(
                sentiment="NEUTRAL",
                positive_score=0.0,
                negative_score=0.0,
                confidence=0.0,
                positive_words=[],
                negative_words=[],
                details="no sentiment words found"
            )

        # 归一化
        pos_norm = round(pos_score / total, 4)
        neg_norm = round(neg_score / total, 4)
        confidence = round(abs(pos_score - neg_score) / total, 4)

        # 判断倾向（带偏置：需要明显优势才判定为非中立）
        threshold = 0.1
        if pos_score > neg_score + threshold * total:
            sentiment = "POSITIVE"
        elif neg_score > pos_score + threshold * total:
            sentiment = "NEGATIVE"
        else:
            sentiment = "NEUTRAL"

        details = json.dumps({
            "positive_count": len(positive_hits),
            "negative_count": len(negative_hits),
            "positive_raw_score": round(pos_score, 2),
            "negative_raw_score": round(neg_score, 2),
            "threshold": threshold,
        }, ensure_ascii=False)

        return SentimentResponse(
            sentiment=sentiment,
            positive_score=pos_norm,
            negative_score=neg_norm,
            confidence=confidence,
            positive_words=positive_hits[:20],
            negative_words=negative_hits[:20],
            details=details,
        )
