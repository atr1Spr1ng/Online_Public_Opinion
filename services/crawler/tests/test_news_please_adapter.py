from pathlib import Path
import sys

import httpx

SOURCE_ROOT = Path(__file__).resolve().parents[1] / "src"
sys.path.insert(0, str(SOURCE_ROOT))

from adapter.news_please_adapter import NewsPleaseAdapter


def test_extracts_news_fields_from_html() -> None:
    html = """
    <!doctype html>
    <html lang="zh-CN">
      <head>
        <title>测试新闻标题</title>
        <meta name="author" content="测试作者">
        <meta property="article:published_time" content="2026-07-07T08:00:00+08:00">
      </head>
      <body>
        <article>
          <h1>测试新闻标题</h1>
          <p>这是测试新闻的第一段正文，用于验证正文抽取是否正常工作。</p>
          <p>这是测试新闻的第二段正文，包含足够的文字以便正文抽取器识别。</p>
          <p>这是测试新闻的第三段正文，网络舆情系统将处理采集到的公开新闻。</p>
        </article>
      </body>
    </html>
    """

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            headers={"content-type": "text/html; charset=utf-8"},
            text=html,
            request=request,
        )

    client = httpx.Client(transport=httpx.MockTransport(handler))
    result = NewsPleaseAdapter(client=client).crawl("https://example.com/news/1")

    assert result.engine == "news-please"
    assert result.status_code == 200
    assert result.title == "测试新闻标题"
    assert "第一段正文" in (result.content or "")
    assert result.final_url == "https://example.com/news/1"


def test_rejects_non_html_response() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            headers={"content-type": "application/json"},
            json={"message": "not html"},
            request=request,
        )

    client = httpx.Client(transport=httpx.MockTransport(handler))
    adapter = NewsPleaseAdapter(client=client)

    try:
        adapter.crawl("https://example.com/api")
    except Exception as exc:
        assert "不是 HTML" in str(exc)
    else:
        raise AssertionError("Expected a crawler exception")
