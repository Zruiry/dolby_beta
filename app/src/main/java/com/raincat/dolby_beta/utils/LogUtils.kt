/**
 * 日志工具类 - 统一管理模块日志输出
 *
 * 通过 BuildConfig.LOG_DEBUG 控制日志输出：
 * - Debug 版本：LOG_DEBUG=true，正常输出日志
 * - Release 版本：LOG_DEBUG=false，所有日志方法直接返回，不输出
 *
 * 统一 TAG 为 "dolby_beta"，调用方无需关心 TAG
 *
 * Debug 版本额外支持将日志写入文件（需在获取到 Context 后调用 init() 初始化）：
 * - 日志文件路径：{网易云外部文件目录}/dolby/dolby.log
 *   标准版：/sdcard/Android/data/com.netease.cloudmusic/files/dolby/dolby.log
 *   精简版：/sdcard/Android/data/com.netease.cloudmusic.lite/files/dolby/dolby.log
 *   荣耀版：/sdcard/Android/data/com.hihonor.cloudmusic/files/dolby/dolby.log
 * - 每次启动覆盖旧日志，避免文件无限增长
 * - 异步写入（单线程执行器），不阻塞调用线程
 * - 仅记录主进程日志，避免多进程写入冲突
 *
 * 日志拉取方式：
 *   adb pull /sdcard/Android/data/com.netease.cloudmusic/files/dolby/dolby.log D:\Project\MyProjects\Android\dolby_beta\logs\dolby.log
 */
package com.raincat.dolby_beta.utils

import android.content.Context
import android.util.Log
import com.raincat.dolby_beta.BuildConfig
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object LogUtils {

    /** 统一日志 TAG */
    private const val TAG = "dolby_beta"

    /** 文件日志是否已初始化（AtomicBoolean 保证多线程安全） */
    private val fileLogInitialized = AtomicBoolean(false)

    /** 日志文件 */
    private var logFile: File? = null

    /** 单线程执行器，用于异步写入日志文件，避免阻塞调用线程 */
    private val fileExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "LogUtils-FileWriter").apply { isDaemon = true }
    }

    /** 日期时间格式化（日志时间戳），线程安全使用 ThreadLocal */
    private val dateTimeFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    }

    /**
     * 初始化文件日志
     *
     * 在获取到 Context 后调用（通常在 Application.attachBaseContext 阶段）。
     * 初始化后，所有日志将同时输出到 logcat 和日志文件。
     *
     * 仅主进程初始化（通过进程名判断），避免多进程写入同一文件导致冲突。
     *
     * @param context 应用上下文（网易云的 Context）
     */
    fun init(context: Context) {
        if (!BuildConfig.LOG_DEBUG) return
        if (fileLogInitialized.get()) return

        // 仅记录主进程日志，子进程（如推送进程）不写入文件
        val processName = Tools.getCurrentProcessName(context)
        if (processName != context.packageName) {
            return
        }

        // CAS 保证只初始化一次
        if (!fileLogInitialized.compareAndSet(false, true)) return

        try {
            // 使用网易云的外部文件目录，无需存储权限，可通过 adb pull 拉取
            val logDir = File(context.getExternalFilesDir(null), "dolby")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            logFile = File(logDir, "dolby.log")

            // 每次启动覆盖旧日志，写入分隔线标记新启动周期
            PrintWriter(FileWriter(logFile, false), true).use { writer ->
                writer.println("========== dolby_beta 日志 - 启动时间: ${dateTimeFormat.get()!!.format(Date())} ==========")
                writer.println("进程: ${context.packageName}")
                writer.println()
            }

            // 记录初始化成功日志（此时文件日志已就绪，会同时写入文件）
            i("LogUtils: 文件日志初始化成功 - 路径=${logFile?.absolutePath}")
        } catch (e: Exception) {
            // 初始化失败，回退状态允许后续重试
            fileLogInitialized.set(false)
            Log.e(TAG, "LogUtils: 文件日志初始化失败 - ${e.message}", e)
        }
    }

    /**
     * 输出日志到文件（异步执行）
     *
     * @param level 日志级别（D/I/W/E/V）
     * @param msg 日志内容
     */
    private fun writeToFile(level: String, msg: String) {
        val file = logFile ?: return
        val timestamp = dateTimeFormat.get()!!.format(Date())
        val logLine = "$timestamp $level/$msg"

        fileExecutor.execute {
            try {
                // 以追加模式写入，autoFlush=true 确保立即刷盘
                PrintWriter(FileWriter(file, true), true).use { writer ->
                    writer.println(logLine)
                }
            } catch (e: Exception) {
                // 文件写入失败，仅输出到 logcat，不影响后续日志
                Log.e(TAG, "LogUtils: 日志文件写入失败 - ${e.message}", e)
            }
        }
    }

    /** Debug 级别日志 */
    fun d(msg: String) {
        if (BuildConfig.LOG_DEBUG) {
            Log.d(TAG, msg)
            writeToFile("D", msg)
        }
    }

    /** Info 级别日志 */
    fun i(msg: String) {
        if (BuildConfig.LOG_DEBUG) {
            Log.i(TAG, msg)
            writeToFile("I", msg)
        }
    }

    /** Warn 级别日志 */
    fun w(msg: String) {
        if (BuildConfig.LOG_DEBUG) {
            Log.w(TAG, msg)
            writeToFile("W", msg)
        }
    }

    /** Error 级别日志 */
    fun e(msg: String) {
        if (BuildConfig.LOG_DEBUG) {
            Log.e(TAG, msg)
            writeToFile("E", msg)
        }
    }

    /** Verbose 级别日志 */
    fun v(msg: String) {
        if (BuildConfig.LOG_DEBUG) {
            Log.v(TAG, msg)
            writeToFile("V", msg)
        }
    }

    /** 获取异常堆栈字符串 */
    fun getStackTraceString(e: Throwable): String {
        return Log.getStackTraceString(e)
    }
}
