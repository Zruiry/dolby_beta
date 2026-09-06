/**
 * 通用反射/Hook 工具（Modern libxposed API 102）
 *
 * 封装项目中反复用到的模式，供各 Hook 复用：
 * - 按名称/参数类型找类、方法、字段
 * - 反射读写字段（含父类）
 * - 安装"无参方法返回固定值 / void 置空 / 布尔决策返回 true"的 Hook
 *
 * 用法：初始化一次（拿到 module/context 后 init），后续各 Hook 直接调用。
 */
package com.raincat.dolby_beta.hook.common

import android.content.Context
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object HookKit {
    private const val TAG = "HookKit"

    @Volatile
    private var module: XposedModule? = null

    @Volatile
    var classLoader: ClassLoader? = null
        private set

    /** 初始化全局上下文（在模块入口 attachBaseContext 阶段调用一次即可） */
    fun init(module: XposedModule, context: Context) {
        this.module = module
        this.classLoader = context.classLoader
    }

    // ==================== 类查找 ====================

    /** 按全名加载类（不存在返回 null），不触发类初始化 */
    fun findClass(name: String): Class<*>? {
        val cl = classLoader ?: return null
        return try {
            Class.forName(name, false, cl)
        } catch (e: Throwable) {
            null
        }
    }

    // ==================== 方法/字段查找 ====================

    /** 按名称（可选参数类型、返回类型、是否静态）查找类内方法，找不到返回 null */
    fun findMethod(
        clazz: Class<*>,
        name: String,
        paramTypes: Array<Class<*>>? = null,
        returnType: Class<*>? = null,
        static: Boolean? = null
    ): Method? {
        for (m in clazz.declaredMethods) {
            if (m.name != name) continue
            if (paramTypes != null && !m.parameterTypes.contentEquals(paramTypes)) continue
            if (returnType != null && m.returnType != returnType) continue
            if (static != null && Modifier.isStatic(m.modifiers) != static) continue
            m.isAccessible = true
            return m
        }
        return null
    }

    /** 查找多个无参方法名中第一个存在的（返回 Boolean 基元） */
    fun findNoArgBoolMethod(clazz: Class<*>, vararg names: String): Method? {
        for (m in clazz.declaredMethods) {
            if (m.parameterTypes.isEmpty() && m.returnType == Boolean::class.javaPrimitiveType && m.name in names) {
                m.isAccessible = true
                return m
            }
        }
        return null
    }

    /** 按名称查找字段（含父类），不存在返回 null */
    fun findField(clazz: Class<*>, name: String): Field? {
        var cur: Class<*>? = clazz
        while (cur != null) {
            try {
                val f = cur.getDeclaredField(name)
                f.isAccessible = true
                return f
            } catch (_: NoSuchFieldException) {
                cur = cur.superclass
            }
        }
        return null
    }

    // ==================== 字段读写 ====================

    /** 反射设置字段（字段不存在则忽略），自动匹配基本类型 */
    fun setField(obj: Any, name: String, value: Any) {
        val f = findField(obj.javaClass, name) ?: return
        try {
            when (value) {
                is Int -> f.setInt(obj, value)
                is Long -> f.setLong(obj, value)
                is Boolean -> f.setBoolean(obj, value)
                is Float -> f.setFloat(obj, value)
                is Double -> f.setDouble(obj, value)
                else -> f.set(obj, value)
            }
        } catch (e: Throwable) {
            // 忽略：字段类型不匹配等
        }
    }

    /** 反射读取字段（字段不存在返回 null） */
    fun getField(obj: Any, name: String): Any? {
        val f = findField(obj.javaClass, name) ?: return null
        return try {
            f.get(obj)
        } catch (e: Throwable) {
            null
        }
    }

    // ==================== Hook 安装 ====================

    private fun requireModule(): XposedModule = module ?: throw IllegalStateException("HookKit 未初始化")

    /** Hook 单个方法，返回自定义值（null 表示拦截 void 方法返回空） */
    fun hookReturnMethod(method: Method, value: (Any?) -> Any?) {
        method.isAccessible = true
        requireModule().hook(method).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? = value(chain)
        })
    }

    /** Hook 无参实例方法返回固定值（找不到静默跳过） */
    fun hookNoArgConstant(clazz: Class<*>, methodName: String, value: Any) {
        val m = clazz.declaredMethods.firstOrNull {
            it.name == methodName && it.parameterTypes.isEmpty() && !Modifier.isStatic(it.modifiers)
        } ?: return
        m.isAccessible = true
        requireModule().hook(m).intercept(object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? = value
        })
    }

    /** Hook 无参布尔决策方法返回 true（含静态、所有同名重载） */
    fun hookNoArgBoolTrue(clazz: Class<*>, vararg names: String): Int {
        var count = 0
        for (m in clazz.declaredMethods) {
            if (m.name in names && m.parameterTypes.isEmpty() && m.returnType == Boolean::class.javaPrimitiveType) {
                m.isAccessible = true
                requireModule().hook(m).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? = true
                })
                count++
            }
        }
        return count
    }

    /** Hook void 方法为无操作（按方法名+参数个数+是否静态匹配第一个） */
    fun hookVoidNoop(clazz: Class<*>, methodName: String, paramCount: Int, isStatic: Boolean): Boolean {
        for (m in clazz.declaredMethods) {
            if (m.name == methodName && m.parameterTypes.size == paramCount &&
                m.returnType == Void.TYPE && (isStatic == Modifier.isStatic(m.modifiers))
            ) {
                m.isAccessible = true
                requireModule().hook(m).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? = null
                })
                return true
            }
        }
        return false
    }

    /** 对目标类上所有满足"无参+布尔返回"的方法安装返回 true 的 Hook，返回数量 */
    fun hookAllNoArgBoolTrue(clazz: Class<*>): Int = hookNoArgBoolTrue(clazz, *clazz.declaredMethods.mapNotNull {
        if (it.parameterTypes.isEmpty() && it.returnType == Boolean::class.javaPrimitiveType) it.name else null
    }.toTypedArray())
}
