package com.example.Japp.leader;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.Japp.R;
import com.example.Japp.data.order;


public class orderDetailActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);

        initialize();
    }

    private void initialize(){

        Button btnAccept=findViewById(R.id.btnAccept);
        btnAccept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Accept_Order();
            }
        });

        ImageView imageView=findViewById(R.id.imgRoute);
        TextView price=findViewById(R.id.txtPrice);
        TextView title=findViewById(R.id.txtTitle);
        TextView meta=findViewById(R.id.txtMeta);
        TextView tags=findViewById(R.id.txtTags);

        order o = getOrderFromIntent();
        if (o != null) {
            String titleText = !o.getTitle().isEmpty() ? o.getTitle() : "研学项目";
            title.setText(titleText);

            String name = o.getCustomer().getUsername();
            String city = o.getCity();
            String date = o.getDepartureDate();
            String metaText = (city.isEmpty() ? "未知城市" : city)
                    + " · " + (name.isEmpty() ? "匿名" : name)
                    + (date.isEmpty() ? "" : " · 出发:" + date);
            meta.setText(metaText);

            String tag = o.getTag();
            String duration = o.getEstimatedDuration();
            String tagsText = (tag.isEmpty() ? "偏好：暂无" : "偏好：" + tag)
                    + (duration == null || duration.isEmpty() ? "" : " · 用时:" + duration);
            tags.setText(tagsText);

            String peopleText = "人数：" + o.getCurrentMembers() + "/" + o.getPeopleCnt();
            price.setText(peopleText);
        }
        // TODO: 设置路线图片（目前使用占位图）
    }

    private order getOrderFromIntent() {
        Intent intent = getIntent();
        if (intent == null) return null;
        Object extra = intent.getSerializableExtra("order_info");
        if (extra instanceof order) {
            return (order) extra;
        }
        return null;
    }
    private void Accept_Order(){
        //TODO:接单成功
    }
}