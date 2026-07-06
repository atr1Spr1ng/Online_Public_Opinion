from pydantic import BaseModel, HttpUrl


class NewsCrawlRequest(BaseModel):
    url: HttpUrl
