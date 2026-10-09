package com.example.Japp.user.util;

import android.app.Activity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.Japp.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * 领队放弃接单的原因输入对话框。
 *
 * <p>负责收集并校验 5-200 字的放弃原因，校验通过后通过回调返回去除首尾空白的原因。</p>
 */
public final class AbandonReasonDialog {

    public static final int MIN_LENGTH = 5;
    public static final int MAX_LENGTH = 200;

    public interface Listener {
        void onConfirmed(@NonNull String reason);
    }

    private AbandonReasonDialog() {}

    public static void show(@NonNull Activity activity, @Nullable String prefill,
                            @NonNull Listener listener) {
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_abandon_order, null);
        TextInputLayout reasonLayout = content.findViewById(R.id.abandonReasonLayout);
        TextInputEditText reasonInput = content.findViewById(R.id.editAbandonReason);
        if (prefill != null && !prefill.isEmpty()) {
            reasonInput.setText(prefill);
            reasonInput.setSelection(reasonInput.getText() != null
                    ? reasonInput.getText().length() : 0);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("放弃接单")
                .setView(content)
                .setNegativeButton("暂不放弃", null)
                .setPositiveButton("确认放弃", null)
                .create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String reason = reasonInput.getText() == null
                    ? "" : reasonInput.getText().toString().trim();
            if (reason.length() < MIN_LENGTH || reason.length() > MAX_LENGTH) {
                reasonLayout.setError("请输入 " + MIN_LENGTH + "-" + MAX_LENGTH + " 字的放弃原因");
                return;
            }
            reasonLayout.setError(null);
            dialog.dismiss();
            listener.onConfirmed(reason);
        });
    }
}
