package com.example.Japp.leader.fragment.order;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.R;
import com.example.Japp.data.order;
import com.example.Japp.leader.adapter.orderListAdapter;

import java.util.ArrayList;
import java.util.List;

public class orderList extends Fragment {

    private RecyclerView recycler;
    private orderListAdapter adapter;
    private List<order> order_list;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                Bundle savedInstanceState) {
        View view=inflater.inflate(R.layout.leader_fragment_order_list,container,false);

        recycler=view.findViewById(R.id.recycler);
        order_list=new ArrayList<>();
        //TODO:从数据库获取现有order存入order_list
        //样例
        order_list.add(new order());
        order_list.add(new order());
        order_list.add(new order());
        order_list.add(new order());
        order_list.add(new order());


        View.OnClickListener listener=new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //点击卡片跳转至详情
                orderDetail orderdetail=new orderDetail();
                FragmentTransaction transaction=getParentFragmentManager().beginTransaction();
                transaction.replace(R.id.container,orderdetail);
                transaction.addToBackStack(null);
                transaction.commit();
            }
        };
        adapter=new orderListAdapter();
        adapter.setListData(order_list);
        adapter.setListener(listener);
        adapter.setParentFragment(this);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        return view;
    }
}
