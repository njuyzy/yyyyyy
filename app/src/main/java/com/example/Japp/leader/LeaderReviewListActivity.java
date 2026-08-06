package com.example.Japp.leader;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.Japp.R;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.LeaderReview;
import com.example.Japp.network.models.Result;
import com.example.Japp.user.util.SessionHelper;
import com.example.Japp.util.InsetDividerDecoration;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LeaderReviewListActivity extends AppCompatActivity {

    private static final int PAGE_SIZE = 20;

    private RecyclerView recycler;
    private SwipeRefreshLayout swipeRefresh;
    private TextView txtEmpty;
    private ReviewAdapter adapter;
    private UserService service;

    private int currentPage = 1;
    private int totalPages = 1;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_leader_review_list);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recycler = findViewById(R.id.recycler);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        txtEmpty = findViewById(R.id.txtEmpty);

        service = ApiClient.getClient().create(UserService.class);
        adapter = new ReviewAdapter();

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
        recycler.addItemDecoration(new InsetDividerDecoration(this, 14, 6));
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || loading || currentPage >= totalPages) return;
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm != null && lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 3) {
                    loadPage(currentPage + 1);
                }
            }
        });

        swipeRefresh.setOnRefreshListener(this::refresh);
        swipeRefresh.setRefreshing(true);
        refresh();
    }

    private void refresh() {
        currentPage = 1;
        totalPages = 1;
        adapter.setItems(new ArrayList<>());
        loadPage(1);
    }

    private void loadPage(int page) {
        if (loading) return;
        if (!SessionHelper.isLoggedIn(this)) {
            swipeRefresh.setRefreshing(false);
            showEmpty("请先登录");
            return;
        }
        loading = true;
        service.getLeaderReviews(page, PAGE_SIZE).enqueue(new Callback<Result<List<LeaderReview>>>() {
            @Override
            public void onResponse(@NonNull Call<Result<List<LeaderReview>>> call,
                                   @NonNull Response<Result<List<LeaderReview>>> response) {
                loading = false;
                swipeRefresh.setRefreshing(false);
                if (response.code() == 401) {
                    SessionHelper.handleUnauthorized(LeaderReviewListActivity.this);
                    return;
                }
                if (!response.isSuccessful() || response.body() == null
                        || response.body().getCode() != 1) {
                    Toast.makeText(LeaderReviewListActivity.this,
                            "加载失败，请下拉刷新重试", Toast.LENGTH_SHORT).show();
                    updateEmptyState();
                    return;
                }
                List<LeaderReview> items = response.body().getData();
                currentPage = page;
                if (items == null) {
                    updateEmptyState();
                    return;
                }
                if (page == 1 && items.size() < PAGE_SIZE) {
                    // 列表整体较短，一次性返回即可，避免无限加载
                    totalPages = 1;
                } else if (items.size() < PAGE_SIZE) {
                    totalPages = page;
                } else {
                    totalPages = page + 1;
                }
                adapter.appendItems(items);
                updateEmptyState();
            }

            @Override
            public void onFailure(@NonNull Call<Result<List<LeaderReview>>> call, @NonNull Throwable t) {
                loading = false;
                swipeRefresh.setRefreshing(false);
                Toast.makeText(LeaderReviewListActivity.this,
                        "网络错误，请检查网络后重试", Toast.LENGTH_SHORT).show();
                updateEmptyState();
            }
        });
    }

    private void updateEmptyState() {
        if (adapter.getItemCount() == 0) {
            txtEmpty.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.GONE);
        } else {
            txtEmpty.setVisibility(View.GONE);
            recycler.setVisibility(View.VISIBLE);
        }
    }

    private void showEmpty(String message) {
        txtEmpty.setText(message);
        txtEmpty.setVisibility(View.VISIBLE);
        recycler.setVisibility(View.GONE);
    }

    private static class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.VH> {

        private final List<LeaderReview> items = new ArrayList<>();

        void setItems(List<LeaderReview> data) {
            items.clear();
            items.addAll(data);
            notifyDataSetChanged();
        }

        void appendItems(List<LeaderReview> data) {
            int start = items.size();
            items.addAll(data);
            notifyItemRangeInserted(start, data.size());
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_leader_review, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(items.get(position));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final TextView txtReviewer;
            final TextView txtTime;
            final TextView txtRoute;
            final TextView txtContent;
            final RatingBar rating;

            VH(View itemView) {
                super(itemView);
                txtReviewer = itemView.findViewById(R.id.txtReviewer);
                txtTime = itemView.findViewById(R.id.txtTime);
                txtRoute = itemView.findViewById(R.id.txtRoute);
                txtContent = itemView.findViewById(R.id.txtContent);
                rating = itemView.findViewById(R.id.rating);
            }

            void bind(LeaderReview review) {
                String name = review.getReviewerName();
                txtReviewer.setText(name == null || name.isEmpty() ? "匿名用户" : name);
                rating.setRating(review.getOverallScore());
                String time = review.getCreatedAt();
                if (time != null && time.length() >= 10) {
                    txtTime.setText(time.substring(0, 10));
                } else {
                    txtTime.setText("");
                }
                txtRoute.setText("对应路线 #" + review.getRouteId() + " / 项目 #" + review.getProjectId());
                String content = review.getContent();
                txtContent.setText(content == null || content.isEmpty() ? "（无评语）" : content);
            }
        }
    }
}
