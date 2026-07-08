import re
import jieba
import jieba.analyse
from bs4 import BeautifulSoup

from src.model.clean_model import CleanRequest, CleanResponse


class CleanService:

    def clean(self, request: CleanRequest) -> CleanResponse:
        content = request.content or ""

        # 1. 去噪: 去除 HTML 标签
        content = self._strip_html(content)

        # 2. 标准化: 移除多余空行、特殊字符
        content = self._normalize(content)

        # 3. 分词 & 关键词提取
        keywords = self._extract_keywords(content, topk=10)
        summary = content[:200] if len(content) > 200 else content

        return CleanResponse(
            title=request.title,
            content=content,
            keywords=",".join(keywords),
            summary=summary,
            language=request.language or "zh",
            status="CLEANED"
        )

    def _strip_html(self, text: str) -> str:
        soup = BeautifulSoup(text, "html.parser")
        return soup.get_text(separator="\n")

    def _normalize(self, text: str) -> str:
        # 合并多个空白行
        text = re.sub(r'\n\s*\n', '\n', text)
        # 合并多个空格
        text = re.sub(r'[ \t]+', ' ', text)
        # 去除行首行尾空白
        text = '\n'.join(line.strip() for line in text.splitlines() if line.strip())
        return text.strip()

    def _extract_keywords(self, text: str, topk: int = 10) -> list[str]:
        # TF-IDF 关键词提取
        return jieba.analyse.extract_tags(text, topK=topk)
