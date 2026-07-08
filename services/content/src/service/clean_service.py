import os
import re
import jieba
import jieba.analyse
from bs4 import BeautifulSoup
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

from src.model.clean_model import CleanRequest, CleanResponse

# 短文本阈值：清洗后正文字数少于此值的文章标记为噪音
MIN_CONTENT_LENGTH = 20


class CleanService:

    def __init__(self):
        self._stopwords = self._load_stopwords()
        jieba.analyse.set_stop_words(self._stopwords_path())

    def clean(self, request: CleanRequest) -> CleanResponse:
        content = request.content or ""

        # 1. 去噪: 去除 HTML 标签
        content = self._strip_html(content)

        # 2. 标准化: 移除多余空行、特殊字符
        content = self._normalize(content)

        # 3. 去噪: 清洗特殊字符（URL/邮箱/手机号/emoji/控制字符）
        content = self._clean_special_chars(content)

        # 4. 分词 & 关键词提取（使用停用词过滤）
        keywords = self._extract_keywords(content, topk=10)
        summary = content[:200] if len(content) > 200 else content

        # 5. 短文本判定：正文过短标记为噪音
        status = "NOISY" if self._is_short_text(content) else "CLEANED"

        return CleanResponse(
            title=request.title,
            content=content,
            keywords=",".join(keywords),
            summary=summary,
            language=request.language or "zh",
            status=status
        )

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

    def _extract_keywords(self, text: str, topk: int = 10) -> list[str]:
        # TF-IDF 关键词提取（已通过 jieba.analyse.set_stop_words 配置停用词）
        return jieba.analyse.extract_tags(text, topK=topk)
