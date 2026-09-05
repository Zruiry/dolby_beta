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
        @JvmField val proxy_configuration_setting = "β_proxy_configuration_setting"
        @JvmField val script_configuration_setting = "β_script_configuration_setting"

        // ==================== 总开关 ====================
        @JvmField val master_key = "β_master_key"
        @JvmField val master_title = "总开关"

        // ==================== DEX缓存 ====================
        @JvmField val dex_key = "β_dex_key"
        @JvmField val dex_title = "启用DEX缓存"
        @JvmField val dex_sub = "加快模块加载速度，但同版本号的内测版与稳定版互装可能会有兼容性问题"

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

        @JvmField val qq_cookie_key = "β_qq_cookie_key"
        @JvmField val qq_cookie_title = "QQCookie"
        @JvmField val qq_cookie_default = "uin=<your_uin>; qm_keyst=<your_qm_keyst>"

        @JvmField val migu_cookie_key = "β_migu_cookie_key"
        @JvmField val migu_cookie_title = "咪咕Cookie"
        @JvmField val migu_cookie_default = "<your_aversionid>"

        @JvmField val proxy_port_key = "β_proxy_port_key"
        @JvmField val proxy_port_title = "代理端口（1~65535）"
        @JvmField val proxy_port_default = 52000

        // 本地脚本监听端口（与服务器代理端口相互独立，脚本 -p 监听用）
        @JvmField val proxy_local_port_key = "β_proxy_local_port_key"
        @JvmField val proxy_local_port_default = 23338

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

        // ==================== GD Studio 在线音源 ====================
        /** GD Studio 在线音源 API 地址 */
        @JvmField val proxy_gd_api = "https://music-api.gdstudio.xyz/api.php"

        @JvmField val proxy_gd_studio_key = "β_proxy_gd_studio_key"
        @JvmField val proxy_gd_studio_title = "GD Studio"
        @JvmField val proxy_gd_studio_sub = "调用 GD Studio 在线音源 API 为无版权歌曲获取可播链接，API: https://music-api.gdstudio.xyz/api.php"

        @JvmField val proxy_gd_source_key = "β_proxy_gd_source_key"
        @JvmField val proxy_gd_source_title = "替换音源"
        @JvmField val proxy_gd_source_default = "joox"

        @JvmField val proxy_gd_flac_key = "β_proxy_gd_flac_key"
        @JvmField val proxy_gd_flac_title = "无损音质优先"
        @JvmField val proxy_gd_flac_sub = "GD Studio 模式优先获取无损音质，但并不是100%能获取到无损音质"

        @JvmField val proxy_gd_configuration_key = "β_proxy_gd_configuration_key"
        @JvmField val proxy_gd_configuration_title = "API音源配置"
        @JvmField val proxy_gd_configuration_sub = "当前可稳定获取播放地址的音源为 joox，kuwo/tencent 等暂不可用"

        // ==================== 美化设置 ====================
        @JvmField val beauty_key = "β_beauty_key"
        @JvmField val beauty_title = "美化设置"

        @JvmField val beauty_follow_dark_key = "β_beauty_follow_dark_key"
        @JvmField val beauty_follow_dark_title = "深色模式跟随系统"
        @JvmField val beauty_follow_dark_sub = "设置界面是否跟随系统深色模式，关闭后固定浅色主题（重新打开设置生效）"

        @JvmField val beauty_tab_hide_key = "β_beauty_tab_hide_key"
        @JvmField val beauty_tab_hide_title = "精简Tab"
        @JvmField val beauty_tab_hide_sub = "首页仅保留\"我的\"与\"发现\"，并默认显示\"我的\""

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

        // 音源代理默认启用
        settingMap[proxy_master_key] = sharedPreferences.getBoolean(proxy_master_key, true)
        settingMap[proxy_server_key] = sharedPreferences.getBoolean(proxy_server_key, false)
        settingMap[proxy_gd_studio_key] = sharedPreferences.getBoolean(proxy_gd_studio_key, false)
        settingMap[proxy_priority_key] = sharedPreferences.getBoolean(proxy_priority_key, false)
        settingMap[proxy_flac_key] = sharedPreferences.getBoolean(proxy_flac_key, false)
        settingMap[proxy_gray_key] = sharedPreferences.getBoolean(proxy_gray_key, false)
        settingMap[proxy_gd_flac_key] = sharedPreferences.getBoolean(proxy_gd_flac_key, false)

        // 美化设置（深色跟随系统默认启用，保持与升级前一致）
        settingMap[beauty_follow_dark_key] = sharedPreferences.getBoolean(beauty_follow_dark_key, true)
        settingMap[beauty_tab_hide_key] = sharedPreferences.getBoolean(beauty_tab_hide_key, false)
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
        // 布尔开关
        deleteSetting(master_key)
        deleteSetting(dex_key)
        deleteSetting(proxy_master_key)
        deleteSetting(proxy_server_key)
        deleteSetting(proxy_gd_studio_key)
        deleteSetting(proxy_priority_key)
        deleteSetting(proxy_flac_key)
        deleteSetting(proxy_gray_key)
        deleteSetting(proxy_gd_flac_key)
        deleteSetting(beauty_follow_dark_key)
        deleteSetting(beauty_tab_hide_key)
        // 输入类配置（代理服务器/端口/Cookie/音源顺序/GD音源）
        deleteSetting(http_proxy_key)
        deleteSetting(proxy_port_key)
        deleteSetting(proxy_local_port_key)
        deleteSetting(qq_cookie_key)
        deleteSetting(migu_cookie_key)
        deleteSetting(proxy_original_key)
        deleteSetting(proxy_gd_source_key)
        // 旧版遗留的无引用配置（当前代码不读写，一并清除还原纯净）
        deleteSetting("β_proxy_mode_key")

        // 重建内存缓存为默认值：删除不更新 settingMap，同进程内读取会命中旧缓存
        settingMap.clear()
        settingMap[master_key] = true
        settingMap[dex_key] = true
        settingMap[proxy_master_key] = true
        settingMap[proxy_server_key] = false
        settingMap[proxy_gd_studio_key] = false
        settingMap[proxy_priority_key] = false
        settingMap[proxy_flac_key] = false
        settingMap[proxy_gray_key] = false
        settingMap[proxy_gd_flac_key] = false
        settingMap[beauty_follow_dark_key] = true
        settingMap[beauty_tab_hide_key] = false
    }

    // ==================== 音源代理相关 ====================

    fun getProxyPort(): Int = sharedPreferences.getInt(proxy_port_key, proxy_port_default)

    fun setProxyPort(port: String?) {
        if (!port.isNullOrEmpty()) {
            sharedPreferences.edit().putInt(proxy_port_key, port.toInt()).apply()
        }
    }

    /** 本地脚本监听端口（默认 23338，与服务器代理端口独立） */
    fun getProxyLocalPort(): Int = sharedPreferences.getInt(proxy_local_port_key, proxy_local_port_default)

    fun setProxyLocalPort(port: String?) {
        if (!port.isNullOrEmpty()) {
            sharedPreferences.edit().putInt(proxy_local_port_key, port.toInt()).apply()
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

    // ==================== GD Studio 相关 ====================

    fun getGdSource(): String = sharedPreferences.getString(proxy_gd_source_key, proxy_gd_source_default) ?: proxy_gd_source_default

    fun setGdSource(source: String?) {
        if (!source.isNullOrEmpty()) {
            sharedPreferences.edit().putString(proxy_gd_source_key, source).apply()
        }
    }

}
