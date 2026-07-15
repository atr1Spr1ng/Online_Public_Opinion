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
    assert result.content_length > 0
    assert result.extract_status == "SUCCESS"
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


def test_marks_site_homepage_title_as_noisy_page() -> None:
    html = """
    <!doctype html>
    <html lang="zh-CN">
      <head><title>央视网</title></head>
      <body>
        <div>
          <a>首页</a><a>新闻</a><a>专题</a><a>客户端</a><a>微博</a><a>微信</a>
          <p>首页 新闻 经济 体育 文化 旅游 视频 专题 客户端 网站地图 联系我们 版权声明</p>
          <p>更多 新闻 频道 专题 广告 版权 联系我们 网站地图 客户端 微博 微信</p>
          <p>首页 新闻 经济 体育 文化 旅游 视频 专题 客户端 网站地图 联系我们 版权声明</p>
          <p>更多 新闻 频道 专题 广告 版权 联系我们 网站地图 客户端 微博 微信</p>
        </div>
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
    result = NewsPleaseAdapter(client=client).crawl("https://news.cctv.com/")

    assert result.title == "央视网"
    assert result.extract_status == "NOISY_PAGE"


def test_marks_short_extracted_content_as_noisy_page() -> None:
    html = """
    <!doctype html>
    <html lang="zh-CN">
      <head><title>短文本新闻</title></head>
      <body><article><h1>短文本新闻</h1><p>正文太短。</p></article></body>
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
    result = NewsPleaseAdapter(client=client).crawl("https://example.com/news/short.html")

    assert result.extract_status == "NOISY_PAGE"
    assert "正文过短" in result.message


def test_corrects_wrong_extracted_date_with_url_date() -> None:
    html = """
    <!doctype html>
    <html lang="zh-CN">
      <head>
        <title>测试新闻标题</title>
        <meta property="article:published_time" content="2018-03-26T00:00:00+08:00">
      </head>
      <body>
        <article>
          <h1>测试新闻标题</h1>
          <p>这是一篇来自新闻网站的测试正文，用于验证发布时间纠偏逻辑是否正常。</p>
          <p>当页面抽取出的发布时间与 URL 日期明显不一致时，系统应优先使用 URL 中的日期。</p>
          <p>这样可以避免新文章被错误标记成多年以前的历史文章。</p>
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
    result = NewsPleaseAdapter(client=client).crawl(
        "https://news.sina.com.cn/c/2026-07-15/doc-test.shtml"
    )

    assert result.extract_status == "SUCCESS"
    assert result.published_at is not None
    assert result.published_at.date().isoformat() == "2026-07-15"
    assert "URL 日期纠偏" in result.message


def test_marks_old_article_as_stale() -> None:
    html = """
    <!doctype html>
    <html lang="zh-CN">
      <head>
        <title>历史旧文标题</title>
        <meta property="article:published_time" content="2021-07-15T08:00:00+08:00">
      </head>
          <body>
            <article>
              <h1>历史旧文标题</h1>
              <p>这是一篇历史旧文，正文长度足够通过正文抽取校验，内容包含完整新闻段落和背景说明。</p>
              <p>但是发布时间距离当前时间过远，不适合默认进入舆情监控流程，避免陈年数据影响当前事件聚类。</p>
              <p>系统应该把它标记为旧文，让后端跳过入库，只在用户明确选择历史采集时才考虑保留。</p>
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
    result = NewsPleaseAdapter(client=client).crawl(
        "https://news.cctv.com/2021/07/15/ARTIold.shtml"
    )

    assert result.extract_status == "STALE_ARTICLE"
    assert "超过" in result.message
