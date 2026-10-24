package com.nackie.ownexpenses.ui

import com.nackie.ownexpenses.domain.model.DefaultCategory
import com.nackie.ownexpenses.domain.model.DefaultCategoryNames
import com.nackie.ownexpenses.ui.resources.Res
import com.nackie.ownexpenses.ui.resources.category_cafe
import com.nackie.ownexpenses.ui.resources.category_clothes
import com.nackie.ownexpenses.ui.resources.category_entertainment
import com.nackie.ownexpenses.ui.resources.category_gift_received
import com.nackie.ownexpenses.ui.resources.category_gifts
import com.nackie.ownexpenses.ui.resources.category_groceries
import com.nackie.ownexpenses.ui.resources.category_health
import com.nackie.ownexpenses.ui.resources.category_home
import com.nackie.ownexpenses.ui.resources.category_mobile_internet
import com.nackie.ownexpenses.ui.resources.category_other_expense
import com.nackie.ownexpenses.ui.resources.category_other_income
import com.nackie.ownexpenses.ui.resources.category_pets
import com.nackie.ownexpenses.ui.resources.category_public_transport
import com.nackie.ownexpenses.ui.resources.category_salary
import com.nackie.ownexpenses.ui.resources.category_taxi
import com.nackie.ownexpenses.ui.resources.category_transport
import com.nackie.ownexpenses.ui.resources.category_utilities
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/** Names of default categories from string resources, in the current device language. */
internal class ResourceDefaultCategoryNames : DefaultCategoryNames {
    override suspend fun nameOf(category: DefaultCategory): String = getString(category.nameResource)
}

private val DefaultCategory.nameResource: StringResource
    get() =
        when (this) {
            DefaultCategory.GROCERIES -> Res.string.category_groceries
            DefaultCategory.CAFE -> Res.string.category_cafe
            DefaultCategory.TRANSPORT -> Res.string.category_transport
            DefaultCategory.TAXI -> Res.string.category_taxi
            DefaultCategory.PUBLIC_TRANSPORT -> Res.string.category_public_transport
            DefaultCategory.HOME -> Res.string.category_home
            DefaultCategory.UTILITIES -> Res.string.category_utilities
            DefaultCategory.MOBILE_INTERNET -> Res.string.category_mobile_internet
            DefaultCategory.HEALTH -> Res.string.category_health
            DefaultCategory.ENTERTAINMENT -> Res.string.category_entertainment
            DefaultCategory.CLOTHES -> Res.string.category_clothes
            DefaultCategory.PETS -> Res.string.category_pets
            DefaultCategory.GIFTS -> Res.string.category_gifts
            DefaultCategory.OTHER_EXPENSE -> Res.string.category_other_expense
            DefaultCategory.SALARY -> Res.string.category_salary
            DefaultCategory.GIFT_RECEIVED -> Res.string.category_gift_received
            DefaultCategory.OTHER_INCOME -> Res.string.category_other_income
        }
