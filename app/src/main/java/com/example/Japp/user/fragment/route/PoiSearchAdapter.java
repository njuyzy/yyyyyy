package com.example.Japp.user.fragment.route;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.services.core.PoiItem;
import com.amap.api.services.core.LatLonPoint;
import com.example.Japp.R;

import java.util.ArrayList;
import java.util.List;

/** 地点搜索结果；点击整行即可把地点加入当前路线。 */
public class PoiSearchAdapter extends RecyclerView.Adapter<PoiSearchAdapter.PoiViewHolder> {

    public interface OnPoiClickListener {
        void onPoiClick(@NonNull PoiItem item);
    }

    private final List<PoiItem> items = new ArrayList<>();
    private final OnPoiClickListener listener;
    private LatLonPoint origin;

    public PoiSearchAdapter(@NonNull OnPoiClickListener listener) {
        this.listener = listener;
    }

    public void submitItems(List<PoiItem> newItems) {
        int oldSize = items.size();
        items.clear();
        if (oldSize > 0) {
            notifyItemRangeRemoved(0, oldSize);
        }
        if (newItems != null) {
            items.addAll(newItems);
        }
        if (!items.isEmpty()) {
            notifyItemRangeInserted(0, items.size());
        }
    }

    public void setOrigin(LatLonPoint origin) {
        this.origin = origin;
        notifyItemRangeChanged(0, items.size());
    }

    @NonNull
    @Override
    public PoiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_route_poi_result, parent, false);
        return new PoiViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PoiViewHolder holder, int position) {
        PoiItem item = items.get(position);
        holder.name.setText(TextUtils.isEmpty(item.getTitle()) ? "未命名地点" : item.getTitle());

        String address = item.getSnippet();
        if (TextUtils.isEmpty(address)) {
            address = joinLocation(item.getCityName(), item.getAdName());
        }
        holder.address.setText(TextUtils.isEmpty(address) ? "暂无地址" : address);
        holder.type.setText(TextUtils.isEmpty(item.getTypeDes()) ? "地点" : item.getTypeDes());
        holder.distance.setText(formatDistance(item, origin));
        holder.itemView.setOnClickListener(v -> listener.onPoiClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String joinLocation(String city, String district) {
        if (TextUtils.isEmpty(city)) {
            return district;
        }
        if (TextUtils.isEmpty(district) || city.equals(district)) {
            return city;
        }
        return city + " · " + district;
    }

    public static String formatDistance(@NonNull PoiItem item, LatLonPoint origin) {
        int meters = distanceMeters(item, origin);
        if (meters == Integer.MAX_VALUE) {
            return "距离暂不可用";
        }
        if (meters < 1000) {
            return meters + " m";
        }
        return String.format(java.util.Locale.CHINA, "%.1f km", meters / 1000f);
    }

    public static int distanceMeters(@NonNull PoiItem item, LatLonPoint origin) {
        int meters = item.getDistance();
        LatLonPoint target = item.getLatLonPoint();
        if (meters <= 0 && origin != null && target != null) {
            float[] result = new float[1];
            android.location.Location.distanceBetween(
                    origin.getLatitude(), origin.getLongitude(),
                    target.getLatitude(), target.getLongitude(), result);
            meters = Math.round(result[0]);
        }
        if (meters <= 0) {
            return Integer.MAX_VALUE;
        }
        return meters;
    }

    static class PoiViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView address;
        final TextView type;
        final TextView distance;

        PoiViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.txtPoiName);
            address = itemView.findViewById(R.id.txtPoiAddress);
            type = itemView.findViewById(R.id.txtPoiType);
            distance = itemView.findViewById(R.id.txtPoiDistance);
        }
    }
}
