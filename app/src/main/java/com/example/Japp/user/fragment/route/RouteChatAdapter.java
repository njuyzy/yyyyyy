package com.example.Japp.user.fragment.route;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.maps.AMap;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Polyline;
import com.example.Japp.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class RouteChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_ASSISTANT = 1;

    private final List<RouteChatItem> items = new ArrayList<>();
    private final Map<RouteChatItem, ProgressiveTextView.Reveal> reveals = new IdentityHashMap<>();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public interface RouteChatListener {
        default void onMapClick(RouteChatItem item) {}

        default void onRetry(RouteChatItem item) {}
        default void onAcceptPlan(RouteChatItem item) {}
        default void onCancelPlan(RouteChatItem item) {}
    }

    private RouteChatListener listener;
    @Nullable
    private Runnable dataChangedListener;
    private RecyclerView recyclerView;
    @Nullable
    private Bundle mapCreateBundle;

    public void setRecyclerView(@Nullable RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    public void setMapCreateBundle(@Nullable Bundle mapCreateBundle) {
        this.mapCreateBundle = mapCreateBundle;
    }

    public void setListener(RouteChatListener listener) {
        this.listener = listener;
    }

    public void setDataChangedListener(@Nullable Runnable listener) {
        dataChangedListener = listener;
    }

    public void addItem(RouteChatItem item) {
        prepareReveal(item);
        items.add(item);
        notifyItemInserted(items.size() - 1);
        dispatchDataChanged();
    }

    public void updateItemText(int position, String text) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        if (items.get(position).getText().equals(text)) return;
        items.get(position).setText(text);
        prepareReveal(items.get(position));
        notifyItemChanged(position);
        dispatchDataChanged();
    }

    public void replaceItem(int position, RouteChatItem item) {
        if (position < 0 || position >= items.size()) {
            return;
        }
        reveals.remove(items.set(position, item));
        prepareReveal(item);
        notifyItemChanged(position);
        dispatchDataChanged();
    }

    public void replaceAllItems(@NonNull List<RouteChatItem> restoredItems) {
        reveals.clear();
        items.clear();
        items.addAll(restoredItems);
        notifyDataSetChanged();
    }

    public void clearItems() {
        if (items.isEmpty()) {
            return;
        }
        int oldSize = items.size();
        reveals.clear();
        items.clear();
        notifyItemRangeRemoved(0, oldSize);
        dispatchDataChanged();
    }

    public int getLastItemPosition() {
        return items.isEmpty() ? -1 : items.size() - 1;
    }

    public void refreshLastAssistantMap() {
        if (recyclerView == null || items.isEmpty()) {
            return;
        }
        int pos = items.size() - 1;
        if (items.get(pos).getType() != RouteChatItem.TYPE_ASSISTANT_ROUTE) {
            return;
        }
        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(pos);
        if (holder instanceof AssistantVH) {
            AssistantVH vh = (AssistantVH) holder;
            vh.mapResume();
            vh.redrawRouteIfNeeded();
        } else {
            notifyItemChanged(pos);
        }
    }

    public List<RouteChatItem> getItems() {
        return items;
    }

    private void dispatchDataChanged() {
        if (dataChangedListener != null) {
            dataChangedListener.run();
        }
    }

    private void prepareReveal(RouteChatItem item) {
        int type = item.getType();
        // Polling refreshes the transient work indicator, not the narrative response.
        if (type == RouteChatItem.TYPE_USER || type == RouteChatItem.TYPE_ASSISTANT_PROGRESS
                || item.getText().isEmpty()) return;
        ProgressiveTextView.Reveal reveal = new ProgressiveTextView.Reveal(
                item.getText(), SystemClock.uptimeMillis());
        reveals.put(item, reveal);
    }

    private void followGrowingText() {
        RecyclerView view = recyclerView;
        if (view == null || view.getScrollState() != RecyclerView.SCROLL_STATE_IDLE
                || view.canScrollVertically(1)) return;
        view.postOnAnimation(() -> {
            if (recyclerView == view && view.getScrollState() == RecyclerView.SCROLL_STATE_IDLE
                    && !items.isEmpty()) view.scrollToPosition(items.size() - 1);
        });
    }

    public void onHostResume() {
        forEachVisibleAssistant(AssistantVH::mapResume);
        forEachVisibleAssistant(vh -> vh.message.resumeReveal());
    }

    public void onHostPause() {
        forEachVisibleAssistant(AssistantVH::mapPause);
        forEachVisibleAssistant(vh -> vh.message.pauseReveal());
    }

    /** 打开全屏地图前彻底释放列表内 MapView，高德 SDK 不支持多个 MapView 同时存在 */
    public void releaseMapsForFullscreen() {
        forEachVisibleAssistant(AssistantVH::releaseMapForFullscreen);
    }

    /** 从全屏地图返回后重建可见项内的 MapView */
    public void recreateVisibleMaps() {
        if (recyclerView == null) {
            return;
        }
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
            if (holder instanceof AssistantVH) {
                ((AssistantVH) holder).recreateMapIfNeeded();
            }
        }
    }

    public void onHostLowMemory() {
        forEachVisibleAssistant(AssistantVH::mapLowMemory);
    }

    public void onHostDestroy() {
        forEachVisibleAssistant(vh -> vh.message.clearReveal());
        forEachVisibleAssistant(AssistantVH::destroyMapCompletely);
        reveals.clear();
        recyclerView = null;
    }

    private void forEachVisibleAssistant(Consumer<AssistantVH> action) {
        if (recyclerView == null) return;
        for (int i = 0; i < recyclerView.getChildCount(); i++) {
            View child = recyclerView.getChildAt(i);
            RecyclerView.ViewHolder h = recyclerView.getChildViewHolder(child);
            if (h instanceof AssistantVH) {
                action.accept((AssistantVH) h);
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        int type = items.get(position).getType();
        return type == RouteChatItem.TYPE_USER ? TYPE_USER : TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            return new UserVH(inflater.inflate(R.layout.item_route_user_requirement, parent, false));
        }
        return new AssistantVH(inflater.inflate(R.layout.item_route_assistant_route, parent, false), listener);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RouteChatItem item = items.get(position);
        String time = timeFmt.format(new Date(item.getTimestamp()));
        if (holder instanceof UserVH) {
            UserVH vh = (UserVH) holder;
            vh.message.setText(item.getText());
            vh.time.setText(time);
        } else if (holder instanceof AssistantVH) {
            ((AssistantVH) holder).bind(item, time, mapCreateBundle,
                    reveals.get(item), this::followGrowingText);
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (holder instanceof AssistantVH) {
            AssistantVH vh = (AssistantVH) holder;
            vh.message.resumeReveal();
            vh.mapResume();
            vh.redrawRouteIfNeeded();
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof AssistantVH) {
            ((AssistantVH) holder).message.pauseReveal();
            ((AssistantVH) holder).mapPause();
        }
        super.onViewDetachedFromWindow(holder);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof AssistantVH) {
            ((AssistantVH) holder).message.clearReveal();
            ((AssistantVH) holder).mapPause();
        }
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class UserVH extends RecyclerView.ViewHolder {
        final TextView message;
        final TextView time;

        UserVH(@NonNull View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.txtMessage);
            time = itemView.findViewById(R.id.txtTime);
        }
    }

    static class AssistantVH extends RecyclerView.ViewHolder {
        final ProgressiveTextView message;
        final MaterialCardView messageCard;
        final LinearLayout routeStops;
        final TextView time;
        final TextView state;
        final MaterialButton retry;
        final View mapContainer;
        final MapView routeMapView;
        final TextView mapTapHint;

        @Nullable
        private final RouteChatListener listener;
        @Nullable
        private AMap aMap;
        @Nullable
        private Polyline polyline;
        @Nullable
        private List<LatLng> pendingPoints;
        @Nullable
        private List<LatLng> pendingWaypoints;
        @Nullable
        private Bundle lastMapBundle;
        private boolean mapCreated;

        AssistantVH(@NonNull View itemView, @Nullable RouteChatListener listener) {
            super(itemView);
            this.listener = listener;
            message = itemView.findViewById(R.id.txtMessage);
            messageCard = itemView.findViewById(R.id.assistantMessageCard);
            routeStops = itemView.findViewById(R.id.assistantRouteStops);
            time = itemView.findViewById(R.id.txtTime);
            state = itemView.findViewById(R.id.txtAssistantState);
            retry = itemView.findViewById(R.id.btnRetry);
            mapContainer = itemView.findViewById(R.id.mapContainer);
            routeMapView = itemView.findViewById(R.id.routeMapView);
            mapTapHint = itemView.findViewById(R.id.mapTapHint);
        }

        void bind(@NonNull RouteChatItem item, @NonNull String timeText, @Nullable Bundle mapBundle,
                  @Nullable ProgressiveTextView.Reveal reveal, Runnable onTextGrowing) {
            boolean routeResult = item.getType() == RouteChatItem.TYPE_ASSISTANT_PLAN
                    || item.getType() == RouteChatItem.TYPE_ASSISTANT_ROUTE;
            float density = itemView.getResources().getDisplayMetrics().density;
            messageCard.setCardBackgroundColor(routeResult
                    ? ContextCompat.getColor(itemView.getContext(), R.color.route_assistant_message_surface)
                    : Color.TRANSPARENT);
            messageCard.setStrokeWidth(routeResult ? Math.max(1, Math.round(density)) : 0);
            messageCard.setRadius(routeResult ? 20 * density : 0);
            routeStops.removeAllViews();
            routeStops.setVisibility(item.getRouteStops().isEmpty() ? View.GONE : View.VISIBLE);
            for (int i = 0; i < item.getRouteStops().size(); i++) {
                LinearLayout row = RouteStopRow.create(
                        itemView.getContext(), item.getRouteStops().get(i), i, true);
                row.setMinimumHeight(Math.round(44 * density));
                routeStops.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            if (message != null) {
                int horizontalPadding = Math.round((routeResult ? 14 : 4) * density);
                message.setPaddingRelative(horizontalPadding, Math.round((routeResult ? 12 : 2) * density),
                        horizontalPadding, Math.round(10 * density));
                message.bind(routeResult ? formatAssistantText(item.getText()) : item.getText(),
                        reveal, onTextGrowing);
            }
            if (time != null) {
                time.setText(timeText);
            }
            boolean failed = item.isStatus()
                    && item.getText() != null
                    && item.getText().startsWith("规划失败");
            boolean progress = item.getType() == RouteChatItem.TYPE_ASSISTANT_PROGRESS;
            boolean candidate = item.getType() == RouteChatItem.TYPE_ASSISTANT_PLAN;
            boolean phase = item.getType() == RouteChatItem.TYPE_ASSISTANT_PHASE;
            if (state != null) {
                state.setText(failed ? "需重试" : (progress ? "规划中"
                        : (candidate ? "待确认" : (phase ? "阶段说明" : "已回复"))));
                state.setTextColor(Color.parseColor(failed ? "#C62828" : "#1E72FF"));
                state.setBackgroundTintList(ColorStateList.valueOf(
                        Color.parseColor(failed ? "#FDECEC" : "#EAF2FF")));
            }
            if (retry != null) {
                retry.setVisibility(failed || progress || candidate ? View.VISIBLE : View.GONE);
                retry.setText(candidate ? "应用" : (progress ? "取消规划" : "重新尝试"));
                retry.setOnClickListener(listener == null ? null : v -> {
                    if (candidate) listener.onAcceptPlan(item);
                    else if (progress) listener.onCancelPlan(item);
                    else listener.onRetry(item);
                });
            }
            if (mapContainer == null || routeMapView == null || !item.hasPolyline()) {
                if (mapContainer != null) {
                    mapContainer.setVisibility(View.GONE);
                }
                pendingPoints = null;
                pendingWaypoints = null;
                return;
            }

            mapContainer.setVisibility(View.VISIBLE);
            pendingPoints = item.getPolylinePoints();
            pendingWaypoints = item.getWaypointPoints();
            lastMapBundle = mapBundle;
            ensureMapCreated(mapBundle);
            setupMapClick(item);
            mapResume();
            routeMapView.post(this::redrawRouteIfNeeded);
            routeMapView.postDelayed(this::redrawRouteIfNeeded, 500);
        }

        @NonNull
        private CharSequence formatAssistantText(@Nullable String text) {
            String safeText = text == null ? "" : text;
            SpannableString styled = new SpannableString(safeText);
            int firstLineEnd = safeText.indexOf('\n');
            if (firstLineEnd < 0) {
                firstLineEnd = safeText.length();
            }
            if (firstLineEnd > 0) {
                styled.setSpan(new StyleSpan(Typeface.BOLD), 0, firstLineEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return styled;
        }

        private void ensureMapCreated(@Nullable Bundle mapBundle) {
            if (mapCreated || routeMapView == null) {
                return;
            }
            Bundle bundle = mapBundle != null ? new Bundle(mapBundle) : new Bundle();
            routeMapView.onCreate(bundle);
            mapCreated = true;
            aMap = routeMapView.getMap();
            if (aMap != null) {
                aMap.getUiSettings().setZoomControlsEnabled(false);
                aMap.getUiSettings().setMyLocationButtonEnabled(false);
                aMap.getUiSettings().setRotateGesturesEnabled(false);
                aMap.getUiSettings().setTiltGesturesEnabled(false);
                aMap.getUiSettings().setScrollGesturesEnabled(false);
                aMap.getUiSettings().setZoomGesturesEnabled(false);
                aMap.setOnMapLoadedListener(this::redrawRouteIfNeeded);
            }
        }

        private void setupMapClick(@NonNull RouteChatItem item) {
            if (mapContainer == null) {
                return;
            }
            View.OnClickListener openFullscreen = v -> {
                if (listener != null && item.hasPolyline()) {
                    listener.onMapClick(item);
                }
            };
            mapContainer.setOnClickListener(openFullscreen);
            if (mapTapHint != null) {
                mapTapHint.setOnClickListener(openFullscreen);
            }
        }

        void redrawRouteIfNeeded() {
            if (!mapCreated || aMap == null || pendingPoints == null || pendingPoints.size() < 2) {
                return;
            }
            polyline = RouteMapDrawHelper.drawRoute(aMap, pendingPoints, pendingWaypoints);
        }

        void mapResume() {
            if (routeMapView == null) {
                return;
            }
            if (!mapCreated && pendingPoints != null && pendingPoints.size() >= 2) {
                ensureMapCreated(lastMapBundle);
            }
            if (mapCreated) {
                routeMapView.onResume();
            }
        }

        void mapPause() {
            if (mapCreated && routeMapView != null) {
                routeMapView.onPause();
            }
        }

        void releaseMapForFullscreen() {
            if (!mapCreated || routeMapView == null) {
                return;
            }
            routeMapView.onPause();
            routeMapView.onDestroy();
            mapCreated = false;
            aMap = null;
            polyline = null;
        }

        void recreateMapIfNeeded() {
            if (routeMapView == null || mapContainer == null
                    || mapContainer.getVisibility() != View.VISIBLE) {
                return;
            }
            if (pendingPoints == null || pendingPoints.size() < 2) {
                return;
            }
            if (!mapCreated) {
                ensureMapCreated(lastMapBundle);
            }
            mapResume();
            routeMapView.post(this::redrawRouteIfNeeded);
        }

        void mapLowMemory() {
            if (mapCreated && routeMapView != null) {
                routeMapView.onLowMemory();
            }
        }

        void destroyMapCompletely() {
            polyline = null;
            aMap = null;
            pendingPoints = null;
            if (mapCreated && routeMapView != null) {
                routeMapView.onDestroy();
                mapCreated = false;
            }
        }
    }
}
