import logging
import time
import numpy as np

logger = logging.getLogger(__name__)

# 中文嵌入模型选择：
# m3e-large: moka.ai 出品，1024维，~400MB，中文优化，比 BGE-small 更准但推理稍慢
# 备选：BAAI/bge-base-zh-v1.5 (768维)、BAAI/bge-small-zh-v1.5 (384维，轻量)
EMBEDDING_MODEL_NAME = "moka-ai/m3e-large"
EMBEDDING_MODEL_PATH = "D:/huggingface_cache/m3e-large-onnx"  # 预导出的 ONNX 模型，首次需运行: python -m optimum.exporters.onnx --model moka-ai/m3e-large --task feature-extraction D:/huggingface_cache/m3e-large-onnx
HF_CACHE_DIR = "D:/huggingface_cache"


class EmbeddingService:
    """稠密向量嵌入服务，基于 sentence-transformers"""

    _instance: "EmbeddingService | None" = None
    _model = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    @property
    def model(self):
        """延迟加载模型，首次调用时才下载/加载"""
        if self._model is None:
            try:
                from sentence_transformers import SentenceTransformer
                import os
                # ONNX Runtime 线程数优化，默认单线程在 CPU 上极慢
                os.environ.setdefault("OMP_NUM_THREADS", "4")
                os.environ.setdefault("OPENBLAS_NUM_THREADS", "4")
                if os.path.isdir(EMBEDDING_MODEL_PATH):
                    logger.info("Loading ONNX embedding model from: %s", EMBEDDING_MODEL_PATH)
                    self._model = SentenceTransformer(
                        EMBEDDING_MODEL_PATH,
                        backend="onnx",
                        model_kwargs={"provider": "CPUExecutionProvider"},
                    )
                else:
                    logger.info("Loading embedding model (PyTorch fallback): %s", EMBEDDING_MODEL_NAME)
                    self._model = SentenceTransformer(
                        EMBEDDING_MODEL_NAME,
                        cache_folder=HF_CACHE_DIR,
                        model_kwargs={"cache_dir": HF_CACHE_DIR},
                    )
                logger.info("Embedding model loaded, dim=%d", self._model.get_sentence_embedding_dimension())
            except Exception as e:
                logger.error("Failed to load embedding model: %s", e)
                raise
        return self._model

    @property
    def available(self) -> bool:
        try:
            return self.model is not None
        except Exception:
            return False

    @staticmethod
    def _sanitize(text: str) -> str:
        """清理文本中的孤立代理字符（lone surrogates），M3E tokenizer 无法处理"""
        if not text:
            return ""
        return text.encode("utf-8", errors="surrogateescape").decode("utf-8", errors="replace")

    def encode(self, texts: list[str]) -> np.ndarray:
        """将文本列表转为归一化的稠密向量矩阵 (N, dim)"""
        if not texts:
            return np.array([]).reshape(0, 0)
        t0 = time.time()
        clean_texts = [self._sanitize(t) for t in texts]
        t1 = time.time()
        embeddings = self.model.encode(
            clean_texts,
            normalize_embeddings=True,  # L2归一化 → 欧氏距离 ≈ 1-余弦相似度
            show_progress_bar=False,
            batch_size=32,
        )
        t2 = time.time()
        logger.info("[EMBED] sanitize=%.2fs encode=%.2fs total=%.2fs for %d texts",
                    t1 - t0, t2 - t1, t2 - t0, len(texts))
        return np.array(embeddings)
