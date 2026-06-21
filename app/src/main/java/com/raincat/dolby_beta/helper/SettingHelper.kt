/**
 * 设置中心 - 管理所有模块配置项
 *
 * 包含音源代理、美化、签到、黑胶VIP等功能开关。
 * 音源代理部分以当前项目为准，其余功能从 dev 分支迁移而来。
 *
 * 使用SharedPreferences存储，支持跨进程读取。
 */
package com.raincat.dolby_beta.helper

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import java.util.LinkedHashMap

/**
 * 设置帮助类 - 管理所有模块配置项
 * 使用SharedPreferences存储，支持跨进程读取
 */
class SettingHelper private constructor(context: Context) {

    companion object {
        // ==================== 广播Action ====================
        @JvmField val refresh_setting = "β_refresh_setting"
        @JvmField val proxy_setting = "β_proxy_setting"
        @JvmField val beauty_setting = "β_beauty_setting"
        @JvmField val sidebar_setting = "β_sidebar_setting"
        @JvmField val background_setting = "β_background_setting"
        @JvmField val proxy_configuration_setting = "β_proxy_configuration_setting"
        @JvmField val script_configuration_setting = "β_script_configuration_setting"

        // ==================== 总开关 ====================
        @JvmField val master_key = "β_master_key"
        @JvmField val master_title = "总开关"

        // ==================== DEX缓存 ====================
        @JvmField val dex_key = "β_dex_key"
        @JvmField val dex_title = "启用DEX缓存"
        @JvmField val dex_sub = "加快模块加载速度，但同版本号的内测版与稳定版互装可能会有兼容性问题"

        // ==================== Hook警告 ====================
        @JvmField val warn_key = "β_warn_key"
        @JvmField val warn_title = "开启Hook警告"
        @JvmField val warn_sub = "当模块出现部分类无法Hook时在通知栏上显示，方便定位排查问题"

        // ==================== 黑胶VIP ====================
        @JvmField val black_key = "β_black_key"
        @JvmField val black_title = "本地黑胶"
        @JvmField val black_sub = "去广告、鲸云音效、个性换肤等（自定义启动图等需要访问网易服务器的设置不可用）"

        // ==================== 一起听 ====================
        @JvmField val listen_key = "β_listen_key"
        @JvmField val listen_title = "解锁一起听蒙面查看权限"
        @JvmField val listen_sub = "开启后可直接查看对方信息，无需对方解除蒙面"

        // ==================== 修复评论 ====================
        @JvmField val fix_comment_key = "β_fix_comment_key"
        @JvmField val fix_comment_title = "修复评论区加载失败"
        @JvmField val fix_comment_sub = "如平时不看评论区或评论区无问题请勿打开"

        // ==================== 隐藏升级 ====================
        @JvmField val update_key = "β_update_key"
        @JvmField val update_title = "隐藏升级提示"

        // ==================== 签到 ====================
        @JvmField val sign_key = "β_sign_key"
        @JvmField val sign_title = "自动签到"

        @JvmField val sign_song_key = "β_sign_song_key"
        @JvmField val sign_song_title = "每日歌曲打卡"
        @JvmField val sign_song_sub = "获取每日推荐歌曲单中的歌曲进行打卡，有利于提高等级，会影响年度听歌总结且有可能导致几天内签到天数不变，介意者慎用"

        @JvmField val sign_self_title = "自助打卡"

        @JvmField val sign_id_key = "β_sign_id_key"
        @JvmField val sign_id_title = "打卡歌单URL"

        @JvmField val sign_start_key = "β_sign_start_key"
        @JvmField val sign_start_title = "期望从第几首开始打"
        @JvmField val sign_start_default = 1

        @JvmField val sign_count_key = "β_sign_count_key"
        @JvmField val sign_count_title = "期望打卡多少首歌"
        @JvmField val sign_count_default = 300

        // ==================== 音源代理 ====================
        @JvmField val proxy_key = "β_proxy_key"
        @JvmField val proxy_title = "音源代理设置"

        @JvmField val proxy_master_key = "β_proxy_master_key"
        @JvmField val proxy_master_title = "启用音源代理"

        @JvmField val proxy_server_key = "β_proxy_server_key"
        @JvmField val proxy_server_title = "启用服务器代理"
        @JvmField val proxy_server_sub = "如果您不想使用高占用的node，有自己的服务器代理可使用此方式并填写自己的服务器地址与端口，且使用服务器对应音质"

        @JvmField val proxy_priority_key = "β_proxy_priority_key"
        @JvmField val proxy_priority_title = "音质优先"
        @JvmField val proxy_priority_sub = "音质优先：使用外部音源提高音质，不可避免的会增大匹配错误概率\n匹配度优先：尽可能采用网易云音源，但非会员很多曲目只有128K/96K"

        @JvmField val proxy_flac_key = "β_proxy_flac_key"
        @JvmField val proxy_flac_title = "无损音质优先"
        @JvmField val proxy_flac_sub = "使用外部音源时优先获取无损音质，但并不是100%能获取到无损音质"

        @JvmField val proxy_gray_key = "β_proxy_gray_key"
        @JvmField val proxy_gray_title = "不变灰"
        @JvmField val proxy_gray_sub = "仅影响显示效果，与是否能播放无关，会导致无音源歌曲无法播放且无法自动跳过"

        @JvmField val http_proxy_key = "β_http_proxy_key"
        @JvmField val http_proxy_title = "代理服务器"
        @JvmField val http_proxy_default = "127.0.0.1"

        @JvmField val kuwo_cookie_key = "β_kuwo_cookie_key"
        @JvmField val kuwo_cookie_title = "酷我Cookie"
        @JvmField val kuwo_cookie_default = "Hm_Iuvt_cdb524f42f0ce19b169b8072123a4727=CQXkhzXjGD6MFQrPTBxEpSmZXF78wP8e; Secret=1d0d220792feb563f97fdb0de2b7ebad69f781cdcdbe51d1203a3be9d3e92f5e04b00a24"

        @JvmField val qq_cookie_key = "β_qq_cookie_key"
        @JvmField val qq_cookie_title = "QQCookie"
        @JvmField val qq_cookie_default = "uin=<your_uin>; qm_keyst=<your_qm_keyst>"

        @JvmField val migu_cookie_key = "β_migu_cookie_key"
        @JvmField val migu_cookie_title = "咪咕Cookie"
        @JvmField val migu_cookie_default = "<your_aversionid>"

        @JvmField val proxy_port_key = "β_proxy_port_key"
        @JvmField val proxy_port_title = "代理端口（1~65535）"
        @JvmField val proxy_port_default = 23338

        @JvmField val proxy_original_key = "β_proxy_original_key"
        @JvmField val proxy_original_title = "音源顺序（空格隔开）"
        @JvmField val proxy_original_default = "kuwo pyncmd"

        @JvmField val proxy_cover_key = "β_proxy_cover_key"
        @JvmField val proxy_cover_title = "重新释放脚本"
        @JvmField val proxy_cover_sub = "当更新后或者发现UnblockNeteaseMusic运行不正常时可尝试重新释放脚本"

        @JvmField val proxy_configuration_key = "β_proxy_configuration_key"
        @JvmField val proxy_configuration_title = "服务器代理配置"
        @JvmField val proxy_configuration_sub = "在此填入对于代理服务器的地址与端口"

        @JvmField val script_configuration_key = "β_script_configuration_key"
        @JvmField val script_configuration_title = "脚本参数配置"
        @JvmField val script_configuration_sub = "在此填入本地脚本的运行参数，仅本地脚本模式生效"

        // ==================== 美化设置 ====================
        @JvmField val beauty_key = "β_beauty_key"
        @JvmField val beauty_title = "美化设置"

        @JvmField val beauty_night_mode_key = "β_beauty_night_mode_key"
        @JvmField val beauty_night_mode_title = "跟随系统切换夜间模式"
        @JvmField val beauty_night_mode_sub = "自动根据系统深色模式状态切换夜间/日间模式"

        @JvmField val beauty_tab_hide_key = "β_beauty_tab_hide_key"
        @JvmField val beauty_tab_hide_title = "精简Tab"
        @JvmField val beauty_tab_hide_sub = "首页仅保留\"我的\"与\"发现\"，并默认显示\"我的\""

        @JvmField val beauty_bubble_hide_key = "β_beauty_bubble_hide_key"
        @JvmField val beauty_bubble_hide_title = "移除小红点"

        @JvmField val beauty_banner_hide_key = "β_beauty_banner_hide_key"
        @JvmField val beauty_banner_hide_title = "移除发现页与歌单广场Banner"

        @JvmField val beauty_ksong_hide_key = "β_beauty_ksong_key"
        @JvmField val beauty_ksong_hide_title = "移除播放页歌曲图谱"

        @JvmField val beauty_black_hide_key = "β_beauty_black_key"
        @JvmField val beauty_black_hide_title = "播放页专辑图片外面的黑胶隐藏"

        @JvmField val beauty_rotation_key = "β_beauty_rotation_key"
        @JvmField val beauty_rotation_title = "播放页专辑图片停止转动"

        @JvmField val beauty_background_key = "β_beauty_background_key"
        @JvmField val beauty_background_title = "自定义播放界面背景"

        @JvmField val beauty_comment_hot_key = "β_beauty_comment_hot_key"
        @JvmField val beauty_comment_hot_title = "评论区优先显示\"最热\"内容"

        @JvmField val beauty_sidebar_hide_key = "β_beauty_sidebar_hide_key"
        @JvmField val beauty_sidebar_hide_title = "精简侧边栏"
        @JvmField val beauty_sidebar_hide_sub = "部分Item需配合\"设置\"->\"侧边栏管理\"开关生效"

        // ==================== 播放界面背景 ====================
        @JvmField val background_key = "β_background_key"
        @JvmField val background_title = "播放界面背景设置"

        @JvmField val background_url_key = "β_background_url_key"
        @JvmField val background_url_title = "图片URL(请自行上传至图床)"
        @JvmField val background_url_default = ""

        @JvmField val background_blur_key = "β_background_blur_key"
        @JvmField val background_blur_title = "高斯模糊度(默认透明无模糊)"
        @JvmField val background_blur_default = 0

        @Volatile
        private var instance: SettingHelper? = null

        @JvmStatic
        fun getInstance(): SettingHelper = instance ?: throw IllegalStateException("SettingHelper未初始化，请先调用init()")

        @JvmStatic
        fun init(context: Context) {
            if (instance == null) {
                instance = SettingHelper(context)
            }
        }
    }

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var settingMap: HashMap<String, Boolean>
    private var sidebarSettingMap: HashMap<String, Boolean>? = null

    init {
        refreshSetting(context)
    }

    /**
     * 刷新设置缓存
     */
    fun refreshSetting(context: Context) {
        sharedPreferences = context.getSharedPreferences("com.netease.cloudmusic.preferences", Context.MODE_MULTI_PROCESS)
        settingMap = HashMap()

        settingMap[master_key] = sharedPreferences.getBoolean(master_key, true)
        settingMap[dex_key] = sharedPreferences.getBoolean(dex_key, true)
        settingMap[warn_key] = sharedPreferences.getBoolean(warn_key, false)
        settingMap[black_key] = sharedPreferences.getBoolean(black_key, false)
        settingMap[listen_key] = sharedPreferences.getBoolean(listen_key, false)
        settingMap[fix_comment_key] = sharedPreferences.getBoolean(fix_comment_key, false)
        settingMap[update_key] = sharedPreferences.getBoolean(update_key, false)
        settingMap[sign_key] = sharedPreferences.getBoolean(sign_key, false)
        settingMap[sign_song_key] = sharedPreferences.getBoolean(sign_song_key, false)

        // 音源代理默认启用
        settingMap[proxy_master_key] = sharedPreferences.getBoolean(proxy_master_key, true)
        settingMap[proxy_server_key] = sharedPreferences.getBoolean(proxy_server_key, false)
        settingMap[proxy_priority_key] = sharedPreferences.getBoolean(proxy_priority_key, false)
        settingMap[proxy_flac_key] = sharedPreferences.getBoolean(proxy_flac_key, false)
        settingMap[proxy_gray_key] = sharedPreferences.getBoolean(proxy_gray_key, false)

        // 美化设置
        settingMap[beauty_night_mode_key] = sharedPreferences.getBoolean(beauty_night_mode_key, false)
        settingMap[beauty_tab_hide_key] = sharedPreferences.getBoolean(beauty_tab_hide_key, false)
        settingMap[beauty_bubble_hide_key] = sharedPreferences.getBoolean(beauty_bubble_hide_key, false)
        settingMap[beauty_banner_hide_key] = sharedPreferences.getBoolean(beauty_banner_hide_key, false)
        settingMap[beauty_ksong_hide_key] = sharedPreferences.getBoolean(beauty_ksong_hide_key, false)
        settingMap[beauty_rotation_key] = sharedPreferences.getBoolean(beauty_rotation_key, false)
        settingMap[beauty_black_hide_key] = sharedPreferences.getBoolean(beauty_black_hide_key, false)
        settingMap[beauty_comment_hot_key] = sharedPreferences.getBoolean(beauty_comment_hot_key, false)
        settingMap[beauty_background_key] = sharedPreferences.getBoolean(beauty_background_key, false)
    }

    fun setSetting(key: String, value: Boolean) {
        settingMap[key] = value
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    fun getSetting(key: String): Boolean = settingMap[key] ?: false

    /**
     * 判断某功能是否启用（需要总开关和该功能开关同时开启）
     */
    fun isEnable(key: String): Boolean = settingMap[master_key] == true && (settingMap[key] ?: false)

    private fun deleteSetting(key: String) {
        if (sharedPreferences.contains(key)) {
            sharedPreferences.edit().remove(key).apply()
        }
    }

    fun resetSetting() {
        deleteSetting(master_key)
        deleteSetting(dex_key)
        deleteSetting(warn_key)
        deleteSetting(black_key)
        deleteSetting(listen_key)
        deleteSetting(fix_comment_key)
        deleteSetting(update_key)
        deleteSetting(sign_key)
        deleteSetting(sign_song_key)
        deleteSetting(proxy_master_key)
        deleteSetting(proxy_server_key)
        deleteSetting(proxy_priority_key)
        deleteSetting(proxy_flac_key)
        deleteSetting(proxy_gray_key)
        deleteSetting(beauty_night_mode_key)
        deleteSetting(beauty_tab_hide_key)
        deleteSetting(beauty_bubble_hide_key)
        deleteSetting(beauty_banner_hide_key)
        deleteSetting(beauty_ksong_hide_key)
        deleteSetting(beauty_rotation_key)
        deleteSetting(beauty_black_hide_key)
        deleteSetting(beauty_comment_hot_key)
        deleteSetting(beauty_background_key)
    }

    // ==================== 侧边栏设置 ====================

    /**
     * 获取侧边栏设置Map
     * @param map 侧边栏枚举Map（key=侧边栏item标识, value=显示名称）
     */
    fun getSidebarSetting(map: LinkedHashMap<String, String>): HashMap<String, Boolean> {
        if (sidebarSettingMap == null) {
            sidebarSettingMap = HashMap()
            for (key in map.keys) {
                sidebarSettingMap!![key] = sharedPreferences.getBoolean(key, false)
            }
        }
        return sidebarSettingMap!!
    }

    fun setSidebarSetting(key: String, value: Boolean) {
        sidebarSettingMap?.put(key, value)
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    // ==================== 签到相关 ====================

    fun getSignId(): String = sharedPreferences.getString(sign_id_key, "") ?: ""

    fun setSignId(id: String?) {
        if (!TextUtils.isEmpty(id)) {
            sharedPreferences.edit().putString(sign_id_key, id).apply()
        }
    }

    fun getSignStart(): Int = sharedPreferences.getInt(sign_start_key, sign_start_default)

    fun setSignStart(start: String?) {
        if (!TextUtils.isEmpty(start)) {
            sharedPreferences.edit().putInt(sign_start_key, start!!.toInt()).apply()
        }
    }

    fun getSignCount(): Int = sharedPreferences.getInt(sign_count_key, sign_count_default)

    fun setSignCount(count: String?) {
        if (!TextUtils.isEmpty(count)) {
            sharedPreferences.edit().putInt(sign_count_key, count!!.toInt()).apply()
        }
    }

    // ==================== 音源代理相关 ====================

    fun getProxyPort(): Int = sharedPreferences.getInt(proxy_port_key, proxy_port_default)

    fun setProxyPort(port: String?) {
        if (!port.isNullOrEmpty()) {
            sharedPreferences.edit().putInt(proxy_port_key, port.toInt()).apply()
        }
    }

    fun getProxyOriginal(): String = sharedPreferences.getString(proxy_original_key, proxy_original_default) ?: proxy_original_default

    fun setProxyOriginal(original: String?) {
        if (!original.isNullOrEmpty()) {
            sharedPreferences.edit().putString(proxy_original_key, original).apply()
        }
    }

    fun setHttpProxy(http: String?) {
        if (!http.isNullOrEmpty()) {
            sharedPreferences.edit().putString(http_proxy_key, http).apply()
        }
    }

    fun getHttpProxy(): String = sharedPreferences.getString(http_proxy_key, http_proxy_default) ?: http_proxy_default

    fun getKuwoCookie(): String = sharedPreferences.getString(kuwo_cookie_key, kuwo_cookie_default) ?: kuwo_cookie_default

    fun setKuwoCookie(cookie: String?) {
        if (!cookie.isNullOrEmpty()) {
            sharedPreferences.edit().putString(kuwo_cookie_key, cookie).apply()
        }
    }

    fun getQqCookie(): String = sharedPreferences.getString(qq_cookie_key, qq_cookie_default) ?: qq_cookie_default

    fun setQqCookie(cookie: String?) {
        if (!cookie.isNullOrEmpty()) {
            sharedPreferences.edit().putString(qq_cookie_key, cookie).apply()
        }
    }

    fun getMiguCookie(): String = sharedPreferences.getString(migu_cookie_key, migu_cookie_default) ?: migu_cookie_default

    fun setMiguCookie(cookie: String?) {
        if (!cookie.isNullOrEmpty()) {
            sharedPreferences.edit().putString(migu_cookie_key, cookie).apply()
        }
    }

    // ==================== 播放界面背景相关 ====================

    fun getPictureUrl(): String = sharedPreferences.getString(background_url_key, background_url_default) ?: background_url_default

    fun setPictureUrl(url: String?) {
        if (!url.isNullOrEmpty()) {
            sharedPreferences.edit().putString(background_url_key, url).apply()
        }
    }

    fun getBackgroundBlur(): Int = sharedPreferences.getInt(background_blur_key, background_blur_default)

    fun setBackgroundBlur(blur: String?) {
        if (!blur.isNullOrEmpty()) {
            sharedPreferences.edit().putInt(background_blur_key, blur.toInt()).apply()
        }
    }
}
