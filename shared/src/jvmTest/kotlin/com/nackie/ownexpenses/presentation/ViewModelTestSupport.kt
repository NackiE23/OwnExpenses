package com.nackie.ownexpenses.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nackie.ownexpenses.domain.TodayProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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

/**
 * viewModelScope runs on Dispatchers.Main, which plain JVM tests do not have. ViewModels made with
 * [tracked] are stopped after each test: otherwise their scope outlives it (stateIn keeps Room flows
 * for its stop timeout) and later resumes on a Main that was already reset, failing whichever test
 * runs at that moment.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal abstract class MainDispatcherTest {
    private val viewModels = mutableListOf<ViewModel>()

    protected fun <T : ViewModel> T.tracked(): T = also { viewModels += it }

    /** Cancels the tracked ViewModels' scopes; call before closing the database they read. */
    protected fun cancelViewModels() {
        viewModels.forEach { it.viewModelScope.cancel() }
        viewModels.clear()
    }

    @kotlin.test.BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @kotlin.test.AfterTest
    fun resetMainDispatcher() {
        cancelViewModels()
        Dispatchers.resetMain()
    }
}
