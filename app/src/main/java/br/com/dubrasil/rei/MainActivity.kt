package br.com.dubrasil.rei

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import br.com.dubrasil.rei.model.ChecklistGroup
import br.com.dubrasil.rei.model.ReportData
import br.com.dubrasil.rei.model.ReportAttachment
import br.com.dubrasil.rei.model.ImplementationSummary
import br.com.dubrasil.rei.model.ReportSchema
import br.com.dubrasil.rei.data.AuthClient
import br.com.dubrasil.rei.data.AuthStore
import br.com.dubrasil.rei.data.AuthUser
import br.com.dubrasil.rei.pdf.PdfExporter
import br.com.dubrasil.rei.ui.theme.ReiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ReiTheme { ReiApp() } }
    }
}

private data class Step(val title: String, val shortTitle: String, val description: String)

private val steps = listOf(
    Step("Identificação", "Dados", "Informações gerais e módulos contratados"),
    Step("Preenchimento técnico", "Técnico", "Instalação, ambiente e configurações"),
    Step("Módulo Estoque", "Estoque", "Cadastros, entradas e saídas"),
    Step("Módulo Financeiro", "Financeiro", "Lançamentos, extratos e boletos"),
    Step("Fiscal e relatórios", "Fiscal", "Obrigações fiscais e relatórios"),
    Step("Entrega e assinaturas", "Entrega", "Conclusão, evidências e responsáveis")
)

private val Navy = Color(0xFF263A7A)
private val NavyDark = Color(0xFF172653)
private val Green = Color(0xFF58AD45)
private val PageBackground = Color(0xFFF4F6FA)
private val Border = Color(0xFFE1E5EE)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReiApp(vm: ReportViewModel = viewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val authStore = remember { AuthStore(context) }
    var currentUser by remember { mutableStateOf(authStore.currentUser()) }
    val report = vm.report
    var showDashboard by rememberSaveable { mutableStateOf(currentUser != null) }
    var viewingReportId by rememberSaveable { mutableStateOf<String?>(null) }
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    if (currentUser == null) {
        LoginScreen(onAuthenticated = { user ->
            currentUser = user
            currentStep = 0
            showDashboard = true
        })
        return
    }
    val authenticatedUser = currentUser!!
    LaunchedEffect(showDashboard, authenticatedUser.username) {
        if (showDashboard) vm.refreshFromServer()
    }
    val logout = {
        authStore.clear()
        currentUser = null
        showDashboard = false
    }
    val exportAndSharePdf = { fileName: String, exportReport: ReportData, archiveAfterShare: Boolean ->
        runCatching {
            val directory = File(context.filesDir, "shared_reports").apply { mkdirs() }
            directory.listFiles()?.forEach { oldFile ->
                if (oldFile.isFile && oldFile.lastModified() < System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000) {
                    oldFile.delete()
                }
            }
            val file = File(directory, fileName)
            file.outputStream().use { output -> PdfExporter.write(context, output, exportReport) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName.removeSuffix(".pdf"))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Compartilhar relatório PDF"))
        }.onSuccess {
            if (archiveAfterShare) {
                vm.archiveCurrentReport()
                showDashboard = true
                Toast.makeText(context, "PDF gerado e implantação registrada", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "PDF gerado novamente com sucesso", Toast.LENGTH_LONG).show()
            }
        }.onFailure {
            Toast.makeText(context, "Não foi possível compartilhar o PDF", Toast.LENGTH_LONG).show()
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val value = pendingCameraUri
        if (saved && value != null) {
            vm.addAttachments(listOf(ReportAttachment(value, "Foto ${dateStamp()}.jpg", "image/jpeg")))
        }
        pendingCameraUri = null
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        vm.addAttachments(uris.map { attachmentFromUri(context, it) })
    }
    val filesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        vm.addAttachments(uris.map { attachmentFromUri(context, it) })
    }

    val viewedReport = viewingReportId?.let { id -> vm.history.firstOrNull { it.id == id } }
    if (viewedReport != null) {
        ReportViewerScreen(
            item = viewedReport,
            onBack = { viewingReportId = null },
            onEdit = if (!authenticatedUser.isSupervisor) ({
                vm.editCompletedReport(viewedReport.id, authenticatedUser.username)
                viewingReportId = null
                currentStep = 0
                showDashboard = false
            }) else null,
            onEvaluate = if (authenticatedUser.isSupervisor) ({ score, rating, supervisionChecks ->
                vm.saveSupervisorEvaluation(viewedReport.id, authenticatedUser.username, score, rating, supervisionChecks)
                Toast.makeText(context, "Avaliação da supervisão salva", Toast.LENGTH_LONG).show()
            }) else null,
            onReprint = {
                val safeName = viewedReport.client.replace(Regex("[^A-Za-zÀ-ÿ0-9_-]"), "_")
                exportAndSharePdf("REI_${safeName}_segunda_via.pdf", viewedReport.report, false)
            }
        )
        return
    }

    if (showDashboard) {
        DashboardScreen(
            history = vm.history,
            draft = report,
            user = authenticatedUser,
            onLogout = logout,
            onResumeDraft = { currentStep = 0; showDashboard = false },
            onNewReport = { vm.startNewReport(authenticatedUser.username); currentStep = 0; showDashboard = false },
            onOpenReport = { viewingReportId = it.id }
        )
        return
    }

    Scaffold(
        containerColor = PageBackground,
        topBar = {
            ReiTopBar(
                onHome = { showDashboard = true },
                onNewReport = { confirmClear = true },
                onLogout = logout
            )
        },
        bottomBar = {
            BottomActions(
                currentStep = currentStep,
                onBack = { currentStep-- },
                onNext = { currentStep++ },
                onExport = {
                    if (report.field("cliente").isBlank()) {
                        Toast.makeText(context, "Informe o cliente/projeto antes de exportar", Toast.LENGTH_LONG).show()
                        currentStep = 0
                    } else {
                        val safeName = report.field("cliente").replace(Regex("[^A-Za-zÀ-ÿ0-9_-]"), "_")
                        exportAndSharePdf("REI_$safeName.pdf", report, true)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
        ) {
            ProgressHero(report, currentStep)
            StepSelector(currentStep) { currentStep = it }
            Column(Modifier.padding(horizontal = 18.dp)) {
                Text(
                    steps[currentStep].title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B2437)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    steps[currentStep].description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF687086)
                )
                Spacer(Modifier.height(20.dp))
                when (currentStep) {
                    0 -> IdentificationStep(report, vm)
                    1 -> TechnicalStep(report, vm)
                    2 -> ChecklistStep("estoque", ReportSchema.stock, report, vm)
                    3 -> ChecklistStep("financeiro", ReportSchema.finance, report, vm)
                    4 -> ChecklistStep("fiscal", ReportSchema.fiscalReports, report, vm)
                    5 -> DeliveryStep(
                        report,
                        vm,
                        onCamera = {
                            val directory = File(context.filesDir, "report_photos").apply { mkdirs() }
                            val file = File(directory, "rei_${System.currentTimeMillis()}.jpg")
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            pendingCameraUri = uri.toString()
                            cameraLauncher.launch(uri)
                        },
                        onGallery = {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onFiles = { filesLauncher.launch(arrayOf("image/*", "application/pdf")) }
                    )
                }
            }
        }
    }

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        icon = { Icon(Icons.Outlined.DeleteOutline, null, tint = Navy) },
        title = { Text("Iniciar novo relatório?") },
        text = { Text("O rascunho atual será apagado deste aparelho.") },
        confirmButton = {
            Button(onClick = { vm.startNewReport(authenticatedUser.username); currentStep = 0; confirmClear = false }) {
                Text("Apagar e iniciar")
            }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun LoginScreen(onAuthenticated: (AuthUser) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { AuthClient(context) }
    val authStore = remember { AuthStore(context) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var serverUrl by rememberSaveable { mutableStateOf(authStore.serverUrl()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFF8F9FD), Color(0xFFEFF2FA)))
        ).safeDrawingPadding().imePadding().padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Border)
        ) {
            Column(Modifier.padding(25.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.logo_dubrasil),
                    contentDescription = "DuBrasil Soluções",
                    modifier = Modifier.width(145.dp).height(112.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(22.dp))
                Box(Modifier.size(50.dp).clip(CircleShape).background(Color(0xFFE8EDFF)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Lock, null, tint = Navy, modifier = Modifier.size(27.dp))
                }
                Spacer(Modifier.height(13.dp))
                Text("Acesso ao R.E.I.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Entre com o usuário cadastrado pelo supervisor.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF778095))
                Spacer(Modifier.height(22.dp))
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it; error = "" },
                    label = { Text("Endereço do servidor") },
                    leadingIcon = { Icon(Icons.Outlined.BusinessCenter, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; error = "" },
                    label = { Text("Usuário") },
                    leadingIcon = { Icon(Icons.Outlined.Person, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = "" },
                    label = { Text("Senha") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp)
                )
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(error, color = Color(0xFFB3261E), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(17.dp))
                Button(
                    onClick = {
                        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                            error = "Informe usuário e senha."
                        } else {
                            loading = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { client.login(username, password, serverUrl) }
                                loading = false
                                result.onSuccess { onAuthenticated(it.user) }
                                    .onFailure { error = it.message ?: "Não foi possível entrar." }
                            }
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy)
                ) { Text(if (loading) "Entrando..." else "Entrar", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(14.dp))
                Text("Servidor do escritório • acesso protegido", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8790A4))
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    history: List<ImplementationSummary>,
    draft: ReportData,
    user: AuthUser,
    onLogout: () -> Unit,
    onResumeDraft: () -> Unit,
    onNewReport: () -> Unit,
    onOpenReport: (ImplementationSummary) -> Unit
) {
    val ordered = history.sortedBy { it.completedAt }
    val averageDays = if (ordered.size >= 2) {
        ordered.zipWithNext { first, second ->
            (second.completedAt - first.completedAt).toDouble() / 86_400_000.0
        }.average()
    } else null
    val hasDraft = draft.fields.any { (key, value) -> key != "_id" && value.isNotBlank() } ||
        draft.checks.isNotEmpty() || draft.attachments.isNotEmpty() || draft.deliveryStatus.isNotBlank()
    val evaluations = history.filter { hasSupervisorEvaluation(it.report) }
    val averageScore = evaluations.mapNotNull { supervisionScore(it.report) }.takeIf { it.isNotEmpty() }?.average()
    val lastDate = history.maxByOrNull { it.completedAt }?.let {
        SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(it.completedAt))
    } ?: "—"

    Scaffold(
        containerColor = PageBackground,
        topBar = { DashboardHeader(user, onLogout) },
        bottomBar = {
            Surface(
                modifier = Modifier.navigationBarsPadding(),
                color = Color.White,
                shadowElevation = 12.dp
            ) {
                Button(
                    onClick = onNewReport,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).height(54.dp),
                    shape = RoundedCornerShape(17.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy)
                ) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Nova implantação", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 20.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(NavyDark, Navy))).padding(22.dp)
            ) {
                Text("PAINEL DE IMPLANTAÇÕES", color = Color(0xFFBFC9F5), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(7.dp))
                Text("Visão geral das entregas", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(7.dp))
                Text("Acompanhe o ritmo e o histórico dos projetos ERP.", color = Color(0xFFD9DFF6), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.BusinessCenter, null, tint = Navy) },
                    value = history.size.toString(),
                    label = "Implantações"
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.Timer, null, tint = Green) },
                    value = averageDays?.let { String.format(Locale("pt", "BR"), "%.1f dias", it) } ?: "—",
                    label = "Intervalo médio"
                )
            }
            if (!user.isSupervisor) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        icon = { Icon(Icons.Rounded.CheckCircle, null, tint = Green) },
                        value = averageScore?.let { String.format(Locale("pt", "BR"), "%.1f/10", it) } ?: "-",
                        label = "Nota media"
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        icon = { Icon(Icons.Outlined.Description, null, tint = Navy) },
                        value = evaluations.size.toString(),
                        label = "Avaliacoes"
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFEBF5E8)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Green)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Última implantação", style = MaterialTheme.typography.bodySmall, color = Color(0xFF778095))
                        Text(lastDate, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            MonthlyDeliveriesChart(history)
            Spacer(Modifier.height(14.dp))
            StatusDistributionChart(history)
            if (!user.isSupervisor) {
                Spacer(Modifier.height(14.dp))
                LatestEvaluationsCard(evaluations.take(3), onOpenReport)
            }
            if (hasDraft) {
                Spacer(Modifier.height(18.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onResumeDraft() },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFFFF8E8),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF0DDAA))
                ) {
                    Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFFFE9AF)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.History, null, tint = Color(0xFF8A6415))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Editar rascunho salvo", fontWeight = FontWeight.Bold, color = Color(0xFF553F10))
                            Text(
                                draft.field("cliente").ifBlank { "Implantação ainda não identificada" },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF7B642E)
                            )
                        }
                        Icon(Icons.Outlined.Edit, contentDescription = "Editar rascunho", tint = Color(0xFF8A6415))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFE8EDFF)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.BusinessCenter, null, Modifier.size(19.dp), tint = Navy)
                }
                Spacer(Modifier.width(10.dp))
                Text("Implantações recentes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF1B2437))
                Spacer(Modifier.weight(1f))
                Text("${history.size} total", style = MaterialTheme.typography.labelMedium, color = Color(0xFF778095))
            }
            Spacer(Modifier.height(12.dp))
            if (history.isEmpty()) {
                EmptyHistoryCard()
            } else {
                history.take(5).forEach { item ->
                    HistoryCard(item) { onOpenReport(item) }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DashboardHeader(user: AuthUser, onLogout: () -> Unit) {
    Surface(
        modifier = Modifier.statusBarsPadding(),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.logo_dubrasil),
                contentDescription = "DuBrasil Soluções",
                modifier = Modifier.width(58.dp).height(45.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("Dashboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Navy)
                Text(user.fullName, style = MaterialTheme.typography.labelSmall, color = Color(0xFF747B8E), maxLines = 1)
            }
            Spacer(Modifier.width(9.dp))
            IconButton(onClick = onLogout, modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFF0F2F7))) {
                Icon(Icons.Outlined.Logout, contentDescription = "Sair", tint = Navy)
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    value: String,
    label: String
) {
    Surface(
        modifier = modifier.height(138.dp),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF0F3FB)), contentAlignment = Alignment.Center) { icon() }
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1B2437))
                Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF778095))
            }
        }
    }
}

@Composable
private fun MonthlyDeliveriesChart(history: List<ImplementationSummary>) {
    val current = YearMonth.now()
    val months = (5 downTo 0).map { current.minusMonths(it.toLong()) }
    val counts = months.map { month ->
        history.count { item ->
            YearMonth.from(Instant.ofEpochMilli(item.completedAt).atZone(ZoneId.systemDefault())) == month
        }
    }
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val formatter = DateTimeFormatter.ofPattern("MMM", Locale("pt", "BR"))
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFE8EDFF)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Timer, null, Modifier.size(19.dp), tint = Navy)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Entregas por mês", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Últimos seis meses", style = MaterialTheme.typography.bodySmall, color = Color(0xFF778095))
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                months.forEachIndexed { index, month ->
                    val count = counts[index]
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = Navy, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.height(92.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier.width(24.dp)
                                    .height(if (count == 0) 5.dp else (18 + (74f * count / max)).dp)
                                    .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                                    .background(if (count == 0) Color(0xFFE4E7EF) else Navy)
                            )
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(month.format(formatter).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = Color(0xFF778095))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDistributionChart(history: List<ImplementationSummary>) {
    val concluded = history.count { it.deliveryStatus.startsWith("Concluído") }
    val notConcluded = history.count { it.deliveryStatus == "Não concluído" }
    val unspecified = history.size - concluded - notConcluded
    val total = history.size
    val segments = listOf(
        Triple("Concluídas", concluded, Green),
        Triple("Não concluídas", notConcluded, Color(0xFFE39A32)),
        Triple("Sem definição", unspecified, Color(0xFFB8BECC))
    )
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFEBF5E8)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(19.dp), tint = Green)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Situação das entregas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Distribuição dos relatórios finalizados", style = MaterialTheme.typography.bodySmall, color = Color(0xFF778095))
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                        if (total == 0) {
                            drawArc(Color(0xFFE4E7EF), -90f, 360f, false, style = Stroke(width = 22f))
                        } else {
                            var start = -90f
                            segments.forEach { (_, count, color) ->
                                if (count > 0) {
                                    val sweep = 360f * count / total
                                    drawArc(color, start, sweep, false, style = Stroke(width = 22f))
                                    start += sweep
                                }
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(total.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text("total", style = MaterialTheme.typography.labelMedium, color = Color(0xFF778095))
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    segments.forEach { (label, count, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                            Spacer(Modifier.width(8.dp))
                            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Color(0xFF596174))
                            Text(count.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

private fun hasSupervisorEvaluation(data: ReportData): Boolean =
    data.rating.isNotBlank() ||
        data.field("_supervisionScore").isNotBlank() ||
        data.checks.any { it in ReportSchema.supervisionChecklistItems() }

private fun supervisionScore(data: ReportData): Double? {
    data.field("_supervisionScore").replace(",", ".").toDoubleOrNull()
        ?.coerceIn(0.0, 10.0)
        ?.let { return it }

    val total = ReportSchema.supervisionChecklistItems().size
    if (total == 0) return null
    val done = data.checks.count { it in ReportSchema.supervisionChecklistItems() }
    return if (done > 0) done * 10.0 / total else null
}

@Composable
private fun LatestEvaluationsCard(
    evaluations: List<ImplementationSummary>,
    onOpenReport: (ImplementationSummary) -> Unit
) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ultimas avaliacoes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1B2437))
                Spacer(Modifier.weight(1f))
                Text("${evaluations.size} recentes", style = MaterialTheme.typography.labelMedium, color = Color(0xFF778095))
            }
            Spacer(Modifier.height(5.dp))
            Text("Feedbacks da supervisao sobre suas implantacoes entregues.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF778095))
            Spacer(Modifier.height(13.dp))
            if (evaluations.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF5F7FB)).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nenhuma avaliacao recebida ainda.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF778095))
                }
            } else {
                evaluations.forEachIndexed { index, item ->
                    EvaluationRow(item) { onOpenReport(item) }
                    if (index < evaluations.lastIndex) HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun EvaluationRow(item: ImplementationSummary, onClick: () -> Unit) {
    val score = supervisionScore(item.report)
    val date = item.report.field("_supervisionReviewedAt").toLongOrNull()?.let {
        SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(it))
    } ?: SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(item.completedAt))

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFEBF5E8)), contentAlignment = Alignment.Center) {
            Text(score?.let { String.format(Locale("pt", "BR"), "%.1f", it) } ?: "-", color = Color(0xFF3E7034), fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.client, fontWeight = FontWeight.Bold, color = Color(0xFF252D40), maxLines = 1)
            Text("Avaliado em $date", style = MaterialTheme.typography.bodySmall, color = Color(0xFF778095))
            if (item.report.rating.isNotBlank()) {
                Text(item.report.rating, style = MaterialTheme.typography.bodySmall, color = Color(0xFF596174), maxLines = 2)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Abrir avaliacao", tint = Color(0xFF778095))
    }
}

@Composable
private fun EmptyHistoryCard() {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFF0F3FB)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.BusinessCenter, null, tint = Navy)
            }
            Spacer(Modifier.height(12.dp))
            Text("Nenhuma implantação registrada", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Gere o primeiro relatório para alimentar suas métricas.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF778095))
        }
    }
}

@Composable
private fun HistoryCard(item: ImplementationSummary, onClick: () -> Unit) {
    val date = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")).format(Date(item.completedAt))
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8EDFF)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.BusinessCenter, null, tint = Navy)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.client, fontWeight = FontWeight.Bold, color = Color(0xFF252D40), maxLines = 1)
                Text(date, style = MaterialTheme.typography.bodySmall, color = Color(0xFF778095))
                if (item.consultant.isNotBlank()) Text(item.consultant, style = MaterialTheme.typography.bodySmall, color = Color(0xFF778095), maxLines = 1)
            }
            Surface(color = Color(0xFFEBF5E8), shape = RoundedCornerShape(50)) {
                Text("${item.checkedItems} itens", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = Color(0xFF3E7034))
            }
        }
    }
}

@Composable
private fun ReportViewerScreen(
    item: ImplementationSummary,
    onBack: () -> Unit,
    onEdit: (() -> Unit)?,
    onEvaluate: ((String, String, Set<String>) -> Unit)?,
    onReprint: (() -> Unit)?
) {
    val data = item.report
    var showEvaluation by remember { mutableStateOf(false) }
    if (showEvaluation && onEvaluate != null) {
        SupervisorEvaluationDialog(
            data = data,
            onDismiss = { showEvaluation = false },
            onSave = { score, rating, checks ->
                onEvaluate(score, rating, checks)
                showEvaluation = false
            }
        )
    }
    Scaffold(
        containerColor = PageBackground,
        topBar = {
            Surface(
                modifier = Modifier.statusBarsPadding(),
                color = Color.White,
                shadowElevation = 1.dp
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar") }
                    Spacer(Modifier.width(5.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.client, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Relatório concluído", style = MaterialTheme.typography.bodySmall, color = Color(0xFF778095))
                    }
                    Surface(color = Color(0xFFEBF5E8), shape = RoundedCornerShape(50)) {
                        Text("Entregue", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = Color(0xFF3E7034))
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.navigationBarsPadding(),
                color = Color.White,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    if (onEvaluate != null) {
                        Button(
                            onClick = { showEvaluation = true },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(17.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Green)
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null)
                            Spacer(Modifier.width(7.dp))
                            Text("Avaliar implantação", fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                    if (onEdit != null) {
                        OutlinedButton(
                            onClick = onEdit,
                            modifier = Modifier.weight(1f).height(54.dp),
                            shape = RoundedCornerShape(17.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Navy)
                        ) {
                            Icon(Icons.Outlined.Edit, null)
                            Spacer(Modifier.width(7.dp))
                            Text("Editar", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (onReprint != null) {
                        Button(
                            onClick = onReprint,
                            modifier = Modifier.weight(1.45f).height(54.dp),
                            shape = RoundedCornerShape(17.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Navy)
                        ) {
                            Icon(Icons.Outlined.PictureAsPdf, null)
                            Spacer(Modifier.width(7.dp))
                            Text("Segunda via", fontWeight = FontWeight.Bold)
                        }
                    }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(NavyDark, Navy))).padding(20.dp)
            ) {
                Text("RELATÓRIO DE IMPLANTAÇÃO", color = Color(0xFFBFC9F5), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(5.dp))
                Text(item.client, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")).format(Date(item.completedAt)),
                    color = Color(0xFFD9DFF6),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(14.dp))
            ViewerSection("Identificação") {
                ViewerValue("Cliente / Projeto", data.field("cliente"))
                ViewerValue("Consultor", data.field("consultor"))
                ViewerValue("Usuários cadastrados", data.field("usuariosTga"))
                ViewerValue("Início", data.field("inicio"))
                ViewerValue("Término", data.field("termino"))
                ViewerValue("Dias contratados", data.field("diasContratados"))
                ViewerValue("Dias utilizados", data.field("diasUtilizados"), divider = false)
            }
            ViewerSelectedChecks("Módulos contratados", ReportSchema.contractedModules.filter { ReportSchema.contractedKey(it) in data.checks })
            ViewerChecklistScope("Preenchimento técnico", "tecnico", ReportSchema.technical, data)
            ViewerChecklistScope("Módulo Estoque", "estoque", ReportSchema.stock, data)
            ViewerChecklistScope("Módulo Financeiro", "financeiro", ReportSchema.finance, data)
            ViewerChecklistScope("Fiscal e relatórios", "fiscal", ReportSchema.fiscalReports, data)
            ViewerSection("Entrega") {
                ViewerValue("Serviços executados", data.field("servicosExecutados"))
                ViewerValue("Posicionamento", data.deliveryStatus)
                ViewerValue("Pendências", data.field("pendencias"), divider = false)
            }
            val supervisionItems = ReportSchema.supervision.flatMap { group ->
                group.items.filter { ReportSchema.key("supervisao", group.title, it) in data.checks }
            }
            if (data.rating.isNotBlank() || supervisionItems.isNotEmpty()) {
                ViewerSection("Avaliação da supervisão") {
                    ViewerValue("Supervisor", data.field("_supervisorName"))
                    ViewerValue("Nota", supervisionScore(data)?.let { String.format(Locale("pt", "BR"), "%.1f/10", it) }.orEmpty())
                    ViewerValue("Parecer / observação", data.rating, divider = supervisionItems.isNotEmpty())
                    if (supervisionItems.isNotEmpty()) {
                        supervisionItems.forEach { item ->
                            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = Green)
                                Spacer(Modifier.width(9.dp))
                                Text(item, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3D4558))
                            }
                        }
                    }
                }
            }
            val analystSignature = data.field("assinaturaAnalistaImagem")
            val clientSignature = data.field("assinaturaClienteImagem")
            if (analystSignature.isNotBlank() || clientSignature.isNotBlank()) {
                ViewerSection("Assinaturas digitais") {
                    if (analystSignature.isNotBlank()) ViewerImage("Analista de implantação", analystSignature)
                    if (clientSignature.isNotBlank()) ViewerImage("Responsável pelo cliente", clientSignature)
                }
            }
            if (data.attachments.isNotEmpty()) {
                ViewerSection("Evidências e anexos") {
                    data.attachments.forEach { attachment ->
                        ViewerAttachment(attachment)
                        Spacer(Modifier.height(9.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SupervisorEvaluationDialog(
    data: ReportData,
    onDismiss: () -> Unit,
    onSave: (String, String, Set<String>) -> Unit
) {
    val supervisionKeys = remember { ReportSchema.supervisionChecklistItems().toSet() }
    var score by remember(data.field("_supervisionScore")) {
        mutableStateOf(data.field("_supervisionScore").replace(",", ".").toFloatOrNull()?.coerceIn(0f, 10f) ?: 0f)
    }
    var rating by remember(data.rating) { mutableStateOf(data.rating) }
    var selected by remember(data.checks) { mutableStateOf(data.checks.filter { it in supervisionKeys }.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CheckCircle, null, tint = Green) },
        title = { Text("Avaliar implantação") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Checklist exclusivo do supervisor para validar a implantação entregue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF778095)
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF7F8FB),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Nota da supervisão",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF596174)
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                String.format(Locale("pt", "BR"), "%.1f/10", score),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Navy
                            )
                        }
                        Slider(
                            value = score,
                            onValueChange = { score = it },
                            valueRange = 0f..10f,
                            steps = 19,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = rating,
                    onValueChange = { rating = it },
                    label = { Text("Parecer / observação da supervisão") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(Modifier.height(16.dp))
                ReportSchema.supervision.forEach { group ->
                    Text(group.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Navy)
                    Spacer(Modifier.height(5.dp))
                    group.items.forEach { item ->
                        val key = ReportSchema.key("supervisao", group.title, item)
                        val checked = key in selected
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                                .clickable {
                                    selected = if (checked) selected - key else selected + key
                                }
                                .padding(horizontal = 3.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selected = if (isChecked) selected + key else selected - key
                                },
                                colors = CheckboxDefaults.colors(checkedColor = Green, uncheckedColor = Color(0xFF8A91A2))
                            )
                            Text(item, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3D4558))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(String.format(Locale.US, "%.1f", score), rating, selected) }, colors = ButtonDefaults.buttonColors(containerColor = Green)) {
                Text("Salvar avaliação")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun ViewerSection(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(13.dp))
            content()
        }
    }
}

@Composable
private fun ViewerValue(label: String, value: String, divider: Boolean = true) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF778095))
        Text(value.ifBlank { "Não informado" }, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF30384B))
    }
    if (divider) HorizontalDivider(color = Border)
}

@Composable
private fun ViewerChecklistScope(title: String, scope: String, groups: List<ChecklistGroup>, data: ReportData) {
    val selected = groups.flatMap { group -> group.items.filter { ReportSchema.key(scope, group.title, it) in data.checks } }
    ViewerSelectedChecks(title, selected)
}

@Composable
private fun ViewerSelectedChecks(title: String, items: List<String>) {
    ViewerSection(title) {
        if (items.isEmpty()) {
            Text("Nenhum item marcado", style = MaterialTheme.typography.bodySmall, color = Color(0xFF778095))
        } else items.forEach { item ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = Green)
                Spacer(Modifier.width(9.dp))
                Text(item, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3D4558))
            }
        }
    }
}

@Composable
private fun ViewerImage(label: String, uri: String) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF778095))
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(14.dp))
            .background(Color.White).border(1.dp, Border, RoundedCornerShape(14.dp))
    ) { AttachmentThumbnail(uri) }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ViewerAttachment(item: ReportAttachment) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF5F7FB))
            .border(1.dp, Border, RoundedCornerShape(14.dp)).padding(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFE8ECF5)), contentAlignment = Alignment.Center) {
            if (item.mimeType.startsWith("image/")) AttachmentThumbnail(item.uri)
            else Icon(Icons.Outlined.Description, null, tint = Navy)
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2)
            Text(if (item.mimeType.startsWith("image/")) "Imagem" else "Documento", style = MaterialTheme.typography.bodySmall, color = Color(0xFF778095))
        }
    }
}

@Composable
private fun ReiTopBar(onHome: (() -> Unit)?, onNewReport: () -> Unit, onLogout: () -> Unit) {
    Surface(
        modifier = Modifier.statusBarsPadding(),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.logo_dubrasil),
                contentDescription = "DuBrasil Soluções",
                modifier = Modifier.width(58.dp).height(45.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("R.E.I.", fontWeight = FontWeight.ExtraBold, color = Navy, style = MaterialTheme.typography.titleLarge)
                Text("Relatório de entrega", style = MaterialTheme.typography.labelSmall, color = Color(0xFF747B8E))
            }
            Spacer(Modifier.width(12.dp))
            if (onHome != null) {
                IconButton(
                    onClick = onHome,
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFF0F2F7))
                ) {
                    Icon(Icons.Outlined.Home, contentDescription = "Dashboard", tint = Navy)
                }
                Spacer(Modifier.width(7.dp))
            }
            IconButton(
                onClick = onNewReport,
                modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFF0F2F7))
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Novo relatório", tint = Color(0xFF596174))
            }
            Spacer(Modifier.width(7.dp))
            IconButton(onClick = onLogout, modifier = Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFF0F2F7))) {
                Icon(Icons.Outlined.Logout, contentDescription = "Sair", tint = Color(0xFF596174))
            }
        }
    }
}

@Composable
private fun ProgressHero(report: ReportData, currentStep: Int) {
    val total = ReportSchema.allChecklistItems().size
    val done = report.checks.count { it in ReportSchema.allChecklistItems() }
    val progress = if (total == 0) 0f else done.toFloat() / total
    Column(
        Modifier.fillMaxWidth().padding(18.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(NavyDark, Navy)))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ETAPA ${currentStep + 1} DE ${steps.size}", color = Color(0xFFBFC9F5), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text("Seu relatório está em andamento", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Surface(color = Color.White.copy(alpha = .13f), shape = RoundedCornerShape(50)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudDone, null, Modifier.size(16.dp), tint = Color(0xFF9EE08D))
                    Spacer(Modifier.width(6.dp))
                    Text("Salvo", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = Green,
            trackColor = Color.White.copy(alpha = .18f)
        )
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Checklist preenchido", color = Color(0xFFD8DDF3), style = MaterialTheme.typography.labelMedium)
            Text("$done de $total itens", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StepSelector(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 18.dp, end = 18.dp, bottom = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        steps.forEachIndexed { index, step ->
            val active = index == selected
            val complete = index < selected
            Surface(
                modifier = Modifier.clickable { onSelect(index) },
                shape = RoundedCornerShape(14.dp),
                color = if (active) Navy else Color.White,
                border = if (active) null else androidx.compose.foundation.BorderStroke(1.dp, Border),
                shadowElevation = if (active) 4.dp else 0.dp
            ) {
                Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (complete) Icon(Icons.Rounded.CheckCircle, null, Modifier.size(17.dp), tint = Green)
                    else Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .background(if (active) Color.White.copy(alpha = .16f) else Color(0xFFF0F2F7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", color = if (active) Color.White else Navy, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(step.shortTitle, color = if (active) Color.White else Color(0xFF343B4D), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BottomActions(currentStep: Int, onBack: () -> Unit, onNext: () -> Unit, onExport: () -> Unit) {
    Surface(
        modifier = Modifier.navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 12.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentStep > 0) OutlinedButton(
                onClick = onBack,
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Border)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                Spacer(Modifier.width(6.dp))
                Text("Voltar")
            }
            Button(
                onClick = if (currentStep < steps.lastIndex) onNext else onExport,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Navy)
            ) {
                if (currentStep == steps.lastIndex) Icon(Icons.Outlined.PictureAsPdf, null, Modifier.size(20.dp))
                Text(if (currentStep < steps.lastIndex) "Continuar" else "Gerar relatório PDF")
                Spacer(Modifier.width(7.dp))
                if (currentStep < steps.lastIndex) Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun IdentificationStep(data: ReportData, vm: ReportViewModel) {
    FormCard("Informações da implantação") {
        FormField("Cliente / Projeto *", "cliente", data, vm)
        FormField("Consultor de implantação", "consultor", data, vm)
        FormField("Usuários cadastrados no TGA", "usuariosTga", data, vm)
        DateTimeField("Início (data e hora)", "inicio", data, vm)
        DateTimeField("Término (data e hora)", "termino", data, vm)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { FormField("Dias contratados", "diasContratados", data, vm) }
            Box(Modifier.weight(1f)) { FormField("Dias utilizados", "diasUtilizados", data, vm) }
        }
    }
    SectionCard("Módulos contratados", "Selecione tudo que faz parte deste projeto") {
        CheckItems(ReportSchema.contractedModules, data, vm) { ReportSchema.contractedKey(it) }
    }
    InfoCard("Suporte Técnico", "suportetga@dubrasilsolucoes.com.br", "(34) 3322-8500")
}

@Composable
private fun TechnicalStep(data: ReportData, vm: ReportViewModel) {
    ChecklistStep("tecnico", ReportSchema.technical, data, vm)
    FormCard("Detalhes técnicos") {
        FormField("Tipo do certificado", "tipoCertificado", data, vm)
        FormField("Quantidade de usuários no Workflow", "qtdWorkflow", data, vm)
        FormField("Observações técnicas", "observacoesTecnicas", data, vm, minLines = 4)
    }
}

@Composable
private fun DeliveryStep(
    data: ReportData,
    vm: ReportViewModel,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFiles: () -> Unit
) {
    FormCard("Resumo da entrega") {
        FormField("Descritivo dos serviços executados", "servicosExecutados", data, vm, minLines = 5)
        FormField("Pendências pós-implantação", "pendencias", data, vm, minLines = 4)
    }
    SectionCard("Posicionamento da entrega", "Escolha a situação final da implantação") {
        RadioOptions(listOf("Concluído", "Concluído, mas deseja novos serviços", "Não concluído"), data.deliveryStatus, vm::setDeliveryStatus)
    }
    FormCard("Assinaturas digitais") {
        SignatureField(
            title = "Analista de implantação – DuBrasil",
            key = "assinaturaAnalistaImagem",
            value = data.field("assinaturaAnalistaImagem"),
            onSaved = vm::setField
        )
        Spacer(Modifier.height(12.dp))
        SignatureField(
            title = "Responsável pelo cliente",
            key = "assinaturaClienteImagem",
            value = data.field("assinaturaClienteImagem"),
            onSaved = vm::setField
        )
    }
    AttachmentsCard(data.attachments, onCamera, onGallery, onFiles, vm::removeAttachment)
}

@Composable
private fun AttachmentsCard(
    attachments: List<ReportAttachment>,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFiles: () -> Unit,
    onRemove: (String) -> Unit
) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFE8EDFF)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PhotoCamera, null, tint = Navy)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Evidências e anexos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (attachments.isEmpty()) "Inclua fotos das telas do sistema" else "${attachments.size} arquivo(s) adicionado(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF778095)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onCamera,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Navy)
            ) {
                Icon(Icons.Outlined.PhotoCamera, null)
                Spacer(Modifier.width(8.dp))
                Text("Tirar foto agora")
            }
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(
                    onClick = onGallery,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(15.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Galeria")
                }
                OutlinedButton(
                    onClick = onFiles,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(15.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border)
                ) {
                    Icon(Icons.Outlined.FolderOpen, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Arquivos")
                }
            }
            if (attachments.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                attachments.forEach { item ->
                    AttachmentRow(item, onRemove)
                    Spacer(Modifier.height(9.dp))
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(item: ReportAttachment, onRemove: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFFF5F7FB))
            .border(1.dp, Border, RoundedCornerShape(15.dp)).padding(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFFE8ECF5)),
            contentAlignment = Alignment.Center
        ) {
            if (item.mimeType.startsWith("image/")) {
                AttachmentThumbnail(item.uri)
            } else {
                Icon(Icons.Outlined.Description, null, tint = Navy, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, maxLines = 2, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                if (item.mimeType.startsWith("image/")) "Imagem" else "Documento",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF778095)
            )
        }
        IconButton(onClick = { onRemove(item.uri) }) {
            Icon(Icons.Outlined.Close, contentDescription = "Remover anexo", tint = Color(0xFF777F91))
        }
    }
}

@Composable
private fun AttachmentThumbnail(uri: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val image by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    if (image != null) Image(
        bitmap = image!!,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    ) else Icon(Icons.Outlined.PhotoLibrary, null, tint = Navy)
}

@Composable
private fun ChecklistStep(scope: String, groups: List<ChecklistGroup>, data: ReportData, vm: ReportViewModel) {
    groups.forEach { group ->
        val done = group.items.count { ReportSchema.key(scope, group.title, it) in data.checks }
        SectionCard(group.title, "$done de ${group.items.size} concluídos") {
            CheckItems(group.items, data, vm) { ReportSchema.key(scope, group.title, it) }
        }
    }
}

@Composable
private fun FormCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF232B3D))
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        Column {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 17.dp, bottom = 13.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF232B3D))
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF778095))
            }
            HorizontalDivider(color = Border)
            Column(Modifier.padding(vertical = 7.dp)) { content() }
        }
    }
}

@Composable
private fun CheckItems(items: List<String>, data: ReportData, vm: ReportViewModel, keyFor: (String) -> String) {
    items.forEach { item ->
        val checked = keyFor(item) in data.checks
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (checked) Color(0xFFF1F8EF) else Color.Transparent)
                .clickable { vm.toggle(keyFor(item)) }
                .padding(horizontal = 5.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = Green, uncheckedColor = Color(0xFF8A91A2))
            )
            Text(
                item,
                Modifier.weight(1f).padding(end = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (checked) Color(0xFF34512D) else Color(0xFF3D4558)
            )
        }
    }
}

@Composable
private fun RadioOptions(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    options.forEach { option ->
        val active = selected == option
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (active) Color(0xFFF0F3FF) else Color.Transparent)
                .clickable { onSelect(option) }.padding(horizontal = 5.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = active, onClick = null)
            Text(option, color = Color(0xFF3D4558))
        }
    }
}

@Composable
private fun FormField(label: String, key: String, data: ReportData, vm: ReportViewModel, minLines: Int = 1) {
    OutlinedTextField(
        value = data.field(key),
        onValueChange = { vm.setField(key, it) },
        label = { Text(label) },
        minLines = minLines,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(15.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Navy,
            unfocusedBorderColor = Color(0xFFD7DBE5),
            focusedContainerColor = Color(0xFFFBFCFF),
            unfocusedContainerColor = Color(0xFFFBFCFF)
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeField(label: String, key: String, data: ReportData, vm: ReportViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    val dateState = androidx.compose.material3.rememberDatePickerState()

    Surface(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(bottom = 12.dp).clickable { showCalendar = true },
        shape = RoundedCornerShape(15.dp),
        color = Color(0xFFFBFCFF),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD7DBE5))
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (data.field(key).isNotBlank()) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = Navy)
                    Text(data.field(key), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF3D4558))
                } else {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF666C7B))
                }
            }
            Icon(Icons.Outlined.CalendarMonth, contentDescription = "Abrir calendário", tint = Navy)
        }
    }

    if (showCalendar) DatePickerDialog(
        onDismissRequest = { showCalendar = false },
        confirmButton = {
            TextButton(onClick = {
                val selected = dateState.selectedDateMillis ?: return@TextButton
                showCalendar = false
                val now = Calendar.getInstance()
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val date = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }.format(Date(selected))
                        vm.setField(key, "$date ${"%02d:%02d".format(hour, minute)}")
                    },
                    now.get(Calendar.HOUR_OF_DAY),
                    now.get(Calendar.MINUTE),
                    true
                ).show()
            }) { Text("Continuar") }
        },
        dismissButton = { TextButton(onClick = { showCalendar = false }) { Text("Cancelar") } },
        shape = RoundedCornerShape(24.dp)
    ) {
        DatePicker(state = dateState, title = { Text("Selecionar data", Modifier.padding(24.dp)) })
    }
}

@Composable
private fun SignatureField(
    title: String,
    key: String,
    value: String,
    onSaved: (String, String) -> Unit
) {
    var showPad by rememberSaveable { mutableStateOf(false) }
    Column {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Color(0xFF3D4558))
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().height(132.dp).clickable { showPad = true },
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFFBFCFF),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD7DBE5))
        ) {
            if (value.isNotBlank()) {
                Box {
                    AttachmentThumbnail(value)
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = .92f),
                        shadowElevation = 2.dp
                    ) {
                        IconButton(onClick = { onSaved(key, "") }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remover assinatura", tint = Color(0xFF6D7485))
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.Draw, null, tint = Navy, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(7.dp))
                    Text("Toque para assinar na tela", color = Navy, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    if (showPad) SignaturePadDialog(
        title = title,
        fileKey = key,
        onDismiss = { showPad = false },
        onSaved = { uri -> onSaved(key, uri); showPad = false }
    )
}

@Composable
private fun SignaturePadDialog(
    title: String,
    fileKey: String,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var strokes by androidx.compose.runtime.remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var canvasSize by androidx.compose.runtime.remember { mutableStateOf(IntSize.Zero) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Draw, null, tint = Navy) },
        title = { Text(title) },
        text = {
            Column {
                Text("Assine com o dedo dentro da área abaixo.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6D7485))
                Spacer(Modifier.height(12.dp))
                Canvas(
                    Modifier.fillMaxWidth().height(250.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(1.dp, Color(0xFFBFC5D2), RoundedCornerShape(14.dp))
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { point -> strokes = strokes + listOf(listOf(point)) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    if (strokes.isNotEmpty()) {
                                        strokes = strokes.dropLast(1) + listOf(strokes.last() + change.position)
                                    }
                                }
                            )
                        }
                ) {
                    strokes.forEach { stroke ->
                        if (stroke.size == 1) drawCircle(Color(0xFF15213D), 2.5f, stroke.first())
                        stroke.zipWithNext().forEach { (start, end) ->
                            drawLine(Color(0xFF15213D), start, end, strokeWidth = 5f, cap = StrokeCap.Round)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { strokes = emptyList() }, enabled = strokes.isNotEmpty()) { Text("Limpar assinatura") }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaved(saveSignature(context, fileKey, strokes, canvasSize)) },
                enabled = strokes.isNotEmpty() && canvasSize != IntSize.Zero
            ) { Text("Salvar assinatura") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun InfoCard(title: String, email: String, phone: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFEBF5E8)).border(1.dp, Color(0xFFCFE7C9), RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Green), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CloudDone, null, tint = Color.White)
        }
        Spacer(Modifier.width(13.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF2F4B29))
            Text(email, style = MaterialTheme.typography.bodySmall, color = Color(0xFF50664B))
            Text(phone, style = MaterialTheme.typography.bodySmall, color = Color(0xFF50664B))
        }
    }
}

private fun attachmentFromUri(context: Context, uri: Uri): ReportAttachment {
    var name = "Arquivo"
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && index >= 0) name = cursor.getString(index) ?: name
    }
    val mimeType = context.contentResolver.getType(uri)
        ?: if (name.endsWith(".pdf", ignoreCase = true)) "application/pdf" else "application/octet-stream"
    return ReportAttachment(uri.toString(), name, mimeType)
}

private fun dateStamp(): String =
    SimpleDateFormat("dd-MM-yyyy HH-mm-ss", Locale("pt", "BR")).format(Date())

private fun saveSignature(
    context: Context,
    fileKey: String,
    strokes: List<List<Offset>>,
    size: IntSize
): String {
    val bitmap = android.graphics.Bitmap.createBitmap(size.width, size.height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(21, 33, 61)
        strokeWidth = 5f
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    strokes.forEach { stroke ->
        if (stroke.size == 1) canvas.drawCircle(stroke.first().x, stroke.first().y, 2.5f, paint)
        stroke.zipWithNext().forEach { (start, end) -> canvas.drawLine(start.x, start.y, end.x, end.y, paint) }
    }
    val directory = File(context.filesDir, "signatures").apply { mkdirs() }
    val file = File(directory, "${fileKey}_${System.currentTimeMillis()}.png")
    file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
}
