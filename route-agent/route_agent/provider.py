import asyncio
import json
import os
import random
from dataclasses import dataclass

import httpx


class UpstreamError(Exception):
    """Only this safe text is exposed to the client, never URLs or credentials."""


class ConfigurationError(UpstreamError):
    """Provider credential/permission problems cannot be repaired by replanning."""


async def fetch_json(client, method, url, **kwargs):
    for attempt in range(3):
        try:
            response = await client.request(method, url, **kwargs)
            if response.status_code in (429, 500, 502, 503, 504) and attempt < 2:
                await asyncio.sleep(0.4 * 2**attempt + random.random() * 0.2)
                continue
            response.raise_for_status()
            result = response.json()
            if not isinstance(result, dict):
                raise ValueError("invalid envelope")
            return result
        except (httpx.HTTPError, ValueError) as exc:
            if isinstance(exc, (httpx.ConnectError, httpx.TimeoutException)) and attempt < 2:
                await asyncio.sleep(0.4 * 2**attempt)
                continue
            raise UpstreamError("外部服务暂时不可用，请稍后重试") from None


@dataclass(frozen=True)
class Settings:
    llm_url: str = "https://api.openai.com/v1"
    llm_key: str = ""
    model: str = ""
    protocol: str = "responses"
    amap_key: str = ""
    auth_url: str = ""
    dev_token: str = ""
    max_rounds: int = 14
    max_tools: int = 48
    deadline_seconds: int = 240
    max_active: int = 8

    @classmethod
    def from_env(cls):
        return cls(llm_url=os.getenv("AGENT_LLM_BASE_URL", "https://api.openai.com/v1").rstrip("/"),
                   llm_key=os.getenv("AGENT_LLM_API_KEY", ""), model=os.getenv("AGENT_LLM_MODEL", ""),
                   protocol=os.getenv("AGENT_LLM_PROTOCOL", "responses"),
                   amap_key=os.getenv("AMAP_WEB_API_KEY", ""), auth_url=os.getenv("AGENT_AUTH_URL", ""),
                   dev_token=os.getenv("AGENT_DEV_TOKEN", ""))

    def validate(self):
        if not self.llm_key or not self.model or not self.amap_key:
            raise RuntimeError("配置 AGENT_LLM_API_KEY、AGENT_LLM_MODEL 和 AMAP_WEB_API_KEY 后再启动")
        if self.protocol not in ("responses", "chat_completions"):
            raise RuntimeError("AGENT_LLM_PROTOCOL must be responses or chat_completions")
        if not self.auth_url and not self.dev_token:
            raise RuntimeError("生产环境需配置 AGENT_AUTH_URL；本机调试可配置 AGENT_DEV_TOKEN")


class ModelClient:
    """Both protocols run a real tool-result feedback loop, with no shell tools."""

    def __init__(self, client, settings):
        self.client, self.settings = client, settings

    async def complete(self, messages, tools):
        body = {"model": self.settings.model, "tools": tools, "tool_choice": "auto"}
        if self.settings.protocol == "responses":
            body.update(input=messages, store=False, max_output_tokens=5000)
            suffix = "/responses"
        else:
            body.update(messages=messages, max_completion_tokens=5000)
            suffix = "/chat/completions"
        data = await fetch_json(self.client, "POST", self.settings.llm_url + suffix,
                                headers={"Authorization": "Bearer " + self.settings.llm_key}, json=body)
        try:
            if self.settings.protocol == "responses":
                output = data["output"]
                calls = [{"id": item["call_id"], "name": item["name"], "arguments": item["arguments"]}
                         for item in output if item.get("type") == "function_call"]
                text = "\n".join(part.get("text", "") for item in output if item.get("type") == "message"
                                 for part in item.get("content", []) if part.get("type") == "output_text")
                return output, calls, text
            message = data["choices"][0]["message"]
            clean = {"role": "assistant", "content": message.get("content")}
            if message.get("tool_calls"):
                clean["tool_calls"] = message["tool_calls"]
            calls = [{"id": call["id"], "name": call["function"]["name"],
                      "arguments": call["function"]["arguments"]} for call in message.get("tool_calls") or []]
            return [clean], calls, message.get("content") or ""
        except (KeyError, TypeError, IndexError):
            raise UpstreamError("模型返回了无法解析的结果，请重试") from None

    def tool_result(self, call_id, result):
        content = json.dumps(result, ensure_ascii=False, allow_nan=False)
        if self.settings.protocol == "responses":
            return {"type": "function_call_output", "call_id": call_id, "output": content}
        return {"role": "tool", "tool_call_id": call_id, "content": content}


class AmapClient:
    BASE = "https://restapi.amap.com"

    def __init__(self, client, key):
        self.client, self.key = client, key
        self.cache = {}
        self.requests = 0

    async def get(self, path, **params):
        key = (path, tuple(sorted(params.items())))
        if key in self.cache:
            return self.cache[key]
        # Includes map calls made inside scheduling, not just model tool calls.
        self.requests += 1
        if self.requests > 90:
            raise UpstreamError("本次地图查询已达上限，请减少地点后重新规划")
        result = await fetch_json(self.client, "GET", self.BASE + path, params={"key": self.key, **params})
        if str(result.get("status")) != "1":
            code = str(result.get("infocode", "unknown"))
            if code in ("10001", "10002", "10008", "10009"):
                raise ConfigurationError("高德服务鉴权失败，请管理员检查服务端 Web 服务 Key 配置")
            if code in ("10003", "10004", "10044", "10045"):
                raise UpstreamError("地图服务查询配额不足，请稍后重试")
            raise UpstreamError("地图服务暂未找到有效数据，请调整地点或交通方式")
        self.cache[key] = result
        return result
