/**
 * 设置界面 UI（Compose + Material 3）
 * 参考 HookWeiXin 项目的 UI 框架：深浅色配色 + 分组卡片 + 开关行，扩展出导航/输入/操作行与子页面导航
 */
package com.raincat.dolby_beta.ui

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raincat.dolby_beta.BuildConfig
import com.raincat.dolby_beta.helper.ExtraHelper
import com.raincat.dolby_beta.helper.ScriptHelper
import com.raincat.dolby_beta.helper.SettingHelper
import com.raincat.dolby_beta.utils.Tools

private const val TAG = "dolby_beta.SettingsScreen"

/** 网易云品牌红（深浅色通用强调色） */
private val NeteaseRed = Color(0xFFD33A31)

/** 弹窗宽度（占满可用宽度但不超过该值，居中并留出屏边距） */
private val DialogWidth = 440.dp

/** 设置弹窗内部页面 */
private enum class Screen { MAIN, PROXY, PROXY_CONFIG, SCRIPT_CONFIG, BEAUTY }

/** 深浅色主题配色集合 */
private data class DolbyColors(
    val dialogBg: Color,          // 弹窗底色
    val card: Color,              // 卡片
    val title: Color,             // 标题文字
    val desc: Color,              // 描述文字
    val version: Color,           // 版本文字
    val inputText: Color,         // 输入文字
    val inputBg: Color,           // 输入框背景
    val inputBgDisabled: Color,   // 输入框禁用背景
    val inputBorder: Color,       // 输入框边框
    val inputBorderDisabled: Color, // 输入框禁用边框
    val cursor: Color,            // 输入光标
    val divider: Color,           // 分割线
    val switchTrackOff: Color,    // 开关未选中轨道
    val switchBorderOff: Color,   // 开关未选中描边
    val switchTrackOn: Color,     // 开关选中轨道
)

/** 根据主题返回配色 */
@Composable
private fun dolbyColors(dark: Boolean): DolbyColors {
    return if (dark) DolbyColors(
        dialogBg = Color(0xFF1E1E1E),
        card = Color(0xFF222222),
        title = Color(0xFFEDEDED),
        desc = Color(0xFF9A9A9A),
        version = Color(0xFF6E7076),
        inputText = Color(0xFFD1D2D6),
        inputBg = Color(0xFF28282A),
        inputBgDisabled = Color(0xFF242426),
        inputBorder = Color(0xFF3C3C3C),
        inputBorderDisabled = Color(0xFF2E3033),
        cursor = Color(0xFF6E7076),
        divider = Color(0xFF2A2C2F),
        switchTrackOff = Color(0xFF3C3C3C),
        switchBorderOff = Color(0xFF3C3C3C),
        switchTrackOn = Color(0xFFF0645C),
    ) else DolbyColors(
        dialogBg = Color(0xFFF0F1F3),
        card = Color(0xFFFFFFFF),
        title = Color(0xFF1A1A1A),
        desc = Color(0xFF999999),
        version = Color(0xFFB0B0B0),
        inputText = Color(0xFF555555),
        inputBg = Color(0xFFFAFAFA),
        inputBgDisabled = Color(0xFFF8F8F8),
        inputBorder = Color(0xFFE0E0E0),
        inputBorderDisabled = Color(0xFFEDEDED),
        cursor = Color(0xFFD0D0D0),
        divider = Color(0xFFF0F0F0),
        switchTrackOff = Color(0xFFF6F6F6),
        switchBorderOff = Color(0xFFEEEEEE),
        switchTrackOn = NeteaseRed,
    )
}

/** 判断设置界面是否采用深色主题（关闭"深色模式跟随系统"时固定浅色） */
internal fun isDarkTheme(activity: Activity): Boolean {
    if (!SettingHelper.getInstance().getSetting(SettingHelper.beauty_follow_dark_key)) return false
    return (activity.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
}

/** 为宿主进程构造带模块 Resources 的 Context（Compose 库资源仅在模块 APK 内，宿主 Resources 无法解析） */
private fun moduleResourceContext(base: Context): Context {
    return try {
        val modulePath = ScriptHelper.modulePath ?: return base
        // AssetManager 构造为隐藏 API，通过反射添加模块 APK 资源
        val amClass = Class.forName("android.content.res.AssetManager")
        val assetManager = amClass.getDeclaredConstructor().newInstance()
        amClass.getMethod("addAssetPath", String::class.java).invoke(assetManager, modulePath)
        val moduleRes = android.content.res.Resources(
            assetManager as android.content.res.AssetManager,
            base.resources.displayMetrics,
            base.resources.configuration,
        )
        object : android.content.ContextWrapper(base) {
            override fun getResources(): android.content.res.Resources = moduleRes
        }
    } catch (e: Throwable) {
        Log.w(TAG, "创建模块资源 Context 失败: ${e.message}")
        base
    }
}

/** 弹出设置菜单 Dialog */
internal fun showSettingsDialog(
    activity: Activity,
    onDismiss: Runnable,
    darkTheme: Boolean,
) {
    val dialog = ComponentDialog(activity)
    dialog.window?.let { window ->
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }
    val composeView = ComposeView(moduleResourceContext(activity)).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setContent {
            SettingsRoot(
                activity = activity,
                darkTheme = darkTheme,
                onDismiss = { dialog.dismiss() },
            )
        }
    }
    dialog.setContentView(composeView)
    dialog.setOnDismissListener {
        try {
            onDismiss.run()
        } catch (e: Throwable) {
            Log.w(TAG, "onDismiss 失败: ${e.message}")
        }
    }
    dialog.show()
    // show 后再次强制窗口铺满：Dialog 主题/窗口管理器会限制宽度，导致内部无法加宽
    dialog.window?.setLayout(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
    )
}

/** 设置根组件：管理页面导航与统一弹窗外观 */
@Composable
private fun SettingsRoot(
    activity: Activity,
    darkTheme: Boolean,
    onDismiss: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val colors = dolbyColors(darkTheme)
    var screen by remember { mutableStateOf(Screen.MAIN) }

    // 逐级返回：子页面回上级，主页面关闭整个弹窗
    val goBack: () -> Unit = {
        when (screen) {
            Screen.MAIN -> onDismiss()
            Screen.PROXY, Screen.BEAUTY -> screen = Screen.MAIN
            Screen.PROXY_CONFIG, Screen.SCRIPT_CONFIG -> screen = Screen.PROXY
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { goBack() },
        contentAlignment = Alignment.Center,
    ) {
        // 横向安全边距容器：窄屏留白不贴边/不溢出；Surface 用 fillMaxWidth 填满该容器
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = DialogWidth) // 超宽屏封顶，普通手机宽度小于上限则自然全宽
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { focusManager.clearFocus() },
                shape = RoundedCornerShape(16.dp),
                color = colors.dialogBg,
                shadowElevation = 10.dp,
            ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when (screen) {
                    Screen.MAIN -> MainScreen(colors, activity) { screen = it }
                    Screen.PROXY -> ProxyScreen(colors, activity) { screen = it }
                    Screen.PROXY_CONFIG -> ProxyConfigScreen(colors) { screen = Screen.PROXY }
                    Screen.SCRIPT_CONFIG -> ScriptConfigScreen(colors) { screen = Screen.PROXY }
                    Screen.BEAUTY -> BeautyScreen(colors) { screen = Screen.MAIN }
                }
                DialogActions(
                    screen = screen,
                    colors = colors,
                    onRestart = { restartApplication(activity) },
                    onBack = goBack,
                )
            }
            }
        }
    }
}

// ==================== 主设置页面 ====================

@Composable
private fun MainScreen(
    colors: DolbyColors,
    activity: Activity,
    onNavigate: (Screen) -> Unit,
) {
    SettingsHeader(
        title = "杜比大喇叭β",
        subtitle = "本模块仅供学习交流，严禁用于商业用途，请于24小时内删除。\n" +
            "注意：模块工作原理为音源替换而非破解，所以单曲付费与无版权歌曲有几率匹配错误，真心支持歌手请付费。",
        rightText = "v${BuildConfig.VERSION_NAME}",
        showBack = false,
        colors = colors,
        onBack = {},
    )

    // 模块总开关状态
    var masterEnabled by remember {
        mutableStateOf(SettingHelper.getInstance().getSetting(SettingHelper.master_key))
    }
    var dexEnabled by remember {
        mutableStateOf(SettingHelper.getInstance().getSetting(SettingHelper.dex_key))
    }
    var showResetConfirm by remember { mutableStateOf(false) }
    val setMaster = { new: Boolean ->
        masterEnabled = new
        SettingHelper.getInstance().setSetting(SettingHelper.master_key, new)
        Tools.showToastOnLooper(activity, "打开/关闭此设置需重启网易云")
    }
    val setDex = { new: Boolean ->
        dexEnabled = new
        SettingHelper.getInstance().setSetting(SettingHelper.dex_key, new)
    }

    SectionLabel("模块", colors)
    GroupCard(colors) {
        SwitchItem(SettingHelper.master_title, "模块总开关，关闭后所有功能不生效，需重启网易云", masterEnabled, setMaster, colors)
        CardDivider(colors)
        SwitchItem(SettingHelper.dex_title, SettingHelper.dex_sub, dexEnabled, setDex, colors)
    }

    SectionLabel("功能", colors)
    GroupCard(colors) {
        NavItem(SettingHelper.proxy_title, "将无版权/付费歌曲的播放请求代理到第三方音源", enabled = masterEnabled, colors = colors) {
            onNavigate(Screen.PROXY)
        }
        CardDivider(colors)
        NavItem(SettingHelper.beauty_title, "界面美化设置", enabled = masterEnabled, colors = colors) {
            onNavigate(Screen.BEAUTY)
        }
        CardDivider(colors)
        ActionItem("重置模块", "模块出现问题可以尝试重置", colors) {
            showResetConfirm = true
        }
    }

    SectionLabel("其他", colors)
    GroupCard(colors) {
        ActionItem("关于", "访问项目 GitHub 主页", colors) {
            val uri = Uri.parse("https://github.com/nining377/dolby_beta")
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = colors.dialogBg,
            titleContentColor = colors.title,
            textContentColor = colors.desc,
            title = { Text("确认重置模块", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "将清除模块的全部设置并恢复默认，需重启网易云生效。是否继续？",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    SettingHelper.getInstance().resetSetting()
                    // 主页面开关复位为默认值
                    masterEnabled = true
                    dexEnabled = true
                    Toast.makeText(activity, "重置完成，手动重启网易云生效", Toast.LENGTH_SHORT).show()
                }) {
                    Text("重置", fontSize = 13.sp, color = colors.switchTrackOn)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("取消", fontSize = 13.sp, color = colors.desc)
                }
            },
        )
    }
}

// ==================== 音源代理设置页面 ====================

@Composable
private fun ProxyScreen(
    colors: DolbyColors,
    activity: Activity,
    onNavigate: (Screen) -> Unit,
) {
    SettingsHeader(
        title = "音源代理设置",
        subtitle = "将音源请求代理到第三方音源（QQ音乐/咪咕等）",
        rightText = null,
        showBack = true,
        colors = colors,
        onBack = { onNavigate(Screen.MAIN) },
    )

    var masterEnabled by remember {
        mutableStateOf(SettingHelper.getInstance().getSetting(SettingHelper.proxy_master_key))
    }
    // true=服务器代理，false=本地代理（映射 proxy_server_key）
    var serverMode by remember {
        mutableStateOf(SettingHelper.getInstance().getSetting(SettingHelper.proxy_server_key))
    }
    val setMaster = { new: Boolean ->
        masterEnabled = new
        SettingHelper.getInstance().setSetting(SettingHelper.proxy_master_key, new)
        if (new) {
            ScriptHelper.initScript(activity, false)
            ScriptHelper.startScript()
        } else {
            ScriptHelper.stopScript()
        }
    }
    val setServerMode = { new: Boolean ->
        serverMode = new
        SettingHelper.getInstance().setSetting(SettingHelper.proxy_server_key, new)
        if (new) {
            // 服务器代理无需本地 node 脚本
            ScriptHelper.stopScript()
        } else {
            ScriptHelper.initScript(activity, false)
            ScriptHelper.startScript()
        }
    }

    // 开关状态需用 remember 持有，直接读 SettingHelper 不会触发重组，界面无法即时刷新
    var grayEnabled by remember {
        mutableStateOf(SettingHelper.getInstance().getSetting(SettingHelper.proxy_gray_key))
    }
    var priorityEnabled by remember {
        mutableStateOf(SettingHelper.getInstance().getSetting(SettingHelper.proxy_priority_key))
    }
    var flacEnabled by remember {
        mutableStateOf(SettingHelper.getInstance().getSetting(SettingHelper.proxy_flac_key))
    }
    val setGray = { new: Boolean ->
        grayEnabled = new
        SettingHelper.getInstance().setSetting(SettingHelper.proxy_gray_key, new)
    }
    val setPriority = { new: Boolean ->
        priorityEnabled = new
        SettingHelper.getInstance().setSetting(SettingHelper.proxy_priority_key, new)
    }
    val setFlac = { new: Boolean ->
        flacEnabled = new
        SettingHelper.getInstance().setSetting(SettingHelper.proxy_flac_key, new)
    }

    SectionLabel("代理设置", colors)
    GroupCard(colors) {
        SwitchItem(SettingHelper.proxy_master_title, "启用音源代理服务", masterEnabled, setMaster, colors)
        CardDivider(colors)
        Text(
            text = "代理模式",
            fontSize = 13.sp,
            color = colors.desc,
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (masterEnabled) 1f else 0.4f)
                .padding(horizontal = 10.dp),
        ) {
            ModeOption(
                title = "本地代理",
                selected = !serverMode,
                enabled = masterEnabled,
                colors = colors,
                modifier = Modifier.weight(1f),
            ) { setServerMode(false) }
            ModeOption(
                title = "服务器代理",
                selected = serverMode,
                enabled = masterEnabled,
                colors = colors,
                modifier = Modifier.weight(1f),
            ) { setServerMode(true) }
        }
        // 选中模式的说明
        Text(
            text = if (serverMode) "连接自建服务器代理，无需运行 node" else "使用内置脚本在本机运行代理",
            fontSize = 12.sp,
            color = colors.desc,
            lineHeight = 16.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
    }

    // 按代理模式展示对应配置（总开关关闭时不展示）
    if (masterEnabled && serverMode) {
        SectionLabel("服务器代理配置", colors)
        GroupCard(colors) {
            NavItem(SettingHelper.proxy_configuration_title, SettingHelper.proxy_configuration_sub, colors = colors) {
                onNavigate(Screen.PROXY_CONFIG)
            }
        }
    } else if (masterEnabled) {
        SectionLabel("本地代理配置", colors)
        GroupCard(colors) {
            NavItem(SettingHelper.script_configuration_title, SettingHelper.script_configuration_sub, colors = colors) {
                onNavigate(Screen.SCRIPT_CONFIG)
            }
            CardDivider(colors)
            SwitchItem(SettingHelper.proxy_gray_title, SettingHelper.proxy_gray_sub,
                grayEnabled, setGray, colors)
            CardDivider(colors)
            SwitchItem(SettingHelper.proxy_priority_title, SettingHelper.proxy_priority_sub,
                priorityEnabled, setPriority, colors)
            CardDivider(colors)
            SwitchItem(SettingHelper.proxy_flac_title, SettingHelper.proxy_flac_sub,
                flacEnabled, setFlac, colors)
            CardDivider(colors)
            ActionItem(SettingHelper.proxy_cover_title, SettingHelper.proxy_cover_sub, colors) {
                ScriptHelper.initScript(activity, true)
                Tools.showToastOnLooper(activity, "操作成功，脚本即将重新启动")
                ScriptHelper.startScript()
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
}

// ==================== 服务器代理配置页面 ====================

@Composable
private fun ProxyConfigScreen(colors: DolbyColors, onBack: () -> Unit) {
    SettingsHeader(
        title = "服务器代理配置",
        subtitle = null,
        rightText = null,
        showBack = true,
        colors = colors,
        onBack = onBack,
    )

    var httpProxy by remember { mutableStateOf(SettingHelper.getInstance().getHttpProxy()) }
    var port by remember { mutableStateOf(SettingHelper.getInstance().getProxyPort().toString()) }
    val setHttpProxy = { new: String ->
        httpProxy = new
        SettingHelper.getInstance().setHttpProxy(new)
    }
    val setPort = { new: String ->
        port = new
        SettingHelper.getInstance().setProxyPort(new)
    }

    GroupCard(colors) {
        InputItem(
            title = SettingHelper.http_proxy_title,
            value = httpProxy,
            placeholder = SettingHelper.http_proxy_default,
            colors = colors,
            isValidInput = { it.all { c -> c.isLetterOrDigit() || c == '.' } },
            onValueChange = setHttpProxy,
            onResetDefault = { setHttpProxy(SettingHelper.http_proxy_default) },
        )
        CardDivider(colors)
        InputItem(
            title = SettingHelper.proxy_port_title,
            value = port,
            placeholder = SettingHelper.proxy_port_default.toString(),
            colors = colors,
            isValidInput = { it.length <= 5 && it.all { c -> c.isDigit() } },
            onValueChange = setPort,
            onResetDefault = { setPort(SettingHelper.proxy_port_default.toString()) },
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
}

// ==================== 脚本参数配置页面 ====================

@Composable
private fun ScriptConfigScreen(colors: DolbyColors, onBack: () -> Unit) {
    SettingsHeader(
        title = "脚本参数配置",
        subtitle = "本地脚本模式的运行参数",
        rightText = null,
        showBack = true,
        colors = colors,
        onBack = onBack,
    )

    var original by remember { mutableStateOf(SettingHelper.getInstance().getProxyOriginal()) }
    var qqCookie by remember { mutableStateOf(SettingHelper.getInstance().getQqCookie()) }
    var miguCookie by remember { mutableStateOf(SettingHelper.getInstance().getMiguCookie()) }
    var scriptCommand by remember { mutableStateOf(ScriptHelper.getScriptCommand()) }
    val refreshCommand = { scriptCommand = ScriptHelper.getScriptCommand() }

    GroupCard(colors) {
        InputItem(
            title = SettingHelper.proxy_original_title,
            value = original,
            placeholder = SettingHelper.proxy_original_default,
            colors = colors,
            isValidInput = { it.all { c -> c.isLetter() || c == ' ' } },
            onValueChange = { new ->
                original = new
                SettingHelper.getInstance().setProxyOriginal(new)
                refreshCommand()
            },
            onResetDefault = {
                original = SettingHelper.proxy_original_default
                SettingHelper.getInstance().setProxyOriginal(SettingHelper.proxy_original_default)
                refreshCommand()
            },
        )
        CardDivider(colors)
        InputItem(
            title = SettingHelper.qq_cookie_title,
            value = qqCookie,
            placeholder = SettingHelper.qq_cookie_default,
            colors = colors,
            onValueChange = { new ->
                qqCookie = new
                SettingHelper.getInstance().setQqCookie(new)
                refreshCommand()
            },
            onResetDefault = {
                qqCookie = SettingHelper.qq_cookie_default
                SettingHelper.getInstance().setQqCookie(SettingHelper.qq_cookie_default)
                refreshCommand()
            },
        )
        CardDivider(colors)
        InputItem(
            title = SettingHelper.migu_cookie_title,
            value = miguCookie,
            placeholder = SettingHelper.migu_cookie_default,
            colors = colors,
            onValueChange = { new ->
                miguCookie = new
                SettingHelper.getInstance().setMiguCookie(new)
                refreshCommand()
            },
            onResetDefault = {
                miguCookie = SettingHelper.migu_cookie_default
                SettingHelper.getInstance().setMiguCookie(SettingHelper.migu_cookie_default)
                refreshCommand()
            },
        )
    }

    SectionLabel("当前启动命令", colors)
    GroupCard(colors) {
        Text(
            text = scriptCommand,
            fontSize = 11.sp,
            color = colors.inputText,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
}

// ==================== 美化设置页面 ====================

@Composable
private fun BeautyScreen(colors: DolbyColors, onBack: () -> Unit) {
    SettingsHeader(
        title = "美化设置",
        subtitle = null,
        rightText = null,
        showBack = true,
        colors = colors,
        onBack = onBack,
    )

    var darkFollowEnabled by remember {
        mutableStateOf(SettingHelper.getInstance().getSetting(SettingHelper.beauty_follow_dark_key))
    }
    var tabHideEnabled by remember {
        mutableStateOf(SettingHelper.getInstance().getSetting(SettingHelper.beauty_tab_hide_key))
    }
    val setDarkFollow = { new: Boolean ->
        darkFollowEnabled = new
        SettingHelper.getInstance().setSetting(SettingHelper.beauty_follow_dark_key, new)
    }
    val setTabHide = { new: Boolean ->
        tabHideEnabled = new
        SettingHelper.getInstance().setSetting(SettingHelper.beauty_tab_hide_key, new)
    }

    SectionLabel("美化", colors)
    GroupCard(colors) {
        SwitchItem(
            SettingHelper.beauty_follow_dark_title,
            SettingHelper.beauty_follow_dark_sub,
            darkFollowEnabled,
            setDarkFollow,
            colors,
        )
        CardDivider(colors)
        SwitchItem(
            SettingHelper.beauty_tab_hide_title,
            SettingHelper.beauty_tab_hide_sub,
            tabHideEnabled,
            setTabHide,
            colors,
        )
    }

    Spacer(modifier = Modifier.height(8.dp))
}

// ==================== 可复用组件 ====================

/** 页面头部（返回按钮/标题与版本号同一行，副标题独立换行） */
@Composable
private fun SettingsHeader(
    title: String,
    subtitle: String?,
    rightText: String?,
    showBack: Boolean,
    colors: DolbyColors,
    onBack: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card)
                .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                Text(
                    text = "←",
                    fontSize = 20.sp,
                    color = colors.title,
                    modifier = Modifier.clickable { onBack() }.padding(end = 6.dp),
                )
            }
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.title,
                modifier = Modifier.weight(1f),
            )
            if (rightText != null) {
                Text(
                    text = rightText,
                    fontSize = 12.sp,
                    color = colors.version,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = colors.desc,
                lineHeight = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.card)
                    .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            )
        }
        HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
    }
}

/** 分组标签 */
@Composable
private fun SectionLabel(text: String, colors: DolbyColors) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = colors.desc,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

/** 分组卡片容器 */
@Composable
private fun GroupCard(colors: DolbyColors, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content,
    )
}

/** 开关列表项（整行可点击切换） */
@Composable
private fun SwitchItem(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: DolbyColors,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(start = 14.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = colors.title)
            if (desc.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(desc, fontSize = 12.sp, color = colors.desc, lineHeight = 16.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.scale(0.8f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.switchTrackOn,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = colors.switchTrackOff,
                uncheckedBorderColor = colors.switchBorderOff,
            ),
        )
    }
}

/** 导航列表项（点击进入子页面，右侧省略号指示） */
@Composable
private fun NavItem(
    title: String,
    desc: String,
    enabled: Boolean = true,
    colors: DolbyColors,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 14.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = colors.title)
            if (desc.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(desc, fontSize = 12.sp, color = colors.desc, lineHeight = 16.sp)
            }
        }
        Text("›", fontSize = 20.sp, color = colors.desc)
    }
}

/** 操作列表项（点击执行操作，无箭头） */
@Composable
private fun ActionItem(
    title: String,
    desc: String,
    colors: DolbyColors,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 14.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = colors.title)
            if (desc.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(desc, fontSize = 12.sp, color = colors.desc, lineHeight = 16.sp)
            }
        }
    }
}

/** 输入列表项（带恢复默认） */
@Composable
private fun InputItem(
    title: String,
    value: String,
    placeholder: String?,
    colors: DolbyColors,
    isValidInput: (String) -> Boolean = { true },
    onValueChange: (String) -> Unit,
    onResetDefault: (() -> Unit)?,
) {
    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 14.sp, color = colors.title, modifier = Modifier.weight(1f))
            if (onResetDefault != null) {
                TextButton(onClick = onResetDefault) {
                    Text("恢复默认", fontSize = 12.sp, color = colors.switchTrackOn)
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { new -> if (isValidInput(new)) onValueChange(new) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder.orEmpty(), fontSize = 12.sp, color = colors.version) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = colors.inputText),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.inputText,
                unfocusedTextColor = colors.inputText,
                cursorColor = colors.switchTrackOn,
                focusedBorderColor = colors.switchTrackOn,
                unfocusedBorderColor = colors.inputBorder,
                focusedContainerColor = colors.inputBg,
                unfocusedContainerColor = colors.inputBg,
            ),
        )
    }
}

/** 卡片内分割线 */
@Composable
private fun CardDivider(colors: DolbyColors) {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = colors.divider, thickness = 0.5.dp)
}

/** 代理模式选项（横向占半宽，选中高亮边框/底色 + 单选指示器） */
@Composable
private fun ModeOption(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    colors: DolbyColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) colors.switchTrackOn.copy(alpha = 0.12f) else Color.Transparent,
            )
            .border(
                width = 1.dp,
                color = if (selected) colors.switchTrackOn else colors.inputBorder,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            color = if (enabled) colors.title else colors.desc,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier.size(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) colors.switchTrackOn else colors.desc,
                        shape = CircleShape,
                    ),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color = colors.switchTrackOn, shape = CircleShape),
                )
            }
        }
    }
}

/** 底部操作栏（主页面：确定=关闭；子页面：仅保存=返回上一级；重启按钮一致） */
@Composable
private fun DialogActions(
    screen: Screen,
    colors: DolbyColors,
    onRestart: () -> Unit,
    onBack: () -> Unit,
) {
    val isMain = screen == Screen.MAIN
    val primaryText = if (isMain) "确定" else "仅保存"
    val secondaryText = if (isMain) "重启网易云" else "保存并重启"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onRestart,
            modifier = Modifier.height(28.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text(secondaryText, fontSize = 12.sp, color = colors.switchTrackOn)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Button(
            onClick = onBack,
            modifier = Modifier.height(28.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.switchTrackOn),
        ) {
            Text(primaryText, fontSize = 12.sp, color = Color.White)
        }
    }
}

/**
 * 重启网易云应用：用 AlarmManager 注册一个稍后触发的启动闹钟（PendingIntent 注册于系统，
 * 进程死亡后仍会触发），随后结束当前进程，实现真正的重启。
 */
private fun restartApplication(context: Context) {
    ExtraHelper.setExtraDate(ExtraHelper.SCRIPT_STATUS, "0")
    // 结束播放子进程
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    activityManager.runningAppProcesses?.forEach { process ->
        if (process.processName.contains(":play")) {
            android.os.Process.killProcess(process.pid)
        }
    }
    // 调度重启闹钟：进程结束后由系统重新拉起主界面
    try {
        val intent = Intent().setClassName(
            context.packageName,
            "com.netease.cloudmusic.activity.MainActivity",
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + 1000
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pendingIntent)
        } catch (e: Exception) {
            Log.w(TAG, "setExactAndAllowWhileIdle 不可用，回退: ${e.message}")
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pendingIntent)
            } catch (e2: Exception) {
                alarmManager.set(AlarmManager.RTC, triggerAt, pendingIntent)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "调度重启闹钟失败: ${e.message}")
    }
    // 结束当前主进程，触发系统按闹钟重建
    Handler(Looper.getMainLooper()).postDelayed({
        android.os.Process.killProcess(android.os.Process.myPid())
        System.exit(0)
    }, 200)
}