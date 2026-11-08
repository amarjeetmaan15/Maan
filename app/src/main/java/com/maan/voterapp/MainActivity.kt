package com.maan.voterapp

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val dao by lazy { VoterDatabase.get(this).dao() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { VoterApp(dao, ::importExcel, this) } }
    }
    private fun importExcel(uri: android.net.Uri, done: (String) -> Unit) {
        lifecycleScope.launch {
            runCatching {
                val rows = ExcelImporter.read(this@MainActivity, uri)
                dao.clear()
                dao.insertAll(rows)
                "लोड हुए: ${rows.size} वोटर्स"
            }.onSuccess(done).onFailure { done(it.message ?: "Excel पढ़ने में समस्या") }
        }
    }
}

@Composable
private fun VoterApp(dao: VoterDao, importExcel: (android.net.Uri, (String) -> Unit) -> Unit, context: Context) {
    var page by remember { mutableStateOf("HOME") }
    var mode by remember { mutableStateOf("CHECK") }
    var rows by remember { mutableStateOf(emptyList<Voter>()) }
    var wards by remember { mutableStateOf(emptyList<String>()) }
    val prefs = remember { context.getSharedPreferences("voter_app_state", Context.MODE_PRIVATE) }
    var selectedWard by remember { mutableStateOf(prefs.getString("last_ward", "ALL") ?: "ALL") }
    var candidates by remember { mutableStateOf(prefs.getStringSet("candidates", emptySet())?.toList() ?: emptyList()) }
    var candidateInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { loading = true; importExcel(uri) { message = it; loading = false; scope.launch { refresh(dao) { r,w -> rows=r; wards=w; selectedWard="ALL" } } } }
    }
    fun refreshNow() { scope.launch { refresh(dao) { r,w -> rows=r; wards=w } } }
    LaunchedEffect(Unit) { refreshNow() }
    LaunchedEffect(candidates) { prefs.edit().putStringSet("candidates", candidates.toSet()).apply() }
    LaunchedEffect(selectedWard) { prefs.edit().putString("last_ward", selectedWard).apply() }
    val shown = if (selectedWard == "ALL") rows else rows.filter { it.ward == selectedWard }
    val green = shown.count { it.attendance == "GREEN" }
    val yellow = shown.count { it.attendance == "YELLOW" }
    val red = shown.count { it.attendance == "RED" }
    val unchecked = shown.count { it.attendance == "NONE" }
    val voteTotals = candidates.associateWith { c -> shown.count { it.selectedCandidate == c } }
    val nota = shown.count { it.selectedCandidate == "NOTA" }

    Scaffold(topBar = { TopAppBarCompat(title = when(page) { "HOME" -> "Voter App"; "CHECK" -> "Check Voters"; "COUNT" -> "Vote Counting"; else -> "Results" }, back = if(page=="HOME") null else { { page="HOME" } }) }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (page == "HOME") {
                Text("अपना काम चुनें", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = { page="CHECK"; mode="CHECK"; refreshNow() }, modifier=Modifier.fillMaxWidth().height(58.dp)) { Text("Check Voters") }
                Button(onClick = { page="COUNT"; mode="COUNT"; refreshNow() }, modifier=Modifier.fillMaxWidth().height(58.dp)) { Text("Vote Counting") }
                OutlinedButton(onClick = { picker.launch("*/*") }, modifier=Modifier.fillMaxWidth()) { Text("Excel File Upload") }
                Text(message)
            } else {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp), verticalAlignment=Alignment.CenterVertically) {
                    Button(onClick={picker.launch("*/*")}) { Text("Upload Excel") }
                    if(loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth=2.dp)
                }
                Text(message, color=MaterialTheme.colorScheme.primary)
                if (page=="COUNT") {
                    OutlinedTextField(value=candidateInput, onValueChange={candidateInput=it}, label={Text("उम्मीदवार का नाम")}, modifier=Modifier.fillMaxWidth())
                    Button(onClick={ if(candidateInput.isNotBlank() && candidateInput !in candidates) candidates = candidates + candidateInput.trim(); candidateInput="" }) { Text("Add Candidate") }
                    Text("उम्मीदवार: ${candidates.joinToString(", ")}")
                }
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    WardChip("ALL", selectedWard=="ALL") { selectedWard="ALL" }
                    wards.forEach { w -> WardChip(w, selectedWard==w) { selectedWard=w } }
                }
                if(page=="CHECK") {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
                        Stat("Total", shown.size); Stat("Green",green); Stat("Yellow",yellow); Stat("Red",red); Stat("Pending",unchecked)
                    }
                } else {
                    Text("कुल दर्ज वोट: ${shown.count { it.selectedCandidate.isNotBlank() }}    NOTA: $nota")
                    candidates.forEach { c -> Text("$c: ${voteTotals[c] ?: 0}") }
                }
                LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(shown, key={it.id}) { voter ->
                        Card(Modifier.fillMaxWidth(), shape=RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(10.dp), verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text("${voter.serial}. ${voter.name}", style=MaterialTheme.typography.titleMedium)
                                if(voter.relation.isNotBlank()) Text(voter.relation)
                                Text("EPIC: ${voter.epic.ifBlank { "—" }}   Ward: ${voter.ward}", style=MaterialTheme.typography.bodySmall)
                                if(page=="CHECK") {
                                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                                        AttendanceButton("✓", Color(0xFF168447), voter.attendance=="GREEN") { update(dao,voter.copy(attendance="GREEN"),::refreshNow) }
                                        AttendanceButton("✓", Color(0xFFE4B323), voter.attendance=="YELLOW") { update(dao,voter.copy(attendance="YELLOW"),::refreshNow) }
                                        AttendanceButton("✕", Color(0xFFC62828), voter.attendance=="RED") { update(dao,voter.copy(attendance="RED"),::refreshNow) }
                                    }
                                } else {
                                    Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                                        candidates.forEach { c -> CandidateButton(c, voter.selectedCandidate==c) { update(dao,voter.copy(selectedCandidate=c),::refreshNow) } }
                                        CandidateButton("NOTA", voter.selectedCandidate=="NOTA") { update(dao,voter.copy(selectedCandidate="NOTA"),::refreshNow) }
                                        TextButton(onClick={update(dao,voter.copy(selectedCandidate=""),::refreshNow)}) { Text("Clear") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun refresh(dao: VoterDao, done: (List<Voter>,List<String>)->Unit) {
    done(dao.all(), dao.wards())
}
private fun update(dao: VoterDao, voter: Voter, refresh: ()->Unit) {
    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) { dao.update(voter); kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { refresh() } }
}
@Composable private fun WardChip(label:String, selected:Boolean, onClick:()->Unit) {
    FilterChip(selected=selected,onClick=onClick,label={Text(if(label=="ALL") "ALL" else "Ward $label")})
}
@Composable private fun Stat(label:String,value:Int) {
    Card(Modifier.widthIn(min=55.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(7.dp), horizontalAlignment=Alignment.CenterHorizontally) { Text("$value", style=MaterialTheme.typography.titleMedium); Text(label, style=MaterialTheme.typography.labelSmall) }
    }
}
@Composable private fun AttendanceButton(label:String,color:Color,selected:Boolean,onClick:()->Unit) {
    Button(onClick=onClick, colors=ButtonDefaults.buttonColors(containerColor=if(selected) color else color.copy(alpha=.65f)), contentPadding=PaddingValues(horizontal=20.dp,vertical=6.dp)) { Text(label) }
}
@Composable private fun CandidateButton(label:String,selected:Boolean,onClick:()->Unit) {
    FilterChip(selected=selected,onClick=onClick,label={Text(label)})
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TopAppBarCompat(title:String,back:(()->Unit)?) {
    TopAppBar(title={Text(title)}, navigationIcon={ if(back!=null) TextButton(onClick=back){Text("Back")} })
}
