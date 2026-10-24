package com.nackie.ownexpenses.domain.model

import com.nackie.ownexpenses.domain.model.TransactionType.EXPENSE
import com.nackie.ownexpenses.domain.model.TransactionType.INCOME

/**
 * Categories created on first launch, in display order. Names are localized by the UI layer
 * through [DefaultCategoryNames]; subcategories inherit type and color from [parent].
 */
enum class DefaultCategory(
    val type: TransactionType,
    val icon: String,
    val color: Int,
    val parent: DefaultCategory? = null,
) {
    GROCERIES(EXPENSE, "groceries", 0xFF7C4DFF),
    CAFE(EXPENSE, "cafe", 0xFFF50057),
    TRANSPORT(EXPENSE, "transport", 0xFF448AFF),
    TAXI(parent = TRANSPORT, icon = "taxi"),
    PUBLIC_TRANSPORT(parent = TRANSPORT, icon = "public_transport"),
    HOME(EXPENSE, "home", 0xFFFF6E40),
    UTILITIES(parent = HOME, icon = "utilities"),
    MOBILE_INTERNET(parent = HOME, icon = "mobile_internet"),
    HEALTH(EXPENSE, "health", 0xFF1DE9B6),
    ENTERTAINMENT(EXPENSE, "entertainment", 0xFF536DFE),
    CLOTHES(EXPENSE, "clothes", 0xFFFF9100),
    PETS(EXPENSE, "pets", 0xFF76FF03),
    GIFTS(EXPENSE, "gifts", 0xFFFF1744),
    OTHER_EXPENSE(EXPENSE, "other", 0xFFB0BEC5),

    SALARY(INCOME, "salary", 0xFF00E676),
    GIFT_RECEIVED(INCOME, "gifts", 0xFFE040FB),
    OTHER_INCOME(INCOME, "other", 0xFF90A4AE),
    ;

    constructor(type: TransactionType, icon: String, color: Long) : this(type, icon, color.toInt())

    constructor(parent: DefaultCategory, icon: String) : this(parent.type, icon, parent.color, parent)
}

/** Resolves localized names of [DefaultCategory] entries (uk / en string resources). */
fun interface DefaultCategoryNames {
    suspend fun nameOf(category: DefaultCategory): String
}
