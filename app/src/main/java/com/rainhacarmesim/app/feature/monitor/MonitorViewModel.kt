package com.rainhacarmesim.app.feature.monitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rainhacarmesim.app.AppContainer
import com.rainhacarmesim.app.domain.Formatters
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

enum class Period(val label: String) { DAY("24 horas"), WEEK("7 dias"), MONTH("30 dias") }

enum class HistorySource { SYSTEM, APP_SAMPLES }

data class HistoryBar(val label: String, val rx: Long, val tx: Long)

data class HistoryUi(
    val loading: Boolean = true,
    val period: Period = Period.DAY,
    val source: HistorySource = HistorySource.APP_SAMPLES,
    val bars: List<HistoryBar> = emptyList(),
    val totalRx: Long = 0,
    val totalTx: Long = 0,
    val wifiTotal: Long? = null,
    val mobileTotal: Long? = null,
    val error: String? = null,
)

class MonitorViewModel(private val c: AppContainer) : ViewModel() {
    val current = c.trafficMonitor.sampler.current
    val live = c.trafficMonitor.sampler.history
    val supported = c.trafficMonitor.sampler.supported

    private val _history = MutableStateFlow(HistoryUi())
    val history: StateFlow<HistoryUi> = _history.asStateFlow()
    private var loadJob: Job? = null

    init {
        load(Period.DAY)
    }

    fun hasUsageAccess(): Boolean = c.usageHistory.hasAccess()

    fun selectPeriod(p: Period) = load(p)

    fun reload() = load(_history.value.period)

    private fun load(period: Period) {
        loadJob?.cancel()
        _history.value = _history.value.copy(loading = true, period = period, error = null)
        loadJob = viewModelScope.launch {
            try {
                val boundaries = boundaries(period, System.currentTimeMillis())
                val labels = boundaries.dropLast(1).map { label(period, it) }
                val system = c.usageHistory.query(boundaries)
                _history.value = if (system != null) {
                    val mobileOk = system.all { it.mobileAvailable }
                    HistoryUi(
                        loading = false,
                        period = period,
                        source = HistorySource.SYSTEM,
                        bars = system.mapIndexed { i, b -> HistoryBar(labels[i], b.rx, b.tx) },
                        totalRx = system.sumOf { it.rx },
                        totalTx = system.sumOf { it.tx },
                        wifiTotal = system.sumOf { it.wifiRx + it.wifiTx },
                        mobileTotal = if (mobileOk) system.sumOf { it.mobileRx + it.mobileTx } else null,
                    )
                } else {
                    val samples = c.trafficRepository.range(boundaries.first(), boundaries.last())
                    val bars = boundaries.zipWithNext().mapIndexed { i, (start, end) ->
                        val inRange = samples.filter { it.minuteStart in start until end }
                        HistoryBar(labels[i], inRange.sumOf { it.rxBytes }, inRange.sumOf { it.txBytes })
                    }
                    HistoryUi(
                        loading = false,
                        period = period,
                        source = HistorySource.APP_SAMPLES,
                        bars = bars,
                        totalRx = bars.sumOf { it.rx },
                        totalTx = bars.sumOf { it.tx },
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _history.value = HistoryUi(loading = false, period = period, error = e.message ?: "Falha ao carregar histórico")
            }
        }
    }

    private fun label(period: Period, start: Long): String = when (period) {
        Period.DAY -> Formatters.time(start)
        else -> Formatters.dayLabel(start)
    }

    /** Marcos dos intervalos: 12 blocos de 2 h (24 h) ou dias inteiros (7/30 dias), terminando agora. */
    private fun boundaries(period: Period, now: Long): List<Long> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        return when (period) {
            Period.DAY -> {
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                cal.set(Calendar.HOUR_OF_DAY, hour - hour % 2)
                cal.add(Calendar.HOUR_OF_DAY, 2) // fim do bloco atual
                val end = cal.timeInMillis
                (12 downTo 0).map { end - it * 2L * 3600_000L }
            }
            Period.WEEK, Period.MONTH -> {
                val days = if (period == Period.WEEK) 7 else 30
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.add(Calendar.DAY_OF_MONTH, 1) // meia-noite de amanhã
                val marks = mutableListOf(cal.timeInMillis)
                repeat(days) {
                    cal.add(Calendar.DAY_OF_MONTH, -1)
                    marks += cal.timeInMillis
                }
                marks.reversed()
            }
        }
    }
}
