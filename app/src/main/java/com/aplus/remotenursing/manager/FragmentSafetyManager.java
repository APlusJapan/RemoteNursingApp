    package com.aplus.remotenursing.manager;


import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.util.Log;
import androidx.fragment.app.Fragment;

import com.aplus.remotenursing.common.InfoPopup;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;

/**
 * 统一的Fragment生命周期安全管理器
 * 包含基础安全功能和自动重试机制
 */
public class FragmentSafetyManager {
    private static final String TAG = "FragmentSafetyManager";
    private static final int MAX_RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 2000; // 2秒后重试

    // ==================== 基础安全功能 ====================

    /**
     * 安全的Fragment状态检查接口
     */
    public interface SafeFragmentCallback {
        void onSafeExecute();
    }

    /**
     * 网络请求成功回调接口
     */
    public interface SafeNetworkSuccessCallback {
        void onSuccess(String responseBody) throws Exception;
    }

    /**
     * 网络请求失败回调接口
     */
    public interface SafeNetworkFailureCallback {
        void onFailure(String errorMessage);
    }

    /**
     * 计数成功回调接口
     */
    public interface CountSuccessCallback {
        void onSuccess(long count);
    }

    /**
     * 用户操作选择接口
     */
    public interface UserChoiceCallback {
        void onRetry();
        void onGoBack();
        void onStayAndWait();
    }

    /**
     * 检查Fragment是否安全可用
     */
    public static boolean isFragmentSafe(Fragment fragment) {
        return fragment != null && fragment.isAdded() && fragment.getActivity() != null;
    }

    /**
     * 安全执行UI操作
     */
    public static void safeExecuteOnUI(Fragment fragment, SafeFragmentCallback callback) {
        if (isFragmentSafe(fragment)) {
            fragment.requireActivity().runOnUiThread(() -> {
                if (isFragmentSafe(fragment)) {
                    try {
                        callback.onSafeExecute();
                    } catch (Exception e) {
                        Log.e(TAG, "Error in safe UI execution: " + e.getMessage(), e);
                    }
                } else {
                    Log.w(TAG, "Fragment became unsafe before UI execution");
                }
            });
        } else {
            Log.w(TAG, "Fragment is not safe for UI operations");
        }
    }

    /**
     * 立即安全执行（不切换到UI线程）
     */
    public static void safeExecuteImmediate(Fragment fragment, SafeFragmentCallback callback) {
        if (isFragmentSafe(fragment)) {
            try {
                callback.onSafeExecute();
            } catch (Exception e) {
                Log.e(TAG, "Error in safe immediate execution: " + e.getMessage(), e);
            }
        } else {
            Log.w(TAG, "Fragment is not safe for immediate execution");
        }
    }

    // ==================== 基础网络回调 ====================

    /**
     * 创建基础的安全网络请求Callback
     */
    public static Callback createSafeCallback(Fragment fragment,
                                              SafeNetworkSuccessCallback successCallback,
                                              SafeNetworkFailureCallback failureCallback) {
        return new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network request failed: " + e.getMessage(), e);
                safeExecuteOnUI(fragment, () -> {
                    if (failureCallback != null) {
                        failureCallback.onFailure("网络请求失败: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        Log.d(TAG, "Network request successful, response length: " + responseBody.length());

                        safeExecuteOnUI(fragment, () -> {
                            if (successCallback != null) {
                                try {
                                    successCallback.onSuccess(responseBody);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error in success callback: " + e.getMessage(), e);
                                    if (failureCallback != null) {
                                        failureCallback.onFailure("数据处理失败: " + e.getMessage());
                                    }
                                }
                            }
                        });
                    } else {
                        Log.w(TAG, "Network request failed with code: " + response.code());
                        safeExecuteOnUI(fragment, () -> {
                            if (failureCallback != null) {
                                failureCallback.onFailure("服务器错误: " + response.code());
                            }
                        });
                    }
                } finally {
                    response.close();
                }
            }
        };
    }

    /**
     * 创建简单的安全网络请求Callback（只有成功回调）
     */
    public static Callback createSafeCallback(Fragment fragment,
                                              SafeNetworkSuccessCallback successCallback) {
        return createSafeCallback(fragment, successCallback, null);
    }

    /**
     * 创建专门用于解析JSON的安全Callback
     */
    public static Callback createSafeJsonCallback(Fragment fragment,
                                                  SafeNetworkSuccessCallback successCallback,
                                                  SafeNetworkFailureCallback failureCallback) {
        return createSafeCallback(fragment, successCallback, failureCallback);
    }

    /**
     * 专门用于处理计数类返回值的安全Callback
     */
    public static Callback createSafeCountCallback(Fragment fragment,
                                                   CountSuccessCallback successCallback,
                                                   SafeNetworkFailureCallback failureCallback) {
        return new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Count request failed: " + e.getMessage(), e);
                safeExecuteOnUI(fragment, () -> {
                    if (failureCallback != null) {
                        failureCallback.onFailure("网络请求失败");
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        Log.d(TAG, "Count request successful: " + responseBody);

                        try {
                            long count = Long.parseLong(responseBody.trim());
                            safeExecuteOnUI(fragment, () -> {
                                if (successCallback != null) {
                                    successCallback.onSuccess(count);
                                }
                            });
                        } catch (NumberFormatException e) {
                            Log.e(TAG, "Count parsing error: " + e.getMessage(), e);
                            safeExecuteOnUI(fragment, () -> {
                                if (failureCallback != null) {
                                    failureCallback.onFailure("数据格式错误");
                                }
                            });
                        }
                    } else {
                        Log.w(TAG, "Count request failed with code: " + response.code());
                        safeExecuteOnUI(fragment, () -> {
                            if (failureCallback != null) {
                                failureCallback.onFailure("服务器错误: " + response.code());
                            }
                        });
                    }
                } finally {
                    response.close();
                }
            }
        };
    }

    // ==================== 自动重试功能 ====================

    /**
     * 创建带自动重试的安全Callback（关键操作专用）
     */
    public static Callback createAutoRetryCallback(Fragment fragment,
                                                   Request request,
                                                   OkHttpClient client,
                                                   SafeNetworkSuccessCallback successCallback,
                                                   String operationName) {
        return createAutoRetryCallback(fragment, request, client, successCallback, operationName, 0);
    }

    private static Callback createAutoRetryCallback(Fragment fragment,
                                                    Request request,
                                                    OkHttpClient client,
                                                    SafeNetworkSuccessCallback successCallback,
                                                    String operationName,
                                                    int currentRetryCount) {
        return new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, operationName + " 失败 (第" + (currentRetryCount + 1) + "次): " + e.getMessage());

                if (currentRetryCount < MAX_RETRY_COUNT) {
                    // 还有重试机会，自动重试
                    safeExecuteOnUI(fragment, () -> {
                        showRetryingDialog(fragment, operationName, currentRetryCount + 1);
                    });

                    // 延迟后重试
                    new android.os.Handler().postDelayed(() -> {
                        if (isFragmentSafe(fragment)) {
                            Log.d(TAG, "自动重试 " + operationName + " (第" + (currentRetryCount + 2) + "次)");
                            client.newCall(request).enqueue(
                                    createAutoRetryCallback(fragment, request, client,
                                            successCallback, operationName, currentRetryCount + 1)
                            );
                        }
                    }, RETRY_DELAY_MS);
                } else {
                    // 重试次数用完，显示友好的错误处理选项
                    showFriendlyErrorDialog(fragment, operationName, () -> {
                        // 用户选择手动重试
                        if (isFragmentSafe(fragment)) {
                            client.newCall(request).enqueue(
                                    createAutoRetryCallback(fragment, request, client,
                                            successCallback, operationName, 0)
                            );
                        }
                    });
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        Log.d(TAG, operationName + " 成功");

                        safeExecuteOnUI(fragment, () -> {
                            try {
                                successCallback.onSuccess(responseBody);
                            } catch (Exception e) {
                                Log.e(TAG, operationName + " 数据处理失败: " + e.getMessage(), e);
                                showFriendlyErrorDialog(fragment, "数据处理", () -> {
                                    try {
                                        successCallback.onSuccess(responseBody);
                                    } catch (Exception ex) {
                                        showSimpleErrorAndGoBack(fragment, "数据格式有误，请联系技术支持");
                                    }
                                });
                            }
                        });
                    } else {
                        Log.w(TAG, operationName + " 服务器错误: " + response.code());
                        onFailure(call, new IOException("服务器错误: " + response.code()));
                    }
                } finally {
                    response.close();
                }
            }
        };
    }

    /**
     * 创建用于计数数据的带重试安全Callback
     */
    public static Callback createCountCallbackWithRetry(Fragment fragment,
                                                        Request request,
                                                        OkHttpClient client,
                                                        CountSuccessCallback successCallback,
                                                        String operationName) {
        return createAutoRetryCallback(fragment, request, client,
                (responseBody) -> {
                    try {
                        long count = Long.parseLong(responseBody.trim());
                        successCallback.onSuccess(count);
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("数据格式错误", e);
                    }
                },
                operationName
        );
    }

    /**
     * 便捷的带重试网络请求执行方法
     */
    public static void executeWithRetry(Fragment fragment,
                                        OkHttpClient client,
                                        Request request,
                                        SafeNetworkSuccessCallback successCallback,
                                        String operationName) {
        if (!isFragmentSafe(fragment)) {
            Log.w(TAG, "Fragment不安全，取消网络请求: " + operationName);
            return;
        }

        client.newCall(request).enqueue(
                createAutoRetryCallback(fragment, request, client, successCallback, operationName)
        );
    }

    // ==================== 用户友好的对话框 ====================

    /**
     * 显示重试中的提示对话框
     */
    private static void showRetryingDialog(Fragment fragment, String operationName, int retryCount) {
        if (!isFragmentSafe(fragment)) return;

        String message = String.format("网络不稳定，正在重新%s...\n第%d次尝试，请稍候", operationName, retryCount);

        AlertDialog dialog = new AlertDialog.Builder(fragment.requireContext())
                .setTitle("自动重试中")
                .setMessage(message)
                .setCancelable(false)
                .create();

        dialog.show();

        // 1.8秒后自动关闭对话框
        new android.os.Handler().postDelayed(() -> {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        }, 1800);
    }

    /**
     * 显示友好的错误处理对话框
     */
    private static void showFriendlyErrorDialog(Fragment fragment, String operationName, Runnable onRetry) {
        if (!isFragmentSafe(fragment)) return;

        String title = operationName + "遇到问题";
        String message = String.format("很抱歉，%s时遇到了网络问题。\n\n请选择下一步操作：", operationName);

        AlertDialog dialog = new AlertDialog.Builder(fragment.requireContext())
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("重新尝试", (d, w) -> {
                    d.dismiss();
                    onRetry.run();
                })
                .setNegativeButton("返回上页", (d, w) -> {
                    d.dismiss();
                    safePopBackStack(fragment);
                })
                .setNeutralButton("稍后再试", (d, w) -> {
                    d.dismiss();
                    showSimpleInfo(fragment, "您可以稍后再来尝试" + operationName);
                })
                .create();

        dialog.show();

        // 设置适老化字体大小
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(18);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextSize(18);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextSize(18);
    }

    /**
     * 显示简单错误信息并返回
     */
    private static void showSimpleErrorAndGoBack(Fragment fragment, String message) {
        if (!isFragmentSafe(fragment)) return;

        AlertDialog dialog = new AlertDialog.Builder(fragment.requireContext())
                .setTitle("提示")
                .setMessage(message)
                .setPositiveButton("我知道了", (d, w) -> {
                    d.dismiss();
                    safePopBackStack(fragment);
                })
                .setCancelable(false)
                .create();

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(18);
    }

    /**
     * 显示简单信息提示
     */
    private static void showSimpleInfo(Fragment fragment, String message) {
        if (!isFragmentSafe(fragment)) return;

        AlertDialog dialog = new AlertDialog.Builder(fragment.requireContext())
                .setTitle("提示")
                .setMessage(message)
                .setPositiveButton("我知道了", (d, w) -> d.dismiss())
                .create();

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextSize(18);
    }

    // ==================== 便捷方法 ====================

    /**
     * 显示错误提示的便捷方法
     */
    public static void showError(Fragment fragment, String message) {
        safeExecuteOnUI(fragment, () -> {
            if (fragment.getContext() != null) {
                InfoPopup.showError(fragment.getContext(), message);
            }
        });
    }

    /**
     * 显示成功提示的便捷方法
     */
    public static void showSuccess(Fragment fragment, String message) {
        safeExecuteOnUI(fragment, () -> {
            if (fragment.getContext() != null) {
                InfoPopup.showSuccess(fragment.getContext(), message);
            }
        });
    }

    /**
     * 安全返回上一页
     */
    public static void safePopBackStack(Fragment fragment) {
        safeExecuteOnUI(fragment, () -> {
            fragment.requireActivity().getSupportFragmentManager().popBackStack();
        });
    }
}