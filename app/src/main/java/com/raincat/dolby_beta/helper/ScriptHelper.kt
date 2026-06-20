/**
 * 脚本帮助类 - 管理UnblockNeteaseMusic脚本的生命周期
 * 脚本启动方式：通过shell执行libnode.so运行app.js
 * 脚本状态通过ExtraHelper.SCRIPT_STATUS管理：0=启动中，1=已启动
 *
 */
package com.raincat.dolby_beta.helper

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import com.raincat.dolby_beta.BuildConfig
import com.raincat.dolby_beta.Hook
import com.raincat.dolby_beta.net.HTTPSTrustManager
import com.raincat.dolby_beta.utils.LogUtils
import com.raincat.dolby_beta.utils.Tools
import com.stericson.RootShell.execution.Command
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory

object ScriptHelper {

    /** 模块路径（APK路径，由MainHook.onModuleLoaded设置） */
    @JvmField
    var modulePath: String? = null

    /** 脚本解压目录 */
    private var scriptPath: String? = null

    /** node可执行文件的PATH环境变量 */
    private var nodeLibPath: String? = null

    private val STOP_PROXY = arrayOf(
        "node=\$(ps -ef |grep \"libnode.so app.js\" |grep -v grep)",
        "if [ -n \"\$node\" ]; then",
        "killall -9 libnode.so >/dev/null 2>&1",
        "fi"
    )

    @SuppressLint("StaticFieldLeak")
    private var neteaseContext: Context? = null

    private fun getScriptPath(context: Context): String {
        if (TextUtils.isEmpty(scriptPath))
            scriptPath = context.filesDir.absolutePath + "/script"
        return scriptPath!!
    }

    /**
     * 初始化脚本
     *
     * 流程：
     * 1. 检查脚本目录是否存在，或版本号是否变更
     * 2. 从模块APK中解压UnblockNeteaseMusic.zip到脚本目录
     * 3. 设置脚本文件权限
     * 4. 构建libnode.so的PATH环境变量
     *
     * @param cover 是否强制覆盖重新释放脚本
     */
    @JvmStatic
    fun initScript(context: Context, cover: Boolean) {
        val scriptDir = File(getScriptPath(context))
        neteaseContext = context

        LogUtils.i("ScriptHelper: initScript开始 - modulePath=$modulePath, scriptDir=${scriptDir.absolutePath}, cover=$cover")

        // 判断是否需要重新释放脚本
        val needExtract = cover || !scriptDir.exists()
                || BuildConfig.VERSION_CODE.toString() != ExtraHelper.getExtraDate(ExtraHelper.APP_VERSION)

        if (needExtract) {
            LogUtils.i("ScriptHelper: 需要释放脚本（cover=$cover, dirExists=${scriptDir.exists()}, versionMatch=${BuildConfig.VERSION_CODE.toString() == ExtraHelper.getExtraDate(ExtraHelper.APP_VERSION)}）")

            if (modulePath.isNullOrEmpty()) {
                LogUtils.e("ScriptHelper: modulePath为空，无法解压脚本文件！")
                return
            }

            // 先删除旧脚本目录
            if (scriptDir.exists()) {
                FileHelper.deleteDirectory(scriptDir.absolutePath)
                LogUtils.d("ScriptHelper: 已删除旧脚本目录")
            }

            // 从模块APK中解压UnblockNeteaseMusic.zip
            val zipExtracted = FileHelper.unzipFile(
                modulePath!!, getScriptPath(context), "assets", "UnblockNeteaseMusic.zip"
            )
            LogUtils.i("ScriptHelper: 解压UnblockNeteaseMusic.zip到临时文件 - ${if (zipExtracted) "成功" else "失败"}")

            if (zipExtracted) {
                val zipFile = File(getScriptPath(context) + "/UnblockNeteaseMusic.zip")
                if (zipFile.exists()) {
                    val unzipResult = FileHelper.unzipFiles(zipFile.absolutePath, getScriptPath(context))
                    LogUtils.i("ScriptHelper: 解压脚本文件 - ${if (unzipResult) "成功" else "失败"}")
                    // 解压完成后删除zip文件
                    zipFile.delete()
                } else {
                    LogUtils.e("ScriptHelper: UnblockNeteaseMusic.zip文件不存在于脚本目录")
                }
            } else {
                LogUtils.e("ScriptHelper: 从APK中解压UnblockNeteaseMusic.zip失败！")
                return
            }

            // 验证关键文件是否存在
            val appJs = File(getScriptPath(context) + "/app.js")
            LogUtils.i("ScriptHelper: app.js存在=${appJs.exists()}")

            // 设置脚本文件权限
            val auth = Command(0, false, "cd ${getScriptPath(context)}", "chmod 0777 *")
            Tools.shell(auth)
            ExtraHelper.setExtraDate(ExtraHelper.APP_VERSION, BuildConfig.VERSION_CODE)
        } else {
            LogUtils.i("ScriptHelper: 脚本已存在且版本匹配，跳过释放")
        }

        // 构建libnode.so的PATH环境变量
        if (TextUtils.isEmpty(nodeLibPath)) {
            val mp = modulePath ?: ""
            if (TextUtils.isEmpty(modulePath)) {
                LogUtils.w("ScriptHelper: modulePath为空，nodeLibPath将不可用")
                nodeLibPath = ""
            } else {
                val moduleDir = mp.substring(0, mp.lastIndexOf('/'))
                nodeLibPath = "export PATH=\$PATH:$moduleDir/lib/arm64:$mp!/lib/arm64-v8a:${context.applicationInfo.nativeLibraryDir}"
                LogUtils.i("ScriptHelper: nodeLibPath=$nodeLibPath")
            }
        }
    }

    /**
     * 采用代理模式执行UnblockNeteaseMusic（使用外部代理服务器，不启动本地脚本）
     */
    @JvmStatic
    fun startHttpProxyMode(context: Context) {
        stopScript()
        ExtraHelper.setExtraDate(ExtraHelper.SCRIPT_STATUS, "1")
        Tools.showToastOnLooper(context, "服务器代理运行成功")
        LogUtils.i("ScriptHelper: 服务器代理模式启动成功")
    }

    /**
     * 生成脚本启动命令（不含nodeLibPath前缀）
     * 根据当前用户配置（音质、Cookie、音源、端口等）构建libnode.so启动命令
     * 供startScript()执行和UI展示共用
     *
     * @return 脚本启动命令字符串
     */
    @JvmStatic
    fun getScriptCommand(): String {
        val setting = SettingHelper.getInstance()
        return String.format(
            "export ENABLE_FLAC=%s&&export MIN_BR=%s&&export QQ_COOKIE=\"%s\"&&export MIGU_COOKIE=\"%s\"&&libnode.so app.js -a 127.0.0.1 -o %s -p %s",
            setting.getSetting(SettingHelper.proxy_flac_key),
            if (setting.getSetting(SettingHelper.proxy_priority_key)) "256000" else "96000",
            setting.getQqCookie() ?: "",
            setting.getMiguCookie() ?: "",
            setting.getProxyOriginal() ?: "pyncmd kuwo",
            "${setting.getProxyPort()}:${setting.getProxyPort() + 1}"
        )
    }

    /**
     * 启动本地脚本模式
     *
     * 脚本启动流程（与dev分支一致）：
     * 1. 检查是否已有脚本进程在运行
     * 2. 如果没有运行，启动libnode.so执行app.js
     * 3. 等待"HTTP Server running"输出，表示代理服务已就绪
     * 4. 设置SCRIPT_STATUS为"1"，通知ProxyHook和EAPIHook代理已可用
     * 5. 如果脚本被kill（如系统回收），自动重启
     */
    @JvmStatic
    fun startScript() {
        val currentScriptPath = scriptPath
        val currentNodeLibPath = nodeLibPath

        if (currentScriptPath.isNullOrEmpty()) {
            LogUtils.e("ScriptHelper: scriptPath为空，请先调用initScript()")
            return
        }
        if (currentNodeLibPath.isNullOrEmpty()) {
            LogUtils.e("ScriptHelper: nodeLibPath为空，请先调用initScript()")
            return
        }

        val script = getScriptCommand()

        LogUtils.i("ScriptHelper: 启动脚本 - scriptPath=$currentScriptPath")
        LogUtils.i("ScriptHelper: 启动脚本 - script=$script")

        val START_PROXY = arrayOf(
            "node=\$(ps -ef |grep \"libnode.so app.js\" |grep -v grep)",
            "if [ ! \"\$node\" ]; then",
            "cd $currentScriptPath", "$currentNodeLibPath&&$script",
            "else",
            "echo \"RESTART\"",
            "killall -9 libnode.so >/dev/null 2>&1",
            "fi"
        )

        val start = object : Command(0, false, *START_PROXY) {
            override fun commandOutput(id: Int, line: String) {
                LogUtils.d("ScriptHelper: 脚本输出 - $line")

                if ((!line.contains("mERROR") && line.contains("Error:")) || line.contains("Port ") || line.contains("Please ")) {
                    val intent = Intent(Hook.MSG_SEND_NOTIFICATION)
                    intent.putExtra("message", line)
                    intent.putExtra("title", "脚本产生如下错误信息，若脚本因此无法运行请提issue")
                    neteaseContext?.sendBroadcast(intent)
                } else if (line.contains("HTTP Server running")) {
                    if (neteaseContext != null && ExtraHelper.getExtraDate(ExtraHelper.SCRIPT_STATUS) == "0")
                        Tools.showToastOnLooper(neteaseContext!!, "UnblockNeteaseMusic运行成功")
                    ExtraHelper.setExtraDate(ExtraHelper.SCRIPT_STATUS, "1")
                    LogUtils.i("ScriptHelper: 脚本启动成功！HTTP Server running")
                } else if (line == "Killed ") {
                    // 脚本被kill后自动重启（与dev分支一致）
                    if (SettingHelper.getInstance().getSetting(SettingHelper.proxy_master_key))
                        startScript()
                } else if (line == "RESTART") {
                    ExtraHelper.setExtraDate(ExtraHelper.SCRIPT_STATUS, "0")
                    LogUtils.w("ScriptHelper: 检测到已有脚本进程，正在重启")
                }
            }
        }

        try {
            Tools.shell(start)
            LogUtils.i("ScriptHelper: 脚本启动命令已发送")
        } catch (e: Exception) {
            LogUtils.e("ScriptHelper: 脚本启动命令执行失败 - ${e.message}")
        }
    }

    @JvmStatic
    fun stopScript() {
        LogUtils.i("ScriptHelper: 停止脚本")
        try {
            Tools.shell(Command(0, false, *STOP_PROXY))
        } catch (e: Exception) {
            LogUtils.e("ScriptHelper: 停止脚本失败 - ${e.message}")
        }
    }

    /**
     * 获取CA证书对应的SSLSocketFactory
     */
    @JvmStatic
    fun getSSLSocketFactory(context: Context): SSLSocketFactory? {
        var sslContext: SSLContext? = null
        try {
            val ca = File(getScriptPath(context) + File.separator + "ca.crt")
            if (!SettingHelper.getInstance().getSetting(SettingHelper.proxy_server_key) && ca.exists()) {
                val certificate: InputStream = FileInputStream(ca)
                val cert = CertificateFactory.getInstance("X.509").generateCertificate(certificate)
                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
                keyStore.load(null, null)
                keyStore.setCertificateEntry("ca", cert)
                val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                trustManagerFactory.init(keyStore)
                sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustManagerFactory.trustManagers, SecureRandom())
            } else {
                val trustManagers = arrayOf<TrustManager>(HTTPSTrustManager())
                try {
                    sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, trustManagers, SecureRandom())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return sslContext?.socketFactory
    }
}
