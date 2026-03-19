package com.example.Japp.leader.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.R;
import com.example.Japp.data.*;
import com.example.Japp.leader.fragment.order.orderDetail;
import com.example.Japp.leader.fragment.order.orderList;

import java.util.ArrayList;
import java.util.List;

import kotlin.jvm.JvmOverloads;

public class orderListAdapter extends RecyclerView.Adapter<orderListAdapter.MyHolder> {

    private CardView orderCard;

    static class MyHolder extends RecyclerView.ViewHolder{
        public MyHolder(@NonNull View itemView){
            super(itemView);
        }
    }

    private View.OnClickListener listener;//卡片点击监听器
    private List<order> orderlist=new ArrayList<>();
    public void setListData(List<order> list){
        this.orderlist=list;
        notifyDataSetChanged();//刷新
    }

    public void setListener(View.OnClickListener listener){
        this.listener=listener;
    }

    @NonNull
    @Override
    public MyHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view=LayoutInflater.from(parent.getContext()).inflate(R.layout.leader_activity_order_card,parent,false);
        orderCard = view.findViewById(R.id.orderCard);
        orderCard.setOnClickListener(listener);
        return new MyHolder(view);
    }


    private User user;
    @Override
    public void onBindViewHolder(@NonNull MyHolder holder, int position) {
        order order=orderlist.get(position);

    }

    @Override
    public int getItemCount() {
        return orderlist.size();
    }


}
