package com.example.Japp.user;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.example.Japp.R;
import com.example.Japp.Chat.util.ChatAvatarLoader;
import com.example.Japp.network.ApiClient;
import com.example.Japp.network.api.UserService;
import com.example.Japp.network.models.Account;
import com.example.Japp.network.models.Result;
import com.example.Japp.user.util.ProjectUiHelper;
import com.example.Japp.user.util.UserOrderPresentation;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Read-only public profile. Account credentials, phone and internal state are never bound to views. */
public final class PublisherProfileSheet extends BottomSheetDialog {
    private final int accountId;
    private Call<Result<Account>> request;
    private final ImageView avatar;
    private final TextView initial;
    private final TextView name;
    private final TextView region;
    private final TextView intro;
    private final View loading;
    private final View retry;

    public static void open(Context context, int accountId, String name, String avatarUrl) {
        if (accountId <= 0) return;
        PublisherProfileSheet sheet = new PublisherProfileSheet(context, accountId, name, avatarUrl);
        sheet.show();
        sheet.loadProfile();
    }

    private PublisherProfileSheet(Context context, int accountId, String displayName, String avatarUrl) {
        super(context);
        this.accountId = accountId;
        setContentView(R.layout.dialog_user_publisher_profile);
        avatar = findViewById(R.id.publisherProfileAvatar);
        initial = findViewById(R.id.publisherProfileInitial);
        name = findViewById(R.id.publisherProfileName);
        region = findViewById(R.id.publisherProfileRegion);
        intro = findViewById(R.id.publisherProfileIntro);
        loading = findViewById(R.id.publisherProfileLoading);
        retry = findViewById(R.id.publisherProfileRetry);
        name.setText(safeName(displayName));
        region.setText("正在加载资料…");
        intro.setText("正在加载简介…");
        ChatAvatarLoader.bind(avatar, initial, avatarUrl, safeName(displayName));
        findViewById(R.id.publisherProfileClose).setOnClickListener(v -> dismiss());
        retry.setOnClickListener(v -> loadProfile());
        getBehavior().setMaxHeight((int) (context.getResources().getDisplayMetrics().heightPixels * 0.85f));
        setOnDismissListener(dialog -> {
            if (request != null) request.cancel();
            avatar.setTag(null);
        });
    }

    private void loadProfile() {
        if (request != null) request.cancel();
        loading.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
        request = ApiClient.getClient().create(UserService.class).getAccount(accountId);
        request.enqueue(new Callback<Result<Account>>() {
            @Override public void onResponse(@NonNull Call<Result<Account>> call,
                                             @NonNull Response<Result<Account>> response) {
                if (!isShowing() || call != request) return;
                loading.setVisibility(View.GONE);
                Account account = response.isSuccessful() && response.body() != null
                        && response.body().getCode() == 1 ? response.body().getData() : null;
                if (account == null) { showFailure(); return; }
                name.setText(safeName(account.getUsername()));
                ChatAvatarLoader.bind(avatar, initial, account.getAvatarUrl(), name.getText().toString());
                String place = UserOrderPresentation.clean(account.getRegionCode());
                if (place.matches("\\d+")) place = ProjectUiHelper.regionAdcodeToCity(place);
                place = UserOrderPresentation.city(place);
                region.setText(place.isEmpty() ? "所在地未填写" : "所在地 · " + place);
                String description = UserOrderPresentation.clean(account.getIntro());
                intro.setText(description.isEmpty() ? "这位发布者还没有填写个人简介。" : description);
            }
            @Override public void onFailure(@NonNull Call<Result<Account>> call, @NonNull Throwable error) {
                if (!isShowing() || call != request || call.isCanceled()) return;
                showFailure();
            }
        });
    }

    private void showFailure() {
        loading.setVisibility(View.GONE);
        retry.setVisibility(View.VISIBLE);
        region.setText("所在地暂未获取");
        intro.setText("暂时无法获取个人简介，请稍后重试。");
    }

    private static String safeName(String value) {
        String name = UserOrderPresentation.clean(value);
        return name.isEmpty() ? "发布者" : name;
    }
}
