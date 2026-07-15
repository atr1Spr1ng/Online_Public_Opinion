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
    result = NewsLinkDiscoverer(client=client).discover("https://news.sina.com.cn/", 2)

    assert result.source_name == "新浪新闻"
    assert result.source_type == "portal"
    assert result.total_found == 2
    assert result.links[0].title == "外交部新闻"
    assert result.links[0].url == "https://news.sina.com.cn/c/2026-07-01/doc-inifhvwq4175336.shtml"
    assert result.links[1].url == "https://news.sina.com.cn/c/2026-07-02/doc-abcdefg1234567.shtml"


def test_discovers_sina_k_article_links() -> None:
    html = """
    <!doctype html>
    <html>
      <body>
        <a href="https://k.sina.com.cn/article_1234567890_49abcdef001001abc.html">新浪看点文章</a>
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
    result = NewsLinkDiscoverer(client=client).discover("https://news.sina.com.cn/", 1)

    assert result.total_found == 1
    assert result.links[0].url == "https://k.sina.com.cn/article_1234567890_49abcdef001001abc.html"


def test_discovers_rss_feed_links() -> None:
    feed = """<?xml version="1.0" encoding="utf-8"?>
    <rss version="2.0">
      <channel>
        <title>中新网滚动新闻</title>
        <item>
          <title>第一条RSS新闻</title>
          <link>https://www.chinanews.com.cn/gn/2026/07-15/1234567.shtml</link>
        </item>
        <item>
          <title>视频应被过滤</title>
          <link>https://www.chinanews.com.cn/shipin/2026/07-15/1234568.shtml</link>
        </item>
      </channel>
    </rss>
    """

    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(
            200,
            headers={"content-type": "text/xml; charset=utf-8"},
            text=feed,
            request=request,
        )

    client = httpx.Client(transport=httpx.MockTransport(handler))
    result = NewsLinkDiscoverer(client=client).discover(
        "https://www.chinanews.com.cn/rss/scroll-news.xml", 10
    )

    assert result.source_name == "中国新闻网"
    assert result.total_found == 1
    assert result.links[0].title == "第一条RSS新闻"
    assert result.links[0].url == "https://www.chinanews.com.cn/gn/2026/07-15/1234567.shtml"


def test_supplements_sina_homepage_with_feed_api() -> None:
    html = """
    <!doctype html>
    <html>
      <body>
        <a href="https://news.sina.com.cn/c/2026-07-01/doc-inifhvwq4175336.shtml">首页新闻</a>
      </body>
    </html>
    """

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.host == "feed.mix.sina.com.cn":
            return httpx.Response(
                200,
                json={
                    "result": {
                        "data": [
                            {
                                "title": "Feed新闻",
                                "url": "https://k.sina.com.cn/article_1234567890_49abcdef001001abc.html",
                            }
                        ]
                    }
                },
                request=request,
            )
        return httpx.Response(
            200,
            headers={"content-type": "text/html; charset=utf-8"},
            text=html,
            request=request,
        )

    client = httpx.Client(transport=httpx.MockTransport(handler))
    result = NewsLinkDiscoverer(client=client).discover("https://news.sina.com.cn/", 2)

    assert result.total_found == 2
    assert [link.title for link in result.links] == ["首页新闻", "Feed新闻"]


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


def test_discovers_cctv_news_and_filters_special_pages() -> None:
    html = """
    <!doctype html>
    <html>
      <body>
        <a href="https://news.cctv.com/special/index.shtml">专题</a>
        <a href="https://news.cctv.com/2026/07/15/ARTIabcdef123456.shtml">央视新闻正文</a>
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
    result = NewsLinkDiscoverer(client=client).discover("https://news.cctv.com/", 1)

    assert result.source_name == "央视新闻"
    assert result.source_type == "official"
    assert result.total_found == 1
    assert result.links[0].title == "央视新闻正文"
