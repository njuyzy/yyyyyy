package com.example.Japp.user.fragment.route;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.example.Japp.R;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class RouteChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_ASSISTANT = 1;

    private final List<RouteChatItem> items = new ArrayList<>();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public interface RouteChatListener {
        void onPublishClick(RouteChatItem item, int position);
    }

    private RouteChatListener listener;
    private RecyclerView recyclerView;
    @Nullable
    private Bundle mapCreateBundle;

    public void setRecyclerView(@Nullable RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    /**
     * 传入 Fragment 的 savedInstanceState，供列表内 MapView.onCreate 使用；可为 null。
     */
    public void setMapCreateBundle(@Nullable Bundle mapCreateBundle) {
        this.mapCreateBundle = mapCreateBundle;
    }

    public void setListener(RouteChatListener listener) {
        this.listener = listener;
    }

    public void addItem(RouteChatItem item) {
        items.add(item);
        notifyItemInserted(items.size() - 1);
    }

    public List<RouteChatItem> getItems() {
        return items;
    }

    /** Fragment.onResume：恢复当前可见项内的 MapView */
    public void onHostResume() {
        forEachVisibleAssistant(AssistantVH::mapResume);
    }

    /** Fragment.onPause */
    public void onHostPause() {
        forEachVisibleAssistant(AssistantVH::mapPause);
    }

    /** Fragment.onLowMemory */
    public void onHostLowMemory() {
        forEachVisibleAssistant(AssistantVH::mapLowMemory);
    }

    /** Fragment.onDestroyView */
    public void onHostDestroy() {
        forEachVisibleAssistant(AssistantVH::destroyMapCompletely);
        recyclerView = null;
    }

    private void forEachVisibleAssistant(Consumer<AssistantVH> action) {
        if (recyclerView == null) return;
        int n = recyclerView.getChildCount();
        for (int i = 0; i < n; i++) {
            View child = recyclerView.getChildAt(i);
            RecyclerView.ViewHolder h = recyclerView.getChildViewHolder(child);
            if (h instanceof AssistantVH) {
                action.accept((AssistantVH) h);
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType() == RouteChatItem.TYPE_USER ? TYPE_USER : TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_USER) {
            View v = inflater.inflate(R.layout.item_route_user_requirement, parent, false);
            return new UserVH(v);
        }
        View v = inflater.inflate(R.layout.item_route_assistant_route, parent, false);
        return new AssistantVH(v);
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
            AssistantVH vh = (AssistantVH) holder;
            vh.message.setText(item.getText());
            vh.time.setText(time);
            vh.bindMapArea(item, mapCreateBundle);
            vh.publish.setOnClickListener(v -> {
                if (listener != null) {
                    int pos = holder.getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        listener.onPublishClick(item, pos);
                    }
                }
            });
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (holder instanceof AssistantVH) {
            ((AssistantVH) holder).mapResume();
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof AssistantVH) {
            ((AssistantVH) holder).mapPause();
        }
        super.onViewDetachedFromWindow(holder);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof AssistantVH) {
            ((AssistantVH) holder).destroyMapCompletely();
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

    public static class AssistantVH extends RecyclerView.ViewHolder {
        final TextView message;
        final TextView time;
        final MaterialButton publish;
        final FrameLayout mapContainer;
        final MapView routeMapView;

        @Nullable
        private AMap aMap;
        @Nullable
        private Polyline polyline;
        private boolean mapCreated;

        AssistantVH(@NonNull View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.txtMessage);
            time = itemView.findViewById(R.id.txtTime);
            publish = itemView.findViewById(R.id.btnPublish);
            mapContainer = itemView.findViewById(R.id.mapContainer);
            routeMapView = itemView.findViewById(R.id.routeMapView);
        }

        void bindMapArea(@NonNull RouteChatItem item, @Nullable Bundle mapBundle) {
            if (!item.hasPolyline()) {
                mapContainer.setVisibility(View.GONE);
                destroyMapCompletely();
                return;
            }
            mapContainer.setVisibility(View.VISIBLE);
            if (!mapCreated) {
                routeMapView.onCreate(mapBundle);
                mapCreated = true;
                aMap = routeMapView.getMap();
                if (aMap != null) {
                    aMap.getUiSettings().setZoomControlsEnabled(false);
                    aMap.getUiSettings().setMyLocationButtonEnabled(false);
                    aMap.getUiSettings().setRotateGesturesEnabled(false);
                    aMap.getUiSettings().setTiltGesturesEnabled(false);
                }
            }
            drawPolyline(item.getPolylinePoints());
        }

        private void drawPolyline(@NonNull List<LatLng> points) {
            if (aMap == null || points.size() < 2) {
                return;
            }
            if (polyline != null) {
                polyline.remove();
                polyline = null;
            }
            polyline = aMap.addPolyline(new PolylineOptions()
                    .addAll(points)
                    .width(12f)
                    .color(0xFF1A73E8));

            LatLngBounds.Builder b = LatLngBounds.builder();
            for (LatLng p : points) {
                b.include(p);
            }
            int pad = (int) (24 * itemView.getResources().getDisplayMetrics().density);
            aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), pad));
        }

        void mapResume() {
            if (mapCreated) {
                routeMapView.onResume();
            }
        }

        void mapPause() {
            if (mapCreated) {
                routeMapView.onPause();
            }
        }

        void mapLowMemory() {
            if (mapCreated) {
                routeMapView.onLowMemory();
            }
        }

        void destroyMapCompletely() {
            if (polyline != null) {
                polyline.remove();
                polyline = null;
            }
            aMap = null;
            if (mapCreated) {
                routeMapView.onDestroy();
                mapCreated = false;
            }
        }
    }
}
