package com.example.Japp.user.fragment.joinTeam;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.example.Japp.network.models.Project;
import com.example.Japp.user.util.SessionHelper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** 按账号保存订单收藏快照。 */
public final class FavoriteOrderStore {

    private static final String PREFS = "favorite_orders";
    private static final String KEY_PREFIX = "orders_v1_account_";
    private static final int MAX_FAVORITES = 100;
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<Record>>() {}.getType();

    private FavoriteOrderStore() {}

    public static synchronized boolean isFavorite(@NonNull Context context, int projectId) {
        if (projectId <= 0) return false;
        for (Record record : read(context)) {
            if (record != null && record.project != null
                    && record.project.getId() == projectId) return true;
        }
        return false;
    }

    /** @return 切换后的收藏状态。 */
    public static synchronized boolean toggle(@NonNull Context context,
                                              @NonNull TeamCardItem item) {
        boolean favorite = item.getProject() != null
                && isFavorite(context, item.getProject().getId());
        setFavorite(context, item, !favorite);
        return !favorite;
    }

    /** 按后端已确认的状态更新本地订单快照。 */
    public static synchronized void setFavorite(@NonNull Context context,
                                                @NonNull TeamCardItem item,
                                                boolean favorite) {
        Project project = item.getProject();
        if (project == null || project.getId() <= 0) return;
        List<Record> records = read(context);
        for (int i = 0; i < records.size(); i++) {
            Record record = records.get(i);
            if (record != null && record.project != null
                    && record.project.getId() == project.getId()) {
                if (!favorite) {
                    records.remove(i);
                    write(context, records);
                }
                return;
            }
        }

        if (!favorite) return;

        Record record = new Record();
        record.savedAt = System.currentTimeMillis();
        record.project = copyProject(project);
        record.ownerName = item.getOwnerName();
        record.city = item.getCity();
        record.routeSummary = item.getRouteSummary();
        record.duration = item.getDuration();
        records.add(0, record);
        if (records.size() > MAX_FAVORITES) {
            records = new ArrayList<>(records.subList(0, MAX_FAVORITES));
        }
        write(context, records);
    }

    @NonNull
    public static synchronized List<TeamCardItem> getAll(@NonNull Context context) {
        List<TeamCardItem> result = new ArrayList<>();
        for (Record record : read(context)) {
            if (record == null || record.project == null) continue;
            TeamCardItem item = new TeamCardItem(copyProject(record.project));
            item.setOwnerName(record.ownerName);
            item.setCity(record.city);
            item.setRouteSummary(record.routeSummary);
            item.setDuration(record.duration);
            result.add(item);
        }
        return result;
    }

    @NonNull
    private static List<Record> read(@NonNull Context context) {
        String raw = preferences(context).getString(key(context), null);
        if (raw == null || raw.trim().isEmpty()) return new ArrayList<>();
        try {
            List<Record> records = GSON.fromJson(raw, LIST_TYPE);
            return records == null ? new ArrayList<>() : records;
        } catch (RuntimeException ignored) {
            return new ArrayList<>();
        }
    }

    private static void write(@NonNull Context context, @NonNull List<Record> records) {
        preferences(context).edit().putString(key(context), GSON.toJson(records)).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(Context context) {
        int accountId = SessionHelper.getAccountId(context);
        return KEY_PREFIX + (accountId > 0 ? accountId : "guest");
    }

    private static Project copyProject(Project project) {
        return GSON.fromJson(GSON.toJson(project), Project.class);
    }

    private static final class Record {
        long savedAt;
        Project project;
        String ownerName;
        String city;
        String routeSummary;
        String duration;
    }
}
