package com.example.Japp.user.fragment.profile;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.R;
import com.example.Japp.user.fragment.route.SavedRouteStore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class SavedRouteAdapter extends RecyclerView.Adapter<SavedRouteAdapter.Holder> {

    interface Listener {
        void onClick(SavedRouteStore.SavedRoute route);
    }

    private final List<SavedRouteStore.SavedRoute> routes = new ArrayList<>();
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
    private final Listener listener;

    SavedRouteAdapter(Listener listener) {
        this.listener = listener;
    }

    void setRoutes(List<SavedRouteStore.SavedRoute> data) {
        routes.clear();
        if (data != null) routes.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_saved_route, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        SavedRouteStore.SavedRoute route = routes.get(position);
        holder.title.setText(route.getTitle());
        holder.summary.setText(route.getSummary());
        holder.city.setText(route.getCity().isEmpty() ? "未设置城市" : route.getCity());
        holder.meta.setText(route.getNodes().size() + "个地点 · 保存于 "
                + timeFormat.format(new Date(route.getSavedAt())));
        holder.itemView.setOnClickListener(v -> listener.onClick(route));
    }

    @Override
    public int getItemCount() {
        return routes.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView summary;
        final TextView city;
        final TextView meta;

        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.txtTitle);
            summary = itemView.findViewById(R.id.txtRoute);
            city = itemView.findViewById(R.id.txtCity);
            meta = itemView.findViewById(R.id.txtMeta);
        }
    }
}
