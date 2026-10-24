package com.nackie.ownexpenses.presentation

import com.nackie.ownexpenses.domain.TodayProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

/** Today that tests move by hand, e.g. across midnight. */
internal class FakeTodayProvider(
    initial: LocalDate,
) : TodayProvider {
    val date = MutableStateFlow(initial)

    override fun today(): LocalDate = date.value

    override fun observeToday(): Flow<LocalDate> = date
}

/** viewModelScope runs on Dispatchers.Main, which plain JVM tests do not have. */
@OptIn(ExperimentalCoroutinesApi::class)
internal abstract class MainDispatcherTest {
    @kotlin.test.BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @kotlin.test.AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }
}
