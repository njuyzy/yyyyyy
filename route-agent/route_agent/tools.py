import math
import re
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from .models import (AskArgs, Constraints, DetailArgs, FinishArgs, LegArgs, Model,
                     NearbyArgs, OptimizeArgs, ScheduleArgs, SearchArgs, WeatherArgs)
from .provider import ConfigurationError, UpstreamError


class ToolError(Exception):
    pass


def string(value, limit=300):
    return value[:limit] if isinstance(value, str) else ""


def number(value):
    try:
        result = float(value)
        return result if math.isfinite(result) and result >= 0 else None
    except (TypeError, ValueError):
        return None


def coordinates(value):
    try:
        lon, lat = map(float, value.split(","))
        if not math.isfinite(lon) or not math.isfinite(lat) or not (73 <= lon <= 136 and 3 <= lat <= 54):
            raise ValueError()
        return lon, lat
    except (AttributeError, ValueError):
        raise ToolError("地点缺少有效的中国境内坐标，请搜索另一个入口或地点") from None


def matches(term, poi):
    term = re.sub(r"\s+", "", term).lower()
    return term == poi["poiId"].lower() or term in re.sub(r"\s+", "", poi["name"]).lower()


def haversine(a, b):
    lon1, lat1 = map(math.radians, coordinates(a["location"]))
    lon2, lat2 = map(math.radians, coordinates(b["location"]))
    h = math.sin((lat2 - lat1) / 2)**2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2)**2
    return 6371000 * 2 * math.asin(min(1, math.sqrt(h)))


def opening_windows(text):
    # Deliberately only parse unequivocal daily windows. Weekly/holiday prose
    # requires a warning, not a guessed "open" result.
    if text in ("全天", "24小时", "00:00-24:00"):
        return [(0, 1440)]
    if re.fullmatch(r"\s*\d{1,2}:\d{2}\s*[-~～—至]\s*\d{1,2}:\d{2}(?:\s*[,，;；]\s*\d{1,2}:\d{2}\s*[-~～—至]\s*\d{1,2}:\d{2})*\s*", text or ""):
        values = re.findall(r"(\d{1,2}):(\d{2})", text)
        times = [int(hour) * 60 + int(minute) for hour, minute in values
                 if int(hour) <= 24 and int(minute) < 60]
        if len(times) != len(values):
            return []
        return [(times[i], times[i + 1]) for i in range(0, len(times), 2) if times[i] < times[i + 1]]
    return []


DEFINITIONS = {
    "set_constraints": (Constraints, "提取并更新用户需求。包含城市、日期、每日时长、每人总预算、交通方式、步行上限、必去/避开地点、群体与无障碍需求；未传字段保留旧值，明确传 null 可清除可空约束。不要悄悄放宽硬约束。必须在规划前调用。"),
    "search_places": (SearchArgs, "按城市搜索真实高德地点；保存已验证 POI 与坐标。关键词应具体；分批查找多个偏好，避免仅选择知名景点。"),
    "get_place_details": (DetailArgs, "按 POI ID 核验地点详情、地址、电话、开放时间与人均消费。人均消费不是门票；缺失表示未知。客户端旧坐标不可作为证据。"),
    "search_nearby": (NearbyArgs, "在已核验地点周围搜索午餐、休息点、卫生间或其他需求。"),
    "use_current_location": (Model, "获取用户授权提供的当前位置并反查城市，可用 __origin 作为当日 startPoiId。没有定位时不得假定当前位置。"),
    "get_weather": (WeatherArgs, "查询未来几天实际天气预报及预报日期；不预测超出范围的日期。"),
    "calculate_leg": (LegArgs, "调用高德道路或公共交通路径计算真实通勤距离、时长、步行量与折线。仅使用已核验的 POI。"),
    "optimize_order": (OptimizeArgs, "在可选固定首尾下提出较短地理顺序（启发式，不是全局最优）。必须再 build_itinerary 用真实道路核验。"),
    "build_itinerary": (ScheduleArgs, "编排多日行程，传入每日 visits、每段交通 modes 和休息时间（出发地 startPoiId、结束地 endPoiId 可选）。未给出出发点则从第一站计时并警示。自动计算道路通勤、缓冲时间、开放等待与预算估算，校验硬约束。失败后修改再调用。"),
    "finish_plan": (FinishArgs, "仅在 build_itinerary 检验可行后完成方案。说明契合用户需求的选择理由、研学目标及假设；不得承诺未核实票价/预约/无障碍。不执行发布或付款。"),
    "ask_user": (AskArgs, "缺少决定性信息、需求冲突或只能通过用户取舍继续时，提出最多三个明确问题。本轮不改变路线。"),
}

LABELS = {"set_constraints": "整理出行需求", "search_places": "检索匹配地点",
          "get_place_details": "核验地点详情", "search_nearby": "查找附近配套",
          "use_current_location": "确认出发位置", "get_weather": "查询出行天气",
          "calculate_leg": "计算实际交通", "optimize_order": "比较游览顺序",
          "build_itinerary": "校验行程与约束", "finish_plan": "整理推荐方案", "ask_user": "确认关键需求"}


def schemas(protocol):
    result = []
    for name, (model, description) in DEFINITIONS.items():
        definition = {"name": name, "description": description,
                      "parameters": model.model_json_schema(by_alias=True), "strict": False}
        if protocol == "responses":
            result.append({"type": "function", **definition})
        else:
            result.append({"type": "function", "function": definition})
    return result


class RouteTools:
    def __init__(self, request, amap):
        self.request, self.amap = request, amap
        self.constraints = request.constraints.model_copy(deep=True)
        self.constraints_set = False
        self.places = {}
        self.detailed = set()
        self.legs = {}
        self.plan = None
        self.result = None
        self.today = datetime.now(ZoneInfo("Asia/Shanghai")).date()
        self.weather = {}

    def place(self, poi_id):
        if poi_id not in self.places:
            raise ToolError("POI 尚未核验，请先搜索或 get_place_details：" + poi_id)
        return self.places[poi_id]

    def register(self, raw, detailed=False):
        poi_id = string(raw.get("id"), 80)
        if not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", poi_id):
            return None
        try:
            coordinates(raw.get("location"))
        except ToolError:
            return None
        business = raw.get("business") if isinstance(raw.get("business"), dict) else {}
        ext = raw.get("biz_ext") if isinstance(raw.get("biz_ext"), dict) else {}
        poi = {"poiId": poi_id, "name": string(raw.get("name"), 160),
               "location": string(raw.get("location"), 80), "address": string(raw.get("address")),
               "cityname": string(raw.get("cityname"), 80), "citycode": string(raw.get("citycode"), 20),
               "adcode": string(raw.get("adcode"), 20), "type": string(raw.get("type")),
               "typecode": string(raw.get("typecode"), 40), "tel": string(business.get("tel") or raw.get("tel")),
               "opentimeToday": string(business.get("opentime_today")),
               "opentimeWeek": string(business.get("opentime_week")),
               "averageCost": number(business.get("cost") or ext.get("cost")),
               "source": "高德地图", "checkedAt": datetime.now(ZoneInfo("Asia/Shanghai")).isoformat()}
        if not poi["name"]:
            return None
        if not detailed and poi_id in self.detailed:
            return self.places[poi_id]
        self.places[poi_id] = poi
        if detailed:
            self.detailed.add(poi_id)
        return poi

    async def set_constraints(self, args):
        merged = self.constraints.model_dump()
        merged.update(args.model_dump(exclude_unset=True))
        self.constraints = Constraints(**merged)
        self.constraints_set = True
        self.plan = None  # A changed constraint invalidates previous validation.
        return {"constraints": self.constraints.model_dump(mode="json", by_alias=True),
                "note": "预算为每人全程估算，步行上限为每日已测通勤步行；场馆内步行与未核实费用另行提示"}

    async def search_places(self, args):
        data = await self.amap.get("/v3/place/text", keywords=args.keyword, city=args.city,
                                   citylimit="true", types=args.types, offset=args.limit, page=1, extensions="all")
        pois = [poi for raw in data.get("pois", []) if isinstance(raw, dict) and (poi := self.register(raw))]
        return {"places": pois, "count": len(pois), "note": "名称相近时请核对城市和地址；无需填充不存在的地点"}

    async def get_place_details(self, args):
        if args.poi_id in self.detailed:
            return self.place(args.poi_id)
        data = await self.amap.get("/v5/place/detail", id=args.poi_id, show_fields="business")
        raw = next((item for item in data.get("pois", []) if isinstance(item, dict)
                    and item.get("id") == args.poi_id), None)
        poi = self.register(raw, detailed=True) if raw else None
        if not poi:
            raise ToolError("未找到具有有效坐标的地点，请搜索确认 POI ID")
        return poi

    async def search_nearby(self, args):
        poi = self.place(args.poi_id)
        data = await self.amap.get("/v3/place/around", location=poi["location"], keywords=args.keyword,
                                   radius=args.radius, offset=10, page=1, extensions="all")
        return {"places": [poi for raw in data.get("pois", [])
                           if isinstance(raw, dict) and (poi := self.register(raw))]}

    async def use_current_location(self, args):
        if not self.request.origin:
            raise ToolError("用户没有提供定位，请询问出发城市或地点")
        origin = self.request.origin
        location = f"{origin.longitude:.6f},{origin.latitude:.6f}"
        data = await self.amap.get("/v3/geocode/regeo", location=location, extensions="base")
        component = data.get("regeocode", {}).get("addressComponent", {})
        city = string(component.get("city"), 80) or string(component.get("province"), 80)
        self.places["__origin"] = {"poiId": "__origin", "name": "用户出发位置", "location": location,
                                   "cityname": city, "adcode": string(component.get("adcode")),
                                   "address": string(data.get("regeocode", {}).get("formatted_address"))}
        self.detailed.add("__origin")
        return self.places["__origin"]

    async def get_weather(self, args):
        data = await self.amap.get("/v3/weather/weatherInfo", city=args.adcode, extensions="all")
        forecasts = data.get("forecasts", [])
        forecast = forecasts[0] if forecasts else {}
        result = {"city": string(forecast.get("city")), "reportTime": string(forecast.get("reporttime")),
                  "casts": [{key: string(day.get(key), 80) for key in
                             ("date", "dayweather", "nightweather", "daytemp", "nighttemp", "daywind", "daypower")}
                            for day in forecast.get("casts", []) if isinstance(day, dict)]}
        self.weather[args.adcode] = result
        return result

    async def calculate_leg(self, args):
        key = (args.origin, args.destination, args.mode)
        if key in self.legs:
            return self.legs[key]
        origin, destination = self.place(args.origin), self.place(args.destination)
        if args.origin == args.destination:
            raise ToolError("起终点不能相同")
        params = {"origin": origin["location"], "destination": destination["location"]}
        if args.mode == "transit":
            params.update(city=origin.get("cityname") or self.constraints.city,
                          cityd=destination.get("cityname") or self.constraints.city, extensions="all")
            data = await self.amap.get("/v3/direction/transit/integrated", **params)
            paths = data.get("route", {}).get("transits", [])
        else:
            data = await self.amap.get("/v3/direction/" + ("walking" if args.mode == "walking" else "driving"), **params)
            paths = data.get("route", {}).get("paths", [])
        valid = [path for path in paths if isinstance(path, dict)
                 and number(path.get("duration")) is not None and number(path.get("distance")) is not None]
        if not valid:
            raise ToolError("未找到可用的" + args.mode + "路径，请换交通方式；不能用直线距离替代道路结果")
        path = min(valid, key=lambda item: float(item["duration"]))
        polyline = []
        instructions = []
        if args.mode == "transit":
            steps = []
            for segment in path.get("segments", []):
                walking = segment.get("walking") if isinstance(segment.get("walking"), dict) else {}
                steps.extend(walking.get("steps", []))
                bus = segment.get("bus") if isinstance(segment.get("bus"), dict) else {}
                for line in bus.get("buslines", []):
                    steps.append(line)
                    instructions.append(string(line.get("name")))
        else:
            steps = path.get("steps", [])
        for step in steps:
            instructions.append(string(step.get("instruction")))
            for point in string(step.get("polyline"), 200000).split(";"):
                try:
                    lon, lat = coordinates(point)
                    if not polyline or polyline[-1] != [lon, lat]:
                        polyline.append([lon, lat])
                except ToolError:
                    continue
        # Preserve a bounded polyline including both ends. All points remain road evidence.
        if len(polyline) > 1800:
            stride = math.ceil(len(polyline) / 1799)
            polyline = polyline[::stride] + [polyline[-1]]
        walking = number(path.get("walking_distance")) if args.mode == "transit" else (
            number(path.get("distance")) if args.mode == "walking" else 0)
        result = {"origin": args.origin, "destination": args.destination, "mode": args.mode,
                  "originName": origin["name"], "destinationName": destination["name"],
                  "minutes": math.ceil(float(path["duration"]) / 60), "distanceMeters": math.ceil(float(path["distance"])),
                  "walkingMeters": None if walking is None else math.ceil(walking),
                  "cost": number(path.get("cost")) if args.mode == "transit" else None,
                  "polyline": polyline, "instructions": [s for s in instructions if s][:24],
                  "source": "高德路径规划", "note": "通勤时间为查询时参考，不保证未来班次或拥堵情况"}
        self.legs[key] = result
        return result

    async def optimize_order(self, args):
        if len(set(args.poi_ids)) != len(args.poi_ids):
            raise ToolError("候选地点有重复")
        remaining = [self.place(poi_id) for poi_id in args.poi_ids]
        end = remaining.pop() if args.fixed_end else None
        if args.fixed_start:
            order = [remaining.pop(0)]
        else:
            order = [min(remaining, key=lambda place: sum(haversine(place, other) for other in remaining))]
            remaining.remove(order[0])
        while remaining:
            candidate = min(remaining, key=lambda other: haversine(order[-1], other))
            order.append(candidate)
            remaining.remove(candidate)
        if end:
            order.append(end)
        # Two-opt, preserving any pinned endpoints, with a bounded iteration count.
        for _ in range(20):
            changed = False
            for left in range(1 if args.fixed_start else 0, len(order) - 1):
                for right in range(left + 1, len(order) - (1 if args.fixed_end else 0)):
                    candidate = order[:left] + list(reversed(order[left:right + 1])) + order[right + 1:]
                    if sum(haversine(a, b) for a, b in zip(candidate, candidate[1:])) + 1 < sum(
                            haversine(a, b) for a, b in zip(order, order[1:])):
                        order, changed = candidate, True
            if not changed:
                break
        return {"poiIds": [poi["poiId"] for poi in order], "method": "地理距离启发式候选",
                "requiresRoadValidation": True}

    async def build_itinerary(self, args):
        self.plan = None
        if not self.constraints_set or not self.constraints.city:
            raise ToolError("先调用 set_constraints 确认城市与完整用户约束；未指定城市时请询问")
        c = self.constraints
        visits = [visit for day in args.days for visit in day.visits]
        if len(visits) > 20 or len({visit.poi_id for visit in visits}) != len(visits):
            raise ToolError("全程最多 20 个不同地点，请移除重复或减少地点")
        if any(visit.poi_id == "__origin" for visit in visits):
            raise ToolError("当前位置只能作为出发点，不能作为研学地点")
        if len(args.days) != c.days:
            raise ToolError("行程天数必须符合用户约束；如需要改天数请先向用户说明取舍")
        # Details are mandatory even if search returned seemingly plausible POIs.
        for visit in visits:
            await self.get_place_details(DetailArgs(poiId=visit.poi_id))
        issues, warnings, nodes, days, all_legs = [], [], [], [], []
        selected = [self.place(visit.poi_id) for visit in visits]
        if c.date and self.today <= c.date <= self.today + timedelta(days=3):
            for adcode in dict.fromkeys(poi.get("adcode", "") for poi in selected):
                if re.fullmatch(r"\d{6}", adcode) and adcode not in self.weather:
                    try:
                        await self.get_weather(WeatherArgs(adcode=adcode))
                    except ConfigurationError:
                        raise
                    except UpstreamError:
                        warnings.append("暂未获取到天气预报，请出发前复查")
        # Explicit single-city requests must not silently pick a same-name POI
        # in another city. Multi-city itineraries need separate user agreement.
        city_name = c.city.removesuffix("市")
        for poi in selected:
            if poi.get("cityname") and city_name.isalpha() and all('\u4e00' <= char <= '\u9fff' for char in city_name):
                if city_name != poi["cityname"].removesuffix("市"):
                    issues.append(poi["name"] + "不在需求城市 " + c.city + "，请核对同名地点")
        for term in c.must_visit:
            if not any(matches(term, poi) for poi in selected):
                issues.append("未满足必去地点：" + term)
        for term in c.avoid:
            if any(matches(term, poi) for poi in selected):
                issues.append("包含用户要求避开的地点：" + term)
        estimated_total = 0
        unknown_costs = 0
        for day_index, day in enumerate(args.days):
            day_date = (c.date + timedelta(days=day_index)) if c.date else None
            if day_date and day_date < self.today:
                issues.append("出发日期已经过去，请确认实际出行日期")
            clock = int(c.start_time[:2]) * 60 + int(c.start_time[3:])
            start_clock = clock
            leg_pairs = list(zip([v.poi_id for v in day.visits], [v.poi_id for v in day.visits][1:]))
            if day.start_poi_id:
                if day.start_poi_id != "__origin":
                    await self.get_place_details(DetailArgs(poiId=day.start_poi_id))
                self.place(day.start_poi_id)
                leg_pairs.insert(0, (day.start_poi_id, day.visits[0].poi_id))
            else:
                warnings.append(f"第 {day_index + 1} 天从第一站开始计时，尚未计入出发地/住宿地到第一站的交通")
            if day.end_poi_id:
                if c.return_to_start and day.end_poi_id != (day.start_poi_id or day.visits[0].poi_id):
                    raise ToolError("结束点与返回出发点约束冲突，请确认用户选择")
                if day.end_poi_id != "__origin":
                    await self.get_place_details(DetailArgs(poiId=day.end_poi_id))
                self.place(day.end_poi_id)
                if day.visits[-1].poi_id != day.end_poi_id:
                    leg_pairs.append((day.visits[-1].poi_id, day.end_poi_id))
            elif c.return_to_start:
                return_id = day.start_poi_id or day.visits[0].poi_id
                if day.visits[-1].poi_id != return_id:
                    leg_pairs.append((day.visits[-1].poi_id, return_id))
            if day.modes and len(day.modes) != len(leg_pairs):
                raise ToolError("每日 modes 数量必须与交通段数量相同（含出发与返程）")
            mode_list = day.modes or [c.modes[0]] * len(leg_pairs)
            if any(mode not in c.modes for mode in mode_list):
                issues.append(f"第 {day_index + 1} 天使用了用户不接受的交通方式")
            legs = []
            for (start, end), mode in zip(leg_pairs, mode_list):
                legs.append(await self.calculate_leg(LegArgs(origin=start, destination=end, mode=mode)))
            walking = sum(leg["walkingMeters"] or 0 for leg in legs)
            if any(leg["walkingMeters"] is None for leg in legs):
                warnings.append(f"第 {day_index + 1} 天部分交通步行数据缺失，步行上限无法完全核验")
                if c.max_walking_meters is not None:
                    issues.append("步行上限为硬约束，但交通服务没有返回必要步行数据")
            if c.max_walking_meters is not None and walking > c.max_walking_meters:
                issues.append(f"第 {day_index + 1} 天通勤步行 {walking} 米，超过上限 {c.max_walking_meters} 米")
            # A lunch/rest block goes after the middle visit, rather than disappearing from timings.
            schedule = []
            day_cost = 0
            for index, visit in enumerate(day.visits):
                poi = self.place(visit.poi_id)
                incoming_index = index if day.start_poi_id else index - 1
                incoming = legs[incoming_index] if incoming_index >= 0 else None
                if incoming:
                    clock += incoming["minutes"] + 5  # Transfers / entry buffer.
                windows = opening_windows(poi.get("opentimeToday", "")) if day_date == self.today else []
                if windows:
                    feasible = [(max(clock, left), right) for left, right in windows
                                if max(clock, left) + visit.minutes <= right]
                    if feasible:
                        clock = min(feasible)[0]
                    else:
                        issues.append(poi["name"] + "的计划停留与今日开放时段冲突")
                else:
                    warnings.append(poi["name"] + "的出行日开放时间/预约要求需确认")
                begin, finish = clock, clock + visit.minutes
                schedule.append({"poiId": visit.poi_id, "name": poi["name"], "arrival": clock_text(begin),
                                 "departure": clock_text(finish), "minutes": visit.minutes,
                                 "learningGoal": visit.learning_goal})
                notes = visit.learning_goal
                node = {key: value for key, value in poi.items() if key not in ("averageCost", "source", "checkedAt")}
                node.update(visitOrder=len(nodes) + 1, recommendedDuration=visit.minutes,
                            visitTime=f"{day_date or ('第' + str(day_index + 1) + '天')} {clock_text(begin)}",
                            notes=notes)
                nodes.append(node)
                clock = finish
                if index == (len(day.visits) - 1) // 2 and day.break_minutes:
                    schedule.append({"type": "break", "arrival": clock_text(clock),
                                     "departure": clock_text(clock + day.break_minutes), "minutes": day.break_minutes})
                    clock += day.break_minutes
                cost = visit.estimated_cost
                if cost is None:
                    unknown_costs += 1
                else:
                    day_cost += cost
            if legs and (day.end_poi_id or c.return_to_start) and legs[-1]["destination"] == (
                    day.end_poi_id or day.start_poi_id or day.visits[0].poi_id) and legs[-1]["origin"] == day.visits[-1].poi_id:
                clock += legs[-1]["minutes"] + 5
            for leg in legs:
                if leg["cost"] is not None:
                    day_cost += leg["cost"]
                elif leg["mode"] != "walking":
                    unknown_costs += 1
            elapsed = clock - start_clock
            if elapsed > c.daily_minutes:
                issues.append(f"第 {day_index + 1} 天共需 {elapsed} 分钟（含交通、休息和等待），超过 {c.daily_minutes} 分钟")
            if c.finish_by_time:
                deadline = int(c.finish_by_time[:2]) * 60 + int(c.finish_by_time[3:])
                if clock > deadline:
                    issues.append(f"第 {day_index + 1} 天结束时间 {clock_text(clock)} 晚于要求 {c.finish_by_time}")
            if clock > 1440:
                issues.append(f"第 {day_index + 1} 天跨越午夜，请减少地点或调整安排")
            if c.pace == "relaxed" and day.break_minutes < 30:
                issues.append("轻松节奏需要至少 30 分钟休息，请调整")
            if c.group and any(word in c.group for word in ("儿童", "孩子", "亲子", "老人", "老年")) and day.break_minutes < 30:
                warnings.append("亲子/长者出行建议至少安排 30 分钟休息")
            forecast = next((cast for weather in self.weather.values() for cast in weather.get("casts", [])
                             if cast.get("date") == str(day_date)), None)
            if forecast is None:
                warnings.append(f"第 {day_index + 1} 天天气不在已查询预报内，临近出发需复查")
            elif any(word in forecast.get("dayweather", "") for word in ("雨", "雪", "雷", "风暴")):
                warnings.append(f"第 {day_index + 1} 天预报 {forecast['dayweather']}，建议优先室内点并准备替代方案")
            estimated_total += day_cost
            days.append({"day": day_index + 1, "date": str(day_date) if day_date else None,
                         "schedule": schedule, "totalMinutes": elapsed, "walkingMeters": walking,
                         "estimatedCost": round(day_cost, 2), "legCount": len(legs), "finishTime": clock_text(clock)})
            # Keep separate leg polylines. Do not invent a road between different days.
            all_legs.extend({**leg, "day": day_index + 1} for leg in legs)
        if c.budget is not None and estimated_total > c.budget:
            issues.append(f"已统计每人费用估算 {estimated_total:.0f} 元超过预算 {c.budget:.0f} 元")
        warnings.append("费用为每人估算，非实时门票报价；预约、场馆内步行和未来交通情况需出发前确认")
        if c.max_walking_meters is not None:
            warnings.append("步行上限仅核验地点间通勤；园区和场馆内部步行量尚未计入")
        if unknown_costs:
            warnings.append(f"有 {unknown_costs} 项费用尚未核实，不能保证总费用在预算内")
        if c.accessibility:
            warnings.append("无障碍设施尚未获得运营方确认，请出发前联系场馆；地图可达不代表无障碍可达")
        original_ids = {stop.poi_id for stop in self.request.current_route}
        selected_ids = {visit.poi_id for visit in visits}
        self.plan = {"feasible": not issues, "issues": list(dict.fromkeys(issues)),
                     "warnings": list(dict.fromkeys(warnings)), "days": days, "nodes": nodes,
                     "legs": all_legs, "estimatedCost": round(estimated_total, 2),
                     "unknownCosts": unknown_costs, "addedPoiIds": sorted(selected_ids - original_ids),
                     "removedPoiIds": sorted(original_ids - selected_ids), "requiresAcceptance": True}
        return {key: value for key, value in self.plan.items() if key not in ("nodes", "legs")} | {
            "route": [node["name"] for node in nodes]}

    async def finish_plan(self, args):
        if not self.plan or not self.plan["feasible"]:
            raise ToolError("尚无通过硬约束校验的方案，请先 build_itinerary；无法满足时 ask_user 说明具体冲突")
        self.result = {"kind": "plan", "message": args.summary, "assumptions": args.assumptions,
                       "constraints": self.constraints.model_dump(mode="json", by_alias=True), **self.plan}
        return {"finished": True}

    async def ask_user(self, args):
        self.result = {"kind": "clarification", "message": args.reason,
                       "questions": args.questions, "nodes": [], "legs": [],
                       "constraints": self.constraints.model_dump(mode="json", by_alias=True)}
        return {"finished": True}


def clock_text(minutes):
    return f"{minutes // 60:02d}:{minutes % 60:02d}"
