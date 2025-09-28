    package com.aplus.remotenursing.common;

    import android.app.AlertDialog;
    import android.content.Context;
    import android.os.Handler;
    import android.os.Looper;
    import android.view.LayoutInflater;
    import android.view.View;
    import android.widget.Button;
    import android.widget.TextView;

    import androidx.core.content.ContextCompat;

    import com.aplus.remotenursing.R;

    /**
     * Utility class to show bigger information popup instead of Toast.
     * Displays message with larger font, color-coded for success or error,
     * includes a close button and dismisses automatically after 5 seconds.
     */
    public class InfoPopup {

        private static final long AUTO_DISMISS_MS = 5000L;

        public static void showSuccess(Context context, String message) {
            show(context, message, true);
        }

        public static void showError(Context context, String message) {
            show(context, message, false);
        }

        private static void show(Context context, String message, boolean success) {
            if (context == null) return;
            View view = LayoutInflater.from(context).inflate(R.layout.info_popup, null);
            View root = view.findViewById(R.id.popup_root);
            TextView tvMessage = view.findViewById(R.id.tv_message);
            Button btnClose = view.findViewById(R.id.btn_close);

            tvMessage.setText(message);
            int color = ContextCompat.getColor(context, success ? R.color.success_color : R.color.error_color);
            root.setBackgroundColor(color);

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setView(view)
                    .create();

            btnClose.setOnClickListener(v -> dialog.dismiss());

            dialog.show();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            }, AUTO_DISMISS_MS);
        }
    }