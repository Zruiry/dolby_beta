/**
 * 工具类 - 提供进程名获取、Toast显示、dp转px、时间戳计算、Shell命令执行等通用工具方法
 *
 */
package com.raincat.dolby_beta.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.stericson.RootShell.execution.Command
import com.stericson.RootTools.RootTools
import java.io.IOException
import java.util.Calendar
import java.util.concurrent.TimeoutException

object Tools {

    /**
     * 获取当前进程名称
     */
    @JvmStatic
    fun getCurrentProcessName(context: Context): String {
        val pid = android.os.Process.myPid()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        activityManager?.runningAppProcesses?.forEach { processInfo ->
            if (processInfo.pid == pid) {
                return processInfo.processName
            }
        }
        return ""
    }

    /**
     * 在主线程显示Toast
     */
    @JvmStatic
    fun showToastOnLooper(context: Context, message: String) {
        try {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * dp转px
     */
    @JvmStatic
    fun dp2px(context: Context, dpValue: Float): Int {
        val scale = context.resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    /**
     * 获取今天0点的时间戳
     */
    @JvmStatic
    fun getTodayStartTime(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        return calendar.time.time
    }

    /**
     * 执行ADB/Shell命令
     * 使用RootTools获取非root shell执行命令
     */
    @JvmStatic
    fun shell(command: Command) {
        try {
            RootTools.closeAllShells()
            RootTools.getShell(false).add(command)
        } catch (e: TimeoutException) {
            LogUtils.e("Tools.shell: 执行超时 - ${e.message}")
            e.printStackTrace()
        } catch (e: com.stericson.RootShell.exceptions.RootDeniedException) {
            LogUtils.e("Tools.shell: Root权限被拒绝 - ${e.message}")
            e.printStackTrace()
        } catch (e: IOException) {
            LogUtils.e("Tools.shell: IO异常 - ${e.message}")
            e.printStackTrace()
        } catch (e: Exception) {
            LogUtils.e("Tools.shell: 执行异常 - ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
    }
}
