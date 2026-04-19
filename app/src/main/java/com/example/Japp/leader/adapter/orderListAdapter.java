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
        TextView customerName, city, tag, route, duration, peopleCnt;

        public MyHolder(@NonNull View itemView) {
            super(itemView);
            customerName = itemView.findViewById(R.id.txtCustomerName);
            city = itemView.findViewById(R.id.txtCity);
            tag = itemView.findViewById(R.id.txtTag);
            route = itemView.findViewById(R.id.txtRoute);
            duration = itemView.findViewById(R.id.txtTime);
            peopleCnt = itemView.findViewById(R.id.txtPeople);
        }
    }

    private List<order> orderlist = new ArrayList<>();

    public void setListData(List<order> list) {
        this.orderlist = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.leader_activity_order_card, parent, false);
        return new MyHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyHolder holder, @SuppressLint("RecyclerView") int position) {
        order o = orderlist.get(position);

        holder.customerName.setText(o.getCustomer().getUsername());

        String cityText = o.getCity();
        holder.city.setText(cityText.isEmpty() ? "未知城市" : cityText);

        String tagText = o.getTag();
        if (tagText.isEmpty()) {
            holder.tag.setVisibility(View.GONE);
        } else {
            holder.tag.setVisibility(View.VISIBLE);
            holder.tag.setText(tagText);
        }

        String routeText = o.getRoute().toString();
        holder.route.setText(routeText.isEmpty() ? "途经：暂无景点信息" : "途经：" + routeText);

        String dur = o.getEstimatedDuration();
        holder.duration.setText(dur != null && !dur.isEmpty() ? "用时：" + dur : "用时：暂无");

        holder.peopleCnt.setText(o.getPeopleCnt());

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
