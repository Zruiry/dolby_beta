package com.raincat.dolby_beta.helper;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;

/**
 * <pre>
 *     author : RainCat
 *     e-mail : nining377@gmail.com
 *     time   : 2021/04/14
 *     desc   : 设置中心 - 仅保留音源代理相关配置
 *     version: 2.0
 * </pre>
 */
public class SettingHelper {
    // 广播Action - 设置刷新
    public static final String refresh_setting = "β_refresh_setting";
    public static final String proxy_configuration_setting = "β_proxy_configuration_setting";
    public static final String script_configuration_setting = "β_script_configuration_setting";

    // 总开关
    public static final String master_key = "β_master_key";
    public static final String master_title = "总开关";

    // DEX缓存（代理功能依赖ClassHelper解析类，需要DEX缓存加速）
    public static final String dex_key = "β_dex_key";
    public static final String dex_title = "启用DEX缓存";
    public static final String dex_sub = "加快模块加载速度，但同版本号的内测版与稳定版互装可能会有兼容性问题";

    // 音源代理设置
    public static final String proxy_master_key = "β_proxy_master_key";
    public static final String proxy_master_title = "启用音源代理";

    public static final String proxy_server_key = "β_proxy_server_key";
    public static final String proxy_server_title = "启用服务器代理";
    public static final String proxy_server_sub = "如果您不想使用高占用的node，有自己的服务器代理可使用此方式并填写自己的服务器地址与端口，且使用服务器对应音质";

    public static final String proxy_priority_key = "β_proxy_priority_key";
    public static final String proxy_priority_title = "音质优先";
    public static final String proxy_priority_sub = "音质优先：使用外部音源提高音质，不可避免的会增大匹配错误概率\n匹配度优先：尽可能采用网易云音源，但非会员很多曲目只有128K/96K";

    public static final String proxy_flac_key = "β_proxy_flac_key";
    public static final String proxy_flac_title = "无损音质优先";
    public static final String proxy_flac_sub = "使用外部音源时优先获取无损音质，但并不是100%能获取到无损音质";

    public static final String proxy_gray_key = "β_proxy_gray_key";
    public static final String proxy_gray_title = "不变灰";
    public static final String proxy_gray_sub = "只影响显示效果，与能否播放无关，会导致无音源歌曲无法播放且无法自动跳过";

    public static final String http_proxy_key = "β_http_proxy_key";
    public static final String http_proxy_title = "代理服务器";
    public static final String http_proxy_default = "127.0.0.1";

    public static final String kuwo_cookie_key = "β_kuwo_cookie_key";
    public static final String kuwo_cookie_title = "酷我Cookie";
    public static final String kuwo_cookie_default = "Hm_Iuvt_cdb524f42f0ce19b169b8072123a4727=CQXkhzXjGD6MFQrPTBxEpSmZXF78wP8e; Secret=1d0d220792feb563f97fdb0de2b7ebad69f781cdcdbe51d1203a3be9d3e92f5e04b00a24";

    public static final String qq_cookie_key = "β_qq_cookie_key";
    public static final String qq_cookie_title = "QQCookie";
    public static final String qq_cookie_default = "uin=<your_uin>; qm_keyst=<your_qm_keyst>";

    public static final String migu_cookie_key = "β_migu_cookie_key";
    public static final String migu_cookie_title = "咪咕Cookie";
    public static final String migu_cookie_default = "<your_aversionid>";

    public static final String proxy_port_key = "β_proxy_port_key";
    public static final String proxy_port_title = "代理端口（1~65535）";
    public static final int proxy_port_default = 23338;

    public static final String proxy_original_key = "β_proxy_original_key";
    public static final String proxy_original_title = "音源顺序（空格隔开）";
    public static final String proxy_original_default = "pyncmd kuwo";

    public static final String proxy_cover_key = "β_proxy_cover_key";
    public static final String proxy_cover_title = "重新释放脚本";
    public static final String proxy_cover_sub = "当更新后或者发现UnblockNeteaseMusic运行不正常时可尝试重新释放脚本";

    public static final String proxy_configuration_key = "β_proxy_configuration_key";
    public static final String proxy_configuration_title = "服务器代理配置";
    public static final String proxy_configuration_sub = "在此填入对于代理服务器的地址与端口";

    // 脚本参数配置
    public static final String script_configuration_key = "β_script_configuration_key";
    public static final String script_configuration_title = "脚本参数配置";
    public static final String script_configuration_sub = "在此填入本地脚本的运行参数，仅本地脚本模式生效";

    private static SettingHelper instance;

    private SharedPreferences sharedPreferences;
    private HashMap<String, Boolean> settingMap;

    public static SettingHelper getInstance() {
        return instance;
    }

    private SettingHelper(Context context) {
        refreshSetting(context);
    }

    public static void init(Context context) {
        if (instance == null) {
            instance = new SettingHelper(context);
        }
    }

    public void refreshSetting(Context context) {
        sharedPreferences = context.getSharedPreferences("com.netease.cloudmusic.preferences", Context.MODE_MULTI_PROCESS);
        settingMap = new HashMap<>();

        settingMap.put(master_key, sharedPreferences.getBoolean(master_key, true));
        settingMap.put(dex_key, sharedPreferences.getBoolean(dex_key, true));

        settingMap.put(proxy_master_key, sharedPreferences.getBoolean(proxy_master_key, true));
        settingMap.put(proxy_server_key, sharedPreferences.getBoolean(proxy_server_key, false));
        settingMap.put(proxy_priority_key, sharedPreferences.getBoolean(proxy_priority_key, false));
        settingMap.put(proxy_flac_key, sharedPreferences.getBoolean(proxy_flac_key, false));
        settingMap.put(proxy_gray_key, sharedPreferences.getBoolean(proxy_gray_key, false));
    }

    public void setSetting(String key, boolean value) {
        settingMap.put(key, value);
        sharedPreferences.edit().putBoolean(key, value).apply();
    }

    public boolean getSetting(String key) {
        return settingMap.get(key);
    }

    public boolean isEnable(String key) {
        return settingMap.get(master_key) && settingMap.get(key);
    }

    private void deleteSetting(String key) {
        if (sharedPreferences.contains(key)) {
            sharedPreferences.edit().remove(key).apply();
        }
    }

    public void resetSetting() {
        deleteSetting(master_key);
        deleteSetting(dex_key);
        deleteSetting(proxy_master_key);
        deleteSetting(proxy_server_key);
        deleteSetting(proxy_priority_key);
        deleteSetting(proxy_flac_key);
        deleteSetting(proxy_gray_key);
    }

    public int getProxyPort() {
        return sharedPreferences.getInt(SettingHelper.proxy_port_key, SettingHelper.proxy_port_default);
    }

    public void setProxyPort(String port) {
        if (port != null && !port.isEmpty())
            sharedPreferences.edit().putInt(SettingHelper.proxy_port_key, Integer.parseInt(port)).apply();
    }

    public String getProxyOriginal() {
        return sharedPreferences.getString(SettingHelper.proxy_original_key, SettingHelper.proxy_original_default);
    }

    public void setProxyOriginal(String original) {
        if (original != null && !original.isEmpty())
            sharedPreferences.edit().putString(SettingHelper.proxy_original_key, original).apply();
    }

    public void setHttpProxy(String http) {
        if (http != null && !http.isEmpty())
            sharedPreferences.edit().putString(SettingHelper.http_proxy_key, http).apply();
    }

    public String getHttpProxy() {
        return sharedPreferences.getString(SettingHelper.http_proxy_key, SettingHelper.http_proxy_default);
    }

    public String getKuwoCookie() {
        return sharedPreferences.getString(SettingHelper.kuwo_cookie_key, SettingHelper.kuwo_cookie_default);
    }

    public void setKuwoCookie(String cookie) {
        if (cookie != null && !cookie.isEmpty())
            sharedPreferences.edit().putString(SettingHelper.kuwo_cookie_key, cookie).apply();
    }

    public String getQqCookie() {
        return sharedPreferences.getString(SettingHelper.qq_cookie_key, SettingHelper.qq_cookie_default);
    }

    public void setQqCookie(String cookie) {
        if (cookie != null && !cookie.isEmpty())
            sharedPreferences.edit().putString(SettingHelper.qq_cookie_key, cookie).apply();
    }

    public String getMiguCookie() {
        return sharedPreferences.getString(SettingHelper.migu_cookie_key, SettingHelper.migu_cookie_default);
    }

    public void setMiguCookie(String cookie) {
        if (cookie != null && !cookie.isEmpty())
            sharedPreferences.edit().putString(SettingHelper.migu_cookie_key, cookie).apply();
    }
}
