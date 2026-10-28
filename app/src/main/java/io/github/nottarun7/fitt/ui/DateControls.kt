package io.github.nottarun7.fitt.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Calendar dates use UTC midnight; the stored value remains the user's ISO date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateInputField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier,
    label: String = "Date", enabled: Boolean = true) {
    var calendar by rememberSaveable { mutableStateOf(false) }
    val parsed = runCatching { LocalDate.parse(value).takeIf { it.toString() == value } }.getOrNull()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(value, onValueChange, modifier, label = { Text("$label (YYYY-MM-DD)") },
        singleLine = true, enabled = enabled, isError = parsed == null,
        supportingText = if (parsed == null) ({ Text("Enter a valid date or choose one from the calendar.") }) else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }),
        trailingIcon = {
            IconButton({ focus.clearFocus(); keyboard?.hide(); calendar = true }, enabled = enabled) {
                Icon(Icons.Default.DateRange, "Choose $label from calendar")
            }
        })
    if (calendar) {
        // DatePicker supports civil years 1–9999. Wider ISO dates stay editable as text.
        val initial = parsed?.takeIf { it.year in 1..9999 }
        val displayed = initial ?: LocalDate.now()
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            initialDisplayedMonthMillis = displayed.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = minOf(1900, displayed.year)..maxOf(2100, displayed.year))
        DatePickerDialog(onDismissRequest = { calendar = false }, modifier = Modifier.testTag("datePickerDialog"),
            confirmButton = {
                TextButton({
                    picker.selectedDateMillis?.let {
                        onValueChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    calendar = false
                }, enabled = enabled && picker.selectedDateMillis != null, modifier = Modifier.testTag("confirmDate")) { Text("Use date") }
            }, dismissButton = { TextButton({ calendar = false }, Modifier.testTag("cancelDate")) { Text("Cancel") } }) {
            DatePicker(picker, modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()))
        }
    }
}
