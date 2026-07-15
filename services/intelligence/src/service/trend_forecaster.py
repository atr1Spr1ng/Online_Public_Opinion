from datetime import datetime, timedelta

import pandas as pd
from prophet import Prophet


class TrendForecaster:
    """单事件热度趋势预测，Prophet + 移动平均降级"""

    MIN_POINTS_FOR_PROPHET = 7  # 数据点足够时才启用 Prophet；少量数据使用移动平均参考

    def forecast(self, daily_counts: list[dict], periods: int = 7) -> dict:
        """
        对单事件的日粒度文章量做预测。

        daily_counts: [{"date": "2025-01-01", "count": 15}, ...]
        periods: 预测未来 N 天

        返回:
        {
            "method": "prophet" | "moving_avg",
            "historical": [{"date": "...", "count": N}, ...],
            "forecast": [{"date": "...", "count": N, "yhat_lower": N, "yhat_upper": N}, ...],
            "trend": "up" | "down" | "stable"
        }
        """
        if not daily_counts:
            return self._empty_result("无数据")

        n = len(daily_counts)

        if n >= self.MIN_POINTS_FOR_PROPHET:
            try:
                return self._prophet_forecast(daily_counts, periods)
            except Exception as e:
                # Prophet 训练失败时降级到移动平均
                return self._moving_avg_forecast(daily_counts, periods, f"Prophet失败({e})")
        else:
            short_periods = min(periods, 3)
            return self._moving_avg_forecast(
                daily_counts, short_periods,
                f"历史数据点较少({n}天)，不做长期趋势预测，仅提供未来{short_periods}天短期参考"
            )

    def _prophet_forecast(self, daily_counts: list[dict], periods: int) -> dict:
        """Prophet 预测"""
        df = pd.DataFrame(daily_counts)
        df = df.rename(columns={"date": "ds", "count": "y"})
        df["ds"] = pd.to_datetime(df["ds"])
        df["y"] = df["y"].astype(float)

        model = Prophet(
            growth="linear",
            yearly_seasonality=False,
            weekly_seasonality=False,
            daily_seasonality=False,
            changepoint_prior_scale=0.05,
            interval_width=0.8,
        )
        model.fit(df)

        future = model.make_future_dataframe(periods=periods)
        forecast_df = model.predict(future)

        historical = []
        forecast = []

        for _, row in forecast_df.iterrows():
            date_str = row["ds"].strftime("%Y-%m-%d")
            is_future = date_str not in {d["date"] for d in daily_counts}

            if is_future:
                forecast.append({
                    "date": date_str,
                    "count": max(0, round(row["yhat"])),
                    "yhat_lower": max(0, round(row["yhat_lower"])),
                    "yhat_upper": max(0, round(row["yhat_upper"])),
                })
            else:
                # Prophet 的拟合值在 yhat 列，实际值需要从原始数据取
                orig = next((d for d in daily_counts if d["date"] == date_str), None)
                historical.append({
                    "date": date_str,
                    "count": orig["count"] if orig else max(0, round(row["yhat"])),
                    "yhat": max(0, round(row["yhat"])),
                })

        return {
            "method": "prophet",
            "historical": historical,
            "forecast": forecast,
            "trend": self._calc_trend(forecast),
        }

    def _moving_avg_forecast(self, daily_counts: list[dict], periods: int, reason: str) -> dict:
        """移动平均降级方案"""
        counts = [d["count"] for d in daily_counts]
        window = min(3, max(1, len(counts) // 3))

        # 计算移动平均
        smoothed = []
        for i in range(len(counts)):
            start = max(0, i - window + 1)
            avg = sum(counts[start:i + 1]) / (i - start + 1)
            smoothed.append(round(avg, 1))

        # 用最后 window 个点的平均趋势外推
        if len(smoothed) >= 2:
            lookback = min(window, len(smoothed) - 1)
            last_slope = (smoothed[-1] - smoothed[-lookback - 1]) / lookback
        else:
            last_slope = 0

        last_date = datetime.strptime(daily_counts[-1]["date"], "%Y-%m-%d")
        forecast = []
        for i in range(1, periods + 1):
            future_date = last_date + timedelta(days=i)
            pred = max(0, round(smoothed[-1] + last_slope * i))
            forecast.append({
                "date": future_date.strftime("%Y-%m-%d"),
                "count": pred,
                "yhat_lower": max(0, pred - abs(pred) // 3),
                "yhat_upper": pred + abs(pred) // 3,
            })

        historical = [{"date": d["date"], "count": d["count"]} for d in daily_counts]

        return {
            "method": "moving_avg",
            "historical": historical,
            "forecast": forecast,
            "trend": self._calc_trend(forecast),
            "note": reason,
        }

    def _empty_result(self, reason: str) -> dict:
        return {
            "method": "none",
            "historical": [],
            "forecast": [],
            "trend": "stable",
            "note": reason,
        }

    def _calc_trend(self, forecast: list[dict]) -> str:
        if not forecast or len(forecast) < 2:
            return "stable"
        first = forecast[0]["count"]
        last = forecast[-1]["count"]
        if last > first * 1.05:
            return "up"
        elif last < first * 0.95:
            return "down"
        return "stable"
