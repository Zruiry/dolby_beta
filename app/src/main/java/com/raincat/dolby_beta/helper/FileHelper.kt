/**
 * 文件操作帮助类 - 提供文件删除、读取、写入、解压等操作
 *
 */
package com.raincat.dolby_beta.helper

import com.raincat.dolby_beta.utils.LogUtils
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object FileHelper {

    /**
     * 删除文件夹以及目录下的文件
     *
     * @param filePath 被删除目录的文件路径
     * @return 目录删除成功返回true，否则返回false
     */
    @JvmStatic
    fun deleteDirectory(filePath: String?): Boolean {
        if (filePath.isNullOrEmpty()) return false
        var path = filePath
        // 如果filePath不以文件分隔符结尾，自动添加文件分隔符
        if (!path.endsWith(File.separator)) {
            path = path + File.separator
        }
        val dirFile = File(path)
        try {
            if (!dirFile.exists() || !dirFile.isDirectory) return false
            val files = dirFile.listFiles() ?: return false
            for (file in files) {
                if (file.isFile) {
                    if (!deleteFile(file.absolutePath)) return false
                } else {
                    if (!deleteDirectory(file.absolutePath)) return false
                }
            }
        } catch (e: Exception) {
            return false
        }
        // 删除当前空目录
        return dirFile.delete()
    }

    /**
     * 删除单个文件
     */
    @JvmStatic
    fun deleteFile(filePath: String): Boolean {
        val file = File(filePath)
        return file.isFile && file.exists() && file.delete()
    }

    /**
     * 从SD卡中读取一个文件
     */
    @JvmStatic
    fun readFileFromSD(path: String): List<String> {
        val list = mutableListOf<String>()
        val file = File(path)
        if (!file.isDirectory) {
            try {
                FileInputStream(file).bufferedReader().use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        list.add(line)
                    }
                }
            } catch (e: Exception) {
                LogUtils.e("FileHelper.readFileFromSD: 读取失败 - ${e.message}")
            }
        }
        return list
    }

    /**
     * 写入内容到一个文件
     */
    @JvmStatic
    fun writeFileFromSD(path: String, content: List<String>) {
        try {
            val file = File(path)
            BufferedWriter(OutputStreamWriter(FileOutputStream(file, false), Charsets.UTF_8)).use { out ->
                for (s in content) {
                    out.write(s)
                    out.write("\n")
                }
            }
        } catch (e: Exception) {
            LogUtils.e("FileHelper.writeFileFromSD: 写入失败 - ${e.message}")
        }
    }

    /**
     * 解压一个文件（按文件名匹配）
     */
    @JvmStatic
    fun unzipFile(zipFileString: String, outPathString: String, fileParentName: String, fileName: String): Boolean {
        return try {
            val outPath = File(outPathString)
            if (!outPath.exists()) outPath.mkdirs()

            ZipFile(zipFileString).use { zipFile ->
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry: ZipEntry = entries.nextElement()
                    if (entry.name.contains(fileParentName) && entry.name.contains(fileName) && !entry.isDirectory) {
                        zipFile.getInputStream(entry).use { input ->
                            FileOutputStream(File("$outPathString/$fileName")).use { fos ->
                                val buffer = ByteArray(8192)
                                var len: Int
                                while (input.read(buffer).also { len = it } != -1) {
                                    fos.write(buffer, 0, len)
                                }
                                fos.flush()
                            }
                        }
                        break
                    }
                }
            }
            true
        } catch (e: IOException) {
            LogUtils.e("FileHelper.unzipFile: 解压失败 - ${e.message}")
            false
        }
    }

    /**
     * 解压整个zip
     */
    @JvmStatic
    fun unzipFiles(zipFileString: String, outPathString: String): Boolean {
        return try {
            val outPath = File(outPathString)
            if (!outPath.exists()) outPath.mkdirs()

            ZipFile(zipFileString).use { zipFile ->
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry: ZipEntry = entries.nextElement()
                    if (entry.isDirectory) {
                        val szName = entry.name.substring(0, entry.name.length - 1)
                        File(outPathString + File.separator + szName).mkdirs()
                    } else {
                        zipFile.getInputStream(entry).use { input ->
                            FileOutputStream(File("$outPathString/${entry.name}")).use { fos ->
                                val buffer = ByteArray(8192)
                                var len: Int
                                while (input.read(buffer).also { len = it } != -1) {
                                    fos.write(buffer, 0, len)
                                }
                                fos.flush()
                            }
                        }
                    }
                }
            }
            true
        } catch (e: IOException) {
            LogUtils.e("FileHelper.unzipFiles: 解压失败 - ${e.message}")
            false
        }
    }
}
