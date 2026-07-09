package com.example.Japp.user.fragment.joinTeam;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.R;
import com.example.Japp.network.models.Project;
import com.example.Japp.user.util.ProjectUiHelper;

import java.util.ArrayList;
import java.util.List;

public class TeamListAdapter extends RecyclerView.Adapter<TeamListAdapter.Holder> {

    public interface OnTeamClickListener {
        void onTeamClick(TeamCardItem item);
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView txtTitle;
        final TextView txtCity;
        final TextView txtTag;
        final TextView txtRoute;
        final TextView txtMeta;
        final TextView txtStatus;

        Holder(@NonNull View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtCity = itemView.findViewById(R.id.txtCity);
            txtTag = itemView.findViewById(R.id.txtTag);
            txtRoute = itemView.findViewById(R.id.txtRoute);
            txtMeta = itemView.findViewById(R.id.txtMeta);
            txtStatus = itemView.findViewById(R.id.txtStatus);
        }
    }

    private final List<TeamCardItem> items = new ArrayList<>();
    private OnTeamClickListener listener;

    public void setItems(List<TeamCardItem> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    public void setOnTeamClickListener(OnTeamClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_team_card, parent, false);
        return new Holder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        TeamCardItem item = items.get(position);
        Project project = item.getProject();

        String title = project.getTitle();
        holder.txtTitle.setText(title == null || title.isEmpty() ? "研学拼单" : title);

        String city = item.getCity();
        holder.txtCity.setText(city.isEmpty() ? "未知城市" : city);

        String tag = project.getTag();
        if (tag == null || tag.isEmpty()) {
            holder.txtTag.setVisibility(View.GONE);
        } else {
            holder.txtTag.setVisibility(View.VISIBLE);
            holder.txtTag.setText(tag);
        }

        holder.txtRoute.setText(item.getRouteSummary().isEmpty()
                ? "途经：暂无景点信息" : item.getRouteSummary());

        String date = project.getDepartureDate() != null ? project.getDepartureDate() : "待定";
        String duration = item.getDuration().isEmpty() ? "暂无" : item.getDuration();
        holder.txtMeta.setText("出发 " + date + " · 用时 " + duration + " · "
                + project.getCurrentMembers() + "/" + project.getMaxMembers() + " 人");

        holder.txtStatus.setText(ProjectUiHelper.statusLabel(project.getStatus()));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTeamClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
