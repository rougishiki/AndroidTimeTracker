package com.timetrack.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.timetrack.app.data.ExportRange
import com.timetrack.app.util.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExportScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy by vm.exportBusy.collectAsStateWithLifecycle()

    // Kept in a state holder so the ActivityResult callbacks -- which outlive any
    // individual recomposition -- always observe the currently selected range.
    val rangeState = rememberSaveable { mutableStateOf(ExportRange.TODAY) }
    val range = rangeState.value

    fun performExport(uri: Uri, asCsv: Boolean) {
        scope.launch {
            vm.markExporting()
            try {
                val payload = vm.renderExport(rangeState.value, asCsv)
                withContext(Dispatchers.IO) {
                    // SAF grants write access to exactly this Uri, so the app needs
                    // no storage permission at all.
                    val stream = context.contentResolver.openOutputStream(uri)
                        ?: error("无法写入所选文件")
                    stream.use { it.write(payload.content.toByteArray(Charsets.UTF_8)) }
                }
                vm.exportFinished(payload)
            } catch (t: Throwable) {
                vm.exportFailed(t.message ?: "未知错误")
            }
        }
    }

    val csvPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> if (uri != null) performExport(uri, asCsv = true) }

    val jsonPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) performExport(uri, asCsv = false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("范围", style = MaterialTheme.typography.titleMedium)
                ExportRange.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { option ->
                            FilterChip(
                                selected = range == option,
                                onClick = { rangeState.value = option },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("格式", style = MaterialTheme.typography.titleMedium)

                Button(
                    onClick = {
                        csvPicker.launch(
                            Exporter.suggestedFileName("csv", System.currentTimeMillis()),
                        )
                    },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("保存 CSV")
                }
                Text(
                    text = "每段计时一行，用 Excel / WPS 打开。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(4.dp))

                OutlinedButton(
                    onClick = {
                        jsonPicker.launch(
                            Exporter.suggestedFileName("json", System.currentTimeMillis()),
                        )
                    },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("保存 JSON")
                }
                Text(
                    text = "完整备份：计时记录按所选范围导出，待办列表全量导出。换手机时用这个。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            text = "保存位置由系统文件选择器决定（可以存到「下载」或网盘目录）。应用不申请任何存储权限，也只会在你选定的那个文件里写入。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
