package com.example.Japp.leader.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TagGridAdapter extends RecyclerView.Adapter<TagGridAdapter.Holder> {

    public interface OnTagSelectedChangeListener {
        void onTagSelectedChange(Set<String> selectedTags);
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView imgTag;
        final TextView txtTagName;
        final View selectedOverlay;

        Holder(@NonNull View itemView) {
            super(itemView);
            imgTag = itemView.findViewById(R.id.imgTag);
            txtTagName = itemView.findViewById(R.id.txtTagName);
            selectedOverlay = itemView.findViewById(R.id.selectedOverlay);
        }
    }

    private final List<String> tags = new ArrayList<>();
    private final Set<String> selectedTags = new HashSet<>();
    private OnTagSelectedChangeListener listener;

    // 标签名 -> drawable 资源名映射
    private static final Map<String, Integer> TAG_ICON_MAP = new HashMap<>();
    static {
        TAG_ICON_MAP.put("历史人文", R.drawable.history);
        TAG_ICON_MAP.put("博物馆研学", R.drawable.museum);
        TAG_ICON_MAP.put("非遗体验", R.drawable.chinese);
        TAG_ICON_MAP.put("科技探索", R.drawable.science);
        TAG_ICON_MAP.put("自然生态", R.drawable.nature);
        TAG_ICON_MAP.put("农耕劳动", R.drawable.farm);
        TAG_ICON_MAP.put("地理地质", R.drawable.geography);
        TAG_ICON_MAP.put("航天航空", R.drawable.astro);
        TAG_ICON_MAP.put("艺术美育", R.drawable.art);
        TAG_ICON_MAP.put("红色教育", R.drawable.red);
        TAG_ICON_MAP.put("高校参访", R.drawable.college);
        TAG_ICON_MAP.put("职业启蒙", R.drawable.job);
        TAG_ICON_MAP.put("英语实践", R.drawable.english);
        TAG_ICON_MAP.put("摄影记录", R.drawable.photo);
        TAG_ICON_MAP.put("亲子互动", R.drawable.parent);
    }

    public void setTags(List<String> data) {
        tags.clear();
        if (data != null) {
            tags.addAll(data);
        }
        notifyDataSetChanged();
    }

    public void setOnTagSelectedChangeListener(OnTagSelectedChangeListener listener) {
        this.listener = listener;
    }

    public Set<String> getSelectedTags() {
        return new HashSet<>(selectedTags);
    }

    public void clearSelection() {
        selectedTags.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_tag_card, parent, false);
        // 每屏正好显示 5 列，item 宽 = 可用宽度 / 5，随 RecyclerView 宽度自适应
        int avail = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        if (avail > 0) {
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            lp.width = avail / 5;
            view.setLayoutParams(lp);
        }
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        String tag = tags.get(position);
        holder.txtTagName.setText(tag);

        Integer iconRes = TAG_ICON_MAP.get(tag);
        if (iconRes != null) {
            holder.imgTag.setImageResource(iconRes);
        }

        boolean isSelected = selectedTags.contains(tag);
        holder.selectedOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        holder.itemView.setAlpha(isSelected ? 1.0f : 0.75f);

        holder.itemView.setOnClickListener(v -> {
            if (selectedTags.contains(tag)) {
                selectedTags.remove(tag);
            } else {
                selectedTags.add(tag);
            }
            notifyItemChanged(position);
            if (listener != null) {
                listener.onTagSelectedChange(new HashSet<>(selectedTags));
            }
        });
    }

    @Override
    public int getItemCount() {
        return tags.size();
    }
}
