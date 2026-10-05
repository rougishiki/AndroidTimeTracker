package com.timetrack.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timetrack.app.data.Todo
import com.timetrack.app.data.TodoPeriod
import com.timetrack.app.data.TodoScope
import com.timetrack.app.ui.theme.Radius
import com.timetrack.app.ui.theme.Space
import com.timetrack.app.util.Fmt
import java.time.LocalDate
import java.time.YearMonth

/**
 * The todo tab.
 *
 * One screen holds both lists because they belong together: the daily view shows
 * the week's items underneath the day's own, so a weekly item can be ticked off
 * from whichever view the user happens to be in. Ticking it finishes it for the
 * whole week, which is what "I finished one of this week's things today" means.
 */
@Composable
fun TodoScreen(vm: TodoViewModel) {
    val mode by vm.mode.collectAsStateWithLifecycle()
    val dayKey by vm.dayKey.collectAsStateWithLifecycle()
    val dayTodos by vm.dayTodos.collectAsStateWithLifecycle()
    val weekTodos by vm.weekTodos.collectAsStateWithLifecycle()
    val openDays by vm.openDays.collectAsStateWithLifecycle()
    val openWeeks by vm.openWeeks.collectAsStateWithLifecycle()
    val pickerMonth by vm.pickerMonth.collectAsStateWithLifecycle()

    var showDayPicker by rememberSaveable { mutableStateOf(false) }
    var showWeekPicker by rememberSaveable { mutableStateOf(false) }
    var addingWeekItem by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Todo?>(null) }

    val onToday = vm.isOnToday()
    val currentWeek = weekTodos

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.pageH, vertical = Space.pageV),
        verticalArrangement = Arrangement.spacedBy(Space.xl),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ModeSwitch(mode = mode, onSelect = { vm.setMode(it) })
            }
            // Beside the mode switch rather than on a row of its own: giving a
            // single secondary action its own row spends a whole line of the
            // screen on something rarely pressed.
            if (!onToday || mode == TodoScope.WEEK) {
                TextButton(onClick = { vm.goToToday() }) { Text("回到今天") }
            }
        }

        PeriodNavigator(
            label = if (mode == TodoScope.DAY) {
                vm.dayLabel(dayKey)
            } else {
                currentWeek?.let { vm.weekLabel(it.weekKey) } ?: "正在载入…"
            },
            onPrev = { vm.shift(-1) },
            onNext = { vm.shift(1) },
            onPick = { if (mode == TodoScope.DAY) showDayPicker = true else showWeekPicker = true },
        )

        when (mode) {
            TodoScope.DAY -> {
                val day = dayTodos
                if (day == null) {
                    LoadingText()
                } else {
                    TodoListCard(
                        title = dayListTitle(dayKey, vm),
                        items = day.own,
                        vm = vm,
                        inputHint = "今天要做什么？",
                        emptyHint = "今天还没有待办，在上面输入一条。",
                        onSubmit = { vm.add(it) },
                        onRename = { renaming = it },
                    )
                    WeekSection(
                        items = day.weekOpen,
                        doneThatDay = day.weekDoneThatDay,
                        vm = vm,
                        onAdd = { addingWeekItem = true },
                        onRename = { renaming = it },
                    )
                }
            }

            TodoScope.WEEK -> {
                val week = currentWeek
                if (week == null) {
                    LoadingText()
                } else {
                    TodoListCard(
                        title = "本周",
                        items = week.items,
                        vm = vm,
                        inputHint = "这周要完成什么？",
                        emptyHint = "本周还没有待办，在上面输入一条。",
                        onSubmit = { vm.add(it) },
                        onRename = { renaming = it },
                    )
                }
            }
        }

        // The three-line explanation that used to sit here now lives in the help
        // dialog, reachable from the timer screen's ⓘ.
    }

    if (showDayPicker) {
        DayPickerDialog(
            month = pickerMonth,
            selectedDayKey = dayKey,
            todayKey = vm.todayKey(),
            openDays = openDays,
            onShowMonth = { vm.showMonth(it) },
            onSelect = { vm.selectDay(it); showDayPicker = false },
            onDismiss = { showDayPicker = false },
        )
    }

    if (showWeekPicker) {
        WeekPickerDialog(
            month = pickerMonth,
            selectedWeekKey = currentWeek?.weekKey,
            openWeeks = openWeeks,
            onShowMonth = { vm.showMonth(it) },
            onSelect = { vm.selectDay(it); showWeekPicker = false },
            onDismiss = { showWeekPicker = false },
        )
    }

    if (addingWeekItem) {
        TextInputDialog(
            title = "添加到本周",
            label = "这周要完成什么？",
            initial = "",
            onConfirm = { vm.addToWeek(it); addingWeekItem = false },
            onDismiss = { addingWeekItem = false },
        )
    }

    renaming?.let { todo ->
        TextInputDialog(
            title = "重命名",
            label = "待办内容",
            initial = todo.title,
            onConfirm = { vm.rename(todo, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
}

/** `今天` / `昨天` / `10月3日`, so the block heading names the day it shows. */
private fun dayListTitle(dayKey: String, vm: TodoViewModel): String {
    val date = TodoPeriod.parseDayKey(dayKey)
    val today = TodoPeriod.parseDayKey(vm.todayKey())
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> Fmt.monthDay(date)
    }
}

@Composable
private fun LoadingText() {
    Text(
        text = "正在载入…",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ModeSwitch(mode: TodoScope, onSelect: (TodoScope) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        // Named by granularity, matching the statistics screen. These used to read
        // "今天"/"本周", which sounded like two different pages rather than the two
        // windows the statistics screen already calls 日 and 周.
        val options = listOf(TodoScope.DAY to "日", TodoScope.WEEK to "周")
        options.forEachIndexed { index, (scope, label) ->
            SegmentedButton(
                selected = mode == scope,
                onClick = { onSelect(scope) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun PeriodNavigator(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一期")
        }
        Text(
            text = label,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onPick)
                .padding(vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onPick) {
            Icon(Icons.Filled.DateRange, contentDescription = "选择日期")
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一期")
        }
    }
}

@Composable
private fun TodoInput(hint: String, onSubmit: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    fun submit() {
        val text = value.trim()
        if (text.isEmpty()) return
        onSubmit(text)
        value = ""
        focusManager.clearFocus()
    }

    // A bare row rather than an outlined field: inside a card, a second box drawn
    // around the input reads as a box inside a box. The placeholder does the
    // labelling, and unlike a floating label it gets out of the way once there is
    // something to read.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.lg, end = Space.sm, top = Space.sm, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
        }
        if (value.isNotBlank()) {
            IconButton(onClick = { submit() }) {
                Icon(Icons.Filled.Check, contentDescription = "添加")
            }
        }
    }
}

/**
 * One list, one card, with the add row as its first line.
 *
 * A list of things to do *is* an object, so it earns a container — the earlier
 * version went too far in the other direction and left the page as one
 * undifferentiated column. Putting the input *inside* the card is what makes it
 * read as "this is the list" rather than as decoration around a list, and it
 * means the page has exactly one place to type.
 *
 * Unfinished items come first — the DAO already orders them that way — and the
 * finished ones sit below a divider rather than disappearing, so the day still
 * shows what it got done.
 */
@Composable
private fun TodoListCard(
    title: String,
    items: List<Todo>,
    vm: TodoViewModel,
    inputHint: String,
    emptyHint: String,
    onSubmit: (String) -> Unit,
    onRename: (Todo) -> Unit,
) {
    val open = items.filter { it.doneAt == null }
    val done = items.filter { it.doneAt != null }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg, vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    text = if (open.isEmpty() && done.isNotEmpty()) {
                        "全部完成"
                    } else {
                        "还剩 ${open.size} 项"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            TodoInput(hint = inputHint, onSubmit = onSubmit)

            // One rule, not two: the header, the input and the items are one
            // object, and a line above *and* below the input was furniture.
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (items.isEmpty()) {
                Text(
                    text = emptyHint,
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.lg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            open.forEach { todo ->
                TodoRow(todo = todo, vm = vm, onRename = { onRename(todo) })
            }

            if (done.isNotEmpty()) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = Space.lg),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    text = "已完成 ${done.size} 项",
                    modifier = Modifier.padding(
                        start = Space.lg,
                        end = Space.lg,
                        top = Space.md,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                done.forEach { todo ->
                    TodoRow(todo = todo, vm = vm, onRename = { onRename(todo) })
                }
            }
        }
    }
}

/**
 * The week's items as seen from one day: whatever was still open, and a note of
 * what that day finished.
 *
 * Collapsible because a full week can run long, and the daily list must stay
 * readable next to it.
 */
@Composable
private fun WeekSection(
    items: List<Todo>,
    doneThatDay: List<Todo>,
    vm: TodoViewModel,
    onAdd: () -> Unit,
    onRename: (Todo) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(true) }

    // Outlined rather than filled, and the same shape as the daily list: the day
    // is the main object, the week is context for it. One step quieter, not a
    // different kind of thing.
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.lg, end = Space.sm, top = Space.sm, bottom = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "本周",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    text = if (items.isEmpty()) "没有未完成的" else "还剩 ${items.size} 项",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // The page has one add control now. A second bare "+" up here made
                // the user guess which one added what, so this one is labelled.
                TextButton(onClick = onAdd) { Text("添加") }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Filled.KeyboardArrowUp
                        } else {
                            Icons.Filled.KeyboardArrowDown
                        },
                        contentDescription = if (expanded) "收起" else "展开",
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (items.isEmpty() && doneThatDay.isEmpty()) {
                    Text(
                        text = "本周还没有待办。添加一条，它会在这一周每天都出现。",
                        modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.lg),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items.forEach { todo ->
                    TodoRow(todo = todo, vm = vm, onRename = { onRename(todo) })
                }
                if (doneThatDay.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = Space.lg),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = "当天完成的本周事项",
                        modifier = Modifier.padding(
                            start = Space.lg,
                            end = Space.lg,
                            top = Space.md,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    doneThatDay.forEach { todo ->
                        TodoRow(todo = todo, vm = vm, onRename = { onRename(todo) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TodoRow(todo: Todo, vm: TodoViewModel, onRename: () -> Unit) {
    val tap = rememberFirmTap()
    val done = todo.doneAt != null
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                tap()
                vm.delete(todo)
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.control))
                .combinedClickable(
                    onClick = {
                        tap()
                        vm.toggle(todo)
                    },
                    onLongClick = onRename,
                ),
            shape = RoundedCornerShape(Radius.control),
            color = Color.Transparent,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (done) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "已完成",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            ),
                    )
                }
                Spacer(Modifier.width(Space.md))
                Text(
                    text = todo.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (done) TextDecoration.LineThrough else null,
                    color = if (done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// --- pickers -----------------------------------------------------------------

@Composable
private fun DayPickerDialog(
    month: YearMonth,
    selectedDayKey: String,
    todayKey: String,
    openDays: Set<String>,
    onShowMonth: (YearMonth) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MonthHeader(month = month, onShowMonth = onShowMonth)

                Row(modifier = Modifier.fillMaxWidth()) {
                    // Monday first, regardless of the device locale: Material's own
                    // DatePicker takes the first day of the week from the locale and
                    // cannot be told otherwise.
                    Fmt.WEEKDAY_HEADS.forEach { head ->
                        Text(
                            text = head,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                TodoPeriod.monthRows(month).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { date ->
                            DayCell(
                                date = date,
                                month = month,
                                selectedDayKey = selectedDayKey,
                                todayKey = todayKey,
                                hasOpen = openDays.contains(TodoPeriod.dayKey(date)),
                                onClick = { onSelect(TodoPeriod.dayKey(date)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Text(
                    text = "带圆点的日期还有未完成的待办。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun DayCell(
    date: LocalDate,
    month: YearMonth,
    selectedDayKey: String,
    todayKey: String,
    hasOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val key = TodoPeriod.dayKey(date)
    val inMonth = YearMonth.from(date) == month
    val selected = key == selectedDayKey
    val isToday = key == todayKey

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    selected -> MaterialTheme.colorScheme.primary
                    inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasOpen) MaterialTheme.colorScheme.error else Color.Transparent,
                    ),
            )
        }
    }
}

@Composable
private fun WeekPickerDialog(
    month: YearMonth,
    selectedWeekKey: String?,
    openWeeks: Set<String>,
    onShowMonth: (YearMonth) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MonthHeader(month = month, onShowMonth = onShowMonth)
                // One row per ISO week touching the month, keyed by its Monday so
                // a week that straddles two months appears exactly once.
                val mondays = TodoPeriod.monthRows(month).map { it.first() }.distinct()
                mondays.forEach { monday ->
                    val weekKey = TodoPeriod.weekKey(monday)
                    val selected = weekKey == selectedWeekKey
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelect(TodoPeriod.dayKey(monday)) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "第 ${TodoPeriod.weekNumber(monday)} 周",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = Fmt.weekRange(monday),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (openWeeks.contains(weekKey)) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error),
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun MonthHeader(month: YearMonth, onShowMonth: (YearMonth) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onShowMonth(month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
        }
        Text(
            text = Fmt.monthTitle(month),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = { onShowMonth(month.plusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
        }
    }
}
