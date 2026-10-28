package com.example.Japp.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.Japp.network.models.Result;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import retrofit2.Response;

/** Reads the backend's unified {code,msg,data} error response. */
public final class ResponseMessageReader {

    private ResponseMessageReader() {}

    @NonNull
    public static String read(@Nullable Response<?> response, @NonNull String fallback) {
        if (response != null && response.body() instanceof Result) {
            String message = ((Result<?>) response.body()).getMsg();
            if (hasText(message)) {
                return message.trim();
            }
        }
        if (response != null && response.errorBody() != null) {
            try {
                String raw = response.errorBody().string();
                if (hasText(raw)) {
                    JsonElement element = JsonParser.parseString(raw);
                    if (element.isJsonObject() && element.getAsJsonObject().has("msg")
                            && !element.getAsJsonObject().get("msg").isJsonNull()) {
                        String message = element.getAsJsonObject().get("msg").getAsString();
                        if (hasText(message)) {
                            return message.trim();
                        }
                    }
                }
            } catch (Exception ignored) {
                // Fall through to the caller-provided message.
            }
        }
        return fallback;
    }

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }
}
