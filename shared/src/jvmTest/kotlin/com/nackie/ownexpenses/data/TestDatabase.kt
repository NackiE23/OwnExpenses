package com.nackie.ownexpenses.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.nackie.ownexpenses.data.local.AppDatabase
import com.nackie.ownexpenses.data.local.entity.CategoryEntity
import com.nackie.ownexpenses.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock
import kotlin.time.Instant

/** Fresh in-memory database with the same driver and schema as production. */
internal fun createTestDatabase(): AppDatabase =
    Room
        .inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

internal suspend fun AppDatabase.insertCategory(
    name: String,
    type: TransactionType,
    parentId: Long? = null,
    sortOrder: Int = 0,
    isArchived: Boolean = false,
): Long =
    categoryDao().insert(
        CategoryEntity(
            parentId = parentId,
            name = name,
            type = type,
            color = 0,
            icon = "other",
            sortOrder = sortOrder,
            isArchived = isArchived,
        ),
    )

internal class MutableClock(
    var now: Instant = Instant.parse("2026-10-24T10:00:00Z"),
) : Clock {
    override fun now(): Instant = now
}
