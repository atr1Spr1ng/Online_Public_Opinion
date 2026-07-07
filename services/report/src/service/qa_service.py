import re
import json

from src.model.report_model import QaRequest, QaResponse, ReportData


# ── 关键词匹配规则 ────────────────────────────────────────────────
# 每条规则: (关键词正则, 回答模板生成函数)

def _extract_keywords(question: str) -> list[str]:
    """从问句中提取关键词"""
    keywords = ["情感", "情绪", "倾向", "正面", "负面", "中立",
                "时间", "开始", "结束", "什么时候", "多久",
                "热度", "热门", "关键词", "标签",
                "文章", "篇", "多少", "几篇",
                "生命周期", "阶段", "趋势",
                "概述", "总结", "概括"]
    found = [kw for kw in keywords if kw in question]
    return found


class QaService:
    """智能问答服务 — 关键词检索 + 大模型预留"""

    def answer(self, request: QaRequest) -> QaResponse:
        question = request.question.strip()
        report = request.report

        # 1. 尝试关键词匹配
        keyword_answer = self._match_keywords(question, report)
        if keyword_answer:
            return QaResponse(answer=keyword_answer, source="keyword")

        # 2. 关键词匹配失败 → 返回通用回答
        if report:
            summary = f"该事件「{report.title}」目前{report.lifecycle}，热度指数{report.hotness:.1f}，共{report.article_count}篇相关报道。"
            return QaResponse(answer=summary, source="keyword")
        else:
            return QaResponse(answer="抱歉，我没有足够的上下文来回答这个问题。请提供具体的事件信息。", source="keyword")

    def _match_keywords(self, question: str, report: ReportData | None) -> str | None:
        if not report:
            return None

        q = question.lower()

        # 情感倾向问答
        if any(kw in q for kw in ["情感", "情绪", "倾向", "正面", "负面", "中立", "态度"]):
            s = report.sentiment
            return (f"事件「{report.title}」的情感倾向分布："
                    f"正面 {s.positive:.0%}，负面 {s.negative:.0%}，中立 {s.neutral:.0%}。")

        # 时间问答
        if any(kw in q for kw in ["时间", "开始", "结束", "什么时候", "何时", "多久"]):
            if report.start_time and report.end_time:
                return f"事件「{report.title}」从 {report.start_time} 持续到 {report.end_time}。"
            elif report.start_time:
                return f"事件「{report.title}」起始于 {report.start_time}。"
            else:
                return f"事件「{report.title}」的时间信息暂不完整。"

        # 热度问答
        if any(kw in q for kw in ["热度", "热门", "关注"]):
            return f"事件「{report.title}」当前热度指数为 {report.hotness:.1f}，处于{report.lifecycle}。"

        # 关键词问答
        if any(kw in q for kw in ["关键词", "标签", "主题", "关键"]):
            return f"事件「{report.title}」的关键词：{report.keywords}。"

        # 文章数量
        if any(kw in q for kw in ["多少", "几篇", "数量", "文章", "报道", "篇"]):
            return f"事件「{report.title}」共包含 {report.article_count} 篇相关报道。"

        # 生命周期／阶段
        if any(kw in q for kw in ["生命周期", "阶段", "趋势", "状态"]):
            lifecycle_desc = {
                "潜伏期": "处于酝酿阶段，相关报道较少。",
                "成长期": "正在发酵，报道数量逐步上升。",
                "高潮期": "处于舆论高峰，社会关注度最高。",
                "衰退期": "热度正在下降，相关报道减少。",
            }
            desc = lifecycle_desc.get(report.lifecycle, "")
            return f"事件「{report.title}」当前处于{report.lifecycle}。{desc}"

        # 概述
        if any(kw in q for kw in ["概述", "总结", "概括", "介绍", "什么", "是什么"]):
            return (f"事件「{report.title}」：关键词 {report.keywords}，"
                    f"共 {report.article_count} 篇报道，热度 {report.hotness:.1f}，"
                    f"当前处于{report.lifecycle}。")

        return None

    # ── 大模型接口（预留） ──────────────────────────────────────────

    def answer_with_llm(self, request: QaRequest) -> QaResponse:
        """
        预留的大模型接口。未来接入大模型时替换实现。

        预期流程:
        1. 用 report 数据构造 System Prompt
        2. 将 question 作为 User Message
        3. 调用 LLM API (OpenAI / Claude / 本地模型)
        4. 返回 LLM 生成的回答

        示例:
            from openai import OpenAI
            client = OpenAI(base_url="...", api_key="...")
            system_prompt = f"你是一个舆情分析师。以下是事件报告：{json.dumps(report.dict(), ensure_ascii=False)}"
            response = client.chat.completions.create(
                model="gpt-4",
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": question}
                ]
            )
            return QaResponse(answer=response.choices[0].message.content, source="llm")
        """
        # 占位：降级为关键词匹配
        return self.answer(request)
