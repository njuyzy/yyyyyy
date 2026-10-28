import asyncio
import json
import unittest
from datetime import timedelta

import httpx
from fastapi.testclient import TestClient
from pydantic import ValidationError

from route_agent.app import create_app
from route_agent.engine import AgentEngine
from route_agent.models import (Constraints, DetailArgs, FinishArgs, LegArgs,
                                OptimizeArgs, PlanRequest, ScheduleArgs, SearchArgs)
from route_agent.provider import AmapClient, ModelClient, Settings, UpstreamError
from route_agent.tools import RouteTools, ToolError, opening_windows


def payload(**changes):
    data = {"requestId": "req1", "accountId": 1, "memoryId": "conversation1", "message": "南京博物馆研学"}
    data.update(changes)
    return data


def raw_place(poi_id, **changes):
    data = {"id": poi_id, "name": "博物馆" + poi_id, "location": "118.790000,32.060000",
            "cityname": "南京市", "citycode": "025", "adcode": "320102", "address": "测试地址",
            "business": {"opentime_today": "09:00-17:00", "cost": "80"}}
    if poi_id == "B":
        data["location"] = "118.800000,32.060000"
    if poi_id == "C":
        data["location"] = "118.810000,32.060000"
    data.update(changes)
    return data


class FakeMap:
    def __init__(self, minutes=20, walking=1200, raw=None, fail_roads=False):
        self.minutes, self.walking, self.raw, self.fail_roads = minutes, walking, raw or {}, fail_roads
        self.requests = []

    async def get(self, path, **params):
        self.requests.append((path, params))
        if "place/detail" in path:
            return {"pois": [self.raw.get(params["id"], raw_place(params["id"]))]}
        if "place/text" in path:
            return {"pois": [raw_place("A"), raw_place("B")]}
        if "direction" in path:
            if self.fail_roads:
                return {"route": {"paths": []}}
            return {"route": {"paths": [{"duration": str(self.minutes * 60), "distance": str(self.walking),
                                          "steps": [{"polyline": "118.790,32.060;118.795,32.063;118.800,32.060"}]}]}}
        return {}


def schedule(*ids, minutes=60, mode="walking", **day_changes):
    day = {"visits": [{"poiId": poi_id, "minutes": minutes, "learningGoal": "观察和记录"} for poi_id in ids],
           "breakMinutes": 30}
    day.update(day_changes)
    return ScheduleArgs(days=[day])


class ToolTests(unittest.IsolatedAsyncioTestCase):
    def tools(self, **map_changes):
        return RouteTools(PlanRequest(**payload()), FakeMap(**map_changes))

    async def test_cannot_finish_without_validation(self):
        with self.assertRaises(ToolError):
            await self.tools().finish_plan(FinishArgs(summary="凭空路线"))

    async def test_details_required_and_actual_roads_used(self):
        tools = self.tools()
        await tools.set_constraints(Constraints(city="南京", dailyMinutes=240, modes=["walking"]))
        result = await tools.build_itinerary(schedule("A", "B"))
        self.assertTrue(result["feasible"])
        self.assertEqual(175, result["days"][0]["totalMinutes"])
        self.assertEqual(1200, result["days"][0]["walkingMeters"])
        self.assertEqual(["/v5/place/detail", "/v5/place/detail", "/v3/direction/walking"],
                         [item[0] for item in tools.amap.requests])
        await tools.finish_plan(FinishArgs(summary="已核验方案"))
        self.assertEqual("plan", tools.result["kind"])
        self.assertEqual("118.790000,32.060000", tools.result["nodes"][0]["location"])
        self.assertTrue(tools.result["requiresAcceptance"])

    async def test_duration_conflict_and_repair(self):
        tools = self.tools(minutes=45)
        await tools.set_constraints(Constraints(city="南京", dailyMinutes=120))
        result = await tools.build_itinerary(schedule("A", "B"))
        self.assertFalse(result["feasible"])
        self.assertTrue(any("超过 120" in issue for issue in result["issues"]))
        with self.assertRaises(ToolError):
            await tools.finish_plan(FinishArgs(summary="不能假称成功"))
        result = await tools.build_itinerary(schedule("A", "B", minutes=20))
        self.assertTrue(result["feasible"])

    async def test_walking_limit(self):
        tools = self.tools()
        await tools.set_constraints(Constraints(city="南京", maxWalkingMeters=500))
        result = await tools.build_itinerary(schedule("A", "B"))
        self.assertFalse(result["feasible"])
        self.assertTrue(any("步行" in issue for issue in result["issues"]))

    async def test_missing_poi_or_bad_coordinate_not_fabricated(self):
        tools = self.tools(raw={"A": raw_place("A", location="NaN,32")})
        with self.assertRaises(ToolError):
            await tools.get_place_details(DetailArgs(poiId="A"))
        with self.assertRaises(ToolError):
            await tools.calculate_leg(LegArgs(origin="A", destination="B", mode="walking"))

    async def test_missing_road_never_falls_back_to_line(self):
        tools = self.tools(fail_roads=True)
        await tools.set_constraints(Constraints(city="南京"))
        with self.assertRaises(ToolError):
            await tools.build_itinerary(schedule("A", "B"))
        self.assertIsNone(tools.plan)

    async def test_must_visit_and_avoid(self):
        tools = self.tools()
        await tools.set_constraints(Constraints(city="南京", mustVisit=["博物馆C"], avoid=["博物馆B"]))
        result = await tools.build_itinerary(schedule("A", "B"))
        self.assertEqual(2, len(result["issues"]))

    async def test_budget_is_not_certified_when_cost_unknown(self):
        tools = self.tools()
        await tools.set_constraints(Constraints(city="南京", budget=100))
        result = await tools.build_itinerary(schedule("A", "B"))
        self.assertTrue(result["feasible"])
        self.assertEqual(2, result["unknownCosts"])
        self.assertTrue(any("不能保证" in warning for warning in result["warnings"]))
        arg = ScheduleArgs(days=[{"visits": [{"poiId": "A", "minutes": 60, "estimatedCost": 120}]}])
        result = await tools.build_itinerary(arg)
        self.assertFalse(result["feasible"])

    async def test_opening_wait_counts_in_time(self):
        tools = self.tools(raw={"A": raw_place("A", business={"opentime_today": "10:00-12:00"})})
        await tools.set_constraints(Constraints(city="南京", date=tools.today, dailyMinutes=100))
        result = await tools.build_itinerary(schedule("A", minutes=30))
        self.assertFalse(result["feasible"])
        self.assertEqual("10:00", result["days"][0]["schedule"][0]["arrival"])
        self.assertEqual(120, result["days"][0]["totalMinutes"])

    async def test_closed_hours_conflict(self):
        tools = self.tools(raw={"A": raw_place("A", business={"opentime_today": "08:00-08:30"})})
        await tools.set_constraints(Constraints(city="南京", date=tools.today))
        result = await tools.build_itinerary(schedule("A"))
        self.assertFalse(result["feasible"])

    async def test_tomorrow_does_not_reuse_today_hours(self):
        tools = self.tools(raw={"A": raw_place("A", business={"opentime_today": "08:00-08:30"})})
        await tools.set_constraints(Constraints(city="南京", date=tools.today + timedelta(days=1)))
        result = await tools.build_itinerary(schedule("A"))
        self.assertTrue(result["feasible"])
        self.assertTrue(any("开放时间" in warning for warning in result["warnings"]))

    async def test_constraint_change_invalidates_plan(self):
        tools = self.tools()
        await tools.set_constraints(Constraints(city="南京"))
        await tools.build_itinerary(schedule("A"))
        await tools.set_constraints(Constraints(city="南京", dailyMinutes=30))
        with self.assertRaises(ToolError):
            await tools.finish_plan(FinishArgs(summary="旧结果不能交付"))

    async def test_partial_update_keeps_existing_user_preferences(self):
        tools = self.tools()
        await tools.set_constraints(Constraints(city="南京", budget=120, mustVisit=["博物馆A"], dailyMinutes=180))
        await tools.set_constraints(Constraints(pace="relaxed"))
        self.assertEqual("南京", tools.constraints.city)
        self.assertEqual(120, tools.constraints.budget)
        self.assertEqual(180, tools.constraints.daily_minutes)
        self.assertEqual(["博物馆A"], tools.constraints.must_visit)

    async def test_round_trip_counted_once(self):
        tools = self.tools()
        await tools.set_constraints(Constraints(city="南京", returnToStart=True))
        result = await tools.build_itinerary(schedule("A", "B"))
        self.assertEqual(200, result["days"][0]["totalMinutes"])
        self.assertEqual("09:00", result["days"][0]["schedule"][0]["arrival"])

    async def test_start_and_end_legs_and_finish_deadline(self):
        tools = self.tools()
        await tools.set_constraints(Constraints(city="南京", dailyMinutes=240, finishByTime="11:00"))
        result = await tools.build_itinerary(schedule("B", startPoiId="A", endPoiId="C", minutes=60))
        self.assertFalse(result["feasible"])
        self.assertEqual(140, result["days"][0]["totalMinutes"])
        self.assertEqual("09:25", result["days"][0]["schedule"][0]["arrival"])
        self.assertEqual(2, result["days"][0]["legCount"])

    async def test_same_name_wrong_city_blocked(self):
        tools = self.tools(raw={"A": raw_place("A", cityname="北京市")})
        await tools.set_constraints(Constraints(city="南京"))
        result = await tools.build_itinerary(schedule("A"))
        self.assertFalse(result["feasible"])
        self.assertTrue(any("不在需求城市" in issue for issue in result["issues"]))

    async def test_multiple_days_keep_separate_legs(self):
        tools = self.tools()
        await tools.set_constraints(Constraints(city="南京", days=2))
        result = await tools.build_itinerary(ScheduleArgs(days=[
            {"visits": [{"poiId": "A", "minutes": 60}, {"poiId": "B", "minutes": 60}]},
            {"visits": [{"poiId": "C", "minutes": 60}]}]))
        self.assertTrue(result["feasible"])
        self.assertEqual(2, len(result["days"]))
        self.assertEqual(1, len(tools.plan["legs"]))
        self.assertEqual(1, tools.plan["legs"][0]["day"])

    async def test_removal_only_in_proposal(self):
        request = PlanRequest(**payload(currentRoute=[{"poiId": "B", "name": "原有地点"}]))
        tools = RouteTools(request, FakeMap())
        await tools.set_constraints(Constraints(city="南京"))
        result = await tools.build_itinerary(schedule("A"))
        self.assertEqual(["B"], result["removedPoiIds"])
        self.assertTrue(result["requiresAcceptance"])
        self.assertEqual("B", request.current_route[0].poi_id)

    async def test_optimize_keeps_fixed_endpoints(self):
        tools = self.tools()
        for poi_id in ("A", "B", "C"):
            await tools.get_place_details(DetailArgs(poiId=poi_id))
        result = await tools.optimize_order(OptimizeArgs(poiIds=["A", "C", "B"], fixedEnd=True))
        self.assertEqual("A", result["poiIds"][0])
        self.assertEqual("B", result["poiIds"][-1])
        self.assertTrue(result["requiresRoadValidation"])

    async def test_no_city_requires_clarification(self):
        with self.assertRaises(ToolError):
            await self.tools().build_itinerary(schedule("A"))

    async def test_duplicate_stops_rejected(self):
        tools = self.tools()
        await tools.set_constraints(Constraints(city="南京"))
        with self.assertRaises(ToolError):
            await tools.build_itinerary(schedule("A", "A"))

    def test_opening_parser_does_not_guess_weekly(self):
        self.assertEqual([], opening_windows("周一闭馆，周二至周日 09:00-17:00"))
        self.assertEqual([(540, 720), (780, 1020)], opening_windows("09:00-12:00,13:00-17:00"))

    def test_input_limits_and_nonfinite(self):
        with self.assertRaises(ValidationError):
            PlanRequest(**payload(message="a" * 4001))
        with self.assertRaises(ValidationError):
            Constraints(budget=float("nan"))


class FakeModel:
    def __init__(self, rounds):
        self.rounds = iter(rounds)
        self.feedback = []

    async def complete(self, messages, tools):
        calls = next(self.rounds)
        return [{"role": "assistant", "content": "", "tool_calls": []}], calls, ""

    def tool_result(self, call_id, result):
        self.feedback.append(result)
        return {"role": "tool", "tool_call_id": call_id, "content": json.dumps(result)}


def call(name, **args):
    return {"id": name, "name": name, "arguments": json.dumps(args)}


class EngineTests(unittest.IsolatedAsyncioTestCase):
    async def test_multi_round_feedback_loop_and_terminal_tools(self):
        model = FakeModel([[call("set_constraints", city="南京")],
                           [call("build_itinerary", days=[{"visits": [{"poiId": "A", "minutes": 60}]}])],
                           [call("finish_plan", summary="适合半日研学")]])
        events = []
        result = await AgentEngine(model, FakeMap(), Settings()).run(PlanRequest(**payload()), events.append)
        self.assertEqual("plan", result["kind"])
        self.assertEqual(3, len(model.feedback))
        self.assertNotIn("polyline", model.feedback[1]["data"])
        self.assertEqual(7, len(events))

    async def test_bad_json_and_unknown_tools_recover(self):
        model = FakeModel([[{"id": "bad", "name": "search_places", "arguments": "not json"}],
                           [call("unregistered_tool")],
                           [call("ask_user", questions=["你计划在哪个城市？"], reason="先确认城市")]])
        result = await AgentEngine(model, FakeMap(), Settings()).run(PlanRequest(**payload()))
        self.assertEqual("clarification", result["kind"])
        self.assertFalse(model.feedback[0]["ok"])
        self.assertFalse(model.feedback[1]["ok"])
        self.assertEqual([], result["nodes"])

    async def test_explicit_time_cannot_be_relaxed(self):
        model = FakeModel([[call("set_constraints", city="南京", dailyMinutes=480)],
                           [call("ask_user", questions=["4 小时内要优先保留哪两个地点？"], reason="确认优先级")]])
        await AgentEngine(model, FakeMap(), Settings()).run(PlanRequest(**payload(message="行程控制在4小时")))
        self.assertFalse(model.feedback[0]["ok"])

    async def test_loop_budget_enforced(self):
        model = FakeModel([[call("unknown")]] * 5)
        with self.assertRaises(UpstreamError):
            await AgentEngine(model, FakeMap(), Settings(max_tools=2)).run(PlanRequest(**payload()))


class ProviderTests(unittest.IsolatedAsyncioTestCase):
    async def test_amap_error_no_credential_leak(self):
        async with httpx.AsyncClient(transport=httpx.MockTransport(
                lambda request: httpx.Response(200, json={"status": "0", "infocode": "10001"}))) as client:
            amap = AmapClient(client, "PRIVATE_SECRET")
            with self.assertRaises(UpstreamError) as error:
                await amap.get("/v3/place/text", keywords="博物馆")
            self.assertNotIn("PRIVATE_SECRET", str(error.exception))
            self.assertIn("Web", str(error.exception))

    async def test_map_cache_and_budget(self):
        count = 0
        def reply(request):
            nonlocal count
            count += 1
            return httpx.Response(200, json={"status": "1", "pois": []})
        async with httpx.AsyncClient(transport=httpx.MockTransport(reply)) as client:
            amap = AmapClient(client, "test")
            await amap.get("/v3/place/text", keywords="博物馆")
            await amap.get("/v3/place/text", keywords="博物馆")
            self.assertEqual(1, count)
            amap.requests = 90
            with self.assertRaises(UpstreamError):
                await amap.get("/v3/place/text", keywords="不同地点")

    async def test_responses_protocol(self):
        seen = []
        def reply(request):
            seen.append(json.loads(request.content))
            return httpx.Response(200, json={"output": [
                {"type": "reasoning", "id": "rs1", "summary": []},
                {"type": "function_call", "call_id": "c1", "name": "ask_user", "arguments": "{}"}]})
        async with httpx.AsyncClient(transport=httpx.MockTransport(reply)) as client:
            model = ModelClient(client, Settings(llm_key="test", model="configured"))
            output, calls, text = await model.complete([{"role": "user", "content": "hello"}], [])
            self.assertEqual("reasoning", output[0]["type"])
            self.assertEqual("c1", calls[0]["id"])
            self.assertFalse(seen[0]["store"])
            self.assertEqual("function_call_output", model.tool_result("c1", {"ok": True})["type"])

    async def test_chat_protocol(self):
        async with httpx.AsyncClient(transport=httpx.MockTransport(lambda request: httpx.Response(200, json={
                "choices": [{"message": {"role": "assistant", "content": None, "tool_calls": [
                    {"id": "c1", "type": "function", "function": {"name": "ask_user", "arguments": "{}"}}]}}]}))) as client:
            model = ModelClient(client, Settings(llm_key="test", model="configured", protocol="chat_completions"))
            output, calls, text = await model.complete([], [])
            self.assertEqual("ask_user", calls[0]["name"])
            self.assertEqual("tool", model.tool_result("c1", {"ok": True})["role"])


class BlockingEngine:
    async def run(self, request, progress):
        progress({"label": "测试工具", "status": "running"})
        await asyncio.sleep(30)


class ApiTests(unittest.TestCase):
    def setUp(self):
        self.settings = Settings(llm_key="test", model="test", amap_key="test", dev_token="local-test")
        self.client = TestClient(create_app(self.settings, engine_factory=BlockingEngine))
        self.client.__enter__()
        self.headers = {"Authorization": "Bearer local-test"}

    def tearDown(self):
        self.client.__exit__(None, None, None)

    def test_auth_required_and_account_not_trusted(self):
        self.assertEqual(401, self.client.post("/routes/agent/runs", json=payload()).status_code)
        self.assertEqual(403, self.client.post("/routes/agent/runs", json=payload(accountId=2), headers=self.headers).status_code)

    def test_idempotency_no_duplicate_and_conflicting_payload(self):
        first = self.client.post("/routes/agent/runs", json=payload(), headers=self.headers)
        second = self.client.post("/routes/agent/runs", json=payload(), headers=self.headers)
        self.assertEqual(202, first.status_code)
        self.assertEqual(first.json()["runId"], second.json()["runId"])
        self.assertEqual(409, self.client.post("/routes/agent/runs", json=payload(message="变更需求"), headers=self.headers).status_code)

    def test_cancellation_releases_conversation(self):
        run = self.client.post("/routes/agent/runs", json=payload(), headers=self.headers).json()
        cancelled = self.client.delete("/routes/agent/runs/" + run["runId"], headers=self.headers)
        self.assertEqual("cancelled", cancelled.json()["status"])
        fresh = self.client.post("/routes/agent/runs", json=payload(requestId="req2"), headers=self.headers)
        self.assertEqual(202, fresh.status_code)

    def test_one_active_run_per_conversation(self):
        self.client.post("/routes/agent/runs", json=payload(), headers=self.headers)
        self.assertEqual(409, self.client.post("/routes/agent/runs", json=payload(requestId="req2"), headers=self.headers).status_code)

    def test_other_user_cannot_read_or_cancel(self):
        # Production verifier must return the authenticated subject, not request accountId.
        settings = Settings(llm_key="test", model="test", amap_key="test", auth_url="https://auth.test/me")
        auth = httpx.AsyncClient(transport=httpx.MockTransport(lambda req: httpx.Response(200, json={
            "accountId": 1 if req.headers["Authorization"] == "Bearer one" else 2})))
        with TestClient(create_app(settings, auth, engine_factory=BlockingEngine)) as client:
            run = client.post("/routes/agent/runs", json=payload(), headers={"Authorization": "Bearer one"}).json()
            for method in (client.get, client.delete):
                response = method("/routes/agent/runs/" + run["runId"], headers={"Authorization": "Bearer two"})
                self.assertEqual(404, response.status_code)

    def test_invalid_input_rejected(self):
        self.assertEqual(422, self.client.post("/routes/agent/runs", json=payload(message=""), headers=self.headers).status_code)


if __name__ == "__main__":
    unittest.main()
