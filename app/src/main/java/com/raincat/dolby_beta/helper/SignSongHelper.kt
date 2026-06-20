/**
 * 歌曲打卡帮助类 - 提供自助打卡和每日歌曲打卡功能
 *
 * 功能：
 * 1. 自助打卡 - 从用户指定的歌单URL中获取歌曲列表进行打卡
 * 2. 每日歌曲打卡 - 自动获取每日推荐歌单进行打卡
 *
 * 打卡通过调用网易云 weapi/feedback/weblog 接口模拟播放行为实现。
 * 依赖：Http（网络请求）、NeteaseAES（参数加密）、ExtraDao（打卡历史记录）
 */
package com.raincat.dolby_beta.helper

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import com.raincat.dolby_beta.db.ExtraDao
import com.raincat.dolby_beta.helper.ExtraHelper
import com.raincat.dolby_beta.net.Http
import com.raincat.dolby_beta.utils.LogUtils
import com.raincat.dolby_beta.utils.NeteaseAES
import com.raincat.dolby_beta.utils.Tools
import com.raincat.dolby_beta.view.BaseDialogItem
import com.raincat.dolby_beta.view.BaseDialogTextItem
import com.raincat.dolby_beta.view.sign.SignCountView
import com.raincat.dolby_beta.view.sign.SignIdView
import com.raincat.dolby_beta.view.sign.SignStartView
import com.raincat.dolby_beta.view.sign.SignTitleView
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Random
import java.util.regex.Pattern

/**
 * 歌曲打卡帮助类
 * 提供自助打卡对话框和每日歌曲打卡功能
 */
object SignSongHelper {

    private const val TAG = "SignSongHelper"

    /** 打卡API地址 */
    private const val SIGN_URL = "https://music.163.com/weapi/feedback/weblog?csrf_token="

    /** 每日推荐歌单API地址 */
    private const val DAILY_RECOMMEND_URL = "https://music.163.com/api/v1/discovery/recommend/resource"

    /** 歌单详情API地址 */
    private const val PLAYLIST_DETAIL_URL = "https://music.163.com/api/v1/playlist/detail?id="

    /** 打卡参数模板 - 模拟播放行为 */
    private const val PARAM_TEMPLATE = """{"logs":"[{\"action\":\"play\",\"json\":{\"sourceId\":\"%s\",\"type\":\"song\",\"wifi\":0,\"download\":0,\"id\":%s,\"time\":%s,\"end\":\"ui\"}}]","csrf_token":""}"""

    /** 进度提示文案 */
    private const val PLAYLIST = "获取到%s个歌单列表，正在获取第%s个歌单详情…"
    private const val SONG = "歌单「%s」共有%s首歌…"
    private const val SIGN = "正在打卡第%s首歌，忽略往日已打卡歌曲%s首，共成功%s首…"
    private const val FINISH = "打卡完成，请点击确定退出打卡！"
    private const val DAILY_FAIL = "获取每日推荐歌单列表失败！"
    private const val FAIL = "打卡失败，请点击确定退出打卡！"

    private val random = Random()
    private const val RANDOM_MAX = 300
    private const val RANDOM_MIN = 250

    /** 是否正在打卡中 */
    @Volatile
    private var signing = false

    /** 主线程Handler（用于更新UI） */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 请求头 */
    private var headers: HashMap<String, Any>? = null

    /** 历史已打卡歌曲ID集合（避免重复打卡） */
    private var historySignSet: MutableSet<Long> = HashSet()

    /** 本次已打卡歌曲ID列表（打卡完成后保存到历史记录） */
    private var signedList: MutableList<Long> = ArrayList()

    /** 歌单列表中的歌单ID和名称 */
    private var playListIds: MutableList<Long> = ArrayList()
    private var playListNames: MutableList<String> = ArrayList()

    /** 当前歌单索引、歌曲索引、忽略数、成功数 */
    private var playListIndex = 0
    private var songIndex = 0
    private var ignoreSignCount = 0
    private var successSignCount = 0

    /** 最大打卡首数 */
    private var maxCount = 350

    /**
     * 显示自助打卡对话框
     * @param context 上下文
     */
    fun showSelfSignDialog(context: Context) {
        val dialogSignRoot = BaseDialogItem(context)
        dialogSignRoot.orientation = LinearLayout.VERTICAL

        val signTitleView = SignTitleView(context)
        signTitleView.setCustomTitle(SettingHelper.sign_self_title)
        dialogSignRoot.addView(signTitleView)
        dialogSignRoot.addView(SignIdView(context))
        dialogSignRoot.addView(SignStartView(context))
        dialogSignRoot.addView(SignCountView(context))

        AlertDialog.Builder(context)
            .setView(dialogSignRoot)
            .setCancelable(true)
            .setPositiveButton("确定") { _, _ ->
                val signId = SettingHelper.getInstance().getSignId()
                if (TextUtils.isEmpty(signId)) {
                    Tools.showToastOnLooper(context, "请先填写打卡歌单URL")
                    return@setPositiveButton
                }

                val playListId = parsePlaylistId(signId)
                if (playListId == 0L) {
                    Tools.showToastOnLooper(context, "无法解析歌单ID，请检查URL")
                    return@setPositiveButton
                }

                // 自助打卡使用用户指定的歌单
                maxCount = SettingHelper.getInstance().getSignCount()
                songIndex = SettingHelper.getInstance().getSignStart()
                songIndex = if (songIndex == 0) 0 else songIndex - 1
                showSignStatusDialog(context, SettingHelper.sign_self_title, playListId)
            }
            .setNegativeButton("取消") { _, _ -> }
            .show()
    }

    /**
     * 显示打卡状态对话框并开始打卡流程
     *
     * @param context 上下文
     * @param title 对话框标题
     * @param playListId 指定歌单ID（自助打卡时传入），null表示获取每日推荐歌单
     */
    @JvmOverloads
    @JvmStatic
    fun showSignStatusDialog(context: Context, title: String, playListId: Long? = null) {
        init(context)

        // 构建打卡进度对话框
        val dialogRoot = BaseDialogItem(context)
        dialogRoot.orientation = LinearLayout.VERTICAL
        val signTitleView = SignTitleView(context)
        signTitleView.setCustomTitle(title)
        dialogRoot.addView(signTitleView)

        // 4个文本行用于显示打卡进度
        val textItems = Array(4) { BaseDialogTextItem(context) }
        textItems.forEach { dialogRoot.addView(it) }

        AlertDialog.Builder(context)
            .setView(dialogRoot)
            .setCancelable(false)
            .setPositiveButton("确定") { _, _ -> signing = false }
            .show()

        // 在后台线程执行打卡流程
        Thread {
            try {
                if (playListId != null && playListId != 0L) {
                    // 自助打卡：使用指定歌单
                    playListIds.clear()
                    playListNames.clear()
                    playListIds.add(playListId)
                    playListNames.add(playListId.toString())
                } else {
                    // 每日打卡：获取每日推荐歌单列表
                    getDailyRecommendList()
                }

                if (playListIds.isNotEmpty()) {
                    processPlaylists(context, textItems)
                } else {
                    updateText(textItems[0], DAILY_FAIL)
                    updateText(textItems[1], FAIL)
                    end(context)
                }
            } catch (e: Exception) {
                LogUtils.e("$TAG: showSignStatusDialog 异常 - ${e.message}")
                updateText(textItems[3], FAIL)
                end(context)
            }
        }.start()
    }

    /**
     * 获取每日推荐歌单列表
     * 解析JSON响应，提取歌单ID和名称
     */
    private fun getDailyRecommendList() {
        val result = Http("GET", DAILY_RECOMMEND_URL, null, headers).getResult()
        val jsonObject = JSONObject(result)
        val recommendArray = jsonObject.optJSONArray("recommend") ?: return

        for (i in 0 until recommendArray.length()) {
            val item = recommendArray.optJSONObject(i) ?: continue
            val id = item.optLong("id")
            val name = item.optString("name")
            if (id != 0L) {
                playListIds.add(id)
                playListNames.add(name)
            }
        }
    }

    /**
     * 遍历歌单列表，逐个获取歌单详情并打卡
     */
    private fun processPlaylists(context: Context, textItems: Array<BaseDialogTextItem>) {
        while (playListIndex < playListIds.size && signing) {
            // 显示当前歌单进度
            updateText(textItems[0], String.format(PLAYLIST, playListIds.size, playListIndex + 1))

            // 获取歌单详情
            val detailResult = Http("GET", PLAYLIST_DETAIL_URL + playListIds[playListIndex], null, headers).getResult()
            val detailJson = JSONObject(detailResult)
            val playlist = detailJson.optJSONObject("playlist")

            if (playlist == null) {
                playListIndex++
                continue
            }

            // 获取歌单中的歌曲ID列表
            val trackIdsArray = playlist.optJSONArray("trackIds")
            if (trackIdsArray == null) {
                playListIndex++
                continue
            }

            val trackIds = mutableListOf<Long>()
            for (i in 0 until trackIdsArray.length()) {
                val trackItem = trackIdsArray.optJSONObject(i) ?: continue
                val trackId = trackItem.optLong("id")
                if (trackId != 0L) {
                    trackIds.add(trackId)
                }
            }

            if (trackIds.isEmpty() || !signing) {
                playListIndex++
                continue
            }

            // 显示当前歌单歌曲数量
            updateText(textItems[1], String.format(SONG, playListNames[playListIndex], trackIds.size))
            playListIndex++

            // 对歌单中的歌曲逐首打卡
            signSongs(context, playlist.optLong("id"), trackIds, textItems)

            if (successSignCount >= maxCount) {
                break
            }
        }

        if (signing || successSignCount >= maxCount) {
            updateText(textItems[3], FINISH)
        }
        end(context)
    }

    /**
     * 对歌单中的歌曲逐首打卡
     *
     * @param context 上下文
     * @param sourceId 歌单ID（打卡参数中的sourceId）
     * @param trackIds 歌曲ID列表
     * @param textItems 进度文本控件数组
     */
    private fun signSongs(context: Context, sourceId: Long, trackIds: List<Long>, textItems: Array<BaseDialogTextItem>) {
        var index = songIndex
        songIndex = 0  // 重置，后续歌单从头开始

        while (index < trackIds.size && signing) {
            val songId = trackIds[index]

            // 检查是否已打卡过（历史记录）
            if (historySignSet.contains(songId)) {
                ignoreSignCount++
                updateText(textItems[2], String.format(SIGN, index + 1, ignoreSignCount, successSignCount))
                index++
                continue
            }

            // 构建打卡参数并加密
            val playTime = (random.nextInt(RANDOM_MAX) % (RANDOM_MAX - RANDOM_MIN + 1) + RANDOM_MIN).toString()
            val localParam = String.format(PARAM_TEMPLATE, sourceId.toString(), songId.toString(), playTime)

            val encryptedParam = try {
                "params=" + URLEncoder.encode(NeteaseAES.getParams(localParam), "UTF-8") + "&encSecKey=" + NeteaseAES.getEncSecKey()
            } catch (e: Exception) {
                e.printStackTrace()
                index++
                continue
            }

            // 发送打卡请求
            val result = Http("POST", SIGN_URL, headers, encryptedParam).getResult()

            // 检查打卡结果
            if (result.contains("success")) {
                signedList.add(songId)
                historySignSet.add(songId)
                successSignCount++
            }

            // 更新进度
            updateText(textItems[2], String.format(SIGN, index + 1, ignoreSignCount, successSignCount))

            // 达到最大打卡数则停止
            if (successSignCount >= maxCount) {
                updateText(textItems[3], FINISH)
                return
            }

            index++
        }
    }

    /**
     * 从URL或纯数字中解析歌单ID
     * 支持格式：
     * - https://music.163.com/playlist?id=123456
     * - https://music.163.com/#/playlist/123456
     * - 纯数字 123456
     *
     * @param input 用户输入的歌单URL或ID
     * @return 歌单ID，解析失败返回0
     */
    private fun parsePlaylistId(input: String): Long {
        // 匹配 playlist/数字 格式
        val pattern1 = Pattern.compile("playlist/(\\d+)")
        val matcher1 = pattern1.matcher(input)
        if (matcher1.find()) {
            return matcher1.group(1)!!.toLong()
        }

        // 匹配 id=数字 格式
        val pattern2 = Pattern.compile("id=(\\d+)")
        val matcher2 = pattern2.matcher(input)
        if (matcher2.find()) {
            return matcher2.group(1)!!.toLong()
        }

        // 匹配纯数字
        if (input.matches(Regex("^\\d+$"))) {
            return input.toLong()
        }

        return 0
    }

    /**
     * 初始化打卡状态
     * 设置请求头、加载历史打卡记录
     */
    private fun init(context: Context) {
        if (headers == null) {
            headers = HashMap()
        }
        headers!!["Cookie"] = ExtraHelper.getExtraDate(ExtraHelper.COOKIE)

        signing = true
        playListIndex = 0
        songIndex = 0
        ignoreSignCount = 0
        successSignCount = 0
        maxCount = 350
        signedList = ArrayList()
        playListIds = ArrayList()
        playListNames = ArrayList()

        // 从ExtraDao加载历史打卡记录（默认值"-1"表示无记录）
        val userId = ExtraHelper.getExtraDate(ExtraHelper.USER_ID)
        historySignSet = HashSet()
        val historyStr = ExtraDao.getInstance().getExtra("sign_song_history_$userId")
        if (historyStr.isNotEmpty() && historyStr != "-1") {
            historyStr.split(",").forEach { id ->
                val songId = id.trim().toLongOrNull()
                if (songId != null && songId > 0) {
                    historySignSet.add(songId)
                }
            }
        }
    }

    /**
     * 打卡结束，保存历史记录并重置状态
     */
    private fun end(context: Context) {
        // 保存本次打卡记录到历史
        val userId = ExtraHelper.getExtraDate(ExtraHelper.USER_ID)
        if (signedList.isNotEmpty()) {
            // 合并历史记录（避免历史记录过大，最多保留最近5000首）
            val allSigned = (historySignSet + signedList).toList().takeLast(5000)
            val historyStr = allSigned.joinToString(",")
            ExtraDao.getInstance().saveExtra("sign_song_history_$userId", historyStr)
        }

        signing = false
        headers = null
        historySignSet = HashSet()
        signedList = ArrayList()
        playListIds = ArrayList()
        playListNames = ArrayList()
        playListIndex = 0
        songIndex = 0
        ignoreSignCount = 0
        successSignCount = 0
        maxCount = 350
    }

    /**
     * 在主线程更新文本控件
     */
    private fun updateText(textItem: BaseDialogTextItem, text: String) {
        mainHandler.post {
            textItem.text = text
            textItem.visibility = View.VISIBLE
        }
    }
}
