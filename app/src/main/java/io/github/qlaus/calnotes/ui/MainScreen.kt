package io.github.qlaus.calnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.qlaus.calnotes.R
import io.github.qlaus.calnotes.ui.calendar.CalendarPane
import io.github.qlaus.calnotes.ui.calendar.CalendarViewModel
import io.github.qlaus.calnotes.ui.calendar.DetailTarget
import io.github.qlaus.calnotes.ui.event.EventDetailPane
import io.github.qlaus.calnotes.ui.event.EventEditorPane
import io.github.qlaus.calnotes.ui.note.NoteEditorPane

private val TwoPaneMinWidth = 700.dp

/**
 * Adaptive layout: on wide windows (tablet) calendar on the left and the detail pane on the right;
 * on narrow windows the detail pane replaces the calendar.
 */
@Composable
fun MainScreen(calendarViewModel: CalendarViewModel, accountEmail: String?, onSignOut: () -> Unit) {
    val detail by calendarViewModel.detail.collectAsState()
    BackHandler(enabled = detail != null) { calendarViewModel.back() }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val twoPane = maxWidth >= TwoPaneMinWidth
            if (twoPane) {
                Row(Modifier.fillMaxSize()) {
                    CalendarPane(calendarViewModel, accountEmail, onSignOut, Modifier.weight(0.6f).fillMaxHeight())
                    VerticalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.weight(0.4f).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                        DetailPane(detail, calendarViewModel, closeIsBack = false)
                    }
                }
            } else {
                if (detail == null) {
                    CalendarPane(calendarViewModel, accountEmail, onSignOut, Modifier.fillMaxSize())
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                        DetailPane(detail, calendarViewModel, closeIsBack = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailPane(target: DetailTarget?, vm: CalendarViewModel, closeIsBack: Boolean) {
    val stackDepthGreaterThanOne = vm.stackDepth() > 1
    when (target) {
        null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.select_event_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is DetailTarget.Event -> EventDetailPane(
            calendarId = target.calendarId,
            eventId = target.eventId,
            canGoBack = stackDepthGreaterThanOne || closeIsBack,
            onBack = vm::back,
            onEdit = { vm.push(DetailTarget.EditEvent(target.calendarId, target.eventId)) },
            onOpenNote = { vm.push(DetailTarget.Note(it)) },
            onDeleted = vm::back,
        )
        is DetailTarget.Note -> NoteEditorPane(
            noteId = target.noteId,
            canGoBack = stackDepthGreaterThanOne || closeIsBack,
            onBack = vm::back,
            onOpenEvent = { c, e -> vm.push(DetailTarget.Event(c, e)) },
            onDeleted = vm::back,
        )
        is DetailTarget.EditEvent -> EventEditorPane(
            calendarId = target.calendarId,
            eventId = target.eventId,
            presetDate = target.presetDate,
            presetTime = target.presetTime,
            canGoBack = stackDepthGreaterThanOne || closeIsBack,
            onBack = vm::back,
            // New events get their Google id only after upload, so return to the previous view.
            onSaved = { _, _ -> vm.back() },
        )
    }
}
