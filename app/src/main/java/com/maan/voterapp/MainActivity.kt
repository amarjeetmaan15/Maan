package com.maan.voterapp

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val dao by lazy { VoterDatabase.get(this).dao() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VoterTheme { VoterApp(dao) } }
    }
}

private enum class Screen { HOME, CHECK, COUNT, RECYCLE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoterApp(dao: VoterDao) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.HOME) }
    var voters by remember { mutableStateOf(emptyList<Voter>()) }
    var candidates by remember { mutableStateOf(emptyList<Candidate>()) }
    var deletedVoters by remember { mutableStateOf(emptyList<Voter>()) }
    var deletedCandidates by remember { mutableStateOf(emptyList<Candidate>()) }
    var wards by remember { mutableStateOf(emptyList<String>()) }
    var selectedWard by remember { mutableStateOf("ALL") }
    var candidateInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmText by remember { mutableStateOf<String?>(null) }
    var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun refresh() {
        scope.launch {
            voters = dao.all()
            wards = dao.wards()
            candidates = dao.candidates()
            deletedVoters = dao.recycleVoters()
            deletedCandidates = dao.recycleCandidates()
        }
    }
    fun askDelete(question: String, action: () -> Unit) {
        confirmText = question
        confirmAction = action
    }
    fun importFile(uri: Uri) {
        scope.launch {
            busy = true
            message = "Excel पढ़ी जा रही है…"
            val result = runCatching {
                val imported = withContext(Dispatchers.IO) { ExcelImporter.read(context, uri) }
                dao.softDeleteAll(System.currentTimeMillis())
                dao.insertAll(imported)
                "सफलतापूर्वक ${imported.size} वोटर्स लोड हुए।"
            }
            message = result.getOrElse { it.message ?: "Excel पढ़ने में समस्या हुई।" }
            selectedWard = "ALL"
            busy = false
            refresh()
        }
    }
    val excelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) {
        if (voters.isNotEmpty()) askDelete(
            "मौजूदा voter list को हटाकर नई Excel list लोड करनी है? पुरानी list Recycle Bin में सुरक्षित रहेगी।"
        ) { importFile(uri); confirmText = null; confirmAction = null }
        else importFile(uri)
    } }

    LaunchedEffect(Unit) { refresh() }
    val shown = if (selectedWard == "ALL") voters else voters.filter { it.ward == selectedWard }
    val green = shown.count { it.attendance == "GREEN" }
    val yellow = shown.count { it.attendance == "YELLOW" }
    val red = shown.count { it.attendance == "RED" }
    val unchecked = shown.count { it.attendance == "NONE" }
    val counted = shown.count { it.selectedCandidate.isNotBlank() }
    val nota = shown.count { it.selectedCandidate == "NOTA" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(when(screen) {
                    Screen.HOME -> "Voter Checking & Counting"
                    Screen.CHECK -> "Check Voters"
                    Screen.COUNT -> "Vote Counting"
                    Screen.RECYCLE -> "Recycle Bin"
                }, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (screen != Screen.HOME) TextButton(onClick = { screen = Screen.HOME; refresh() }) { Text("Back") }
                },
                actions = {
                    if (screen != Screen.RECYCLE) {
                        TextButton(onClick = { screen = Screen.RECYCLE; refresh() }) { Text("Recycle Bin") }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (screen) {
                Screen.HOME -> {
                    Text("मुख्य विकल्प", style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = { screen = Screen.CHECK; refresh() }, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                        Text("Check Voters", style = MaterialTheme.typography.titleMedium)
                    }
                    Button(onClick = { screen = Screen.COUNT; refresh() }, modifier = Modifier.fillMaxWidth().height(58.dp)) {
                        Text("Vote Counting", style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedButton(
                        onClick = { excelPicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel")) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    ) { Text(if (busy) "Please wait…" else "Upload Excel File (.xlsx)") }
                    if (voters.isNotEmpty()) {
                        Text("वर्तमान voter list: ${voters.size} रिकॉर्ड")
                        OutlinedButton(
                            onClick = { askDelete("क्या आप पूरी voter list को Recycle Bin में भेजना चाहते हैं?") {
                                scope.launch { dao.softDeleteAll(System.currentTimeMillis()); message = "Voter list Recycle Bin में भेज दी गई।"; refresh() }
                                confirmText = null; confirmAction = null
                            } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Delete Complete Voter List") }
                    }
                    if (message.isNotBlank()) Text(message, color = MaterialTheme.colorScheme.primary)
                    Text("डेटा इसी फोन में सेव रहता है। इंटरनेट या Login की जरूरत नहीं है।", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Screen.CHECK, Screen.COUNT -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ward", style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { excelPicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel")) }) { Text("Upload / Replace Excel") }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = selectedWard == "ALL", onClick = { selectedWard = "ALL" }, label = { Text("ALL") })
                        wards.forEach { ward ->
                            FilterChip(selected = selectedWard == ward, onClick = { selectedWard = ward }, label = { Text("Ward $ward") })
                        }
                    }
                    if (screen == Screen.CHECK) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SummaryTile("Total", shown.size)
                            SummaryTile("Green", green, Color(0xFF168447))
                            SummaryTile("Yellow", yellow, Color(0xFFB8860B))
                            SummaryTile("Red", red, Color(0xFFC62828))
                            SummaryTile("Not Checked", unchecked)
                        }
                    } else {
                        Text("Total counted: $counted   •   NOTA: $nota", fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            candidates.forEach { candidate ->
                                SummaryTile(candidate.name, shown.count { it.selectedCandidate == candidate.name })
                            }
                            SummaryTile("NOTA", nota)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = candidateInput,
                                onValueChange = { candidateInput = it },
                                label = { Text("Candidate Name") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(onClick = {
                                val name = candidateInput.trim()
                                if (name.isNotEmpty() && candidates.none { it.name.equals(name, true) }) {
                                    scope.launch { dao.insertCandidate(Candidate(name = name)); candidateInput = ""; refresh() }
                                }
                            }) { Text("Add") }
                        }
                        if (candidates.isNotEmpty()) {
                            Text("Candidates (tap × to move a name to Recycle Bin)", style = MaterialTheme.typography.labelMedium)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                candidates.forEach { c ->
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(c.name) },
                                        trailingIcon = {
                                            TextButton(
                                                onClick = { askDelete("क्या आप candidate “${c.name}” को Recycle Bin में भेजना चाहते हैं?") {
                                                    scope.launch { dao.softDeleteCandidate(c.id, System.currentTimeMillis()); voters.filter { it.selectedCandidate == c.name }.forEach { dao.update(it.copy(selectedCandidate = "")) }; refresh() }
                                                    confirmText = null; confirmAction = null
                                                } },
                                                contentPadding = PaddingValues(0.dp)
                                            ) { Text("×") }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (voters.isEmpty()) {
                        Text("अभी कोई voter data नहीं है। पहले Excel File Upload करें।", color = MaterialTheme.colorScheme.error)
                    } else if (shown.isEmpty()) {
                        Text("इस Ward में कोई voter नहीं मिला।")
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 18.dp)
                        ) {
                            items(shown, key = { it.id }) { voter ->
                                VoterCard(
                                    voter = voter,
                                    counting = screen == Screen.COUNT,
                                    candidates = candidates.map { it.name },
                                    onAttendance = { value -> scope.launch { dao.update(voter.copy(attendance = value)); refresh() } },
                                    onVote = { value -> scope.launch { dao.update(voter.copy(selectedCandidate = value)); refresh() } },
                                    onDelete = {
                                        askDelete("क्या आप voter “${voter.name}” को डिलीट करना चाहते हैं? वह Recycle Bin में जाएगा।") {
                                            scope.launch { dao.softDeleteVoter(voter.id, System.currentTimeMillis()); refresh() }
                                            confirmText = null; confirmAction = null
                                        }
                                    }
                                )
                            }
                        }
                    }
                    if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodySmall)
                }
                Screen.RECYCLE -> {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            askDelete("क्या आप Recycle Bin के सभी रिकॉर्ड हमेशा के लिए डिलीट करना चाहते हैं?") {
                                scope.launch { dao.emptyVoterBin(); dao.emptyCandidateBin(); refresh() }
                                confirmText = null; confirmAction = null
                            }
                        }) { Text("Empty Recycle Bin") }
                        OutlinedButton(onClick = {
                            scope.launch { deletedVoters.forEach { dao.restoreVoter(it.id) }; deletedCandidates.forEach { dao.restoreCandidate(it.id) }; refresh() }
                        }) { Text("Restore All") }
                    }
                    Text("Deleted Voters", style = MaterialTheme.typography.titleMedium)
                    if (deletedVoters.isEmpty()) Text("कोई deleted voter नहीं है।")
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(deletedVoters, key = { "v${it.id}" }) { voter ->
                            Card {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${voter.serial}. ${voter.name}", fontWeight = FontWeight.Medium)
                                        Text("Ward ${voter.ward} • EPIC ${voter.epic.ifBlank { "—" }}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = { scope.launch { dao.restoreVoter(voter.id); refresh() } }) { Text("Recover") }
                                    TextButton(onClick = { askDelete("इस voter को हमेशा के लिए डिलीट करना है?") {
                                        scope.launch { dao.permanentlyDeleteVoter(voter.id); refresh() }
                                        confirmText = null; confirmAction = null
                                    } }) { Text("Delete") }
                                }
                            }
                        }
                        item { Text("Deleted Candidates", style = MaterialTheme.typography.titleMedium) }
                        items(deletedCandidates, key = { "c${it.id}" }) { candidate ->
                            Card {
                                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(candidate.name, Modifier.weight(1f))
                                    TextButton(onClick = { scope.launch { dao.restoreCandidate(candidate.id); refresh() } }) { Text("Recover") }
                                    TextButton(onClick = { askDelete("इस candidate को हमेशा के लिए डिलीट करना है?") {
                                        scope.launch { dao.permanentlyDeleteCandidate(candidate.id); refresh() }
                                        confirmText = null; confirmAction = null
                                    } }) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmText != null) {
        AlertDialog(
            onDismissRequest = { confirmText = null; confirmAction = null },
            title = { Text("Are you sure?") },
            text = { Text(confirmText.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { confirmAction?.invoke(); confirmText = null; confirmAction = null }) {
                    Text("Yes, continue", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmText = null; confirmAction = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun VoterCard(
    voter: Voter,
    counting: Boolean,
    candidates: List<String>,
    onAttendance: (String) -> Unit,
    onVote: (String) -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("${voter.serial}. ${voter.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (voter.relation.isNotBlank()) Text(voter.relation, style = MaterialTheme.typography.bodyMedium)
            Text("EPIC: ${voter.epic.ifBlank { "—" }}     Ward: ${voter.ward}", style = MaterialTheme.typography.bodySmall)
            if (!counting) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AttendanceChoice("✓", "Green", Color(0xFF168447), voter.attendance == "GREEN") { onAttendance("GREEN") }
                    AttendanceChoice("✓", "Yellow", Color(0xFFB8860B), voter.attendance == "YELLOW") { onAttendance("YELLOW") }
                    AttendanceChoice("✕", "Red", Color(0xFFC62828), voter.attendance == "RED") { onAttendance("RED") }
                }
            } else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    candidates.forEach { candidate ->
                        FilterChip(selected = voter.selectedCandidate == candidate, onClick = { onVote(candidate) }, label = { Text(candidate) })
                    }
                    FilterChip(selected = voter.selectedCandidate == "NOTA", onClick = { onVote("NOTA") }, label = { Text("NOTA") })
                    if (voter.selectedCandidate.isNotBlank()) {
                        TextButton(onClick = { onVote("") }) { Text("Clear") }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun AttendanceChoice(label: String, name: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) color else color.copy(alpha = 0.16f),
            contentColor = if (selected) Color.White else color
        ),
        contentPadding = PaddingValues(horizontal = 5.dp, vertical = 8.dp)
    ) {
        Text("$label  $name", maxLines = 1)
    }
}

@Composable
private fun SummaryTile(label: String, value: Int, accent: Color = MaterialTheme.colorScheme.primary) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), color = accent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun VoterTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
