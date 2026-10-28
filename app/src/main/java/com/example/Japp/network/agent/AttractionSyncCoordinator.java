package com.example.Japp.network.agent;

import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Result;
import com.example.Japp.network.models.RouteNode;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Await successful POI sync before applying a proposal or saving a manual route. */
public final class AttractionSyncCoordinator {
    public interface Listener {
        default void onProgress(int completed, int total) {}
        void onComplete();
        void onError(String message, boolean unauthorized);
    }

    private final UserService service;
    private final Set<Call<Result<JsonElement>>> pending = new LinkedHashSet<>();
    private List<String> ids = new ArrayList<>();
    private Listener listener;
    private int generation;
    private int next;
    private int completed;

    public AttractionSyncCoordinator(UserService service) { this.service = service; }

    public void sync(List<RouteNode> nodes, Listener listener) {
        cancel();
        this.listener = listener;
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (RouteNode node : nodes) {
            if (node == null || node.getPoiId() == null || !node.getPoiId().matches("[A-Za-z0-9_-]{1,80}")) {
                fail("路线包含无法识别的地点，请重新搜索或规划", false);
                return;
            }
            unique.put(node.getPoiId(), node.getName());
        }
        ids = new ArrayList<>(unique.keySet());
        next = completed = 0;
        listener.onProgress(0, ids.size());
        if (ids.isEmpty()) complete();
        else pump(generation);
    }

    private void pump(int owner) {
        while (owner == generation && listener != null && next < ids.size() && pending.size() < 3) {
            String id = ids.get(next++);
            Call<Result<JsonElement>> call = service.syncAttraction(id);
            pending.add(call);
            call.enqueue(new Callback<Result<JsonElement>>() {
                @Override public void onResponse(Call<Result<JsonElement>> call, Response<Result<JsonElement>> response) {
                    if (owner != generation || listener == null) return;
                    pending.remove(call);
                    if (response.code() == 401) { fail("登录已失效，请重新登录", true); return; }
                    Result<JsonElement> body = response.body();
                    if (!response.isSuccessful() || body == null || body.getCode() != 1) {
                        fail("地点同步未成功，原路线已保留，请稍后重试", false);
                        return;
                    }
                    completed++;
                    listener.onProgress(completed, ids.size());
                    if (owner != generation || listener == null) return;
                    if (completed == ids.size()) complete();
                    else pump(owner);
                }
                @Override public void onFailure(Call<Result<JsonElement>> call, Throwable error) {
                    if (owner != generation || listener == null || call.isCanceled()) return;
                    pending.remove(call);
                    fail("暂时无法同步地点，原路线已保留，请检查网络后重试", false);
                }
            });
        }
    }

    private void complete() {
        Listener completed = listener;
        listener = null;
        generation++;
        if (completed != null) completed.onComplete();
    }

    private void fail(String message, boolean unauthorized) {
        Listener failed = listener;
        cancel();
        if (failed != null) failed.onError(message, unauthorized);
    }

    public void cancel() {
        generation++;
        listener = null;
        List<Call<Result<JsonElement>>> calls = new ArrayList<>(pending);
        pending.clear();
        for (Call<Result<JsonElement>> call : calls) call.cancel();
    }
}
