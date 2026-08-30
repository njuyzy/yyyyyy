package com.example.Japp.user.fragment.route;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.Japp.network.models.RouteNode;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.user.util.SessionHelper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 用户主动保存的路线快照，按账号保存在本地。 */
public final class SavedRouteStore {

    private static final String PREFS = "saved_routes";
    private static final String KEY_ROUTES_PREFIX = "routes_v1_account_";
    private static final String KEY_PENDING_PREFIX = "pending_route_account_";
    private static final int MAX_ROUTES = 50;
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<SavedRoute>>() {}.getType();

    private SavedRouteStore() {}

    @NonNull
    public static synchronized SavedRoute save(@NonNull Context context,
                                               @NonNull List<RouteNode> nodes) {
        if (nodes.isEmpty()) throw new IllegalArgumentException("路线不能为空");
        List<SavedRoute> routes = read(context);
        String fingerprint = fingerprint(nodes);
        SavedRoute saved = null;
        for (SavedRoute route : routes) {
            if (fingerprint.equals(route.fingerprint)) {
                saved = route;
                break;
            }
        }
        if (saved == null) {
            saved = new SavedRoute();
            saved.id = System.currentTimeMillis();
            routes.add(saved);
        }
        saved.savedAt = System.currentTimeMillis();
        saved.fingerprint = fingerprint;
        saved.nodes = copyNodes(nodes);
        saved.photoUrls = photoUrls(nodes);
        saved.title = buildTitle(nodes);
        saved.summary = ProjectUiHelper.buildRouteSummary(nodes);
        saved.city = firstCity(nodes);

        routes.sort((left, right) -> Long.compare(right.savedAt, left.savedAt));
        if (routes.size() > MAX_ROUTES) {
            routes = new ArrayList<>(routes.subList(0, MAX_ROUTES));
        }
        write(context, routes);
        return saved;
    }

    @NonNull
    public static synchronized List<SavedRoute> getAll(@NonNull Context context) {
        List<SavedRoute> routes = read(context);
        routes.sort((left, right) -> Long.compare(right.savedAt, left.savedAt));
        return routes;
    }

    public static synchronized void requestOpen(@NonNull Context context, long routeId) {
        preferences(context).edit()
                .putLong(pendingKey(context), routeId)
                .apply();
    }

    @Nullable
    public static synchronized SavedRoute consumePending(@NonNull Context context) {
        SharedPreferences preferences = preferences(context);
        String key = pendingKey(context);
        long routeId = preferences.getLong(key, 0L);
        if (routeId <= 0) return null;
        preferences.edit().remove(key).apply();
        for (SavedRoute route : read(context)) {
            if (route.id == routeId) return route;
        }
        return null;
    }

    @NonNull
    private static List<SavedRoute> read(@NonNull Context context) {
        String raw = preferences(context).getString(routesKey(context), null);
        if (raw == null || raw.trim().isEmpty()) return new ArrayList<>();
        try {
            List<SavedRoute> routes = GSON.fromJson(raw, LIST_TYPE);
            if (routes == null) return new ArrayList<>();
            for (SavedRoute route : routes) restorePhotos(route);
            return routes;
        } catch (RuntimeException ignored) {
            return new ArrayList<>();
        }
    }

    private static void write(@NonNull Context context,
                              @NonNull List<SavedRoute> routes) {
        preferences(context).edit()
                .putString(routesKey(context), GSON.toJson(routes))
                .apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String routesKey(Context context) {
        return KEY_ROUTES_PREFIX + accountKey(context);
    }

    private static String pendingKey(Context context) {
        return KEY_PENDING_PREFIX + accountKey(context);
    }

    private static String accountKey(Context context) {
        int accountId = SessionHelper.getAccountId(context);
        return accountId > 0 ? String.valueOf(accountId) : "guest";
    }

    private static List<RouteNode> copyNodes(List<RouteNode> nodes) {
        RouteNode[] copied = GSON.fromJson(GSON.toJson(nodes), RouteNode[].class);
        ArrayList<RouteNode> result = new ArrayList<>();
        if (copied != null) Collections.addAll(result, copied);
        return result;
    }

    private static List<String> photoUrls(List<RouteNode> nodes) {
        List<String> urls = new ArrayList<>(nodes.size());
        for (RouteNode node : nodes) urls.add(node == null ? null : node.getPhotoUrl());
        return urls;
    }

    private static void restorePhotos(SavedRoute route) {
        if (route == null) return;
        if (route.nodes == null) route.nodes = new ArrayList<>();
        if (route.photoUrls == null) route.photoUrls = new ArrayList<>();
        int count = Math.min(route.nodes.size(), route.photoUrls.size());
        for (int i = 0; i < count; i++) {
            RouteNode node = route.nodes.get(i);
            if (node != null) node.setPhotoUrl(route.photoUrls.get(i));
        }
    }

    private static String buildTitle(List<RouteNode> nodes) {
        List<String> names = new ArrayList<>();
        for (RouteNode node : nodes) {
            if (node != null && node.getName() != null && !node.getName().trim().isEmpty()) {
                names.add(node.getName().trim());
            }
        }
        if (names.isEmpty()) return "已保存路线";
        if (names.size() == 1) return names.get(0) + "路线";
        if (names.size() == 2) return names.get(0) + " → " + names.get(1);
        return names.get(0) + " → … → " + names.get(names.size() - 1);
    }

    private static String firstCity(List<RouteNode> nodes) {
        for (RouteNode node : nodes) {
            if (node != null && node.getCityname() != null
                    && !node.getCityname().trim().isEmpty()) {
                return node.getCityname().trim();
            }
        }
        return "";
    }

    private static String fingerprint(List<RouteNode> nodes) {
        StringBuilder result = new StringBuilder();
        List<RouteNode> ordered = new ArrayList<>(nodes);
        ordered.sort(Comparator.comparingInt(RouteNode::getVisitOrder));
        for (RouteNode node : ordered) {
            if (node == null) continue;
            result.append(node.getPoiId()).append('\u0001')
                    .append(node.getName()).append('\u0001')
                    .append(node.getLocation()).append('\u0002');
        }
        return result.toString();
    }

    public static final class SavedRoute {
        private long id;
        private long savedAt;
        private String title;
        private String summary;
        private String city;
        private String fingerprint;
        private List<RouteNode> nodes = new ArrayList<>();
        private List<String> photoUrls = new ArrayList<>();

        public long getId() { return id; }
        public long getSavedAt() { return savedAt; }
        public String getTitle() { return title == null ? "已保存路线" : title; }
        public String getSummary() { return summary == null ? "" : summary; }
        public String getCity() { return city == null ? "" : city; }
        @NonNull public List<RouteNode> getNodes() {
            List<RouteNode> copied = copyNodes(nodes);
            int count = Math.min(copied.size(), photoUrls == null ? 0 : photoUrls.size());
            for (int i = 0; i < count; i++) copied.get(i).setPhotoUrl(photoUrls.get(i));
            return copied;
        }
    }
}
