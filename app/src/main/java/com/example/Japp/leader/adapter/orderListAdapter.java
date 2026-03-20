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

    static class MyHolder extends RecyclerView.ViewHolder{
        TextView customerName,route,duration,peopleCnt;
        public MyHolder(@NonNull View itemView){
            super(itemView);
            //初始化控件
            customerName=itemView.findViewById(R.id.txtCustomerName);
            route=itemView.findViewById(R.id.txtRoute);
            duration=itemView.findViewById(R.id.txtTime);
            peopleCnt=itemView.findViewById(R.id.txtPeople);
        }
    }

    private List<order> orderlist=new ArrayList<>();
    public void setListData(List<order> list){
        this.orderlist=list;
        notifyDataSetChanged();//刷新
    }


    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view=LayoutInflater.from(parent.getContext()).inflate(R.layout.leader_activity_order_card,parent,false);
        return new MyHolder(view);
    }


    private User user;
    @Override
    public void onBindViewHolder(@NonNull MyHolder holder, @SuppressLint("RecyclerView") int position) {

        order order=orderlist.get(position);

        //设置文本
        holder.customerName.setText(order.getCustomer().getUsername());
        holder.route.setText(order.getRoute().toString());
        holder.duration.setText(order.getEstimatedDuration());
        holder.peopleCnt.setText(order.getPeopleCnt());

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(null!=orderOnClickListener){
                    orderOnClickListener.onItemClick(position);
                }
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

    public interface OrderOnClickListener{
        void onItemClick(int position);
    }

}
