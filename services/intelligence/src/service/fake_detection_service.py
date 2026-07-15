import json
import re
import jieba
import logging

from src.model.fake_detection_model import (
    FakeDetectionRequest,
    FakeDetectionResponse,
    FakeDetectionFeature,
)
from src.config import FakeDetectionConfig, FAKE_DETECTION_SYSTEM_PROMPT

logger = logging.getLogger(__name__)

# ── 煽情/诱导性词汇 ──────────────────────────────────────────────

SENSATIONAL_WORDS: set[str] = {
    "震惊", "速看", "紧急", "最新", "刚刚", "突发", "重磅",
    "绝密", "内幕", "曝光", "揭秘", "真相", "黑幕", "暗箱",
    "惊人", "可怕", "恐怖", "吓人", "骇人", "不可思议",
    "必看", "赶紧", "马上", "立即", "速转", "转发",
    "删前速看", "不转不是", "惊天", "轰动", "爆炸性",
    "独家", "首次", "史无前例", "前所未有", "触目惊心",
    "崩溃", "完蛋", "毁灭", "末日", "灾难",
}

# ── 标题党模式 ──────────────────────────────────────────────────

CLICKBAIT_PATTERNS: list[re.Pattern] = [
    re.compile(r"震惊[！!]"),
    re.compile(r"不看后悔"),
    re.compile(r"删前"),
    re.compile(r"[！!]{2,}"),
    re.compile(r"[？?]{3,}"),
    re.compile(r"出大事了"),
    re.compile(r"全网.*疯传"),
    re.compile(r"朋友圈.*刷屏"),
    re.compile(r"再不看就晚了"),
]

# ── 信息源标记词 ────────────────────────────────────────────────

SOURCE_INDICATORS: set[str] = {
    "据", "报道", "记者从", "记者从", "新华社", "央视",
    "人民日报", "环球时报", "发布", "通报", "公告",
    "声明", "回应", "表示", "称", "指出", "透露",
    "介绍", "披露", "证实", "否认", "援引",
}

LOW_CONFIDENCE_RULE_MIN = 0.35
LOW_CONFIDENCE_RULE_MAX = 0.65


class FakeDetectionService:
    """虚假文本检测 — 规则引擎 + LLM"""

    def __init__(self):
        self._client = None

    @property
    def client(self):
        if self._client is None and FakeDetectionConfig.enabled():
            try:
                from openai import OpenAI
                self._client = OpenAI(
                    api_key=FakeDetectionConfig.api_key,
                    base_url=FakeDetectionConfig.base_url,
                )
                logger.info("FakeDetection LLM client initialized")
            except Exception as e:
                logger.warning("Failed to init FakeDetection LLM client: %s", e)
        return self._client

    def detect(self, request: FakeDetectionRequest) -> FakeDetectionResponse:
        text = f"{request.title} {request.content}".strip()
        if not text:
            return FakeDetectionResponse(
                fake_score=0.0,
                is_fake=False,
                detection_method="rule",
                features=[],
                details="empty text",
            )

        # ── Step 1: 规则特征提取 ──
        features: list[FakeDetectionFeature] = []
        scores: list[float] = []

        # 特征1: 情绪煽动指数
        f_sensational = self._sensational_score(text)
        features.append(f_sensational)
        scores.append(f_sensational.score)

        # 特征2: 标题党检测
        f_clickbait = self._clickbait_score(request.title)
        features.append(f_clickbait)
        scores.append(f_clickbait.score)

        # 特征3: 信息源缺失
        f_source = self._source_missing_score(text)
        features.append(f_source)
        scores.append(f_source.score)

        # 特征4: 夸张标点/格式
        f_format = self._exaggerated_format_score(text)
        features.append(f_format)
        scores.append(f_format.score)

        # 综合规则分数（加权平均）
        weights = [0.30, 0.25, 0.30, 0.15]
        rule_score = sum(s * w for s, w in zip(scores, weights))
        rule_score = round(min(rule_score, 1.0), 4)
        is_fake_by_rule = rule_score >= 0.5

        details_parts = [
            f"规则综合得分: {rule_score:.4f}",
            f"情绪煽动: {f_sensational.score:.4f}",
            f"标题党: {f_clickbait.score:.4f}",
            f"信息源缺失: {f_source.score:.4f}",
            f"夸张格式: {f_format.score:.4f}",
        ]

        mode = (request.mode or "").lower()
        should_call_llm = (
            mode != "fast"
            and FakeDetectionConfig.enabled()
            and self.client
            and (
                mode != "auto"
                or LOW_CONFIDENCE_RULE_MIN <= rule_score <= LOW_CONFIDENCE_RULE_MAX
            )
        )

        # ── Step 2: LLM 文本质量评估（精准模式直接用；自动模式低置信度兜底） ──
        if should_call_llm:
            llm_result = self._call_llm(text)
            if llm_result is not None:
                # 从文本质量维度推导虚假分数：
                # 缺乏来源 + 逻辑矛盾 + 情绪煽动 + 信息不完整 → 更可能是虚假
                src = llm_result.get("source_citation", 0.5)
                coh = llm_result.get("logical_coherence", 0.5)
                emo = llm_result.get("emotional_manipulation", 0.5)
                inf = llm_result.get("information_completeness", 0.5)
                analysis = llm_result.get("analysis", "")

                llm_fake_score = round(
                    (1 - src) * 0.35 + (1 - coh) * 0.30 + emo * 0.20 + (1 - inf) * 0.15, 4
                )
                # 混合分数: 规则50% + LLM 50%
                hybrid_score = round(rule_score * 0.5 + llm_fake_score * 0.5, 4)
                is_hybrid_fake = hybrid_score >= 0.5
                details_parts.append(f"LLM文本质量-来源引用: {src:.2f}, 逻辑: {coh:.2f}, 情绪: {emo:.2f}, 信息完整: {inf:.2f}")
                if mode == "auto":
                    details_parts.append(f"自动模式低置信度LLM兜底: 规则分数 {rule_score:.4f}")
                details_parts.append(f"LLM分析: {analysis}")
                details_parts.append(f"LLM推导虚假分: {llm_fake_score:.4f}")
                details_parts.append(f"混合得分: {hybrid_score:.4f}")

                return FakeDetectionResponse(
                    fake_score=hybrid_score,
                    is_fake=is_hybrid_fake,
                    detection_method="hybrid",
                    features=features,
                    details=" | ".join(details_parts),
                )

        if mode == "auto" and LOW_CONFIDENCE_RULE_MIN <= rule_score <= LOW_CONFIDENCE_RULE_MAX:
            details_parts.append("自动模式低置信度，但LLM不可用或调用失败，使用规则结果")

        # ── 降级: 纯规则结果 ──
        return FakeDetectionResponse(
            fake_score=rule_score,
            is_fake=is_fake_by_rule,
            detection_method="rule",
            features=features,
            details=" | ".join(details_parts),
        )

    # ── 特征计算 ────────────────────────────────────────────────

    def _sensational_score(self, text: str) -> FakeDetectionFeature:
        """情绪煽动指数: 煽情词密度 / 情感词密度"""
        words = list(jieba.cut(text))
        words = [w.strip() for w in words if w.strip()]
        if not words:
            return FakeDetectionFeature(name="sensational", score=0.0, description="空文本")

        sensational_count = sum(1 for w in words if w in SENSATIONAL_WORDS)
        density = sensational_count / len(words)
        # 密度 > 5% → 高可疑
        score = min(density / 0.05, 1.0)
        return FakeDetectionFeature(
            name="sensational",
            score=round(score, 4),
            description=f"煽情词{sensational_count}个, 密度{round(density*100,1)}%",
        )

    def _clickbait_score(self, title: str) -> FakeDetectionFeature:
        """标题党检测"""
        if not title:
            return FakeDetectionFeature(name="clickbait", score=0.0, description="无标题")

        match_count = 0
        matched_patterns: list[str] = []
        for pattern in CLICKBAIT_PATTERNS:
            if pattern.search(title):
                match_count += 1
                matched_patterns.append(pattern.pattern)

        # 检查标题长度（过长或过短都可疑）
        title_len = len(title)
        len_suspicious = title_len > 50 or title_len < 5

        score = min((match_count * 0.3) + (0.2 if len_suspicious else 0), 1.0)
        desc = f"匹配{match_count}个标题党模式"
        if matched_patterns:
            desc += f": {', '.join(matched_patterns)}"
        return FakeDetectionFeature(name="clickbait", score=round(score, 4), description=desc)

    def _source_missing_score(self, text: str) -> FakeDetectionFeature:
        """信息源缺失指数: 文中是否有可验证的信息源引用"""
        if len(text) < 50:
            return FakeDetectionFeature(name="source_missing", score=0.3, description="文本过短")

        words = list(jieba.cut(text))
        words = [w.strip() for w in words if w.strip()]

        source_hits = sum(1 for w in words if w in SOURCE_INDICATORS)

        # 按每百字的信息源标记密度计算
        char_count = len(text)
        density_per_100 = source_hits / (char_count / 100) if char_count > 0 else 0

        # 每百字 < 0.5 个信息源标记 → 高度可疑
        if density_per_100 < 0.5:
            score = 0.8
        elif density_per_100 < 1.0:
            score = 0.5
        elif density_per_100 < 2.0:
            score = 0.3
        else:
            score = 0.1

        return FakeDetectionFeature(
            name="source_missing",
            score=round(score, 4),
            description=f"信息源标记{source_hits}个, 密度{density_per_100:.2f}/百字",
        )

    def _exaggerated_format_score(self, text: str) -> FakeDetectionFeature:
        """夸张格式检测: 感叹号、问号、全部大写词汇"""
        exclamation_ratio = text.count("！") + text.count("!")
        question_ratio = text.count("？") + text.count("?")

        # 全部大写词汇（中文语境下较少见，但英文内容可能有）
        all_caps_count = len(re.findall(r'[A-Z]{4,}', text))

        total_chars = max(len(text), 1)
        exclamation_density = exclamation_ratio / (total_chars / 100)
        question_density = question_ratio / (total_chars / 100)

        score = 0.0
        parts = []
        if exclamation_density > 3:
            score += 0.4
            parts.append(f"感叹号密度高({exclamation_density:.1f}/百字)")
        if question_density > 3:
            score += 0.3
            parts.append(f"问号密度高({question_density:.1f}/百字)")
        if all_caps_count > 2:
            score += 0.3
            parts.append(f"全大写词{all_caps_count}个")

        score = min(score, 1.0)
        desc = "; ".join(parts) if parts else "格式正常"
        return FakeDetectionFeature(name="exaggerated_format", score=round(score, 4), description=desc)

    # ── LLM 调用 ──────────────────────────────────────────────────

    def _call_llm(self, text: str) -> dict | None:
        """调用 DeepSeek 评估文本质量维度，返回 {source_citation, logical_coherence, ...}"""
        try:
            truncated = text[:2000] if len(text) > 2000 else text

            response = self.client.chat.completions.create(
                model=FakeDetectionConfig.model,
                messages=[
                    {"role": "system", "content": FAKE_DETECTION_SYSTEM_PROMPT},
                    {"role": "user", "content": f"请评估以下新闻文本的写作质量:\n\n{truncated}"}
                ],
                temperature=0.3,
                max_tokens=300,
            )
            content = response.choices[0].message.content
            return self._parse_llm_response(content)

        except Exception as e:
            logger.error("FakeDetection LLM call failed: %s", e)
            return None

    def _parse_llm_response(self, content: str) -> dict:
        """解析 LLM 返回的文本质量维度评分"""
        result = {
            "source_citation": 0.5,
            "logical_coherence": 0.5,
            "emotional_manipulation": 0.5,
            "information_completeness": 0.5,
            "analysis": "",
        }
        try:
            json_match = re.search(r'\{[\s\S]*\}', content)
            if json_match:
                data = json.loads(json_match.group())
                for key in result:
                    if key in data and key != "analysis":
                        result[key] = max(0.0, min(1.0, float(data[key])))
                result["analysis"] = str(data.get("analysis", ""))
        except (json.JSONDecodeError, ValueError, KeyError):
            pass
        return result
