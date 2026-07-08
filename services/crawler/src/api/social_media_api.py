from fastapi import APIRouter, HTTPException, status

from adapter.social_media_adapter import SocialMediaAdapter
from exception.crawler_exception import CrawlerException
from model.social_hot_item import SocialHotResult

router = APIRouter(prefix="/internal/crawler/social", tags=["social crawler"])
adapter = SocialMediaAdapter()


@router.get("/weibo/hot", response_model=SocialHotResult, response_model_by_alias=True)
def weibo_hot() -> SocialHotResult:
    """获取微博实时热搜榜（无需登录）"""
    try:
        return adapter.fetch_weibo_hot()
    except CrawlerException as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=str(exc),
        ) from exc


@router.get("/baidu/hot", response_model=SocialHotResult, response_model_by_alias=True)
def baidu_hot() -> SocialHotResult:
    """获取百度实时热搜榜（无需登录）"""
    try:
        return adapter.fetch_baidu_hot()
    except CrawlerException as exc:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=str(exc),
        ) from exc
