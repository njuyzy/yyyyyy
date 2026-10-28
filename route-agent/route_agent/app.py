import asyncio
import hashlib
import hmac
import json
import logging
import time
import uuid
from collections import defaultdict, deque
from contextlib import asynccontextmanager
from dataclasses import dataclass, field

import httpx
from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse

from .engine import AgentEngine
from .models import PlanRequest
from .provider import AmapClient, ModelClient, Settings, UpstreamError, fetch_json

log = logging.getLogger("route_agent")


@dataclass
class Run:
    account_id: int
    request_id: str
    fingerprint: str
    memory_id: str
    run_id: str = field(default_factory=lambda: uuid.uuid4().hex)
    created_at: float = field(default_factory=time.monotonic)
    status: str = "queued"
    stage: str = "等待规划"
    events: list = field(default_factory=list)
    result: dict | None = None
    error: str | None = None
    task: asyncio.Task | None = None

    def event(self, event):
        self.stage = event["label"]
        self.events.append(event)
        self.events = self.events[-110:]

    def view(self):
        return {"runId": self.run_id, "status": self.status, "stage": self.stage,
                "events": self.events, "result": self.result, "error": self.error}


def create_app(settings=None, http_client=None, engine_factory=None):
    settings = settings or Settings.from_env()
    runs, idempotency, sessions = {}, {}, {}
    rates = defaultdict(deque)
    lock = asyncio.Lock()
    client = http_client or httpx.AsyncClient(timeout=httpx.Timeout(55, connect=8), follow_redirects=False)

    @asynccontextmanager
    async def lifespan(app):
        settings.validate()
        # Avoid the HTTP library logging full AMAP query URLs containing keys.
        logging.getLogger("httpx").setLevel(logging.WARNING)
        logging.getLogger("httpcore").setLevel(logging.WARNING)
        yield
        active = [run.task for run in runs.values() if run.task and not run.task.done()]
        for task in active:
            task.cancel()
        if active:
            await asyncio.gather(*active, return_exceptions=True)
        if http_client is None:
            await client.aclose()

    app = FastAPI(title="研学路线 Agent", version="1.0", lifespan=lifespan)

    async def identity(request, authorization):
        if not authorization or not authorization.startswith("Bearer "):
            raise HTTPException(401, "请先登录")
        if settings.auth_url:
            try:
                result = await fetch_json(client, "GET", settings.auth_url,
                                          headers={"Authorization": authorization})
            except UpstreamError:
                raise HTTPException(401, "登录验证失败，请重新登录") from None
            data = result.get("data", result)
            if not isinstance(data, dict) or type(data.get("accountId")) is not int or data["accountId"] <= 0:
                raise HTTPException(401, "登录验证失败，请重新登录")
            if "code" in result and result["code"] != 1:
                raise HTTPException(401, "登录验证失败，请重新登录")
            return data["accountId"]
        # The development bypass works ONLY for direct localhost connections;
        # it ignores forwarded headers and must not be enabled behind a proxy.
        host = request.client.host if request.client else ""
        if settings.dev_token and host in ("127.0.0.1", "::1", "testclient") and hmac.compare_digest(
                authorization[7:], settings.dev_token):
            return 1
        raise HTTPException(401, "登录验证失败，请检查 Agent 认证配置")

    def prune():
        now = time.monotonic()
        expired = [run for run in runs.values() if now - run.created_at > 1800 and run.status not in ("queued", "running")]
        for run in expired:
            runs.pop(run.run_id, None)
            idempotency.pop((run.account_id, run.request_id), None)
        for key in list(rates):
            if not rates[key] or now - rates[key][-1] > 120:
                rates.pop(key, None)

    async def execute(run, payload):
        run.status = "running"
        try:
            engine = engine_factory() if engine_factory else AgentEngine(
                ModelClient(client, settings), AmapClient(client, settings.amap_key), settings)
            run.result = await engine.run(payload, run.event)
            run.status, run.stage = "completed", "方案已完成" if run.result["kind"] == "plan" else "等待你补充需求"
        except asyncio.CancelledError:
            run.status, run.stage = "cancelled", "规划已取消"
        except TimeoutError:
            run.status, run.error = "failed", "本次规划超时，请减少地点或明确优先级后重试"
        except UpstreamError as exc:
            run.status, run.error = "failed", str(exc)
        except Exception:
            # Log only a generated run ID, not user prompts, tokens or map keys.
            log.error("Agent run failed: %s", run.run_id)
            run.status, run.error = "failed", "路线规划暂时失败，请稍后重试"
        finally:
            sessions.pop((run.account_id, payload.memory_id), None)

    @app.exception_handler(HTTPException)
    async def error_handler(request, exception):
        return JSONResponse({"error": exception.detail}, status_code=exception.status_code)

    @app.get("/health")
    async def health():
        return {"status": "ok", "version": "1.0"}

    @app.post("/routes/agent/runs", status_code=202)
    async def start(payload: PlanRequest, request: Request, authorization: str | None = Header(default=None)):
        account = await identity(request, authorization)
        if account != payload.account_id:
            raise HTTPException(403, "账号与登录身份不一致")
        async with lock:
            prune()
            key = (account, payload.request_id)
            fingerprint = hashlib.sha256(json.dumps(payload.model_dump(mode="json"), sort_keys=True).encode()).hexdigest()
            if key in idempotency:
                run = runs[idempotency[key]]
                if run.fingerprint != fingerprint:
                    raise HTTPException(409, "同一请求编号不能用于不同需求")
                return run.view()
            if (account, payload.memory_id) in sessions:
                raise HTTPException(409, "本轮对话仍在规划，请等待或取消")
            if sum(run.status in ("queued", "running") for run in runs.values()) >= settings.max_active:
                raise HTTPException(429, "路线助手正在忙，请稍后重试")
            if len(runs) >= 500:
                raise HTTPException(429, "路线助手正在忙，请稍后重试")
            now = time.monotonic()
            bucket = rates[account]
            while bucket and now - bucket[0] > 60:
                bucket.popleft()
            if len(bucket) >= 6:
                raise HTTPException(429, "请求过于频繁，请稍后重试")
            bucket.append(now)
            run = Run(account, payload.request_id, fingerprint, payload.memory_id)
            runs[run.run_id], idempotency[key] = run, run.run_id
            sessions[(account, payload.memory_id)] = run.run_id
            run.task = asyncio.create_task(execute(run, payload))
            return run.view()

    async def owned_run(run_id, request, authorization):
        account = await identity(request, authorization)
        prune()
        run = runs.get(run_id)
        if not run or run.account_id != account:
            raise HTTPException(404, "规划记录已过期，请重新发送需求")
        return run

    @app.get("/routes/agent/runs/{run_id}")
    async def status(run_id: str, request: Request, authorization: str | None = Header(default=None)):
        return (await owned_run(run_id, request, authorization)).view()

    @app.delete("/routes/agent/runs/{run_id}")
    async def cancel(run_id: str, request: Request, authorization: str | None = Header(default=None)):
        run = await owned_run(run_id, request, authorization)
        if run.task and not run.task.done():
            run.task.cancel()
            await asyncio.gather(run.task, return_exceptions=True)
            # Also handles cancellation before the coroutine first starts.
            run.status, run.stage = "cancelled", "规划已取消"
            sessions.pop((run.account_id, run.memory_id), None)
        return run.view()

    return app


app = create_app()
