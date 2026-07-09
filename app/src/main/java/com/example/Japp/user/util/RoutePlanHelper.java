package com.example.Japp.user.util;

import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.example.Japp.network.models.Result;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.net.SocketTimeoutException;

import retrofit2.Response;

public final class RoutePlanHelper {

    private RoutePlanHelper() {}

    public static String buildMemoryId(int accountId) {
        return "user-" + accountId;
    }

    public static int parseRouteId(@Nullable JsonElement data) {
        if (data == null || data.isJsonNull()) {
            return -1;
        }
        // 兼容后端多种返回：纯数字 / {"routeId":n} / {"id":n} / {"data":{...}}
        if (data.isJsonPrimitive()) {
            return parsePrimitiveInt(data.getAsJsonPrimitive());
        }
        if (data.isJsonObject()) {
            JsonObject obj = data.getAsJsonObject();
            int fromRouteId = readIntField(obj, "routeId");
            if (fromRouteId > 0) {
                return fromRouteId;
            }
            int fromId = readIntField(obj, "id");
            if (fromId > 0) {
                return fromId;
            }
            int fromSnake = readIntField(obj, "route_id");
            if (fromSnake > 0) {
                return fromSnake;
            }
            if (obj.has("data") && !obj.get("data").isJsonNull()) {
                return parseRouteId(obj.get("data"));
            }
            if (obj.has("route") && obj.get("route").isJsonObject()) {
                return parseRouteId(obj.get("route"));
            }
        }
        return -1;
    }

    private static int readIntField(JsonObject obj, String field) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) {
            return -1;
        }
        return parsePrimitiveInt(obj.get(field).getAsJsonPrimitive());
    }

    private static int parsePrimitiveInt(@Nullable JsonPrimitive primitive) {
        if (primitive == null) {
            return -1;
        }
        try {
            if (primitive.isNumber()) {
                return primitive.getAsInt();
            }
            if (primitive.isString()) {
                String value = primitive.getAsString();
                if (value == null || value.trim().isEmpty()) {
                    return -1;
                }
                return Integer.parseInt(value.trim());
            }
        } catch (Exception ignored) {
            return -1;
        }
        return -1;
    }

    public static String failureMessage(Throwable t) {
        if (t instanceof SocketTimeoutException) {
            return "路线规划超时，请稍后重试";
        }
        String message = t.getMessage();
        if (message == null || message.isEmpty()) {
            return "网络错误，路线规划失败";
        }
        if (message.contains("timeout") || message.contains("Timeout")) {
            return "路线规划超时，请稍后重试";
        }
        return "网络错误，路线规划失败";
    }

    public static String readErrorMessage(@Nullable Response<?> response,
                                          @Nullable Result<?> body) {
        if (body != null && !TextUtils.isEmpty(body.getMsg())) {
            return normalizeServerMessage(body.getMsg());
        }
        if (response != null && response.errorBody() != null) {
            try {
                String raw = response.errorBody().string();
                if (!TextUtils.isEmpty(raw)) {
                    JsonElement element = JsonParser.parseString(raw);
                    if (element.isJsonObject() && element.getAsJsonObject().has("msg")) {
                        String msg = element.getAsJsonObject().get("msg").getAsString();
                        if (!TextUtils.isEmpty(msg)) {
                            return normalizeServerMessage(msg);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (response != null) {
            if (response.code() == 401) {
                return "登录已失效，请重新登录";
            }
            if (response.code() == 404) {
                return "规划接口不存在，请联系管理员";
            }
            if (response.code() >= 500) {
                return "路线规划服务暂时不可用，请稍后再试或联系后端同学检查 AI 规划服务";
            }
            if (!response.isSuccessful()) {
                return "路线规划失败(" + response.code() + ")";
            }
        }
        return "路线规划失败";
    }

    private static String normalizeServerMessage(String msg) {
        if (msg == null) {
            return "路线规划失败";
        }
        String trimmed = msg.trim();
        if (trimmed.contains("系统繁忙") || trimmed.contains("服务器繁忙")) {
            return "路线规划服务暂时不可用（后端返回：系统繁忙），请稍后重试或检查 AI 规划服务";
        }
        return trimmed;
    }
}
