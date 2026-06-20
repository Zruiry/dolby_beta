package com.raincat.dolby_beta.helper;

import android.content.Context;

import com.raincat.dolby_beta.db.ExtraDao;

/**
 * <pre>
 *     author : RainCat
 *     e-mail : nining377@gmail.com
 *     time   : 2021/04/14
 *     desc   : 额外数据帮助类 - 仅保留音源代理相关数据
 *     version: 2.0
 * </pre>
 */
public class ExtraHelper {
    // 脚本运行情况，运行中1，未运行0
    public static final String SCRIPT_STATUS = "script_status";
    // APP版本号
    public static final String APP_VERSION = "app_version";

    // 初始化数据库
    public static void init(Context context) {
        ExtraDao.init(context);
    }

    public static String getExtraDate(String key) {
        return ExtraDao.getInstance().getExtra(key);
    }

    public static void setExtraDate(String key, Object value) {
        ExtraDao.getInstance().saveExtra(key, value.toString());
    }
}
