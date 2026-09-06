/**
 * 文件操作帮助类 - 提供文件删除、读取、写入、解压等操作
 *
 */
package com.raincat.dolby_beta.helper

import java.io.*
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
                val inputStream: InputStream = FileInputStream(file)
                val inputStreamReader = InputStreamReader(inputStream)
                val bufferedReader = BufferedReader(inputStreamReader)
                var line: String?
                while (bufferedReader.readLine().also { line = it } != null) {
                    list.add(line!!)
                }
                inputStream.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return list
    }

    /**
     * 写入内容到一个文件
     */
    @JvmStatic
    fun writeFileFromSD(path: String, content: List<String>) {
        var out: BufferedWriter? = null
        try {
            val file = File(path)
            out = BufferedWriter(OutputStreamWriter(FileOutputStream(file, false), "utf-8"))
            for (s in content) {
                out.write(s)
                out.write("\n")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                out?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 解压一个文件（按文件名匹配）
     */
    @JvmStatic
    fun unzipFile(zipFileString: String, outPathString: String, fileParentName: String, fileName: String): Boolean {
        try {
            val outPath = File(outPathString)
            if (!outPath.exists()) outPath.mkdirs()

            val zipFile = ZipFile(zipFileString)
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry: ZipEntry = entries.nextElement()
                if (entry.name.contains(fileParentName) && entry.name.contains(fileName) && !entry.isDirectory) {
                    val inputStream = zipFile.getInputStream(entry)
                    val dstFile = File("$outPathString/$fileName")
                    val fos = FileOutputStream(dstFile)
                    val buffer = ByteArray(8192)
                    var len: Int
                    while (inputStream.read(buffer).also { len = it } != -1) {
                        fos.write(buffer, 0, len)
                    }
                    fos.flush()
                    fos.close()
                    inputStream.close()
                    break
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
        return true
    }

    /**
     * 解压整个zip
     */
    @JvmStatic
    fun unzipFiles(zipFileString: String, outPathString: String): Boolean {
        try {
            val outPath = File(outPathString)
            if (!outPath.exists()) outPath.mkdirs()

            val zipFile = ZipFile(zipFileString)
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry: ZipEntry = entries.nextElement()
                if (entry.isDirectory) {
                    var szName = entry.name
                    szName = szName.substring(0, szName.length - 1)
                    val folder = File(outPathString + File.separator + szName)
                    folder.mkdirs()
                } else {
                    val inputStream = zipFile.getInputStream(entry)
                    val dstFile = File("$outPathString/${entry.name}")
                    val fos = FileOutputStream(dstFile)
                    val buffer = ByteArray(8192)
                    var len: Int
                    while (inputStream.read(buffer).also { len = it } != -1) {
                        fos.write(buffer, 0, len)
                    }
                    fos.flush()
                    fos.close()
                    inputStream.close()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
        return true
    }
}
