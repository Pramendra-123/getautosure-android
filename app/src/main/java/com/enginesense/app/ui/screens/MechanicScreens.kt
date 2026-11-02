package com.enginesense.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enginesense.app.consultation.Leave
import com.enginesense.app.consultation.LoadState
import com.enginesense.app.consultation.MechanicAccess
import com.enginesense.app.consultation.MechanicBooking
import com.enginesense.app.consultation.MechanicViewModel
import com.enginesense.app.consultation.WeeklyHours
import com.enginesense.app.ui.theme.GetAutoSureColors
import com.enginesense.app.ui.theme.GetAutoSureType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import live.hms.roomkit.ui.HMSPrebuiltOptions
import live.hms.roomkit.ui.HMSRoomKit

// Mechanic mode is written for people who'd rather tap than type: big buttons, times picked from
// lists, and nothing to fill in by hand.

private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

/** Matches the backend's mechanic join window (MECHANIC_EARLY_MINUTES and OVERRUN_MINUTES in video.ts). */
private val MECHANIC_JOIN_OPENS_BEFORE_MILLIS = TimeUnit.MINUTES.toMillis(10)
private val MECHANIC_JOIN_CLOSES_AFTER_MILLIS = TimeUnit.MINUTES.toMillis(10)

/** Every half hour from 06:00 to 22:00 — the times a mechanic can pick for a working block. */
private val TIME_CHOICES: List<String> = (6 * 2..22 * 2).map { "%02d:%02d".format(it / 2, (it % 2) * 30) }

@Composable
fun MechanicHomeScreen(viewModel: MechanicViewModel, onEditHours: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.load(context) }
    val access by viewModel.access.collectAsState()

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Mechanic mode", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(16.dp))

        when (val a = access) {
            is MechanicAccess.Checking -> LoadingRow()
            is MechanicAccess.Unavailable -> Text("Mechanic mode needs the live GetAutoSure service.", style = GetAutoSureType.Caption)
            is MechanicAccess.Failed -> ErrorRow(a.message, onRetry = { viewModel.load(context) })
            is MechanicAccess.NotLinked -> NotLinkedCard(a.message, onRetry = { viewModel.load(context) })
            is MechanicAccess.Ready -> {
                Text(a.profile.expert.name, style = GetAutoSureType.SectionTitle)
                Text(a.profile.email, style = GetAutoSureType.Caption)
                Spacer(Modifier.height(24.dp))
                CallsSection(viewModel, mechanicName = a.profile.expert.name)
                Spacer(Modifier.height(28.dp))
                HoursSection(viewModel, onEditHours)
                Spacer(Modifier.height(28.dp))
                LeaveSection(viewModel)
            }
        }
    }
}

@Composable
private fun NotLinkedCard(message: String, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Not linked yet", style = GetAutoSureType.SectionTitle)
            Spacer(Modifier.height(8.dp))
            Text(message, style = GetAutoSureType.Body)
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Check again") }
        }
    }
}

// ---- Calls -------------------------------------------------------------------------------------

@Composable
private fun CallsSection(viewModel: MechanicViewModel, mechanicName: String) {
    val context = LocalContext.current
    val bookings by viewModel.bookings.collectAsState()
    // Ticks so Join buttons switch on by themselves at call time.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Your calls", style = GetAutoSureType.SectionTitle, modifier = Modifier.weight(1f))
        TextButton(onClick = { viewModel.refreshBookings(context) }) { Text("Refresh") }
    }
    Spacer(Modifier.height(8.dp))
    when (val b = bookings) {
        is LoadState.Loading -> LoadingRow()
        is LoadState.Error -> ErrorRow(b.message, onRetry = { viewModel.refreshBookings(context) })
        is LoadState.Success -> {
            val upcoming = b.data.filter { now <= it.slotEndEpochMillis + MECHANIC_JOIN_CLOSES_AFTER_MILLIS }
            if (upcoming.isEmpty()) {
                Text("No booked calls yet.", style = GetAutoSureType.Caption)
            } else {
                upcoming.forEach { booking -> CallCard(viewModel, booking, now, mechanicName) }
            }
        }
    }
}

@Composable
private fun CallCard(viewModel: MechanicViewModel, booking: MechanicBooking, now: Long, mechanicName: String) {
    val context = LocalContext.current
    val canJoin = now in (booking.slotStartEpochMillis - MECHANIC_JOIN_OPENS_BEFORE_MILLIS)..(booking.slotEndEpochMillis + MECHANIC_JOIN_CLOSES_AFTER_MILLIS)
    var joining by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                SimpleDateFormat("EEE, d MMM — h:mm a", Locale.getDefault()).format(Date(booking.slotStartEpochMillis)),
                style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(booking.bookingReference, style = GetAutoSureType.Caption)
            Spacer(Modifier.height(10.dp))

            val car = booking.car
            if (car == null) {
                Text("No scan attached by the customer.", style = GetAutoSureType.Caption)
            } else {
                car.brand?.let { Text(it, style = GetAutoSureType.Body) }
                when (car.checkEngineOn) {
                    true -> Text("Check-engine light ON", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention))
                    false -> Text("Check-engine light off", style = GetAutoSureType.Caption)
                    null -> {}
                }
                if (car.issues.isEmpty()) {
                    Text("No problems found in the scan.", style = GetAutoSureType.Caption)
                } else {
                    car.issues.forEach { Text("• $it", style = GetAutoSureType.Caption) }
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    val activity = context.findActivity() ?: return@Button
                    joining = true
                    error = null
                    viewModel.requestJoinToken(booking.id, context) { result ->
                        joining = false
                        result
                            .onSuccess { token -> HMSRoomKit.launchPrebuiltUsingAuthToken(token, activity, HMSPrebuiltOptions(userName = mechanicName)) }
                            .onFailure { e -> error = e.message ?: "Couldn't join the call" }
                    }
                },
                enabled = canJoin && !joining,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Filled.Videocam, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (joining) "Connecting…" else "Join call")
            }
            if (!canJoin) {
                Spacer(Modifier.height(6.dp))
                Text("Join opens 10 minutes before the call.", style = GetAutoSureType.Caption)
            }
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention))
            }
        }
    }
}

// ---- Working hours -----------------------------------------------------------------------------

@Composable
private fun HoursSection(viewModel: MechanicViewModel, onEditHours: () -> Unit) {
    val schedule by viewModel.schedule.collectAsState()
    Text("Working hours", style = GetAutoSureType.SectionTitle)
    Spacer(Modifier.height(8.dp))
    when (val s = schedule) {
        is LoadState.Loading -> LoadingRow()
        is LoadState.Error -> Text(s.message, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention))
        is LoadState.Success -> {
            if (s.data.hours.isEmpty()) {
                Text("No hours set — customers can't book you yet.", style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Watch))
            } else {
                DAY_NAMES.forEachIndexed { index, day ->
                    val ranges = s.data.hours.filter { it.weekday == index + 1 }.sortedBy { it.start }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(day.take(3), style = GetAutoSureType.Body, modifier = Modifier.width(56.dp))
                        Text(
                            if (ranges.isEmpty()) "Off" else ranges.joinToString(",  ") { "${it.start}–${it.end}" },
                            style = if (ranges.isEmpty()) GetAutoSureType.Caption else GetAutoSureType.Body
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onEditHours, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Change hours") }
        }
    }
}

/** One editable block of time in [MechanicHoursScreen]. */
private data class EditRange(val start: String, val end: String)

@Composable
fun MechanicHoursScreen(viewModel: MechanicViewModel, onSaved: () -> Unit) {
    val context = LocalContext.current
    val schedule by viewModel.schedule.collectAsState()
    val initial = (schedule as? LoadState.Success)?.data?.hours.orEmpty()

    // weekday (1..7) -> that day's blocks; an empty list means the day is off.
    val days = remember {
        mutableStateMapOf<Int, List<EditRange>>().apply {
            (1..7).forEach { day -> put(day, initial.filter { it.weekday == day }.sortedBy { it.start }.map { EditRange(it.start, it.end) }) }
        }
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Your working hours", style = GetAutoSureType.ScreenTitle)
        Spacer(Modifier.height(4.dp))
        Text("Customers can book 15-minute calls inside these hours. India time.", style = GetAutoSureType.Caption)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { (2..6).forEach { days[it] = days[1].orEmpty() } },
            enabled = days[1].orEmpty().isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Same as Monday for Tue–Sat") }
        Spacer(Modifier.height(16.dp))

        (1..7).forEach { day ->
            val ranges = days[day].orEmpty()
            Card(colors = CardDefaults.cardColors(containerColor = GetAutoSureColors.Surface), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(DAY_NAMES[day - 1], style = GetAutoSureType.Body.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                        Text(if (ranges.isEmpty()) "Off" else "Working", style = GetAutoSureType.Caption)
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = ranges.isNotEmpty(),
                            onCheckedChange = { on -> days[day] = if (on) listOf(EditRange("10:00", "13:00")) else emptyList() }
                        )
                    }
                    ranges.forEachIndexed { i, range ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            TimePicker(range.start, modifier = Modifier.weight(1f)) { picked ->
                                days[day] = ranges.toMutableList().also { it[i] = range.copy(start = picked) }
                            }
                            Text("  to  ", style = GetAutoSureType.Caption)
                            TimePicker(range.end, modifier = Modifier.weight(1f)) { picked ->
                                days[day] = ranges.toMutableList().also { it[i] = range.copy(end = picked) }
                            }
                            if (ranges.size > 1) {
                                IconButton(onClick = { days[day] = ranges.toMutableList().also { it.removeAt(i) } }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove this time")
                                }
                            }
                        }
                    }
                    if (ranges.isNotEmpty() && ranges.size < 3) {
                        TextButton(onClick = {
                            // A sensible next block after the last one, e.g. an evening shift.
                            val lastEnd = ranges.maxOf { it.end }
                            val startIndex = (TIME_CHOICES.indexOf(lastEnd) + 8).coerceAtMost(TIME_CHOICES.size - 2).coerceAtLeast(0)
                            days[day] = ranges + EditRange(TIME_CHOICES[startIndex], TIME_CHOICES[(startIndex + 6).coerceAtMost(TIME_CHOICES.size - 1)])
                        }) { Text("+ Add another time") }
                    }
                }
            }
        }

        error?.let {
            Text(it, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention))
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = {
                saving = true
                error = null
                val hours = (1..7).flatMap { day -> days[day].orEmpty().map { WeeklyHours(day, it.start, it.end) } }
                viewModel.saveWeeklyHours(hours, context) { message ->
                    saving = false
                    if (message == null) onSaved() else error = message
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (saving) "Saving…" else "Save hours") }
    }
}

@Composable
private fun TimePicker(value: String, modifier: Modifier = Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text(value) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TIME_CHOICES.forEach { time ->
                DropdownMenuItem(text = { Text(time) }, onClick = {
                    open = false
                    onPick(time)
                })
            }
        }
    }
}

// ---- Leave -------------------------------------------------------------------------------------

@Composable
private fun LeaveSection(viewModel: MechanicViewModel) {
    val context = LocalContext.current
    val schedule by viewModel.schedule.collectAsState()
    var picking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Text("Leave", style = GetAutoSureType.SectionTitle)
    Spacer(Modifier.height(4.dp))
    Text("Days you're off. Customers can't book you then; calls already booked stay.", style = GetAutoSureType.Caption)
    Spacer(Modifier.height(8.dp))

    val leave = (schedule as? LoadState.Success)?.data?.leave.orEmpty()
    leave.forEach { item ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(leaveLabel(item), style = GetAutoSureType.Body, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                error = null
                viewModel.removeLeave(item.id, context) { error = it }
            }) { Text("Remove") }
        }
    }
    error?.let { Text(it, style = GetAutoSureType.Caption.copy(color = GetAutoSureColors.Attention)) }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Add leave") }

    if (picking) {
        LeavePickerDialog(
            onDismiss = { picking = false },
            onPicked = { start, end ->
                picking = false
                error = null
                viewModel.addLeave(start, end, context) { error = it }
            }
        )
    }
}

private val ISO_DAY = SimpleDateFormat("yyyy-MM-dd", Locale.US)
private fun leaveLabel(leave: Leave): String {
    val shown = SimpleDateFormat("EEE d MMM", Locale.getDefault())
    val start = ISO_DAY.parse(leave.startDate)?.let(shown::format) ?: leave.startDate
    val end = ISO_DAY.parse(leave.endDate)?.let(shown::format) ?: leave.endDate
    return if (leave.startDate == leave.endDate) start else "$start – $end"
}

/** The date range picker reports each picked day as UTC midnight, so it's formatted in UTC to get that calendar date back. */
private fun pickerDay(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LeavePickerDialog(onDismiss: () -> Unit, onPicked: (String, String) -> Unit) {
    val todayUtcMidnight = remember {
        val todayLocal = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(todayLocal)!!.time
    }
    val state = rememberDateRangePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtcMidnight
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = state.selectedStartDateMillis ?: return@TextButton
                    // One tap picks a single day.
                    val end = state.selectedEndDateMillis ?: start
                    onPicked(pickerDay(start), pickerDay(end))
                },
                enabled = state.selectedStartDateMillis != null
            ) { Text("Save leave") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DateRangePicker(
            state = state,
            title = { Text("Pick your leave days", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            modifier = Modifier.heightIn(max = 520.dp)
        )
    }
}
