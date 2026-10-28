package com.example.Japp.network.agent;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import retrofit2.Response;

/** Agent error bodies are separate from the legacy Result envelope. Never display input values. */
final class AgentApiErrors {
    private AgentApiErrors() {}

    static String message(Response<?> response, boolean knownRun) {
        int code = response.code();
        if (code == 401) return "登录已失效，请重新登录";
        if (code == 403) return "登录账号与本次请求不一致，请重新登录";
        if (code == 404) return knownRun
                ? "规划记录已失效或无法访问，请重新发送需求"
                : "路线助手暂不可用，请稍后重试";
        JsonObject error = null;
        try {
            if (response.errorBody() != null) {
                JsonElement element = JsonParser.parseString(response.errorBody().string());
                if (element.isJsonObject()) error = element.getAsJsonObject();
            }
        } catch (Exception ignored) {}
        if (code == 422) {
            if (error != null && error.has("detail") && error.get("detail").isJsonArray()) {
                for (JsonElement detail : error.getAsJsonArray("detail")) {
                    if (!detail.isJsonObject()) continue;
                    JsonElement locations = detail.getAsJsonObject().get("loc");
                    if (locations == null || !locations.isJsonArray()) continue;
                    JsonArray loc = locations.getAsJsonArray();
                    for (JsonElement field : loc) {
                        if (field.isJsonPrimitive() && "memoryId".equals(field.getAsString()))
                            return "这轮对话标识已失效，请新建对话后重试";
                        if (field.isJsonPrimitive() && "currentRoute".equals(field.getAsString()))
                            return "当前路线数据不符合要求，请检查地点后重试";
                        if (field.isJsonPrimitive() && "message".equals(field.getAsString()))
                            return "请填写不超过 4000 字的路线需求";
                    }
                }
            }
            return "请求格式不符合要求，请更新应用或调整需求后重试";
        }
        if (code == 409) return "这轮对话仍有任务在处理，请等待完成或取消后重试";
        if (code == 429) return "路线助手正在忙，请稍后重试";
        return "暂时无法完成路线规划，请稍后重试";
    }
}
