/**
 * 类加载帮助 - 仅保留音源代理所需的内部类
 * 保留：Cookie, OKHttp3Response, OKHttp3Header, HttpResponse, HttpUrl, HttpParams, HttpInterceptor
 *
 * 从Legacy API迁移到Modern libxposed API
 * 旧版：XposedHelpers.findClassIfExists/findMethodsByExactParameters/callMethod/callStaticMethod
 * 新版：Java反射 ClassLoader.loadClass/Method.invoke/Field.get
 *
 */
package com.raincat.dolby_beta.helper

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.annimon.stream.Stream
import com.raincat.dolby_beta.utils.LogUtils
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.dexbacked.DexBackedDexFile
import org.jf.dexlib2.iface.MultiDexContainer
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.Serializable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.NoSuchElementException
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object ClassHelper {

    /** 混淆包名匹配正则：根包名为1-3个小写字母+数字（如 dl0、ek1、ab 等），不同版本会变化 */
    private val obfuscatePackagePattern = Pattern.compile("^[a-z][a-z0-9]{0,2}$")
    /** DEX 缓存 schema 版本号：扫描逻辑变更时递增，使旧缓存自动失效 */
    private const val CACHE_SCHEMA_VERSION = 2

    /** 类加载器 */
    private var classLoader: ClassLoader? = null
    /** dex缓存 */
    private var classCacheList: MutableList<String>? = null
    /** dex缓存路径 */
    private var classCachePath: String? = null
    /** 网易云版本 */
    private var versionCode = 0

    @JvmStatic
    @Synchronized
    fun getCacheClassList(context: Context, version: Int, listener: OnCacheClassListener) {
        if (classLoader == null) {
            classLoader = context.classLoader
            versionCode = version
            val cacheFile = context.getExternalFilesDir(null)!!
            if (cacheFile.exists() || cacheFile.mkdirs())
                classCachePath = cacheFile.path
        }
        if (classCacheList == null) {
            classCacheList = if (SettingHelper.getInstance().isEnable(SettingHelper.dex_key))
                FileHelper.readFileFromSD(classCachePath + File.separator + "class-$version-v$CACHE_SCHEMA_VERSION").toMutableList()
            else
                mutableListOf()
            if (classCacheList!!.isEmpty()) {
                Thread { getCacheClassByZip(context, version, listener) }.start()
            } else {
                listener.onGet()
            }
        } else {
            listener.onGet()
        }
    }

    private fun getCacheClassByZip(context: Context, version: Int, listener: OnCacheClassListener) {
        try {
            // 优先使用 applicationInfo.sourceDir（原始 APK 路径）
            // 避免 Tinker 热修复场景下 packageResourcePath 返回补丁包路径（不含完整 DEX）
            val appInstallFile = File(context.applicationInfo.sourceDir)
            LogUtils.i("ClassHelper: 开始DEX扫描 - APK路径=${appInstallFile.path}")
            val zip = ZipFile(appInstallFile).entries()
            var dexCount = 0
            var classCount = 0
            while (zip.hasMoreElements()) {
                val dexInZip: ZipEntry = zip.nextElement()
                if (dexInZip.name.startsWith("classes") && dexInZip.name.endsWith(".dex")) {
                    dexCount++
                    @Suppress("UNCHECKED_CAST")
                    val dexEntry = DexFileFactory.loadDexEntry(appInstallFile, dexInZip.name, true, null)
                            as MultiDexContainer.DexEntry<DexBackedDexFile>
                    val dexFile = dexEntry.dexFile
                    for (classDef in dexFile.classes) {
                        var classType = classDef.type
                        // 扫描 com/netease/cloudmusic、okhttp3 开头的类
                        if (classType.contains("com/netease/cloudmusic") || classType.contains("okhttp3")) {
                            classType = classType.substring(1, classType.length - 1).replace("/", ".")
                            classCacheList!!.add(classType)
                            classCount++
                        } else {
                            // 扫描混淆包名下的类（根包名为1-3个小写字母+数字，如 dl0、ek1、ab 等）
                            // 混淆包名在不同版本会变化，通过正则匹配避免硬编码
                            val path = classType.substring(1, classType.length - 1)
                            val rootPackage = path.substringBefore("/", "")
                            if (obfuscatePackagePattern.matcher(rootPackage).matches()) {
                                classCacheList!!.add(path.replace("/", "."))
                                classCount++
                            }
                        }
                    }
                }
            }
            LogUtils.i("ClassHelper: DEX扫描完成 - dex文件数=$dexCount, 扫描类数=$classCount, 总缓存数=${classCacheList!!.size}")
        } catch (e: Exception) {
            LogUtils.e("ClassHelper: DEX扫描异常 - ${e.message}")
            e.printStackTrace()
        } finally {
            FileHelper.writeFileFromSD(classCachePath + File.separator + "class-$version-v$CACHE_SCHEMA_VERSION", classCacheList!!)
            listener.onGet()
        }
    }

    interface OnCacheClassListener {
        fun onGet()
    }

    @JvmStatic
    fun getFilteredClasses(pattern: Pattern, comparator: Comparator<String>?): List<String> {
        val cache = classCacheList ?: return emptyList()
        val list = Stream.of(*cache.toTypedArray())
            .filter { s -> pattern.matcher(s).find() }
            .toList()
        if (comparator != null) {
            Collections.sort(list, comparator)
        }
        return list
    }

    /**
     * 通过类名加载类（替代XposedHelpers.findClassIfExists的包装方法）
     * 加载失败时回退到加载NeteaseMusicApplication类，确保Stream.map不返回null
     */
    private fun getClassByXposed(className: String): Class<*>? {
        var clazz = findClassIfExists(className, classLoader!!)
        if (clazz == null)
            clazz = findClassIfExists("com.netease.cloudmusic.NeteaseMusicApplication", classLoader!!)
        return clazz
    }

    /**
     * 安全加载类（替代XposedHelpers.findClassIfExists）
     */
    @JvmStatic
    fun findClassIfExists(className: String, classLoader: ClassLoader): Class<*>? {
        return try {
            classLoader.loadClass(className)
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    /**
     * 按精确参数类型查找方法（替代XposedHelpers.findMethodsByExactParameters）
     */
    private fun findMethodsByExactParameters(clazz: Class<*>, returnType: Class<*>?, vararg parameterTypes: Class<*>): List<Method> {
        val result = mutableListOf<Method>()
        for (method in clazz.declaredMethods) {
            val methodParamTypes = method.parameterTypes
            if (methodParamTypes.size != parameterTypes.size) continue
            var paramsMatch = true
            for (i in parameterTypes.indices) {
                if (methodParamTypes[i] != parameterTypes[i]) {
                    paramsMatch = false
                    break
                }
            }
            if (!paramsMatch) continue
            if (returnType != null && method.returnType != returnType) continue
            result.add(method)
        }
        return result
    }

    /**
     * 按精确类型查找字段（替代XposedHelpers.findFirstFieldByExactType）
     */
    @Throws(NoSuchFieldException::class)
    private fun findFirstFieldByExactType(clazz: Class<*>, type: Class<*>): Field {
        for (field in clazz.declaredFields) {
            if (field.type == type) {
                return field
            }
        }
        throw NoSuchFieldException("Field of type ${type.name} not found in ${clazz.name}")
    }

    /**
     * Cookie获取 - 代理请求时需要携带Cookie
     */
    object Cookie {
        private var clazz: Class<*>? = null
        private var abstractClazz: Class<*>? = null

        @JvmStatic
        fun getCookie(context: Context): String {
            if (clazz == null) {
                val pattern: Pattern = when {
                    versionCode < 154 -> Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z]\\.[a-z]\\.[a-z]\\.[a-z]$")
                    versionCode < 8008050 -> Pattern.compile("^com\\.netease\\.cloudmusic\\.network\\.[a-z]+\\.[a-z]+\\.[a-z]+$")
                    else -> Pattern.compile("^com\\.netease\\.cloudmusic\\.network\\.cookie\\.store\\.[a-zA-Z0-9]{1,25}$")
                }

                val list = getFilteredClasses(pattern, null)

                try {
                    abstractClazz = Stream.of(list)
                        .map { getClassByXposed(it) }
                        .filter { it != null }
                        .map { it!! }
                        .filter { c -> Modifier.isPublic(c.modifiers) }
                        .filter { c -> c.superclass == Any::class.java }
                        .filter { c -> Stream.of(*c.declaredFields).anyMatch { m -> m.type == ConcurrentHashMap::class.java } }
                        .filter { c -> Stream.of(*c.declaredFields).anyMatch { m -> m.type == SharedPreferences::class.java } }
                        .filter { c -> Stream.of(*c.declaredFields).anyMatch { m -> m.type == Long::class.javaPrimitiveType } }
                        .findFirst()
                        .orElse(null)

                    if (versionCode >= 154 && abstractClazz != null) {
                        val absClazz = abstractClazz!!
                        clazz = Stream.of(list)
                            .map { getClassByXposed(it) }
                            .filter { it != null }
                            .map { it!! }
                            .filter { c -> Modifier.isPublic(c.modifiers) }
                            .filter { c -> !Modifier.isInterface(c.modifiers) }
                            .filter { c -> c.superclass == absClazz }
                            .findFirst()
                            .orElse(null)
                    } else {
                        clazz = abstractClazz
                    }
                } catch (e: NoSuchElementException) {
                    LogUtils.e("ClassHelper: 找不到Cookie核心类")
                }
            }

            var cookieString: Any? = null
            if (versionCode >= 154 && clazz != null && abstractClazz != null) {
                val cookieMethod = findMethodsByExactParameters(clazz!!, clazz).getOrNull(0) ?: return "MUSIC_U="
                val cookie = try {
                    cookieMethod.invoke(null)
                } catch (e: Exception) {
                    LogUtils.e("ClassHelper: Cookie静态方法调用失败 - ${e.message}")
                    return "MUSIC_U="
                }
                for (method in findMethodsByExactParameters(abstractClazz!!, String::class.java)) {
                    if (method.typeParameters.isEmpty() && method.modifiers == Modifier.PUBLIC) {
                        try {
                            cookieString = method.invoke(cookie)
                        } catch (e: Exception) {
                            LogUtils.e("ClassHelper: Cookie实例方法调用失败 - ${e.message}")
                        }
                    }
                }
            } else if (clazz != null) {
                val cookieMethod = findMethodsByExactParameters(clazz!!, String::class.java).getOrNull(0) ?: return "MUSIC_U="
                try {
                    cookieString = cookieMethod.invoke(null)
                } catch (e: Exception) {
                    LogUtils.e("ClassHelper: Cookie静态方法调用失败 - ${e.message}")
                }
            }

            return "MUSIC_U=$cookieString"
        }
    }

    /**
     * OkHttp3 Response封装 - EAPIHook旧版方式需要
     */
    class OKHttp3Response(private val okHttp3Response: Any) {

        companion object {
            private var clazz: Class<*>? = null

            fun getClazz(context: Context): Class<*>? {
                if (clazz == null) {
                    val pattern = Pattern.compile("^okhttp3\\.[a-zA-Z]{1,8}$")
                    val list = getFilteredClasses(pattern, Collections.reverseOrder())

                    try {
                        clazz = Stream.of(list)
                            .map { getClassByXposed(it) }
                            .filter { it != null }
                            .map { it!! }
                            .filter { c -> !Modifier.isAbstract(c.modifiers) }
                            .filter { c -> Modifier.isPublic(c.modifiers) }
                            .filter { c -> c.interfaces.size == 1 }
                            .filter { c -> c.interfaces[0] == Closeable::class.java }
                            .filter { c -> Stream.of(*c.declaredFields).anyMatch { m -> m.type == Int::class.javaPrimitiveType } }
                            .filter { c -> Stream.of(*c.declaredFields).anyMatch { m -> m.type == String::class.java } }
                            .filter { c -> Stream.of(*c.declaredFields).anyMatch { m -> m.type == Long::class.javaPrimitiveType } }
                            .findFirst()
                            .orElse(null)
                    } catch (e: Exception) {
                        LogUtils.e("ClassHelper: 找不到OKHttp3Response核心类，音源代理功能可能失效")
                    }
                }
                return clazz
            }
        }

        @Throws(IllegalAccessException::class, NullPointerException::class)
        fun getHeadersObject(context: Context): Any {
            val fields = getClazz(context)!!.declaredFields
            val dataField = Stream.of(*fields)
                .filter { f -> f.type == OKHttp3Header.getClazz(context) }
                .filter { f -> Stream.of(*f.type.declaredFields).anyMatch { pf -> pf.type == Array<String>::class.java } }
                .findFirst().get()

            dataField.isAccessible = true
            return dataField.get(okHttp3Response)!!
        }
    }

    /**
     * OkHttp3 Header封装 - EAPIHook旧版方式需要
     */
    class OKHttp3Header(private val okHttp3Header: Any) {

        companion object {
            private var clazz: Class<*>? = null

            fun getClazz(context: Context): Class<*>? {
                if (clazz == null) {
                    val pattern = Pattern.compile("^okhttp3\\.[a-zA-Z]{1,7}$")
                    val list = getFilteredClasses(pattern, Collections.reverseOrder())

                    try {
                        clazz = Stream.of(list)
                            .map { getClassByXposed(it) }
                            .filter { it != null }
                            .map { it!! }
                            .filter { c -> !Modifier.isAbstract(c.modifiers) }
                            .filter { c -> Modifier.isPublic(c.modifiers) }
                            .filter { c -> Stream.of(*c.declaredFields).anyMatch { m -> m.type == Array<String>::class.java } }
                            .findFirst()
                            .orElse(null)
                    } catch (e: Exception) {
                        LogUtils.e("ClassHelper: 找不到OKHttp3Header核心类，音源代理功能可能失效")
                    }
                }
                return clazz
            }
        }

        @Throws(IllegalAccessException::class, NullPointerException::class)
        fun getHeaders(context: Context): Array<String> {
            val fields = getClazz(context)!!.declaredFields
            val dataField = Stream.of(*fields)
                .filter { f -> f.type == Array<String>::class.java }
                .findFirst().get()

            dataField.isAccessible = true
            return dataField.get(okHttp3Header) as Array<String>
        }
    }

    /**
     * 获取请求返回 - EAPIHook旧版方式需要
     */
    class HttpResponse(private val httpResponse: Any) {

        companion object {
            private var clazz: Class<*>? = null
            private var getResultMethod: Method? = null

            fun getClazz(context: Context): Class<*>? {
                if (clazz == null) {
                    val pattern: Pattern = if (versionCode < 154)
                        Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z]\\.[a-z]\\.[a-z]\\.[a-z]$")
                    else
                        Pattern.compile("^com\\.netease\\.cloudmusic\\.network\\.[a-z]+\\.[a-z]+\\.[a-z]+$")
                    val list = getFilteredClasses(pattern, Collections.reverseOrder())

                    try {
                        clazz = Stream.of(list)
                            .map { getClassByXposed(it) }
                            .filter { it != null }
                            .map { it!! }
                            .filter { c -> !Modifier.isAbstract(c.modifiers) }
                            .filter { c -> Modifier.isPublic(c.modifiers) }
                            .filter { c -> c.superclass == Any::class.java }
                            .filter { c -> Stream.of(*c.declaredFields).anyMatch { m -> m.type == OKHttp3Response.getClazz(context) } }
                            .findFirst()
                            .orElse(null)
                    } catch (e: Exception) {
                        LogUtils.e("ClassHelper: 找不到HttpResponse核心类，音源代理功能可能失效")
                    }
                }
                return clazz
            }

            fun getResultMethod(context: Context): Method? {
                if (getResultMethod == null) {
                    try {
                        val methodList = getClazz(context)?.declaredMethods?.toList() ?: return null
                        getResultMethod = Stream.of(methodList)
                            .filter { m -> m.exceptionTypes.size == 2 }
                            .findFirst()
                            .orElse(null)
                    } catch (e: Exception) {
                        LogUtils.e("ClassHelper: 找不到getResultMethod，音源代理功能可能失效")
                    }
                }
                return getResultMethod
            }
        }

        @Throws(IllegalAccessException::class, NullPointerException::class)
        fun getResponseObject(context: Context): Any {
            val fields = getClazz(context)!!.declaredFields
            val dataField = Stream.of(*fields)
                .filter { f -> Stream.of(*f.type.interfaces).anyMatch { i -> i == Closeable::class.java } }
                .filter { f -> Stream.of(*f.type.declaredFields).anyMatch { pf -> pf.type.name.startsWith("okhttp3") } }
                .findFirst().get()

            dataField.isAccessible = true
            return dataField.get(httpResponse)!!
        }

        @Throws(IllegalAccessException::class, NullPointerException::class)
        fun getEapi(context: Context): Any {
            val fields = getClazz(context)!!.declaredFields
            val dataField = Stream.of(*fields)
                .filter { c -> Modifier.isAbstract(c.type.modifiers) }
                .filter { c -> c.type.superclass == Any::class.java }
                .filter { c -> Stream.of(*c.type.declaredFields).anyMatch { m -> m.type.name.startsWith("okhttp3") } }
                .findFirst().get()

            dataField.isAccessible = true
            return dataField.get(httpResponse)!!
        }
    }

    /**
     * 获取请求URL - EAPIHook旧版方式需要
     */
    object HttpUrl {
        private var clazz: Class<*>? = null

        fun getClazz(context: Context): Class<*>? {
            if (clazz == null) {
                val pattern: Pattern = if (versionCode < 154)
                    Pattern.compile("^com\\.netease\\.cloudmusic\\.[a-z]\\.[a-z]\\.[a-z]\\.[a-z]$")
                else
                    Pattern.compile("^com\\.netease\\.cloudmusic\\.network\\.[a-z]+\\.[a-z]+\\.[a-z]+$")
                val list = getFilteredClasses(pattern, Collections.reverseOrder())

                try {
                    clazz = Stream.of(list)
                        .map { getClassByXposed(it) }
                        .filter { it != null }
                        .map { it!! }
                        .filter { c -> !Modifier.isAbstract(c.modifiers) }
                        .filter { c -> Modifier.isPublic(c.modifiers) }
                        .filter { c -> c.superclass == Any::class.java }
                        .filter { c -> Stream.of(*c.declaredFields).anyMatch { m -> m.type.name.startsWith("okhttp3") } }
                        .filter { c -> Stream.of(*c.declaredFields).anyMatch { m -> m.type == Int::class.javaPrimitiveType } }
                        .findFirst()
                        .orElse(null)
                } catch (e: Exception) {
                    LogUtils.e("ClassHelper: 找不到HttpUrl核心类，音源代理功能可能失效")
                }
            }
            return clazz
        }

        fun getUri(context: Context, eapi: Any): Uri {
            try {
                val fields = eapi.javaClass.declaredFields
                val dataField = Stream.of(*fields)
                    .filter { f -> Stream.of(*f.type.declaredFields).anyMatch { pf -> pf.type.name.startsWith("okhttp3") } }
                    .filter { f -> Stream.of(*f.type.declaredFields).anyMatch { pf -> pf.type == Int::class.javaPrimitiveType } }
                    .findFirst().orElse(null) ?: return Uri.EMPTY

                dataField.isAccessible = true
                val urlObj = dataField.get(eapi) ?: return Uri.EMPTY

                val urlFields = urlObj.javaClass.declaredFields
                val urlField = Stream.of(*urlFields)
                    .filter { f -> f.type == String::class.java }
                    .findFirst().orElse(null) ?: return Uri.EMPTY

                urlField.isAccessible = true
                val url = urlField.get(urlObj) as? String ?: return Uri.EMPTY
                return Uri.parse(url)
            } catch (e: Exception) {
                return Uri.EMPTY
            }
        }
    }

    /**
     * 获取请求参数 - EAPIHook旧版方式需要
     */
    object HttpParams {
        fun getParams(context: Context, eapi: Any): LinkedHashMap<String, String> {
            val paramsMap = LinkedHashMap<String, String>()
            try {
                val fields = eapi.javaClass.declaredFields
                val dataField = Stream.of(*fields)
                    .filter { f -> f.type == LinkedHashMap::class.java }
                    .findFirst().orElse(null) ?: return paramsMap

                dataField.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val rawMap = dataField.get(eapi) as? LinkedHashMap<String, Any> ?: return paramsMap
                for (key in rawMap.keys) {
                    val value = rawMap[key]
                    paramsMap[key] = value?.toString() ?: ""
                }
            } catch (e: Exception) {
                // 提取参数失败
            }
            return paramsMap
        }
    }

    /**
     * CDN拦截器方法获取
     */
    object HttpInterceptor {
        fun getMethodList(context: Context): List<Method>? {
            try {
                val pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.network\\.[a-z]+\\.[a-z]+\$")
                val list = getFilteredClasses(pattern, null)

                val interceptorClass = Stream.of(list)
                    .map { getClassByXposed(it) }
                    .filter { it != null }
                    .map { it!! }
                    .filter { c -> !Modifier.isAbstract(c.modifiers) }
                    .filter { c -> Stream.of(*c.declaredMethods).anyMatch { m -> m.name == "intercept" } }
                    .filter { c -> Stream.of(*c.declaredMethods).anyMatch { m -> m.name == "a" || m.name == "b" } }
                    .findFirst()
                    .orElse(null) ?: return null

                val methods = mutableListOf<Method>()
                for (m in interceptorClass.declaredMethods) {
                    if (m.name == "a" || m.name == "b") {
                        val paramTypes = m.parameterTypes
                        if (paramTypes.size == 3) {
                            methods.add(m)
                        }
                    }
                }
                return methods
            } catch (e: Exception) {
                LogUtils.e("ClassHelper: HttpInterceptor方法查找失败 - ${e.message}")
                return null
            }
        }
    }

    /**
     * 底部Tab管理类查找（完全通过特征匹配，不依赖混淆类名和方法名）
     *
     * 通用特征：
     * 1. 实现 java.io.Serializable 接口
     * 2. 包含返回 List 的无参方法（获取Tab列表）
     * 3. 包含 (List): void 方法（保存Tab列表）
     *
     * 经反编译源码验证，9.5.25/9.5.30/9.5.35 中 BottomTabManager 方法名一致：
     * - 9.5.25~9.5.30: dl0.g，h() 返回 List，u(List) 返回 void
     * - 9.5.35: dl0.h，h() 返回 List，u(List) 返回 void
     * 但为兼容未来版本，特征匹配不依赖方法名
     *
     * 使用正则匹配混淆包名下的类（根包名1-3个小写字母+数字），通过 DEX 缓存进行特征筛选
     */
    object BottomTabManager {
        private var clazz: Class<*>? = null

        @JvmStatic
        fun getClazz(context: Context): Class<*>? {
            if (clazz != null) return clazz
            try {
                // 正则匹配混淆包名下的类（不硬编码包名，兼容不同版本）
                val pattern = Pattern.compile("^[a-z][a-z0-9]{0,2}\\.[a-z]{1,3}$")
                val list = getFilteredClasses(pattern, null)
                LogUtils.i("ClassHelper: BottomTabManager 特征匹配扫描混淆包类，共 ${list.size} 个")
                // 调试：打印前20个类名，确认 dl0.g/dl0.h 是否在缓存中
                list.take(20).forEach { LogUtils.i("ClassHelper: BottomTabManager 候选类 - $it") }
                clazz = Stream.of(list)
                    .map { getClassByXposed(it) }
                    .filter { it != null }
                    .map { it!! }
                    // 特征1：直接实现 java.io.Serializable 接口（非继承）
                    // BottomTabManager 直接 implements Serializable，而误匹配类如 xk.g 通过继承 MemberPushOption 间接实现
                    .filter { c -> c.interfaces.any { it == Serializable::class.java } }
                    // 特征2：包含返回 List 的无参方法（获取Tab列表）
                    .filter { c -> Stream.of(*c.declaredMethods).anyMatch { m ->
                        m.parameterTypes.isEmpty() &&
                        List::class.java.isAssignableFrom(m.returnType)
                    } }
                    // 特征3：包含 (List): void 方法（保存Tab列表）
                    .filter { c -> Stream.of(*c.declaredMethods).anyMatch { m ->
                        m.returnType == Void::class.javaPrimitiveType &&
                        m.parameterTypes.size == 1 && List::class.java.isAssignableFrom(m.parameterTypes[0])
                    } }
                    .findFirst()
                    .orElse(null)
                if (clazz != null) {
                    LogUtils.i("ClassHelper: 特征匹配找到BottomTabManager: ${clazz!!.name}")
                } else {
                    LogUtils.e("ClassHelper: 特征匹配未找到BottomTabManager")
                }
            } catch (e: Exception) {
                LogUtils.e("ClassHelper: BottomTabManager查找失败 - ${e.message}")
            }
            return clazz
        }
    }

    /**
     * Tab索引管理类查找（完全通过特征匹配，不依赖混淆类名和方法名）
     *
     * 通用特征：
     * 1. 继承 androidx.lifecycle.ViewModel
     * 2. 包含 (String): int 方法（tabCode转position）
     * 3. 包含 (int): String 方法（position转tabCode）
     *
     * 经反编译源码验证，9.5.25/9.5.30/9.5.35 中 TabIndexManager 仅有1对互逆方法：
     * - 9.5.25~9.5.30: s4(String):int 和 p4(int):String（dl0.m）
     * - 9.5.35: t4(String):int 和 q4(int):String（dl0.n）
     * 该类中 (String):int 和 (int):String 方法各只有1个，特征唯一
     *
     * 使用正则匹配混淆包名下的类（根包名1-3个小写字母+数字），通过 DEX 缓存进行特征筛选
     */
    object TabIndexManager {
        private var clazz: Class<*>? = null

        @JvmStatic
        fun getClazz(context: Context): Class<*>? {
            if (clazz != null) return clazz
            try {
                // 正则匹配混淆包名下的类（不硬编码包名，兼容不同版本）
                val pattern = Pattern.compile("^[a-z][a-z0-9]{0,2}\\.[a-z]{1,3}$")
                val list = getFilteredClasses(pattern, null)
                val viewModelClass = findClassIfExists("androidx.lifecycle.ViewModel", context.classLoader)
                    ?: return null
                clazz = Stream.of(list)
                    .map { getClassByXposed(it) }
                    .filter { it != null }
                    .map { it!! }
                    .filter { c -> c.superclass == viewModelClass }
                    // 特征2：包含 (String): int 方法（tabCode转position）
                    .filter { c -> Stream.of(*c.declaredMethods).anyMatch { m ->
                        m.returnType == Int::class.javaPrimitiveType &&
                        m.parameterTypes.size == 1 && m.parameterTypes[0] == String::class.java
                    } }
                    // 特征3：包含 (int): String 方法（position转tabCode）
                    .filter { c -> Stream.of(*c.declaredMethods).anyMatch { m ->
                        m.returnType == String::class.java &&
                        m.parameterTypes.size == 1 && m.parameterTypes[0] == Int::class.javaPrimitiveType
                    } }
                    .findFirst()
                    .orElse(null)
                if (clazz != null) {
                    LogUtils.i("ClassHelper: 特征匹配找到TabIndexManager: ${clazz!!.name}")
                } else {
                    LogUtils.e("ClassHelper: 特征匹配未找到TabIndexManager")
                }
            } catch (e: Exception) {
                LogUtils.e("ClassHelper: TabIndexManager查找失败 - ${e.message}")
            }
            return clazz
        }
    }

    /**
     * Fragment适配器类查找（完全通过特征匹配，不依赖混淆类名）
     *
     * 特征：
     * 1. 继承 androidx.viewpager2.adapter.FragmentStateAdapter
     * 2. 包含 createFragment(int): Fragment 方法
     */
    object FragmentPagerAdapter {
        private var clazz: Class<*>? = null

        @JvmStatic
        fun getClazz(context: Context): Class<*>? {
            if (clazz != null) return clazz
            try {
                // 特征匹配：在 com.netease.cloudmusic.adapter 包下查找
                val pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.adapter\\.[a-z]{1,3}$")
                val list = getFilteredClasses(pattern, null)
                val fragmentStateAdapterClass = findClassIfExists(
                    "androidx.viewpager2.adapter.FragmentStateAdapter", context.classLoader
                ) ?: return null
                val fragmentClass = findClassIfExists("androidx.fragment.app.Fragment", context.classLoader)
                    ?: return null
                clazz = Stream.of(list)
                    .map { getClassByXposed(it) }
                    .filter { it != null }
                    .map { it!! }
                    .filter { c -> fragmentStateAdapterClass.isAssignableFrom(c) }
                    .filter { c -> Stream.of(*c.declaredMethods).anyMatch { m ->
                        m.name == "createFragment" && m.returnType == fragmentClass &&
                        m.parameterTypes.size == 1 && m.parameterTypes[0] == Int::class.javaPrimitiveType
                    } }
                    .findFirst()
                    .orElse(null)
                if (clazz != null) {
                    LogUtils.i("ClassHelper: 特征匹配找到FragmentPagerAdapter: ${clazz!!.name}")
                } else {
                    LogUtils.e("ClassHelper: 特征匹配未找到FragmentPagerAdapter")
                }
            } catch (e: Exception) {
                LogUtils.e("ClassHelper: FragmentPagerAdapter查找失败 - ${e.message}")
            }
            return clazz
        }
    }

    /**
     * 下载传输类查找 - DownloadMD5Hook需要
     * 查找 com.netease.cloudmusic.module.transfer.download 下的混淆类
     */
    object DownloadTransfer {
        private var checkMd5Method: Method? = null
        private var checkDownloadStatusMethod: Method? = null

        /**
         * 下载完成后的MD5检查方法
         * 特征：4个参数，第1个为File，第2个为File
         */
        @JvmStatic
        fun getCheckMd5Method(context: Context): Method? {
            if (checkMd5Method == null) {
                val pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.module\\.transfer\\.download\\.[a-z0-9]{1,2}$")
                val list = getFilteredClasses(pattern, Collections.reverseOrder())

                try {
                    val targetClass = Stream.of(list)
                        .map { getClassByXposed(it) }
                        .filter { it != null }
                        .map { it!! }
                        .filter { c -> !Modifier.isAbstract(c.modifiers) }
                        .filter { c -> Modifier.isPublic(c.modifiers) }
                        .filter { c -> Stream.of(*c.declaredMethods).anyMatch { m ->
                            m.parameterTypes.size == 4 &&
                            m.parameterTypes[0] == File::class.java &&
                            m.parameterTypes[1] == File::class.java
                        }}
                        .findFirst()
                        .orElse(null)

                    if (targetClass != null) {
                        checkMd5Method = Stream.of(*targetClass.declaredMethods)
                            .filter { m -> m.parameterTypes.size == 4 }
                            .filter { m -> m.parameterTypes[0] == File::class.java }
                            .filter { m -> m.parameterTypes[1] == File::class.java }
                            .findFirst()
                            .orElse(null)
                    }
                } catch (e: Exception) {
                    LogUtils.e("ClassHelper: 找不到Transfer核心类 - ${e.message}")
                }
            }
            return checkMd5Method
        }

        /**
         * 下载之前的下载状态检查方法
         * 特征：返回long，5个参数，第2个为int，第4个为File，第5个为long
         */
        @JvmStatic
        fun getCheckDownloadStatusMethod(context: Context): Method? {
            if (checkDownloadStatusMethod == null) {
                val pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.module\\.transfer\\.download\\.[a-z0-9]{1,2}$")
                val list = getFilteredClasses(pattern, Collections.reverseOrder())

                try {
                    val targetClass = Stream.of(list)
                        .map { getClassByXposed(it) }
                        .filter { it != null }
                        .map { it!! }
                        .filter { c -> !Modifier.isAbstract(c.modifiers) }
                        .filter { c -> Modifier.isPublic(c.modifiers) }
                        .filter { c -> Stream.of(*c.declaredMethods).anyMatch { m ->
                            m.returnType == Long::class.javaPrimitiveType &&
                            m.parameterTypes.size == 5 &&
                            m.parameterTypes[1] == Integer.TYPE &&
                            m.parameterTypes[3] == File::class.java &&
                            m.parameterTypes[4] == Long::class.javaPrimitiveType
                        }}
                        .findFirst()
                        .orElse(null)

                    if (targetClass != null) {
                        checkDownloadStatusMethod = Stream.of(*targetClass.declaredMethods)
                            .filter { m -> m.returnType == Long::class.javaPrimitiveType }
                            .filter { m -> m.parameterTypes.size == 5 }
                            .filter { m -> m.parameterTypes[1] == Integer.TYPE }
                            .filter { m -> m.parameterTypes[3] == File::class.java }
                            .filter { m -> m.parameterTypes[4] == Long::class.javaPrimitiveType }
                            .findFirst()
                            .orElse(null)
                    }
                } catch (e: Exception) {
                    LogUtils.e("ClassHelper: 找不到Transfer状态检查方法 - ${e.message}")
                }
            }
            return checkDownloadStatusMethod
        }
    }

    /**
     * 广告类查找 - AdExtraHook需要
     * 查找 com.netease.cloudmusic.module.ad 下的混淆类
     */
    object Ad {
        private var adClazz: Class<*>? = null
        private var clazz: Class<*>? = null

        @JvmStatic
        fun getClazz(context: Context): Class<*>? {
            if (clazz == null) {
                adClazz = findClassIfExists("com.netease.cloudmusic.meta.Ad", classLoader!!)
                try {
                    val pattern = Pattern.compile("^com\\.netease\\.cloudmusic\\.module\\.ad\\.[a-z]$")
                    val list = getFilteredClasses(pattern, Collections.reverseOrder())
                    clazz = Stream.of(list)
                        .map { getClassByXposed(it) }
                        .filter { it != null }
                        .map { it!! }
                        .filter { c -> Modifier.isPublic(c.modifiers) }
                        .filter { c -> !Modifier.isInterface(c.modifiers) }
                        .filter { c -> !Modifier.isStatic(c.modifiers) }
                        .filter { c -> !Modifier.isAbstract(c.modifiers) }
                        .filter { c -> Stream.of(*c.declaredMethods).anyMatch { m -> m.returnType.name.contains("VideoAdInfo") } }
                        .filter { c -> Stream.of(*c.declaredMethods).anyMatch { m -> m.returnType == adClazz } }
                        .findFirst()
                        .orElse(null)
                } catch (e: Exception) {
                    LogUtils.e("ClassHelper: Ad类查找失败 - ${e.message}")
                }
            }
            return clazz
        }

        /**
         * 获取广告相关方法列表
         * 筛选返回类型为meta包下类、且参数包含JSONObject的方法
         */
        @JvmStatic
        fun getAdMethod(context: Context): List<Method>? {
            return try {
                val adClass = getClazz(context) ?: return null
                val methodList = adClass.declaredMethods.toList()
                val hookMethodList = Stream.of(methodList)
                    .filter { m -> m.returnType.name.contains("com.netease.cloudmusic.meta") }
                    .filter { m -> Stream.of(*m.parameterTypes).anyMatch { c -> c == JSONObject::class.java } }
                    .toList()
                hookMethodList.addAll(Stream.of(methodList)
                    .filter { m -> Stream.of(*m.parameterTypes).anyMatch { c -> c.name.contains("com.netease.cloudmusic.meta") } }
                    .filter { m -> Stream.of(*m.parameterTypes).anyMatch { c -> c == JSONObject::class.java } }
                    .toList())
                hookMethodList
            } catch (e: Exception) {
                LogUtils.e("ClassHelper: getAdMethod失败 - ${e.message}")
                null
            }
        }
    }
}
