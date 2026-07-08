from pathlib import Path
import sys

import httpx

SOURCE_ROOT = Path(__file__).resolve().parents[1] / "src"
sys.path.insert(0, str(SOURCE_ROOT))

from adapter.news_link_discoverer import NewsLinkDiscoverer


def test_discovers_sina_news_detail_links() -> None:
    html = """
    <!doctype html>
    <html>
      <body>
        <a href="https://news.sina.com.cn/c/2026-07-01/doc-inifhvwq4175336.shtml">外交部新闻</a>
        <a href="https://news.sina.com.cn/">首页</a>
        <a href="https://news.sina.com.cn/c/2026-07-02/doc-abcdefg1234567.shtml#comment">第二条新闻</a>
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
    result = NewsLinkDiscoverer(client=client).discover("https://news.sina.com.cn/", 10)

    assert result.source_name == "新浪新闻"
    assert result.source_type == "portal"
    assert result.total_found == 2
    assert result.links[0].title == "外交部新闻"
    assert result.links[0].url == "https://news.sina.com.cn/c/2026-07-01/doc-inifhvwq4175336.shtml"
    assert result.links[1].url == "https://news.sina.com.cn/c/2026-07-02/doc-abcdefg1234567.shtml"


def test_respects_discover_limit() -> None:
    html = """
    <!doctype html>
    <html>
      <body>
        <a href="https://www.jiemian.com/article/11111111.html">第一条</a>
        <a href="https://www.jiemian.com/article/22222222.html">第二条</a>
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
    result = NewsLinkDiscoverer(client=client).discover("https://www.jiemian.com/", 1)

    assert result.source_name == "界面新闻"
    assert result.total_found == 1
    assert result.links[0].url == "https://www.jiemian.com/article/11111111.html"
