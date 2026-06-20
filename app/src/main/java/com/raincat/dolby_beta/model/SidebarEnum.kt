/**
 * 侧边栏枚举 - 定义网易云侧边栏所有可隐藏的Item
 *
 * 用于"精简侧边栏"功能，动态匹配当前版本网易云侧边栏实际存在的Item。
 * 通过 setSidebarEnum() 传入运行时获取的侧边栏Item列表，与预设枚举交叉匹配。
 */
package com.raincat.dolby_beta.model

import java.util.LinkedHashMap

/**
 * 侧边栏枚举管理
 * 维护所有已知侧边栏Item的标识与显示名称映射
 */
object SidebarEnum {

    /** 所有已知侧边栏枚举（标识 -> 显示名称） */
    private val enumMap: LinkedHashMap<String, String> = LinkedHashMap()

    /** 当前版本实际存在的侧边栏枚举（运行时动态填充） */
    private var sidebarMap: LinkedHashMap<String, String> = LinkedHashMap()

    /**
     * 设置当前版本网易云侧边栏实际存在的Item列表
     * 将运行时获取的Item与预设枚举交叉匹配，生成当前版本可用的侧边栏Map
     *
     * @param objectList 运行时从网易云获取的侧边栏Item标识数组
     */
    fun setSidebarEnum(objectList: Array<Any>) {
        sidebarMap = LinkedHashMap()
        val tempMap = HashMap<String, String>()
        for (obj in objectList) {
            tempMap[obj.toString()] = obj.toString()
        }
        // 先添加已知的枚举项（保持顺序）
        for ((key, value) in enumMap) {
            if (tempMap.containsKey(key)) {
                sidebarMap[key] = value
                tempMap.remove(key)
            }
        }
        // 再添加未知的新Item
        if (tempMap.isNotEmpty()) {
            for (key in tempMap.values) {
                // 跳过不需要隐藏的Item
                if (key == "SETTING" || key == "DYNAMIC_ITEM" || key == "PROFILE" || key == "CHILD_MODE" ||
                    key == "CLASSICAL" || key == "AVATAR" || key == "DYNAMIC_CONTAINER" || key == "SMALL_ICE"
                )
                    continue
                if (key == "GROUP") {
                    sidebarMap[key + "1"] = "音乐服务（组）"
                    sidebarMap[key + "2"] = "其他（组）"
                } else {
                    sidebarMap[key] = ""
                }
            }
        }
    }

    /**
     * 获取当前版本可用的侧边栏枚举
     */
    fun getSidebarEnum(): LinkedHashMap<String, String> = sidebarMap

    init {
        enumMap["LOGIN"] = "登录"
        enumMap["MESSAGE"] = "我的消息"
        enumMap["VIP"] = "我的会员"
        enumMap["CLOUD_SHELL_CENTER"] = "云贝中心"
        enumMap["MUSICIAN"] = "音乐人中心"
        enumMap["CREATOR_CENTER"] = "创作者中心"
        enumMap["MUSICIAN_CREATOR_CENTER"] = "创作者中心"
        enumMap["MUSICIAN_VIEWER"] = "加入网易音乐人"
        enumMap["TICKET"] = "云村有票"
        enumMap["NEARBY"] = "附近的人"
        enumMap["STORE"] = "商城"
        enumMap["BEAT"] = "Beat交易平台"
        enumMap["GAME"] = "游戏专区"
        enumMap["COLOR_RING"] = "口袋彩铃"
        enumMap["SETTING"] = "设置"
        enumMap["NIGHT_THEME_MODE"] = "夜间模式"
        enumMap["CLOCK_PLAY"] = "定时停止播放"
        enumMap["THEME"] = "个性装扮"
        enumMap["IDENTIFY"] = "听歌识曲"
        enumMap["SCAN"] = "扫一扫"
        enumMap["CACHE_WHILE_LISTEN"] = "边听边存"
        enumMap["FREE"] = "在线听歌免流量"
        enumMap["MUSIC_BLACKLIST"] = "音乐黑名单"
        enumMap["PRIVATE_CLOUD"] = "音乐云盘"
        enumMap["YOUTH_MODE"] = "青少年模式"
        enumMap["ALARM_CLOCK"] = "音乐闹钟"
        enumMap["MY_ORDER"] = "我的订单"
        enumMap["MY_FRIEND"] = "我的好友"
        enumMap["VEHICLE_PLAYER"] = "驾驶模式"
        enumMap["DISCOUNT_COUPON"] = "优惠券"
        enumMap["RED_PACKET"] = "音乐红包"
        enumMap["PROFIT"] = "赞赏收入"
        enumMap["DYNAMIC_ITEM"] = "第三方私密协议"
        enumMap["FEEDBACK_HELP"] = "帮助与反馈"
        enumMap["SHARE_APP"] = "分享网易云音乐"
        enumMap["ABOUT"] = "关于"
        enumMap["LOGOUT"] = "登出"
        enumMap["DIV1"] = "分割线1"
        enumMap["DIV2"] = "分割线2"
        enumMap["DIV3"] = "分割线3"
        enumMap["DIV4"] = "分割线4"
    }
}
