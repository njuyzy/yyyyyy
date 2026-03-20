package com.example.Japp.leader;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.Japp.R;


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
        //TODO:设置各控件内容
    }
    private void Accept_Order(){
        //TODO:接单成功
    }
}