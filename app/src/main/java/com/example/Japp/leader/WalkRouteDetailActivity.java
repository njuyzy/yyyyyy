package com.example.Japp.leader;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.Japp.R;

import java.util.ArrayList;

public class WalkRouteDetailActivity extends AppCompatActivity {
    public static final String EXTRA_WALK_INSTRUCTIONS = "walk_instructions";
    public static final String EXTRA_WALK_SUMMARY = "walk_summary";

    private ArrayList<String> walkInstructions = new ArrayList<>();
    private String walkSummary = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_walk_route_detail);
        readIntentData();

        TextView title = findViewById(R.id.title_center);
        TextView summary = findViewById(R.id.firstline);
        ListView walkSegmentList = findViewById(R.id.walk_segment_list);

        title.setText("步行路线详情");
        summary.setText(walkSummary);
        walkSegmentList.setAdapter(new WalkSegmentListAdapter(this, walkInstructions));
    }

    private void readIntentData() {
        Intent intent = getIntent();
        if (intent == null) return;
        ArrayList<String> instructions = intent.getStringArrayListExtra(EXTRA_WALK_INSTRUCTIONS);
        if (instructions != null) {
            walkInstructions = instructions;
        }
        String summary = intent.getStringExtra(EXTRA_WALK_SUMMARY);
        walkSummary = summary == null ? "" : summary;
    }

    public void onBackClick(View view) {
        finish();
    }
}
