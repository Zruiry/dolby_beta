package com.raincat.dolby_beta;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.raincat.dolby_beta.helper.ClassHelper;
import com.raincat.dolby_beta.helper.ExtraHelper;
import com.raincat.dolby_beta.helper.SettingHelper;
import com.raincat.dolby_beta.hook.CdnHook;
import com.raincat.dolby_beta.hook.EAPIHook;
import com.raincat.dolby_beta.hook.ProxyHook;
import com.raincat.dolby_beta.hook.SettingHook;
import com.raincat.dolby_beta.utils.Tools;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * <pre>
 *     author : RainCat
 *     e-mail : nining377@gmail.com
 *     time   : 2021/09/22
 *     desc   : Hook入口 - 仅保留音源代理功能
 *     version: 2.0
 * </pre>
 */
public class Hook {
    private final static String PACKAGE_NAME = "com.netease.cloudmusic";
    // 进程初始化状态
    public boolean playProcessInit = false;
    public boolean mainProcessInit = false;
    // 主线程反编译dex完成后通知可以对play进程进行hook了
    private final String msg_hook_play_process = "hookPlayProcess";
    // play进程初始化完成通知主线程
    private final String msg_play_process_init_finish = "playProcessInitFinish";

    public Hook(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> appClass = XposedHelpers.findClass("com.netease.cloudmusic.NeteaseMusicApplication", lpparam.classLoader);

        // hook NeteaseMusicApplication.onCreate，在Application完全初始化后创建SettingHook
        XposedHelpers.findAndHookMethod(appClass, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                final Context context = (Context) param.thisObject;
                final int versionCode = context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0).versionCode;
                final String processName = Tools.getCurrentProcessName(context);
                if (processName.equals(PACKAGE_NAME)) {
                    // 设置 - 在Application.onCreate完成后注册LifecycleCallbacks
                    new SettingHook(context, versionCode);
                }
            }
        });

        XposedHelpers.findAndHookMethod(appClass,
                "attachBaseContext", Context.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        final Context context = (Context) param.thisObject;
                        final int versionCode = context.getPackageManager().getPackageInfo(PACKAGE_NAME, 0).versionCode;
                        // 初始化仓库
                        ExtraHelper.init(context);
                        // 初始化设置
                        SettingHelper.init(context);

                        final String processName = Tools.getCurrentProcessName(context);
                        if (processName.equals(PACKAGE_NAME)) {
                            // 总开关
                            if (!SettingHelper.getInstance().getSetting(SettingHelper.master_key))
                                return;
                            // 音源代理
                            new ProxyHook(context, false);
                            // 不变灰（与旧版GrayHook一致，在ClassHelper扫描dex之前初始化）
                            EAPIHook.hookGrayFunction(context);
                            // 音源代理：Hook SongPrivilege（同上，提前初始化）
                            EAPIHook.hookSongPrivilege(context);

                            ClassHelper.getCacheClassList(context, versionCode, () -> {
                                // 网络访问（EAPI拦截，用于代理替换空音源）
                                new EAPIHook(context);
                                // 绕过CDN责任链拦截器检测
                                new CdnHook(context, versionCode);

                                mainProcessInit = true;
                                if (mainProcessInit && playProcessInit)
                                    context.sendBroadcast(new Intent(msg_hook_play_process));
                            });
                        } else if (processName.equals(PACKAGE_NAME + ":play") && SettingHelper.getInstance().getSetting(SettingHelper.master_key)) {
                            // 音源代理（play进程）
                            new ProxyHook(context, true);
                            // 不变灰和SongPrivilege提前初始化（与主进程一致）
                            EAPIHook.hookGrayFunction(context);
                            EAPIHook.hookSongPrivilege(context);
                            IntentFilter intentFilter = new IntentFilter();
                            intentFilter.addAction(msg_hook_play_process);
                            context.registerReceiver(new BroadcastReceiver() {
                                @Override
                                public void onReceive(Context c, Intent intent) {
                                    if (msg_hook_play_process.equals(intent.getAction())) {
                                        ClassHelper.getCacheClassList(context, versionCode, () -> {
                                            new EAPIHook(context);
                                            new CdnHook(context, versionCode);
                                        });
                                    }
                                }
                            }, intentFilter);
                            context.sendBroadcast(new Intent(msg_play_process_init_finish));
                        }
                    }
                });
    }
}
