package com.example.Japp.user.fragment.route;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amap.api.maps.model.LatLng;
import com.example.Japp.network.models.RouteNode;
import com.example.Japp.user.util.SessionHelper;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 路线设计页的本地草稿。数据按账号隔离，退出应用或系统杀进程后仍可恢复。
 */
final class RouteDraftStore {

    private static final String TAG = "RouteDraftStore";
    private static final String PREFS = "route_design_drafts";
    private static final String KEY_PREFIX = "draft_v1_account_";
    private static final int VERSION = 1;
    private static final Gson GSON = new Gson();

    private RouteDraftStore() {
    }

    static void save(@NonNull Context context,
                     @NonNull List<RouteNode> routeNodes,
                     @NonNull List<RouteChatItem> chatItems,
                     @Nullable String memoryId,
                     int publishableRouteId,
                     @Nullable String publishableRouteSummary,
                     boolean requestInFlight,
                     int waitingPosition,
                     boolean synchronous) {
        DraftRecord record = new DraftRecord();
        record.version = VERSION;
        record.savedAt = System.currentTimeMillis();
        record.routeNodes = new ArrayList<>(routeNodes);
        record.photoUrls = new ArrayList<>(routeNodes.size());
        for (RouteNode node : routeNodes) {
            record.photoUrls.add(node != null ? node.getPhotoUrl() : null);
        }
        record.chatItems = new ArrayList<>(chatItems.size());
        for (RouteChatItem item : chatItems) {
            if (item != null) {
                record.chatItems.add(ChatRecord.from(item));
            }
        }
        record.memoryId = memoryId;
        record.publishableRouteId = publishableRouteId;
        record.publishableRouteSummary = publishableRouteSummary;
        record.requestInFlight = requestInFlight;
        record.waitingPosition = waitingPosition;

        try {
            SharedPreferences.Editor editor = preferences(context).edit()
                    .putString(accountKey(context), GSON.toJson(record));
            if (synchronous) {
                editor.commit();
            } else {
                editor.apply();
            }
        } catch (RuntimeException exception) {
            Log.e(TAG, "Failed to save route draft", exception);
        }
    }

    @Nullable
    static Draft load(@NonNull Context context) {
        String json = preferences(context).getString(accountKey(context), null);
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            DraftRecord record = GSON.fromJson(json, DraftRecord.class);
            if (record == null || record.version != VERSION) {
                return null;
            }
            List<RouteNode> nodes = record.routeNodes != null
                    ? record.routeNodes : Collections.emptyList();
            if (record.photoUrls != null) {
                int count = Math.min(nodes.size(), record.photoUrls.size());
                for (int i = 0; i < count; i++) {
                    RouteNode node = nodes.get(i);
                    if (node != null) {
                        node.setPhotoUrl(record.photoUrls.get(i));
                    }
                }
            }

            ArrayList<RouteChatItem> items = new ArrayList<>();
            if (record.chatItems != null) {
                for (ChatRecord chatRecord : record.chatItems) {
                    RouteChatItem item = chatRecord != null ? chatRecord.toItem() : null;
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
            if (record.requestInFlight
                    && record.waitingPosition >= 0
                    && record.waitingPosition < items.size()) {
                RouteChatItem interrupted = items.get(record.waitingPosition);
                items.set(record.waitingPosition, RouteChatItem.restore(
                        RouteChatItem.TYPE_ASSISTANT_STATUS,
                        "上次路线规划因应用关闭而中断，请重新发送需求。",
                        interrupted.getTimestamp(),
                        0,
                        null,
                        null,
                        false));
            }
            return new Draft(nodes, items, record.memoryId,
                    record.publishableRouteId, record.publishableRouteSummary);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Failed to restore route draft", exception);
            return null;
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String accountKey(Context context) {
        int accountId = SessionHelper.getAccountId(context);
        return KEY_PREFIX + (accountId > 0 ? accountId : "guest");
    }

    static final class Draft {
        final List<RouteNode> routeNodes;
        final List<RouteChatItem> chatItems;
        @Nullable
        final String memoryId;
        final int publishableRouteId;
        @Nullable
        final String publishableRouteSummary;

        Draft(List<RouteNode> routeNodes,
              List<RouteChatItem> chatItems,
              @Nullable String memoryId,
              int publishableRouteId,
              @Nullable String publishableRouteSummary) {
            this.routeNodes = routeNodes;
            this.chatItems = chatItems;
            this.memoryId = memoryId;
            this.publishableRouteId = publishableRouteId;
            this.publishableRouteSummary = publishableRouteSummary;
        }
    }

    private static final class DraftRecord {
        int version;
        long savedAt;
        List<RouteNode> routeNodes;
        List<String> photoUrls;
        List<ChatRecord> chatItems;
        String memoryId;
        int publishableRouteId;
        String publishableRouteSummary;
        boolean requestInFlight;
        int waitingPosition = -1;
    }

    private static final class ChatRecord {
        int type;
        String text;
        long timestamp;
        int routeId;
        boolean publishAllowed;
        List<PointRecord> polyline;
        List<PointRecord> waypoints;

        static ChatRecord from(RouteChatItem item) {
            ChatRecord record = new ChatRecord();
            record.type = item.getType();
            record.text = item.getText();
            record.timestamp = item.getTimestamp();
            record.routeId = item.getRouteId();
            record.publishAllowed = item.isPublishAllowed();
            record.polyline = PointRecord.from(item.getPolylinePoints());
            record.waypoints = PointRecord.from(item.getWaypointPoints());
            return record;
        }

        @Nullable
        RouteChatItem toItem() {
            if (type != RouteChatItem.TYPE_USER
                    && type != RouteChatItem.TYPE_ASSISTANT_ROUTE
                    && type != RouteChatItem.TYPE_ASSISTANT_STATUS) {
                return null;
            }
            return RouteChatItem.restore(type, text, timestamp, routeId,
                    PointRecord.toLatLng(polyline),
                    PointRecord.toLatLng(waypoints),
                    publishAllowed);
        }
    }

    private static final class PointRecord {
        double latitude;
        double longitude;

        PointRecord(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        static List<PointRecord> from(List<LatLng> points) {
            ArrayList<PointRecord> records = new ArrayList<>();
            if (points != null) {
                for (LatLng point : points) {
                    if (point != null) {
                        records.add(new PointRecord(point.latitude, point.longitude));
                    }
                }
            }
            return records;
        }

        static List<LatLng> toLatLng(List<PointRecord> records) {
            ArrayList<LatLng> points = new ArrayList<>();
            if (records != null) {
                for (PointRecord record : records) {
                    if (record != null) {
                        points.add(new LatLng(record.latitude, record.longitude));
                    }
                }
            }
            return points;
        }
    }
}
