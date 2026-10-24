package com.nackie.ownexpenses.domain.model

/**
 * Kind of money movement. Also used as the type of a category: an expense category
 * only holds expenses, an income category only holds incomes.
 */
enum class TransactionType {
    EXPENSE,
    INCOME,
}
