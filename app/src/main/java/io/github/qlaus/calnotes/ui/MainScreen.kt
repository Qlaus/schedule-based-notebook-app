package io.github.qlaus.calnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.qlaus.calnotes.R
import io.github.qlaus.calnotes.ui.calendar.CalendarPane
import io.github.qlaus.calnotes.ui.calendar.CalendarViewModel
import io.github.qlaus.calnotes.ui.event.EventDetailPane
import io.github.qlaus.calnotes.ui.event.EventEditorPane
import io.github.qlaus.calnotes.ui.nav.DetailTarget
import io.github.qlaus.calnotes.ui.nav.NavViewModel
import io.github.qlaus.calnotes.ui.nav.Section
import io.github.qlaus.calnotes.ui.note.NoteEditorPane
import io.github.qlaus.calnotes.ui.notebook.NotebooksPane
import io.github.qlaus.calnotes.ui.search.SearchPane
import io.github.qlaus.calnotes.ui.settings.SettingsPane

private val TwoPaneMinWidth = 700.dp

private data class NavItem(val section: Section, val label: Int, val icon: ImageVector)

private val NavItems = listOf(
    NavItem(Section.CALENDAR, R.string.nav_calendar, Icons.Outlined.CalendarMonth),
    NavItem(Section.NOTEBOOKS, R.string.nav_notebooks, Icons.Outlined.Book),
    NavItem(Section.SEARCH, R.string.nav_search, Icons.Outlined.Search),
    NavItem(Section.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings),
)

/**
 * Adaptive layout. Wide windows (tablet): navigation rail | section pane | detail pane.
 * Narrow windows: section pane with a bottom bar; the detail pane replaces it when open.
 */
@Composable
fun MainScreen(accountEmail: String?, onSignOut: () -> Unit) {
    val nav: NavViewModel = hiltViewModel()
    val calendarViewModel: CalendarViewModel = hiltViewModel()
    val section by nav.section.collectAsState()
    val stack by nav.stack.collectAsState()
    val notebookSelection by nav.notebookSelection.collectAsState()
    val noteFullscreen by nav.noteFullscreen.collectAsState()
    val detail = stack.lastOrNull()

    BackHandler(enabled = stack.isNotEmpty() || section != Section.CALENDAR || notebookSelection != io.github.qlaus.calnotes.ui.nav.NotebookSelection.All) {
        nav.handleBack()
    }

    val sectionPane: @Composable (Modifier) -> Unit = { modifier ->
        when (section) {
            Section.CALENDAR -> CalendarPane(calendarViewModel, onShow = nav::show, modifier = modifier)
            Section.NOTEBOOKS -> NotebooksPane(
                selection = notebookSelection,
                onSelect = nav::selectNotebook,
                onOpenNote = { nav.show(DetailTarget.Note(it)) },
                modifier = modifier,
            )
            Section.SEARCH -> SearchPane(onShow = nav::show, modifier = modifier)
            Section.SETTINGS -> SettingsPane(accountEmail, onSignOut, modifier)
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide = maxWidth >= TwoPaneMinWidth
            val fullscreenNote = wide && noteFullscreen && detail is DetailTarget.Note
            ImmersiveMode(enabled = fullscreenNote)
            if (fullscreenNote) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    DetailPane(detail, stack.size, nav, closeIsBack = false, wide = true, fullscreen = true)
                }
            } else if (wide) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.background) {
                        Spacer(Modifier.padding(top = 8.dp))
                        NavItems.forEach { item ->
                            NavigationRailItem(
                                selected = section == item.section,
                                onClick = { nav.selectSection(item.section) },
                                icon = { Icon(item.icon, null) },
                                label = { Text(stringResource(item.label)) },
                            )
                        }
                    }
                    VerticalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    sectionPane(Modifier.weight(0.58f).fillMaxHeight())
                    VerticalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.weight(0.42f).fillMaxHeight().background(MaterialTheme.colorScheme.surface)) {
                        DetailPane(detail, stack.size, nav, closeIsBack = false, wide = true, fullscreen = false)
                    }
                }
            } else if (detail != null) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                    DetailPane(detail, stack.size, nav, closeIsBack = true, wide = false, fullscreen = false)
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    sectionPane(Modifier.weight(1f))
                    NavigationBar {
                        NavItems.forEach { item ->
                            NavigationBarItem(
                                selected = section == item.section,
                                onClick = { nav.selectSection(item.section) },
                                icon = { Icon(item.icon, null) },
                                label = { Text(stringResource(item.label)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailPane(target: DetailTarget?, depth: Int, nav: NavViewModel, closeIsBack: Boolean, wide: Boolean, fullscreen: Boolean) {
    val canGoBack = depth > 1 || closeIsBack
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
            canGoBack = canGoBack,
            onBack = nav::back,
            onEdit = { nav.push(DetailTarget.EditEvent(target.calendarId, target.eventId)) },
            onOpenNote = { nav.push(DetailTarget.Note(it)) },
            onDeleted = nav::back,
        )
        is DetailTarget.Note -> NoteEditorPane(
            noteId = target.noteId,
            canGoBack = canGoBack,
            onBack = {
                nav.setNoteFullscreen(false)
                nav.back()
            },
            fullscreen = fullscreen,
            onToggleFullscreen = if (wide) ({ nav.setNoteFullscreen(!fullscreen) }) else null,
            onOpenEvent = { c, e -> nav.push(DetailTarget.Event(c, e)) },
            onDeleted = nav::back,
        )
        is DetailTarget.EditEvent -> EventEditorPane(
            calendarId = target.calendarId,
            eventId = target.eventId,
            presetDate = target.presetDate,
            presetTime = target.presetTime,
            canGoBack = canGoBack,
            onBack = nav::back,
            // New events get their Google id only after upload, so return to the previous view.
            onSaved = { _, _ -> nav.back() },
        )
    }
}

/** Hides the status and navigation bars while the note is full screen (swipe from an edge shows them). */
@Composable
private fun ImmersiveMode(enabled: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(enabled) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        if (enabled && controller != null) {
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }
}
