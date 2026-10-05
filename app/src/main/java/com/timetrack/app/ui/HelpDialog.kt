package com.timetrack.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Everything the app used to explain by leaving a paragraph on the screen.
 *
 * Those paragraphs were a symptom, not a feature: a list that needs three lines
 * under it to be usable is a list with a design problem. Moving them here does
 * not fix that, but it stops the explanation from competing with the content,
 * and it puts all of it in one place instead of four.
 */
@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("使用说明") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HelpSection(
                    title = "计时",
                    lines = listOf(
                        "输入任务名后回车即可开始；开始新任务会自动结束上一个。",
                        "「补录时间」用来事后补上一段，不会影响正在进行的计时。",
                        "「管理任务」可以重命名、归档、恢复。重命名为已有的名字会把两者合并，历史记录都保留。",
                        "通知栏的「停止计时」按钮可以一步停止。",
                        "长按桌面图标可以直接开始最近用过的任务。",
                    ),
                )
                HelpSection(
                    title = "待办",
                    lines = listOf(
                        "「日」和「周」是两个清单。日清单每天早上都是空的，昨天的事不会自动延续。",
                        "日清单下方常驻显示本周还没完成的事项；在那里勾掉一条，即整周完成。",
                        "点标题可以跳到任意一天，或打开周列表跳到任意一周。",
                        "左滑删除，长按重命名。",
                    ),
                )
                HelpSection(
                    title = "统计",
                    lines = listOf(
                        "「日 / 周 / 月」三种粒度。周和月会与上一期并排对比。",
                        "点某一项，可以查看并修改它这一天的时间段——记错了、忘了按停止，都能修。",
                        "一段连续计时超过 8 小时，计时页会提醒确认是否还在进行。",
                    ),
                )
                HelpSection(
                    title = "备份",
                    lines = listOf(
                        "CSV 给表格软件看：一行一段记录。",
                        "JSON 是完整备份，换手机时用这个。",
                        "数据只存在这台手机上，卸载应用会一并删除。",
                    ),
                )
            }
        },
    )
}

@Composable
private fun HelpSection(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        lines.forEach { line ->
            Text(
                text = "· $line",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
