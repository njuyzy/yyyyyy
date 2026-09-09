package com.example.Japp.user.fragment.joinTeam;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.util.LruCache;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.Japp.R;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Project;
import com.example.Japp.network.models.Account;
import com.example.Japp.Chat.util.ChatAvatarLoader;
import com.example.Japp.user.PublisherProfileSheet;
import com.example.Japp.network.models.Result;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.user.util.UserOrderPresentation;
import com.example.Japp.user.util.SessionHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TeamListAdapter extends RecyclerView.Adapter<TeamListAdapter.Holder> {

    public interface OnTeamClickListener {
        void onTeamClick(TeamCardItem item);
    }

    public interface OnFavoriteChangedListener {
        void onFavoriteChanged(TeamCardItem item, boolean favorite);
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView txtTitle;
        final TextView txtCity;
        final TextView txtTag;
        final TextView txtRoute;
        final TextView txtMeta;
        final TextView txtStatus;
        final ImageButton btnFavorite;
        final View clickTarget;
        final View publisher;
        final ImageView publisherAvatar;
        final TextView publisherInitial;
        final TextView publisherName;

        Holder(@NonNull View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtCity = itemView.findViewById(R.id.txtCity);
            txtTag = itemView.findViewById(R.id.txtTag);
            txtRoute = itemView.findViewById(R.id.txtRoute);
            txtMeta = itemView.findViewById(R.id.txtMeta);
            txtStatus = itemView.findViewById(R.id.txtStatus);
            btnFavorite = itemView.findViewById(R.id.btnFavorite);
            View cardContent = itemView.findViewById(R.id.userOrderCardContent);
            clickTarget = cardContent == null ? itemView : cardContent;
            publisher = itemView.findViewById(R.id.userOrderPublisher);
            publisherAvatar = itemView.findViewById(R.id.userPublisherAvatar);
            publisherInitial = itemView.findViewById(R.id.userPublisherInitial);
            publisherName = itemView.findViewById(R.id.userPublisherName);
        }
    }

    private final List<TeamCardItem> items = new ArrayList<>();
    private OnTeamClickListener listener;
    private OnFavoriteChangedListener favoriteChangedListener;
    private boolean favoriteEnabled;
    private boolean userPresentation;

    public TeamListAdapter() {}

    public TeamListAdapter(boolean userPresentation) {
        this.userPresentation = userPresentation;
    }
    private final Set<Integer> pendingFavoriteProjectIds = new HashSet<>();
    private final LruCache<Integer, PublisherAvatar> publishers = new LruCache<>(64);
    private final LruCache<Integer, Long> publisherRetryAfter = new LruCache<>(64);
    private final Map<Integer, Call<Result<Account>>> publisherRequests = new HashMap<>();

    private static final class PublisherAvatar {
        final String name;
        final String url;
        PublisherAvatar(Account account) {
            name = UserOrderPresentation.clean(account.getUsername());
            url = account.getAvatarUrl();
        }
    }

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

    public void setOnFavoriteChangedListener(OnFavoriteChangedListener listener) {
        this.favoriteChangedListener = listener;
    }

    public void setFavoriteEnabled(boolean enabled) {
        favoriteEnabled = enabled;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Favorites is a user-only entry; shared leader lists keep the default presentation.
        boolean userStyle = userPresentation
                || parent.getContext() instanceof com.example.Japp.FavoriteOrdersActivity;
        View view = LayoutInflater.from(parent.getContext())
                .inflate(userStyle ? R.layout.item_user_order_card : R.layout.item_team_card, parent, false);
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
        holder.txtMeta.setText("出发 " + date + " · 用时 " + duration
                + "\n已有人数 " + Math.max(0, project.getCurrentMembers())
                + " / 人数上限 " + (project.getMaxMembers() > 0
                ? String.valueOf(project.getMaxMembers()) : "不限"));

        holder.txtStatus.setText(ProjectUiHelper.statusLabel(project.getStatus()));

        if (userPresentation || holder.itemView.getContext() instanceof com.example.Japp.FavoriteOrdersActivity) {
            String publicCity = UserOrderPresentation.city(city);
            holder.txtCity.setText(publicCity);
            holder.txtCity.setVisibility(publicCity.isEmpty() ? View.GONE : View.VISIBLE);
            holder.txtRoute.setText(item.getRouteSummary().isEmpty()
                    ? "打开行程，查看沿途安排" : item.getRouteSummary().replaceFirst("^途经[：:]", ""));
            holder.txtMeta.setText(UserOrderPresentation.departure(project)
                    + "\n参团 " + UserOrderPresentation.capacity(project)
                    + (item.getDuration().isEmpty() || "暂无".equals(item.getDuration())
                    ? "" : " · 游览约 " + item.getDuration()));
            String label = UserOrderPresentation.statusLabel(project.getStatus());
            holder.txtStatus.setText(label);
            holder.txtStatus.setVisibility(label.isEmpty() ? View.GONE : View.VISIBLE);
            String phase = UserOrderPresentation.phase(project.getStatus());
            holder.txtStatus.setTextColor("CANCELLED".equals(phase) ? 0xFFB45353
                    : "DONE".equals(phase) ? 0xFF637086 : 0xFF2563EB);
        }

        bindFavorite(holder, item);
        bindPublisher(holder, item);

        holder.clickTarget.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTeamClick(item);
            }
        });
    }

    private void bindPublisher(Holder holder, TeamCardItem item) {
        if (holder.publisher == null) return; // Leader layouts do not contain this user-only entry.
        int ownerId = item.getProject().getOwnerAccountId();
        PublisherAvatar profile = publishers.get(ownerId);
        String displayName = profile == null ? "" : profile.name;
        if (displayName.isEmpty()) displayName = UserOrderPresentation.clean(item.getProject().getPublisherName());
        if (displayName.isEmpty()) displayName = UserOrderPresentation.clean(item.getOwnerName());
        if (displayName.isEmpty()) displayName = "发布者";
        String avatarUrl = profile == null ? null : profile.url;
        holder.publisherName.setText(displayName);
        holder.publisher.setContentDescription("查看发布者“" + displayName + "”的资料");
        holder.publisher.setEnabled(ownerId > 0);
        ChatAvatarLoader.bind(holder.publisherAvatar, holder.publisherInitial, avatarUrl, displayName);
        final String visibleName = displayName;
        holder.publisher.setOnClickListener(v -> PublisherProfileSheet.open(
                v.getContext(), ownerId, visibleName, avatarUrl));
        Long retryAfter = publisherRetryAfter.get(ownerId);
        if (ownerId <= 0 || profile != null || publisherRequests.containsKey(ownerId)
                || (retryAfter != null && android.os.SystemClock.elapsedRealtime() < retryAfter)) return;

        Call<Result<Account>> request = ApiClient.getClient().create(UserService.class).getAccount(ownerId);
        publisherRequests.put(ownerId, request);
        request.enqueue(new Callback<Result<Account>>() {
            @Override public void onResponse(@NonNull Call<Result<Account>> call,
                                             @NonNull Response<Result<Account>> response) {
                if (publisherRequests.get(ownerId) != call) return;
                publisherRequests.remove(ownerId);
                Account account = response.isSuccessful() && response.body() != null
                        && response.body().getCode() == 1 ? response.body().getData() : null;
                if (account != null) {
                    publishers.put(ownerId, new PublisherAvatar(account));
                    for (int i = 0; i < items.size(); i++) {
                        if (items.get(i).getProject().getOwnerAccountId() == ownerId) notifyItemChanged(i);
                    }
                } else publisherRetryAfter.put(ownerId, android.os.SystemClock.elapsedRealtime() + 30_000L);
            }
            @Override public void onFailure(@NonNull Call<Result<Account>> call, @NonNull Throwable error) {
                if (publisherRequests.get(ownerId) != call) return;
                publisherRequests.remove(ownerId);
                if (!call.isCanceled()) publisherRetryAfter.put(ownerId, android.os.SystemClock.elapsedRealtime() + 30_000L);
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        if (holder.publisherAvatar != null) holder.publisherAvatar.setTag(null);
        super.onViewRecycled(holder);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        for (Call<Result<Account>> call : publisherRequests.values()) call.cancel();
        publisherRequests.clear();
        super.onDetachedFromRecyclerView(recyclerView);
    }

    private void bindFavorite(Holder holder, TeamCardItem item) {
        holder.btnFavorite.setVisibility(favoriteEnabled ? View.VISIBLE : View.GONE);
        if (!favoriteEnabled) {
            holder.btnFavorite.setOnClickListener(null);
            return;
        }
        Project project = item.getProject();
        int projectId = project == null ? 0 : project.getId();
        int routeId = project == null ? 0 : project.getRouteId();
        holder.btnFavorite.setTag(projectId);
        boolean favorite = FavoriteOrderStore.isFavorite(
                holder.itemView.getContext(), projectId);
        holder.btnFavorite.setImageResource(favorite
                ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_outline);
        holder.btnFavorite.setContentDescription(favorite ? "取消收藏" : "收藏订单");
        holder.btnFavorite.setEnabled(projectId > 0 && routeId > 0
                && !pendingFavoriteProjectIds.contains(projectId));
        holder.btnFavorite.setOnClickListener(v -> {
            if (!SessionHelper.isLoggedIn(v.getContext())) {
                SessionHelper.handleUnauthorized(v.getContext());
                return;
            }
            boolean targetFavorite = !FavoriteOrderStore.isFavorite(v.getContext(), projectId);
            pendingFavoriteProjectIds.add(projectId);
            notifyProjectChanged(projectId);

            UserService service = ApiClient.getClient().create(UserService.class);
            Call<Result> call = targetFavorite
                    ? service.favoriteRoute(routeId)
                    : service.unfavoriteRoute(routeId);
            call.enqueue(new Callback<Result>() {
                @Override
                public void onResponse(@NonNull Call<Result> call,
                                       @NonNull Response<Result> response) {
                    pendingFavoriteProjectIds.remove(projectId);
                    if (response.code() == 401) {
                        notifyProjectChanged(projectId);
                        SessionHelper.handleUnauthorized(v.getContext());
                        return;
                    }
                    Result body = response.body();
                    if (!response.isSuccessful() || body == null || body.getCode() != 1) {
                        notifyProjectChanged(projectId);
                        boolean userStyle = userPresentation
                                || v.getContext() instanceof com.example.Japp.FavoriteOrdersActivity;
                        Toast.makeText(v.getContext(), userStyle ? "暂时无法更新收藏，请稍后重试"
                                        : body != null && body.getMsg() != null ? body.getMsg() : "收藏操作失败",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    FavoriteOrderStore.setFavorite(v.getContext(), item, targetFavorite);
                    notifyProjectChanged(projectId);
                    animateFavoriteButton(holder, projectId);
                    Toast.makeText(v.getContext(), targetFavorite ? "已收藏" : "已取消收藏",
                            Toast.LENGTH_SHORT).show();
                    if (favoriteChangedListener != null) {
                        favoriteChangedListener.onFavoriteChanged(item, targetFavorite);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<Result> call, @NonNull Throwable t) {
                    pendingFavoriteProjectIds.remove(projectId);
                    notifyProjectChanged(projectId);
                    Toast.makeText(v.getContext(), "网络错误，收藏状态未更改",
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void notifyProjectChanged(int projectId) {
        for (int i = 0; i < items.size(); i++) {
            Project project = items.get(i).getProject();
            if (project != null && project.getId() == projectId) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    private void animateFavoriteButton(Holder holder, int projectId) {
        Object boundProjectId = holder.btnFavorite.getTag();
        if (!(boundProjectId instanceof Integer)
                || ((Integer) boundProjectId) != projectId) {
            return;
        }
        holder.btnFavorite.animate().cancel();
        holder.btnFavorite.setScaleX(0.82f);
        holder.btnFavorite.setScaleY(0.82f);
        holder.btnFavorite.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

}
