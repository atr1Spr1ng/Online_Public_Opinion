import json
import logging

from src.model.report_model import QaResponse, ReportData
from src.config import LLMConfig, SYSTEM_PROMPT

logger = logging.getLogger(__name__)


class QaService:
    """智能问答服务 — DeepSeek 大模型 + 关键词检索降级"""

    def __init__(self):
        self._client = None

    @property
    def client(self):
        if self._client is None and LLMConfig.enabled():
            try:
                from openai import OpenAI
                self._client = OpenAI(
                    api_key=LLMConfig.api_key,
                    base_url=LLMConfig.base_url,
                )
                logger.info("DeepSeek client initialized")
            except Exception as e:
                logger.warning("Failed to init DeepSeek client: %s", e)
        return self._client

    def answer_question(self, question: str, report: ReportData | None) -> QaResponse:
        question = question.strip()

        # 有报告上下文 + LLM 可用 → 使用大模型
        if report and LLMConfig.enabled() and self.client:
            llm_answer = self._call_llm(question, report)
            if llm_answer:
                return QaResponse(answer=llm_answer, source="llm")

        # 降级：关键词匹配
        keyword_answer = self._match_keywords(question, report)
        if keyword_answer:
            return QaResponse(answer=keyword_answer, source="keyword")

        # 再次降级：通用回答
        if report:
            summary = (f"该事件「{report.title}」目前{report.lifecycle}，"
                       f"热度指数{report.hotness:.1f}，共{report.article_count}篇相关报道。")
            return QaResponse(answer=summary, source="keyword")
        else:
            return QaResponse(
                answer="抱歉，我没有足够的上下文来回答这个问题。请提供具体的事件信息。",
                source="keyword"
            )

    def _call_llm(self, question: str, report: ReportData) -> str | None:
        try:
            # 构建报告上下文
            report_context = self._format_report_context(report)

            response = self.client.chat.completions.create(
                model=LLMConfig.model,
                messages=[
                    {"role": "system", "content": SYSTEM_PROMPT},
                    {"role": "user", "content": f"事件报告：\n{report_context}\n\n问题：{question}"}
                ],
                temperature=0.7,
                max_tokens=500,
            )
            return response.choices[0].message.content

        except Exception as e:
            logger.error("DeepSeek API call failed: %s", e)
            return None

    def _format_report_context(self, report: ReportData) -> str:
        parts = [
            f"事件标题：{report.title}",
            f"关键词：{report.keywords}",
            f"生命周期：{report.lifecycle}",
            f"时间范围：{report.start_time} 至 {report.end_time}",
            f"文章数量：{report.article_count}",
            f"热度指数：{report.hotness:.1f}",
            f"情感分布：正面{report.sentiment.positive:.0%} / "
            f"负面{report.sentiment.negative:.0%} / "
            f"中立{report.sentiment.neutral:.0%}",
        ]
        s = report.summary
        if s.summary or s.cause or s.key_steps:
            summary_parts = []
            if s.summary:
                summary_parts.append(f"事件概述：{s.summary}")
            if s.time:
                summary_parts.append(f"时间：{s.time}")
            if s.location:
                summary_parts.append(f"地点：{s.location}")
            if s.cause:
                summary_parts.append(f"起因：{s.cause}")
            if s.persons:
                summary_parts.append(f"涉事人物：{s.persons}")
            if s.key_steps:
                summary_parts.append(f"关键步骤：{s.key_steps}")
            if s.important_info:
                summary_parts.append(f"重要信息：{s.important_info}")
            if summary_parts:
                parts.append("--- AI 摘要 ---")
                parts.extend(summary_parts)
        if report.article_titles:
            parts.append(f"相关文章：{'; '.join(report.article_titles[:10])}")
        return "\n".join(parts)

    # ── 关键词匹配（LLM 不可用时的降级方案） ─────────────────────────

    def _match_keywords(self, question: str, report: ReportData | None) -> str | None:
        if not report:
            return None

        q = question.lower()

        if any(kw in q for kw in ["情感", "情绪", "倾向", "正面", "负面", "中立", "态度"]):
            s = report.sentiment
            return (f"事件「{report.title}」的情感倾向分布："
                    f"正面 {s.positive:.0%}，负面 {s.negative:.0%}，中立 {s.neutral:.0%}。")

        if any(kw in q for kw in ["时间", "开始", "结束", "什么时候", "何时", "多久"]):
            if report.start_time and report.end_time:
                return f"事件「{report.title}」从 {report.start_time} 持续到 {report.end_time}。"
            elif report.start_time:
                return f"事件「{report.title}」起始于 {report.start_time}。"
            else:
                return f"事件「{report.title}」的时间信息暂不完整。"

        if any(kw in q for kw in ["热度", "热门", "关注"]):
            return f"事件「{report.title}」当前热度指数为 {report.hotness:.1f}，处于{report.lifecycle}。"

        if any(kw in q for kw in ["关键词", "标签", "主题", "关键"]):
            return f"事件「{report.title}」的关键词：{report.keywords}。"

        if any(kw in q for kw in ["多少", "几篇", "数量", "文章", "报道", "篇"]):
            return f"事件「{report.title}」共包含 {report.article_count} 篇相关报道。"

        if any(kw in q for kw in ["生命周期", "阶段", "趋势", "状态"]):
            lifecycle_desc = {
                "潜伏期": "处于酝酿阶段，相关报道较少。",
                "成长期": "正在发酵，报道数量逐步上升。",
                "高潮期": "处于舆论高峰，社会关注度最高。",
                "衰退期": "热度正在下降，相关报道减少。",
            }
            desc = lifecycle_desc.get(report.lifecycle, "")
            return f"事件「{report.title}」当前处于{report.lifecycle}。{desc}"

        if any(kw in q for kw in ["概述", "总结", "概括", "介绍", "什么", "是什么"]):
            return (f"事件「{report.title}」：关键词 {report.keywords}，"
                    f"共 {report.article_count} 篇报道，热度 {report.hotness:.1f}，"
                    f"当前处于{report.lifecycle}。")

        return None
