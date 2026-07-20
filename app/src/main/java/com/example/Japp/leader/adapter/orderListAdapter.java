package com.example.Japp.leader.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.R;
import com.example.Japp.data.*;

import java.util.ArrayList;
import java.util.List;

public class orderListAdapter extends RecyclerView.Adapter<orderListAdapter.MyHolder> {

    static class MyHolder extends RecyclerView.ViewHolder {
        final TextView txtTitle;
        final TextView txtCity;
        final TextView txtTag;
        final TextView txtRoute;
        final TextView txtMeta;
        final TextView txtStatus;

        public MyHolder(@NonNull View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtCity = itemView.findViewById(R.id.txtCity);
            txtTag = itemView.findViewById(R.id.txtTag);
            txtRoute = itemView.findViewById(R.id.txtRoute);
            txtMeta = itemView.findViewById(R.id.txtMeta);
            txtStatus = itemView.findViewById(R.id.txtStatus);
        }
    }

    private List<order> orderlist = new ArrayList<>();

    public void setListData(List<order> list) {
        this.orderlist = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_team_card, parent, false);
        return new MyHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull MyHolder holder, @SuppressLint("RecyclerView") int position) {
        order o = orderlist.get(position);

        String title = o.getTitle();
        holder.txtTitle.setText(title.isEmpty() ? "研学拼单" : title);

        String cityText = o.getCity();
        holder.txtCity.setText(cityText.isEmpty() ? "未知城市" : cityText);

        String tagText = o.getTag();
        if (tagText.isEmpty()) {
            holder.txtTag.setVisibility(View.GONE);
        } else {
            holder.txtTag.setVisibility(View.VISIBLE);
            holder.txtTag.setText(tagText);
        }

        String routeText = o.getRoute().toString();
        if (!routeText.isEmpty()) {
            routeText = routeText.replace("→", " → ");
        }
        holder.txtRoute.setText(routeText.isEmpty() ? "途经：暂无景点信息" : "途经：" + routeText);

        String date = o.getDepartureDate().isEmpty() ? "待定" : o.getDepartureDate();
        String duration = (o.getEstimatedDuration() == null || o.getEstimatedDuration().isEmpty()) ? "暂无" : o.getEstimatedDuration();
        holder.txtMeta.setText("出发 " + date + " · 用时 " + duration + " · "
                + o.getCurrentMembers() + "/" + o.getMaxMembers() + " 人");

        holder.txtStatus.setText("可接");

        holder.itemView.setOnClickListener(view -> {
            if (orderOnClickListener != null) {
                orderOnClickListener.onItemClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return orderlist.size();
    }

    public void setOrderOnClickListener(OrderOnClickListener orderOnClickListener) {
        this.orderOnClickListener = orderOnClickListener;
    }

    private OrderOnClickListener orderOnClickListener;

    public interface OrderOnClickListener {
        void onItemClick(int position);
    }

}

