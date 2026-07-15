package com.example.Japp.user.fragment.route;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.services.core.PoiItem;
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

    static class PoiViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView address;

        PoiViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.txtPoiName);
            address = itemView.findViewById(R.id.txtPoiAddress);
        }
    }
}
