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
    GROCERIES(EXPENSE, "groceries", 0xFF7E57C2),
    CAFE(EXPENSE, "cafe", 0xFFD81B60),
    TRANSPORT(EXPENSE, "transport", 0xFF1E88E5),
    TAXI(parent = TRANSPORT, icon = "taxi"),
    PUBLIC_TRANSPORT(parent = TRANSPORT, icon = "public_transport"),
    HOME(EXPENSE, "home", 0xFF8D6E63),
    UTILITIES(parent = HOME, icon = "utilities"),
    MOBILE_INTERNET(parent = HOME, icon = "mobile_internet"),
    HEALTH(EXPENSE, "health", 0xFF00897B),
    ENTERTAINMENT(EXPENSE, "entertainment", 0xFF3949AB),
    CLOTHES(EXPENSE, "clothes", 0xFFFB8C00),
    PETS(EXPENSE, "pets", 0xFF7CB342),
    GIFTS(EXPENSE, "gifts", 0xFFE53935),
    OTHER_EXPENSE(EXPENSE, "other", 0xFF757575),

    SALARY(INCOME, "salary", 0xFF2E7D32),
    GIFT_RECEIVED(INCOME, "gifts", 0xFFAD1457),
    OTHER_INCOME(INCOME, "other", 0xFF546E7A),
    ;

    constructor(type: TransactionType, icon: String, color: Long) : this(type, icon, color.toInt())

    constructor(parent: DefaultCategory, icon: String) : this(parent.type, icon, parent.color, parent)
}

/** Resolves localized names of [DefaultCategory] entries (uk / en string resources). */
fun interface DefaultCategoryNames {
    suspend fun nameOf(category: DefaultCategory): String
}
