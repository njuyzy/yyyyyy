package com.example.Japp.leader;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.Japp.R;

import java.util.ArrayList;
import java.util.List;

public class WalkSegmentListAdapter extends BaseAdapter {
    private final LayoutInflater inflater;
    private final List<String> items = new ArrayList<>();

    public WalkSegmentListAdapter(Context context, List<String> steps) {
        inflater = LayoutInflater.from(context);
        items.add("出发");
        if (steps != null) {
            items.addAll(steps);
        }
        items.add("到达终点");
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_walk_segment, parent, false);
            holder = new ViewHolder();
            holder.index = convertView.findViewById(R.id.walk_step_index);
            holder.content = convertView.findViewById(R.id.walk_step_content);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        holder.index.setText(String.valueOf(position + 1));
        holder.content.setText(items.get(position));
        return convertView;
    }

    private static class ViewHolder {
        TextView index;
        TextView content;
    }
}
