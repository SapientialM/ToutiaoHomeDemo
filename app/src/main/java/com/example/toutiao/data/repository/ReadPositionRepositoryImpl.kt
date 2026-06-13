package com.example.toutiao.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.toutiao.domain.repository.ReadPositionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 频道阅读位置仓库实现 —— SharedPreferences + 内存缓存双层
 *
 * 缓存策略:
 *  - 第一层:ConcurrentHashMap 内存缓存(本类实例字段,@Singleton 跨 ViewModel 共享)
 *  - 第二层:SharedPreferences 持久化
 *
 * 读路径:先查内存(包括 "未记录" 的 null 命中),命中即返回;未命中才读 SharedPreferences,
 *        并把结果(含 null)写回内存,避免同一频道反复磁盘 I/O。
 * 写路径:同时写内存与 SharedPreferences(apply 异步),保证重启后行为一致。
 *
 * 线程安全:ConcurrentHashMap 保证多协程 / 多 ViewModel 并发读写安全;
 *         SharedPreferences 自身也是线程安全的。
 */
@Singleton
class ReadPositionRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : ReadPositionRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // 注意:ConcurrentHashMap 运行时禁止 null value(JDK 限制,Kotlin 的可空类型不会绕过)。
    // 策略:只缓存"有数据"的结果;"未记录"分支每次都走 SharedPreferences(本仓库就 8 个频道,
    // 缓存穿透的影响可忽略,且 SharedPreferences 命中是常驻内存的 Map 查找,开销极小)。
    private val cache = ConcurrentHashMap<String, CachedPosition>()

    private data class CachedPosition(val id: String, val at: Long)

    override fun getLastSeenId(channel: String): String? = readPosition(channel)?.id

    override fun getLastSeenAt(channel: String): Long = readPosition(channel)?.at ?: 0L

    private fun readPosition(channel: String): CachedPosition? {
        if (channel.isBlank()) return null
        // 命中(只缓存"有数据",null 结果不缓存,见上方注释)
        cache[channel]?.let { return it }
        val id = prefs.getString(keyId(channel), null)
        val at = prefs.getLong(keyAt(channel), 0L)
        val pos = if (id.isNullOrBlank()) null else CachedPosition(id, at)
        if (pos != null) cache[channel] = pos
        return pos
    }

    override fun setLastSeenId(channel: String, cardId: String, timestamp: Long) {
        if (channel.isBlank() || cardId.isBlank()) return
        cache[channel] = CachedPosition(cardId, timestamp)
        prefs.edit()
            .putString(keyId(channel), cardId)
            .putLong(keyAt(channel), timestamp)
            .apply()
    }

    override fun clear(channel: String) {
        if (channel.isBlank()) return
        cache.remove(channel)
        prefs.edit()
            .remove(keyId(channel))
            .remove(keyAt(channel))
            .apply()
    }

    private fun keyId(channel: String) = "${KEY_PREFIX_ID}$channel"
    private fun keyAt(channel: String) = "${KEY_PREFIX_AT}$channel"

    companion object {
        private const val PREFS_NAME = "read_position_prefs"
        private const val KEY_PREFIX_ID = "last_seen_id_"
        private const val KEY_PREFIX_AT = "last_seen_at_"
    }
}
