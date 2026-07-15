import os
import re
import logging
import jieba
import jieba.analyse
from bs4 import BeautifulSoup
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from src.model.clean_model import CleanRequest, CleanResponse

logger = logging.getLogger(__name__)

# 短文本阈值：清洗后正文字数少于此值的文章标记为噪音
MIN_CONTENT_LENGTH = 20

COPYRIGHT_NOISE_TERMS = [
    "copyright",
    "all rights reserved",
    "版权所有",
    "未经授权禁止转载",
    "刊用本网站稿件",
    "务经书面授权",
    "建立镜像",
    "chinanews.com",
    "sina corporation",
]

ARTICLE_SUMMARY_SYSTEM_PROMPT = """你是一个专业的文本摘要助手。请将以下新闻正文压缩为一句话中文摘要（30-50字），直接概括核心事实即可。

要求：
1. 只返回摘要文本，不要加引号、前缀或任何格式标记
2. 保留关键实体（人名、地名、数字、机构名）
3. 不要评价，只陈述事实"""


class CleanService:

    def __init__(self):
        self._stopwords = self._load_stopwords()
        self._llm_client = None
        jieba.analyse.set_stop_words(self._stopwords_path())

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
                    logger.info("CleanService LLM client initialized")
                except Exception as e:
                    logger.warning("Failed to init CleanService LLM client: %s", e)
        return self._llm_client

    def clean(self, request: CleanRequest) -> CleanResponse:
        content = request.content or ""

        # 1. 去噪: 去除 HTML 标签
        content = self._strip_html(content)

        # 2. 标准化: 移除多余空行、特殊字符
        content = self._normalize(content)

        # 3. 去噪: 清洗特殊字符（URL/邮箱/手机号/emoji/控制字符）
        content = self._clean_special_chars(content)

        is_boilerplate_noise = self._is_boilerplate_noise(content)

        # 4. 分词 & 关键词提取（使用停用词过滤）
        keywords = [] if is_boilerplate_noise else self._extract_keywords(content, topk=10)

        # 5. LLM 摘要生成，降级为取前三句
        summary = "" if is_boilerplate_noise else (
            self._extract_first_sentences(content) if request.mode == "fast" else self._generate_summary(content)
        )

        # 6. 噪声判定：正文过短或明显是版权/站点模板，不进入后续分析聚类
        status = "NOISY" if self._is_short_text(content) or is_boilerplate_noise else "CLEANED"

        return CleanResponse(
            title=request.title,
            content=content,
            keywords=",".join(keywords),
            summary=summary,
            language=request.language or "zh",
            status=status
        )

    def _generate_summary(self, content: str) -> str:
        """LLM 摘要生成，降级为提取前三句"""
        if not content:
            return ""

        # 1. LLM 摘要
        if self._llm and len(content) >= 50:
            llm_summary = self._summarize_by_llm(content)
            if llm_summary:
                return llm_summary

        # 2. 降级：取前三句完整句子
        return self._extract_first_sentences(content)

    def _summarize_by_llm(self, content: str) -> str | None:
        """调用 LLM 生成摘要，失败返回 None"""
        try:
            truncated = content[:1500] if len(content) > 1500 else content
            response = self._llm.chat.completions.create(
                model=os.getenv("DEEPSEEK_MODEL", "deepseek-chat"),
                messages=[
                    {"role": "system", "content": ARTICLE_SUMMARY_SYSTEM_PROMPT},
                    {"role": "user", "content": truncated}
                ],
                temperature=0.2,
                max_tokens=120,
            )
            result = response.choices[0].message.content
            if result:
                result = result.strip().strip('"').strip("'").strip("「").strip("」")
                if len(result) > 200:
                    result = result[:200]
                return result
        except Exception as e:
            logger.error("LLM summary failed: %s", e)
        return None

    def _extract_first_sentences(self, text: str, n: int = 3) -> str:
        """按句号/问号/感叹号/换行切分，取前 n 个完整句子"""
        sentences = re.split(r'[。！？\n]', text)
        parts = [s.strip() for s in sentences[:n] if len(s.strip()) >= 5]
        if not parts and text:
            return text[:200]
        result = "。".join(parts) + "。"
        return result[:300]

    def vectorize(self, texts: list[str]) -> tuple[list[list[float]], int]:
        """对文本列表进行 TF-IDF 向量化，返回 (向量列表, 词汇表大小)"""
        if not texts:
            return [], 0

        def tokenizer(text: str) -> list[str]:
            text = self._strip_html(text)
            text = self._normalize(text)
            text = self._clean_special_chars(text)
            words = jieba.cut(text)
            return [w for w in words if len(w.strip()) > 1 and w.strip() not in self._stopwords]

        vectorizer = TfidfVectorizer(
            tokenizer=tokenizer,
            token_pattern=None,  # 使用自定义 tokenizer
            max_features=5000,
            sublinear_tf=True,
        )
        tfidf_matrix = vectorizer.fit_transform(texts)
        vocab_size = len(vectorizer.vocabulary_)

        # 稀疏矩阵 → 稠密列表（每篇文档的向量）
        vectors = tfidf_matrix.toarray().tolist()
        return vectors, vocab_size

    def _load_stopwords(self) -> set[str]:
        path = self._stopwords_path()
        if not os.path.exists(path):
            return set()
        with open(path, "r", encoding="utf-8") as f:
            words = set()
            for line in f:
                line = line.strip()
                if line and not line.startswith("#"):
                    words.add(line)
            return words

    def _stopwords_path(self) -> str:
        return os.path.join(os.path.dirname(__file__), "..", "data", "stopwords.txt")

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

    def _clean_special_chars(self, text: str) -> str:
        """去除 URL、邮箱、手机号、emoji、控制字符等噪音"""
        # 移除 URL（仅匹配 ASCII 字符组成的 URL，避免误删中文）
        text = re.sub(r'https?://[^\s\u4e00-\u9fff\u3000-\u303f\uff00-\uffef]*', '', text)
        text = re.sub(r'www\.[^\s\u4e00-\u9fff\u3000-\u303f\uff00-\uffef]*', '', text)
        # 移除邮箱（仅匹配 ASCII 字符组成的邮箱）
        text = re.sub(r'[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}', '', text)
        # 移除手机号（中国大陆格式）
        text = re.sub(r'1[3-9]\d{9}', '', text)
        # 移除 emoji 和特殊 Unicode 符号
        text = re.sub(r'[\U0001F300-\U0001FFFF]', '', text)
        # 移除控制字符（保留换行和制表符）
        text = re.sub(r'[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]', '', text)
        # 移除 Unicode 私用区字符
        text = re.sub(r'[\ue000-\uf8ff]', '', text)
        # 合并被移除后产生的多余空行
        text = re.sub(r'\n\s*\n', '\n', text)
        text = '\n'.join(line.strip() for line in text.splitlines() if line.strip())
        return text.strip()

    def _is_short_text(self, text: str) -> bool:
        """判断清洗后正文是否过短（无意义噪音）"""
        # 统计中文字符数
        chinese_chars = len(re.findall(r'[\u4e00-\u9fff]', text))
        return chinese_chars < MIN_CONTENT_LENGTH

    def _is_boilerplate_noise(self, text: str) -> bool:
        """判断正文是否主要由版权声明、站点声明等模板噪声构成。"""
        if not text:
            return True

        normalized = re.sub(r'\s+', ' ', text).strip().lower()
        if not normalized:
            return True

        hit_count = sum(1 for term in COPYRIGHT_NOISE_TERMS if term in normalized)
        if hit_count >= 2:
            return True

        # 中新网常见正文抽取失败结果：正文几乎只剩版权声明。
        if "本网站所刊载信息" in text and "不代表中新社和中新网观点" in text:
            return True

        # 模板噪声通常新闻实体极少，版权/授权词占比极高。
        content_chars = len(re.findall(r'[\u4e00-\u9fffA-Za-z0-9]', text))
        boilerplate_chars = sum(len(term) for term in COPYRIGHT_NOISE_TERMS if term in normalized)
        return content_chars > 0 and boilerplate_chars / content_chars > 0.25

    def _extract_keywords(self, text: str, topk: int = 10) -> list[str]:
        # TF-IDF 关键词提取（已通过 jieba.analyse.set_stop_words 配置停用词）
        return jieba.analyse.extract_tags(text, topK=topk)
