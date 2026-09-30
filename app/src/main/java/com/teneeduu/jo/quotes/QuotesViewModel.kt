package com.teneeduu.jo.quotes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime

class QuotesViewModel(application: Application) : AndroidViewModel(application) {

    private val library = QuoteLibrary(application)

    private val _quotes = MutableStateFlow<List<Quote>>(emptyList())
    val quotes: StateFlow<List<Quote>> = _quotes.asStateFlow()

    private val _custom = MutableStateFlow<List<Quote>>(emptyList())
    val custom: StateFlow<List<Quote>> = _custom.asStateFlow()

    private val _current = MutableStateFlow<Quote?>(null)
    val current: StateFlow<Quote?> = _current.asStateFlow()

    private val _reminders = MutableStateFlow(QuoteReminders.settings(application))
    val reminders: StateFlow<ReminderSettings> = _reminders.asStateFlow()

    private val _canNotify = MutableStateFlow(QuoteReminders.canNotify(application))
    val canNotify: StateFlow<Boolean> = _canNotify.asStateFlow()

    init {
        reload()
        // Re-queue on every launch too, in case the system dropped the alarms.
        QuoteReminders.reschedule(application)
    }

    fun reload() {
        viewModelScope.launch {
            val (all, custom) = withContext(Dispatchers.IO) { library.all() to library.custom() }
            _quotes.value = all
            _custom.value = custom
            if (_current.value == null || _current.value !in all) _current.value = QuoteLibrary.ofTheDay(all)
        }
    }

    /** Called whenever the background photo changes, so words and picture turn over together. */
    fun shuffle() {
        val all = _quotes.value
        if (all.size < 2) return
        var next = all.random()
        while (next == _current.value) next = all.random()
        _current.value = next
    }

    fun addCustom(line: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.addCustom(line) }
            reload()
        }
    }

    fun removeCustom(quote: Quote) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { library.removeCustom(quote) }
            reload()
        }
    }

    fun setRemindersEnabled(enabled: Boolean) = updateReminders(_reminders.value.copy(enabled = enabled))

    fun setMorning(time: LocalTime) = updateReminders(_reminders.value.copy(morning = time))

    fun setEvening(time: LocalTime) = updateReminders(_reminders.value.copy(evening = time))

    fun sendTest() = QuoteReminders.postTest(getApplication())

    fun refreshPermission() {
        _canNotify.value = QuoteReminders.canNotify(getApplication())
    }

    private fun updateReminders(settings: ReminderSettings) {
        _reminders.value = settings
        QuoteReminders.save(getApplication(), settings)
    }
}
