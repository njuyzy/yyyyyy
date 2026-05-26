package com.example.Japp.user.fragment.route;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.example.Japp.R;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 用户端智能路线：对话式需求输入；高德地图嵌入在助手回答气泡内展示折线。
 */
public class routeDesign extends Fragment {

    private RecyclerView chatRecyclerView;
    private RouteChatAdapter adapter;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MapsInitializer.updatePrivacyShow(requireContext(), true, true);
        MapsInitializer.updatePrivacyAgree(requireContext(), true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.user_fragment_route_design, container, false);

        MaterialToolbar toolbar = root.findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(null);

        chatRecyclerView = root.findViewById(R.id.chatRecyclerView);
        adapter = new RouteChatAdapter();
        adapter.setRecyclerView(chatRecyclerView);
        adapter.setMapCreateBundle(savedInstanceState);
        adapter.setListener((item, position) -> {
            // TODO: 调用后端「发布路线」接口，例如 POST /api/routes/publish
            Toast.makeText(requireContext(), "已触发发布（待接后端）", Toast.LENGTH_SHORT).show();
        });
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        chatRecyclerView.setAdapter(adapter);

        root.findViewById(R.id.btnSend).setOnClickListener(v -> sendRouteRequest());

        return root;
    }

    private void sendRouteRequest() {
        View root = getView();
        if (root == null) return;
        com.google.android.material.textfield.TextInputEditText edit =
                root.findViewById(R.id.editMessage);
        String text = edit.getText() != null ? edit.getText().toString().trim() : "";
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(requireContext(), "请输入路线需求", Toast.LENGTH_SHORT).show();
            return;
        }

        adapter.addItem(RouteChatItem.user(text));
        edit.setText("");
        scrollChatToBottom();

        /*
         * ========== 后端对接（请在此实现）==========
         * 1. 将 text POST 到服务端，解析得到 description + List<LatLng>（GCJ-02）
         * 2. 成功回调中：adapter.addItem(RouteChatItem.assistantRoute(description, points));
         *    scrollChatToBottom();
         * ==========================================
         */
        simulateAssistantResponse(text);
    }

    private void simulateAssistantResponse(String userRequirement) {
        mainHandler.postDelayed(() -> {
            String description = buildMockDescription(userRequirement);
            List<LatLng> points = buildMockPolyline();
            adapter.addItem(RouteChatItem.assistantRoute(description, points));
            scrollChatToBottom();
        }, 900);
    }

    private String buildMockDescription(String requirement) {
        return "根据你的描述「" + requirement + "」，推荐如下方案：\n\n"
                + "• 出行方式：地铁 + 步行\n"
                + "• 预计总时长：约 45 分钟\n"
                + "• 途经：鼓楼 → 珠江路 → 大行宫 → 夫子庙景区\n"
                + "• 地图中为示意路径，接入后端后将替换为真实规划。\n\n"
                + "确认无误可点击下方「发布」。";
    }

    private List<LatLng> buildMockPolyline() {
        return new ArrayList<>(Arrays.asList(
                new LatLng(32.058, 118.772),
                new LatLng(32.052, 118.788),
                new LatLng(32.048, 118.798),
                new LatLng(32.022, 118.796)
        ));
    }

    private void scrollChatToBottom() {
        if (adapter.getItemCount() > 0) {
            chatRecyclerView.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.onHostResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (adapter != null) {
            adapter.onHostPause();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (adapter != null) {
            adapter.onHostDestroy();
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (adapter != null) {
            adapter.onHostLowMemory();
        }
    }
}
