package com.raincat.dolby_beta.hook;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.raincat.dolby_beta.helper.ExtraHelper;
import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.view.BaseDialogInputItem;
import com.raincat.dolby_beta.view.BaseDialogItem;
import com.raincat.dolby_beta.view.proxy.*;
import com.raincat.dolby_beta.view.proxy.configuration.*;
import com.raincat.dolby_beta.view.setting.TitleView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * <pre>
 *     author : RainCat
 *     time   : 2019/10/26
 *     desc   : 设置Hook - 仅保留音源代理相关设置界面
 *              入口方式：长按底部导航栏"我的"按钮，弹出代理设置对话框
 *
 *              长按事件完整调用链（基于反编译源码分析）：
 *              1. rm0.m.e() 给视图设置 OnLongClickListener
 *                 回调为 rm0.m.n(handler, this$0, view)
 *              2. n() 调用 handler.b(this$0.getTabCode())
 *                 handler 实际类型为 dl0.c$d (classes20.dex)
 *              3. dl0.c$d.b(tabCode) → parentTabLayoutHandler.b(tabCode)
 *                 parentTabLayoutHandler 实际类型为 p$b (classes5.dex)
 *              4. p$b.b(tabCode) → p.k() → NavigationTabLayout.l(p)
 *
 *              Hook策略：Hook p$b.b(String) 具体实现类方法
 *              p$b 是 dl0.h 接口的最终实现类，直接调用 p.k()
 *              位于 classes5.dex，比 dl0.c$d (classes20.dex) 更早可用
 *
 *              为什么不能Hook接口方法dl0.h.b()：
 *              Xposed hookMethod 对接口方法不生效，ART运行时调用的是
 *              具体实现类的方法，不会经过接口方法入口
 *
 *              为什么不使用视图注入方式：
 *              rm0.m.e() 在 LiveData 数据变化时被重新调用，会覆盖监听器
 *
 *     version: 4.1
 * </pre>
 */
public class SettingHook {
    private static final String TAG = "dolby_beta";

    private LinearLayout dialogRoot, dialogProxyRoot, dialogScriptRoot;
    private BroadcastReceiver broadcastReceiver;
    /** 标记hook是否已成功应用 */
    private boolean hookApplied = false;
    /** 主线程Handler */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public SettingHook(Context context, int versionCode) {
        XposedBridge.log("SettingHook: 初始化，入口方式=长按底部'我的'按钮");
        Log.d(TAG, "SettingHook: 初始化，入口方式=长按底部'我的'按钮");

        // 注册Activity生命周期回调，在onResume时尝试hook
        if (context instanceof android.app.Application) {
            registerLifecycleCallbacks((android.app.Application) context);
        }

        // 立即尝试hook（Application.onCreate时可能dex已加载）
        tryHook(context.getClassLoader());
    }

    /**
     * 注册ActivityLifecycleCallbacks
     * 在MainActivity.onResume时尝试hook（此时dex一定已加载）
     */
    private void registerLifecycleCallbacks(android.app.Application app) {
        app.registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, android.os.Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(Activity activity) {}

            @Override
            public void onActivityResumed(Activity activity) {
                if (!hookApplied && activity.getClass().getName().equals("com.netease.cloudmusic.activity.MainActivity")) {
                    // 延迟执行，确保所有类已加载
                    mainHandler.postDelayed(() -> tryHook(activity.getClassLoader()), 1000);
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {}

            @Override
            public void onActivityStopped(Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(Activity activity, android.os.Bundle outState) {}

            @Override
            public void onActivityDestroyed(Activity activity) {}
        });
    }

    /**
     * 尝试Hook长按事件的具体实现类方法
     *
     * 按优先级依次尝试Hook以下类（都是dl0.h接口的具体实现类）：
     * 1. com.netease.cloudmusic.theme.ui.p$b (classes5.dex) — 最优，最早可用
     *    p$b.b(tabCode) 直接调用 p.k() → NavigationTabLayout.l(p)
     * 2. dl0.c$d (classes20.dex) — 备选，中转层
     *    dl0.c$d.b(tabCode) 调用 parentTabLayoutHandler.b(tabCode)
     *
     * 注意：不能Hook接口dl0.h.b()，Xposed对接口方法hook不生效
     */
    private void tryHook(ClassLoader classLoader) {
        if (hookApplied) return;

        // 策略1：Hook p$b.b(String) — 在classes5.dex，更早可用
        if (tryHookPb(classLoader)) return;

        // 策略2：Hook dl0.c$d.b(String) — 在classes20.dex，备选
        if (tryHookDl0Cd(classLoader)) return;

        XposedBridge.log("SettingHook: 所有hook策略均未成功，等待重试");
    }

    /**
     * Hook com.netease.cloudmusic.theme.ui.p$b.b(String)
     *
     * p$b 是 p 的内部类，实现了 dl0.h 接口
     * 其 b(String tabCode) 方法是长按事件的最终实现，直接调用 p.k()
     *
     * 源码：
     * public boolean b(@NotNull String tabCode) {
     *     Intrinsics.checkNotNullParameter(tabCode, "tabCode");
     *     return p.this.k();  // 调用NavigationTabLayout.l(p)
     * }
     */
    private boolean tryHookPb(ClassLoader classLoader) {
        try {
            Class<?> pbClass = XposedHelpers.findClassIfExists(
                    "com.netease.cloudmusic.theme.ui.p$b", classLoader);
            if (pbClass == null) {
                XposedBridge.log("SettingHook: [策略1] 找不到p$b类");
                return false;
            }

            // 查找 b(String) 方法 — 长按回调
            Method methodB = findBooleanStringMethod(pbClass);
            if (methodB == null) {
                XposedBridge.log("SettingHook: [策略1] p$b中未找到b(String)方法");
                return false;
            }

            XposedBridge.hookMethod(methodB, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String tabCode = (String) param.args[0];
                    if ("mine".equals(tabCode)) {
                        Activity activity = getCurrentActivity();
                        if (activity != null) {
                            showSettingDialog(activity);
                            param.setResult(true);
                            XposedBridge.log("SettingHook: [策略1-p$b] 长按'我的'成功!");
                        }
                    }
                }
            });

            hookApplied = true;
            XposedBridge.log("SettingHook: [策略1] 成功hook p$b.b(String)!");
            return true;
        } catch (Throwable e) {
            XposedBridge.log("SettingHook: [策略1] hook p$b失败 - " + e.getMessage());
            return false;
        }
    }

    /**
     * Hook dl0.c$d.b(String)
     *
     * dl0.c$d 是 dl0.c 的内部类，实现了 dl0.h 接口
     * 其 b(String tabCode) 方法是长按事件的中转层
     *
     * 源码：
     * public boolean b(@NotNull String tabCode) {
     *     Intrinsics.checkNotNullParameter(tabCode, "tabCode");
     *     return c.this.parentTabLayoutHandler.b(tabCode);
     * }
     */
    private boolean tryHookDl0Cd(ClassLoader classLoader) {
        try {
            Class<?> cdClass = XposedHelpers.findClassIfExists("dl0.c$d", classLoader);
            if (cdClass == null) {
                XposedBridge.log("SettingHook: [策略2] 找不到dl0.c$d类");
                return false;
            }

            Method methodB = findBooleanStringMethod(cdClass);
            if (methodB == null) {
                XposedBridge.log("SettingHook: [策略2] dl0.c$d中未找到b(String)方法");
                return false;
            }

            XposedBridge.hookMethod(methodB, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String tabCode = (String) param.args[0];
                    if ("mine".equals(tabCode)) {
                        Activity activity = getCurrentActivity();
                        if (activity != null) {
                            showSettingDialog(activity);
                            param.setResult(true);
                            XposedBridge.log("SettingHook: [策略2-dl0.c$d] 长按'我的'成功!");
                        }
                    }
                }
            });

            hookApplied = true;
            XposedBridge.log("SettingHook: [策略2] 成功hook dl0.c$d.b(String)!");
            return true;
        } catch (Throwable e) {
            XposedBridge.log("SettingHook: [策略2] hook dl0.c$d失败 - " + e.getMessage());
            return false;
        }
    }

    /**
     * 在类中查找 boolean b(String) 方法
     * 用于定位 dl0.h 接口的 b(String tabCode) 长按回调实现
     */
    private Method findBooleanStringMethod(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getReturnType() == boolean.class
                    && method.getParameterTypes().length == 1
                    && method.getParameterTypes()[0] == String.class) {
                return method;
            }
        }
        return null;
    }

    /**
     * 通过反射获取当前最顶层的Activity
     */
    private Activity getCurrentActivity() {
        try {
            Class<?> atClass = Class.forName("android.app.ActivityThread");
            Object at = atClass.getMethod("currentActivityThread").invoke(null);
            Field activitiesField = atClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<Object, Object> activities = (Map<Object, Object>) activitiesField.get(at);
            if (activities != null) {
                for (Object record : activities.values()) {
                    Field activityField = record.getClass().getDeclaredField("activity");
                    activityField.setAccessible(true);
                    Activity a = (Activity) activityField.get(record);
                    if (a != null && !a.isFinishing() && !a.isDestroyed()) return a;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ==================== 对话框相关方法 ====================

    private void showSettingDialog(final Context context) {
        dialogRoot = new BaseDialogItem(context);
        dialogRoot.setOrientation(LinearLayout.VERTICAL);
        ScrollView scrollView = new ScrollView(context);
        scrollView.setOverScrollMode(ScrollView.OVER_SCROLL_NEVER);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(dialogRoot);

        // 代理总开关（其他选项依赖此开关）
        ProxyMasterView proxyMasterView = new ProxyMasterView(context);
        // 重新释放脚本、脚本参数配置（放在代理开关下方）
        ProxyCoverView proxyCoverView = new ProxyCoverView(context);
        proxyCoverView.setBaseOnView(proxyMasterView);
        ScriptConfigurationView scriptConfigurationView = new ScriptConfigurationView(context);
        scriptConfigurationView.setBaseOnView(proxyMasterView);
        // 各功能项，均依赖总开关
        ProxyPriorityView proxyPriorityView = new ProxyPriorityView(context);
        proxyPriorityView.setBaseOnView(proxyMasterView);
        ProxyFlacView proxyFlacView = new ProxyFlacView(context);
        proxyFlacView.setBaseOnView(proxyMasterView);
        ProxyGrayView proxyGrayView = new ProxyGrayView(context);
        proxyGrayView.setBaseOnView(proxyMasterView);
        // 启用服务器代理（放在服务器代理配置上面）
        ProxyServerView proxyServerView = new ProxyServerView(context);
        proxyServerView.setBaseOnView(proxyMasterView);
        ProxyConfigurationView proxyConfigurationView = new ProxyConfigurationView(context);
        proxyConfigurationView.setBaseOnView(proxyMasterView);

        dialogRoot.addView(new TitleView(context));
        dialogRoot.addView(proxyMasterView);
        dialogRoot.addView(proxyCoverView);
        dialogRoot.addView(scriptConfigurationView);
        dialogRoot.addView(proxyPriorityView);
        dialogRoot.addView(proxyFlacView);
        dialogRoot.addView(proxyGrayView);
        dialogRoot.addView(proxyServerView);
        dialogRoot.addView(proxyConfigurationView);

        registerBroadcastReceiver(context);

        new AlertDialog.Builder(context)
                .setView(scrollView)
                .setCancelable(false)
                .setPositiveButton("确定", (dialogInterface, i) -> {})
                .setNegativeButton("重启网易云", (dialogInterface, i) -> restartApplication(context)).show();
    }

    private void showProxyConfigurationDialog(final Context context) {
        dialogProxyRoot = new BaseDialogItem(context);
        dialogProxyRoot.setOrientation(LinearLayout.VERTICAL);
        ProxyHttpView proxyHttpView = new ProxyHttpView(context);
        ProxyPortView proxyPortView = new ProxyPortView(context);

        dialogProxyRoot.addView(new ProxyConfigurationTitleView(context));
        dialogProxyRoot.addView(proxyHttpView);
        dialogProxyRoot.addView(proxyPortView);

        new AlertDialog.Builder(context)
                .setView(dialogProxyRoot)
                .setCancelable(true)
                .setPositiveButton("仅保存", (dialogInterface, i) -> {})
                .setNegativeButton("保存并重启", (dialogInterface, i) -> restartApplication(context)).show();
    }

    private void showScriptConfigurationDialog(final Context context) {
        dialogScriptRoot = new BaseDialogItem(context);
        dialogScriptRoot.setOrientation(LinearLayout.VERTICAL);
        ProxyOriginalView proxyOriginalView = new ProxyOriginalView(context);
        ProxyQqView proxyQqView = new ProxyQqView(context);
        ProxyMiguView proxyMiguView = new ProxyMiguView(context);

        dialogScriptRoot.addView(new ScriptConfigurationTitleView(context));
        dialogScriptRoot.addView(proxyOriginalView);
        dialogScriptRoot.addView(proxyQqView);
        dialogScriptRoot.addView(proxyMiguView);

        new AlertDialog.Builder(context)
                .setView(dialogScriptRoot)
                .setCancelable(true)
                .setPositiveButton("仅保存", (dialogInterface, i) -> {})
                .setNegativeButton("保存并重启", (dialogInterface, i) -> restartApplication(context)).show();
    }

    private void registerBroadcastReceiver(final Context context) {
        if (broadcastReceiver != null) {
            try { context.unregisterReceiver(broadcastReceiver); } catch (Exception ignored) {}
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(SettingHelper.refresh_setting);
        intentFilter.addAction(SettingHelper.proxy_configuration_setting);
        intentFilter.addAction(SettingHelper.script_configuration_setting);
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                if (intent.getAction().equals(SettingHelper.refresh_setting)) {
                    if (dialogRoot != null)
                        for (int i = 0; i < dialogRoot.getChildCount(); i++) {
                            if (dialogRoot.getChildAt(i) instanceof BaseDialogItem)
                                ((BaseDialogItem) dialogRoot.getChildAt(i)).refresh();
                        }
                    if (dialogProxyRoot != null)
                        for (int i = 0; i < dialogProxyRoot.getChildCount(); i++) {
                            if (dialogProxyRoot.getChildAt(i) instanceof BaseDialogItem)
                                ((BaseDialogItem) dialogProxyRoot.getChildAt(i)).refresh();
                            else if (dialogProxyRoot.getChildAt(i) instanceof BaseDialogInputItem)
                                ((BaseDialogInputItem) dialogProxyRoot.getChildAt(i)).refresh();
                        }
                    if (dialogScriptRoot != null)
                        for (int i = 0; i < dialogScriptRoot.getChildCount(); i++) {
                            if (dialogScriptRoot.getChildAt(i) instanceof BaseDialogItem)
                                ((BaseDialogItem) dialogScriptRoot.getChildAt(i)).refresh();
                            else if (dialogScriptRoot.getChildAt(i) instanceof BaseDialogInputItem)
                                ((BaseDialogInputItem) dialogScriptRoot.getChildAt(i)).refresh();
                        }
                } else if (intent.getAction().equals(SettingHelper.proxy_configuration_setting)) {
                    showProxyConfigurationDialog(context);
                } else if (intent.getAction().equals(SettingHelper.script_configuration_setting)) {
                    showScriptConfigurationDialog(context);
                }
            }
        };
        context.registerReceiver(broadcastReceiver, intentFilter);
    }

    private void restartApplication(Context context) {
        ExtraHelper.setExtraDate(ExtraHelper.SCRIPT_STATUS, "0");
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningAppProcessInfoListist = activityManager.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : runningAppProcessInfoListist) {
            if (runningAppProcessInfo.processName.contains(":play")) {
                android.os.Process.killProcess(runningAppProcessInfo.pid);
                break;
            }
        }
        final Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
