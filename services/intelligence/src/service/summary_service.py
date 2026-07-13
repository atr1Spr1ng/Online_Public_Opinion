import json
import re
import logging

from src.config import EventSummaryConfig, EVENT_SUMMARY_SYSTEM_PROMPT
from src.model.summary_model import EventSummaryRequest, EventSummaryResponse

logger = logging.getLogger(__name__)


class SummaryService:
    """事件摘要服务 — LLM + 降级"""

    def __init__(self):
        self._client = None

    @property
    def client(self):
        if self._client is None and EventSummaryConfig.enabled():
            try:
                from openai import OpenAI
                self._client = OpenAI(
                    api_key=EventSummaryConfig.api_key,
                    base_url=EventSummaryConfig.base_url,
                )
                logger.info("EventSummary LLM client initialized")
            except Exception as e:
                logger.warning("Failed to init EventSummary LLM client: %s", e)
        return self._client

    def summarize(self, request: EventSummaryRequest) -> EventSummaryResponse:
        # Attempt LLM
        if EventSummaryConfig.enabled() and self.client:
            llm_result = self._call_llm(request)
            if llm_result is not None:
                return llm_result

        # Fallback
        return self._fallback_summary(request)

    def _call_llm(self, request: EventSummaryRequest) -> EventSummaryResponse | None:
        try:
            articles_text = ""
            for i, a in enumerate(request.articles):
                articles_text += (
                    f"[{i + 1}] 标题：{a.title}\n"
                    f"    来源：{a.source_name}\n"
                    f"    时间：{a.published_at}\n"
                    f"    摘要：{a.summary}\n\n"
                )

            user_content = (
                f"事件标题：{request.event_title}\n"
                f"事件关键词：{request.event_keywords}\n\n"
                f"相关报道：\n{articles_text}\n"
                f"请根据以上信息提取事件概述，严格按 JSON 格式回复。"
            )

            response = self.client.chat.completions.create(
                model=EventSummaryConfig.model,
                messages=[
                    {"role": "system", "content": EVENT_SUMMARY_SYSTEM_PROMPT},
                    {"role": "user", "content": user_content}
                ],
                temperature=0.3,
                max_tokens=2000,
            )
            content = response.choices[0].message.content
            return self._parse_response(content)

        except Exception as e:
            logger.error("EventSummary LLM call failed: %s", e)
            return None

    def _parse_response(self, content: str) -> EventSummaryResponse:
        """解析 LLM 返回的 JSON，regex 兜底"""
        try:
            # 尝试查找 JSON 块
            json_match = re.search(r'\{[\s\S]*\}', content)
            if json_match:
                data = json.loads(json_match.group())
                return EventSummaryResponse(
                    summary=data.get("summary", ""),
                    time=data.get("time", ""),
                    location=data.get("location", ""),
                    cause=data.get("cause", ""),
                    persons=data.get("persons", ""),
                    key_steps=data.get("key_steps", ""),
                    important_info=data.get("important_info", ""),
                    method="llm",
                )
        except (json.JSONDecodeError, KeyError) as e:
            logger.warning("Failed to parse LLM JSON: %s, raw: %s", e, content[:200])

        # Regex fallback: 尝试在句子边界截断，避免中间切断
        result = EventSummaryResponse(method="llm")
        raw = content.strip() if content else ""
        if len(raw) > 500:
            cut = raw[:500]
            # 在最后一个句号、问号、感叹号或换行处截断
            for sep in ("。", "！", "？", "\n", "；", "，"):
                idx = cut.rfind(sep)
                if idx > 300:
                    raw = cut[:idx + 1]
                    break
            else:
                raw = cut
        result.summary = raw

        patterns = {
            "time": r"(?:时间|发生时间)[：:]\s*(.+?)(?:\n|$)",
            "location": r"(?:地点|发生地点|事发地点)[：:]\s*(.+?)(?:\n|$)",
            "cause": r"(?:起因|原因|事件起因)[：:]\s*(.+?)(?:\n|$)",
            "persons": r"(?:人物|涉事人物|相关人物)[：:]\s*(.+?)(?:\n|$)",
            "key_steps": r"(?:关键步骤|事件经过|主要过程)[：:]\s*(.+?)(?:\n|$)",
            "important_info": r"(?:重要信息|补充信息)[：:]\s*(.+?)",
        }

        for field, pattern in patterns.items():
            match = re.search(pattern, content)
            if match:
                setattr(result, field, match.group(1).strip())

        return result

    def _fallback_summary(self, request: EventSummaryRequest) -> EventSummaryResponse:
        """降级：用关键词 + 标题拼段落"""
        article_count = len(request.articles)
        title = request.event_title or "未命名事件"
        keywords = request.event_keywords or "暂无关键词"

        sources = set()
        for a in request.articles:
            if a.source_name:
                sources.add(a.source_name)

        parts = [
            f"该事件为「{title}」，核心关键词包括 {keywords}。",
            f"共涉及 {article_count} 篇相关报道。",
        ]
        if sources:
            parts.append(f"信息来源覆盖 {', '.join(sorted(sources))} 等平台。")

        return EventSummaryResponse(
            summary="\n".join(parts),
            time="",
            location="",
            cause="",
            persons="",
            key_steps="",
            important_info="",
            method="fallback",
        )
