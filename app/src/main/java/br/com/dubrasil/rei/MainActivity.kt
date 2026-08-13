package br.com.dubrasil.rei

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ErrorOutline
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
import androidx.compose.ui.graphics.luminance
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
import androidx.core.content.ContextCompat
import br.com.dubrasil.rei.model.ChecklistGroup
import br.com.dubrasil.rei.model.ReportData
import br.com.dubrasil.rei.model.ReportAttachment
import br.com.dubrasil.rei.model.ImplementationSummary
import br.com.dubrasil.rei.model.ReportSchema
import br.com.dubrasil.rei.model.RequiredRequirement
import br.com.dubrasil.rei.model.SchemaItem
import br.com.dubrasil.rei.data.AuthClient
import br.com.dubrasil.rei.data.AuthStore
import br.com.dubrasil.rei.data.AuthUser
import br.com.dubrasil.rei.data.DeviceSyncStatus
import br.com.dubrasil.rei.data.ReiReminderScheduler
import br.com.dubrasil.rei.data.SyncDiagnostic
import br.com.dubrasil.rei.data.SupervisorDashboard
import br.com.dubrasil.rei.data.SupervisorDashboardFilters
import br.com.dubrasil.rei.data.DashboardRecordSummary
import br.com.dubrasil.rei.pdf.PdfExporter
import br.com.dubrasil.rei.ui.theme.ReiTheme
import br.com.dubrasil.rei.ui.theme.ReiThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val themePreferences = remember {
                context.getSharedPreferences("rei_appearance", Context.MODE_PRIVATE)
            }
            var themeMode by remember {
                mutableStateOf(ReiThemeMode.fromValue(themePreferences.getString("theme_mode", "system")))
            }
            ReiTheme(themeMode) {
                ReiApp(
                    themeMode = themeMode,
                    onThemeModeChange = { selected ->
                        themePreferences.edit().putString("theme_mode", selected.value).apply()
                        themeMode = selected
                    }
                )
            }
        }
    }
}

private data class Step(val title: String, val shortTitle: String, val description: String)
private data class DashboardGroup(
    val key: String,
    val title: String,
    val value: Int,
    val subtitle: String,
    val icon: String,
    val items: List<ImplementationSummary>,
    val emptyText: String
)

private val steps = listOf(
    Step("Identificação", "Dados", "Informações gerais e módulos contratados"),
    Step("Preenchimento técnico", "Técnico", "Instalação, ambiente e configurações"),
    Step("Módulo Estoque", "Estoque", "Cadastros, entradas e saídas"),
    Step("Módulo Financeiro", "Financeiro", "Lançamentos, extratos e boletos"),
    Step("Fiscal e relatórios", "Fiscal", "Obrigações fiscais e relatórios"),
    Step("Entrega e assinaturas", "Entrega", "Conclusão, evidências e responsáveis")
)

private enum class SurveyFieldType { Text, TextArea, Choice, DateTime, Photo }
private data class SurveyFieldDef(
    val key: String,
    val label: String,
    val type: SurveyFieldType = SurveyFieldType.Text,
    val options: List<String> = emptyList(),
    val minLines: Int = 1,
    val definition: SchemaItem? = null
)
private data class SurveySectionDef(val title: String, val fields: List<SurveyFieldDef>)

private val yesNoOptions = listOf("Sim", "Não")
private val baseSurveySections = listOf(
    SurveySectionDef("Levantamento de dados – Implantação TGA", listOf(
        SurveyFieldDef("empresa", "Empresa"),
        SurveyFieldDef("contato", "Contato"),
        SurveyFieldDef("telefone", "Tel/Cel"),
        SurveyFieldDef("email", "E-mail"),
        SurveyFieldDef("cnpj", "CNPJ"),
        SurveyFieldDef("inscricaoEstadual", "Insc. Estadual"),
        SurveyFieldDef("_surveyScheduledAt", "Data e hora do levantamento"),
        SurveyFieldDef("analistaLevantamento", "Analista responsável pelo levantamento"),
        SurveyFieldDef("presentesReuniao", "Presentes na reunião", SurveyFieldType.TextArea, minLines = 2)
    )),
    SurveySectionDef("Financeiro", listOf(
        SurveyFieldDef("financeiroCentroCusto", "Centro de custo", SurveyFieldType.Choice, listOf("Importar", "Usar padrão")),
        SurveyFieldDef("financeiroFormasPagamento", "Formas de pagamento", SurveyFieldType.TextArea, minLines = 3),
        SurveyFieldDef("financeiroContasPagarReceber", "Gerencia Contas a pagar/receber?", SurveyFieldType.Choice, yesNoOptions),
        SurveyFieldDef("financeiroFluxoCaixa", "Utiliza Fluxo de caixa?", SurveyFieldType.Choice, yesNoOptions),
        SurveyFieldDef("financeiroConciliacao", "Utiliza Conciliação bancária?", SurveyFieldType.Choice, yesNoOptions),
        SurveyFieldDef("financeiroCartao", "Utiliza Controle de cartão?", SurveyFieldType.Choice, yesNoOptions),
        SurveyFieldDef("financeiroCartaoMaquina", "Qual máquina utilizada?"),
        SurveyFieldDef(
            "financeiroTipoIntegracaoBoleto",
            "Tipo de integração do boleto",
            SurveyFieldType.Choice,
            listOf("Arquivo de remessa e retorno", "API", "Ambos")
        ),
        SurveyFieldDef("financeiroCheque", "Utiliza Controle de cheque?", SurveyFieldType.Choice, yesNoOptions),
        SurveyFieldDef("financeiroDescontoTitulo", "Utiliza Desconto de Título?", SurveyFieldType.Choice, yesNoOptions),
        SurveyFieldDef("financeiroPrevisaoFutura", "Utiliza Previsão futura de Contas a Pagar?", SurveyFieldType.Choice, yesNoOptions),
        SurveyFieldDef("financeiroParticularidades", "Particularidades perfil financeiro", SurveyFieldType.TextArea, minLines = 4)
    )),
    SurveySectionDef("Estoque", listOf(
        SurveyFieldDef("estoquePdv", "Utiliza PDV?", SurveyFieldType.Choice, listOf("Online", "Offline")),
        SurveyFieldDef("estoqueDevolucao", "Utiliza devolução de compra e venda?", SurveyFieldType.Choice, yesNoOptions),
        SurveyFieldDef("estoqueSerieNf", "Série da Nota Fiscal"),
        SurveyFieldDef("estoqueTiposNotas", "Quais tipos de notas emitidas sem ser venda", SurveyFieldType.TextArea, minLines = 3),
        SurveyFieldDef("estoqueParticularidades", "Particularidades perfil estoque", SurveyFieldType.TextArea, minLines = 4),
        SurveyFieldDef("estoqueComissao", "Utiliza comissão?", SurveyFieldType.Choice, yesNoOptions),
        SurveyFieldDef("estoqueComissaoPagamento", "Se SIM, pagamento sobre?", SurveyFieldType.Choice, listOf("Recebimento", "Faturamento")),
        SurveyFieldDef("estoqueOrdemServico", "Utiliza Ordem de serviço?", SurveyFieldType.Choice, yesNoOptions),
        SurveyFieldDef("estoqueControlaEstoque", "Controla Estoque?", SurveyFieldType.Choice, yesNoOptions),
        SurveyFieldDef("estoqueDetalhes", "Detalhes", SurveyFieldType.TextArea, minLines = 5),
        SurveyFieldDef("estoqueFormacaoPreco", "Utiliza formação de preço?", SurveyFieldType.Choice, yesNoOptions),
        SurveyFieldDef("estoqueCertificado", "Utiliza qual certificado?", SurveyFieldType.Choice, listOf("A1", "A3")),
        SurveyFieldDef("estoqueEmailNf", "Qual e-mail para envio NF?"),
        SurveyFieldDef("estoqueBalanca", "Utiliza balança?", SurveyFieldType.Choice, yesNoOptions),
        SurveyFieldDef("estoqueLote", "Utiliza controle de lote?", SurveyFieldType.Choice, yesNoOptions),
        SurveyFieldDef("estoqueComposicao", "Utiliza composição?", SurveyFieldType.Choice, yesNoOptions),
        SurveyFieldDef("estoqueSimilar", "Utiliza similar?", SurveyFieldType.Choice, yesNoOptions),
        SurveyFieldDef("estoqueSerieProduto", "Utiliza controle de série cadastro produto?", SurveyFieldType.Choice, yesNoOptions)
    )),
    SurveySectionDef("Gerais", listOf(
        SurveyFieldDef("geralAgendamento", "A implantação pode ser agendada em qualquer período?", SurveyFieldType.Choice, yesNoOptions),
        SurveyFieldDef("geralRelatorios", "Relatórios: Quais relatórios são utilizados ao longo do mês?", SurveyFieldType.TextArea, minLines = 5),
        SurveyFieldDef("geralWorkflow", "Workflow", SurveyFieldType.TextArea, minLines = 5),
        SurveyFieldDef("geralCustomizacao", "Customização", SurveyFieldType.TextArea, minLines = 3)
    )),
    SurveySectionDef("Movimentos de entrada", listOf(SurveyFieldDef("movimentosEntrada", "Movimentos de entrada", SurveyFieldType.TextArea, minLines = 5))),
    SurveySectionDef("Movimentos de saída", listOf(SurveyFieldDef("movimentosSaida", "Movimentos de saída", SurveyFieldType.TextArea, minLines = 5))),
    SurveySectionDef("Anotações", listOf(SurveyFieldDef("anotacoes", "Anotações", SurveyFieldType.TextArea, minLines = 5))),
    SurveySectionDef("Fluxograma inicial", listOf(SurveyFieldDef("fluxogramaInicial", "Fluxograma inicial", SurveyFieldType.TextArea, minLines = 5)))
)

private fun activeSurveySections(): List<SurveySectionDef> {
    val result = baseSurveySections.map { it.copy(fields = it.fields.toMutableList()) }.toMutableList()
    ReportSchema.surveySections.forEach { custom ->
        val index = result.indexOfFirst { it.title.equals(custom.title, ignoreCase = true) }
        val customFields = custom.fields.mapNotNull { field ->
            val type = when (field.type.lowercase(Locale.ROOT)) {
                "choice" -> SurveyFieldType.Choice
                "textarea" -> SurveyFieldType.TextArea
                "date", "datetime-local" -> SurveyFieldType.DateTime
                "photo" -> SurveyFieldType.Photo
                else -> SurveyFieldType.Text
            }
            if (field.key.isBlank() || field.label.isBlank()) null
            else SurveyFieldDef(
                key = field.key,
                label = field.label,
                type = type,
                options = if (type == SurveyFieldType.Choice) field.options.ifEmpty { yesNoOptions } else emptyList(),
                minLines = if (type == SurveyFieldType.TextArea) field.minLines.coerceAtLeast(3) else 1,
                definition = field.asSchemaItem()
            )
        }
        if (index >= 0) {
            val current = result[index]
            val merged = current.fields.toMutableList()
            customFields.forEach { customField ->
                val fieldIndex = merged.indexOfFirst { it.key == customField.key }
                if (fieldIndex >= 0) merged[fieldIndex] = customField else merged.add(customField)
            }
            result[index] = current.copy(fields = merged)
        } else if (customFields.isNotEmpty()) {
            result.add(SurveySectionDef(custom.title, customFields))
        }
    }
    return result
}

private fun surveyTabTitle(title: String): String = when {
    title.startsWith("Levantamento", ignoreCase = true) -> "Identificação"
    title.equals("Movimentos de entrada", ignoreCase = true) -> "Entrada"
    title.startsWith("Movimentos de sa", ignoreCase = true) -> "Saída"
    else -> title
}

private val Navy = Color(0xFF263A7A)
private val NavyDark = Color(0xFF172653)
private val Green = Color(0xFF58AD45)
@Composable private fun appPageColor() = MaterialTheme.colorScheme.background
@Composable private fun appSurfaceColor() = MaterialTheme.colorScheme.surface
@Composable private fun appBorderColor() = MaterialTheme.colorScheme.outlineVariant
@Composable private fun appTextColor() = MaterialTheme.colorScheme.onSurface
@Composable private fun appMutedColor() = MaterialTheme.colorScheme.onSurfaceVariant
@Composable private fun appLogoResource(): Int =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        R.drawable.logo_dubrasil_white
    } else {
        R.drawable.logo_dubrasil_blue
    }

/** Raiz da navegação Compose e ponto de integração com câmera, arquivos e compartilhamento. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReiApp(
    themeMode: ReiThemeMode,
    onThemeModeChange: (ReiThemeMode) -> Unit,
    vm: ReportViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        ReiReminderScheduler.scheduleDailyReminders(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val authStore = remember { AuthStore(context) }
    val authClient = remember { AuthClient(context) }
    var currentUser by remember { mutableStateOf(authStore.currentUser()) }
    val report = vm.report
    var showDashboard by rememberSaveable { mutableStateOf(currentUser != null) }
    var viewingReportId by rememberSaveable { mutableStateOf<String?>(null) }
    var viewingSurveyReadOnly by rememberSaveable { mutableStateOf(false) }
    var surveyReportId by rememberSaveable { mutableStateOf<String?>(null) }
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var showNewClientDialog by rememberSaveable { mutableStateOf(false) }
    var editingClientId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    var editorRequirements by remember { mutableStateOf<List<RequiredRequirement>>(emptyList()) }

    if (currentUser == null) {
        LoginScreen(onAuthenticated = { user ->
            currentUser = user
            currentStep = 0
            showDashboard = true
        })
        return
    }
    val authenticatedUser = currentUser!!
    val pendingSupervisorEvaluations = vm.history.filter {
        authenticatedUser.isSupervisor &&
            !it.isSurveyStage() &&
            it.isReadyForSupervisorEvaluation() &&
            !hasSupervisorEvaluation(it.report)
    }
    var showEvaluationReminder by rememberSaveable(authenticatedUser.username) { mutableStateOf(false) }
    LaunchedEffect(
        authenticatedUser.username,
        showDashboard,
        pendingSupervisorEvaluations.joinToString(separator = "|") { it.id }
    ) {
        if (showDashboard && pendingSupervisorEvaluations.isNotEmpty()) {
            val preferences = context.getSharedPreferences("rei_daily_reminders", Context.MODE_PRIVATE)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
            val key = "supervisor_evaluation_${authenticatedUser.username.lowercase(Locale.ROOT)}"
            if (preferences.getString(key, "") != today) {
                preferences.edit().putString(key, today).apply()
                showEvaluationReminder = true
            }
        }
    }
    LaunchedEffect(showDashboard, authenticatedUser.username) {
        if (showDashboard) vm.refreshFromServer()
    }
    LaunchedEffect(vm.serverMessage) {
        vm.serverMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            vm.consumeServerMessage()
        }
    }
    val logout = {
        authStore.clear()
        currentUser = null
        showDashboard = false
    }
    val exportAndSharePdf = { fileName: String, exportReport: ReportData, archiveAfterShare: Boolean, surveyReport: Boolean ->
        runCatching {
            val directory = File(context.filesDir, "shared_reports").apply { mkdirs() }
            directory.listFiles()?.forEach { oldFile ->
                if (oldFile.isFile && oldFile.lastModified() < System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000) {
                    oldFile.delete()
                }
            }
            val file = File(directory, fileName)
            file.outputStream().use { output -> PdfExporter.write(context, output, exportReport, surveyReport) }
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
    val surveyItem = surveyReportId?.let { id -> vm.history.firstOrNull { it.id == id } }
    if (surveyItem != null) {
        LaunchedEffect(surveyItem.id) { vm.openSurvey(surveyItem.id) }
        SurveyScreen(
            data = report,
            vm = vm,
            onBack = { surveyReportId = null },
            onLogout = logout,
            onChangePassword = { currentPassword, newPassword ->
                authClient.changePassword(currentPassword, newPassword)
            },
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onSave = {
                vm.saveSurveyDraft()
                    .onSuccess {
                        surveyReportId = null
                        showDashboard = true
                        Toast.makeText(context, "Levantamento salvo no dispositivo", Toast.LENGTH_LONG).show()
                    }
                    .onFailure { error ->
                        Toast.makeText(context, "Não foi possível gravar o levantamento: ${error.message.orEmpty()}", Toast.LENGTH_LONG).show()
                    }
            },
            onComplete = {
                vm.completeSurvey()
                    .onSuccess { completed ->
                        surveyReportId = null
                        showDashboard = true
                        Toast.makeText(context, "Levantamento salvo no dispositivo e liberado para R.E.I.", Toast.LENGTH_LONG).show()
                        exportAndSharePdf(surveyPdfFileName(completed.client), completed.report, false, true)
                    }
                    .onFailure { error ->
                        Toast.makeText(context, "Não foi possível concluir o levantamento: ${error.message.orEmpty()}", Toast.LENGTH_LONG).show()
                    }
            }
        )
        return
    }
    if (viewedReport != null) {
        ReportViewerScreen(
            item = viewedReport,
            surveyMode = viewingSurveyReadOnly,
            onBack = { viewingReportId = null; viewingSurveyReadOnly = false },
            onLogout = logout,
            onChangePassword = { currentPassword, newPassword ->
                authClient.changePassword(currentPassword, newPassword)
            },
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onEdit = if (!viewingSurveyReadOnly && !authenticatedUser.isSupervisor) ({
                vm.editCompletedReport(viewedReport.id, authenticatedUser.username)
                viewingReportId = null
                currentStep = 0
                showDashboard = false
            }) else if (!viewingSurveyReadOnly && viewedReport.stage() == "levantamento_pendente") ({
                editingClientId = viewedReport.id
                viewingReportId = null
                showNewClientDialog = true
            }) else null,
            onEvaluate = if (!viewingSurveyReadOnly && authenticatedUser.isSupervisor && viewedReport.isReadyForSupervisorEvaluation() && !hasSupervisorEvaluation(viewedReport.report)) ({ score, rating, supervisionChecks ->
                vm.saveSupervisorEvaluation(viewedReport.id, authenticatedUser.username, score, rating, supervisionChecks)
                Toast.makeText(context, "Avaliação da supervisão salva", Toast.LENGTH_LONG).show()
            }) else null,
            onReprint = if ((viewingSurveyReadOnly && viewedReport.hasCompletedSurvey()) || (!viewingSurveyReadOnly && viewedReport.isReadyForSupervisorEvaluation())) ({
                val fileName = if (viewingSurveyReadOnly) surveyPdfFileName(viewedReport.client) else reportPdfFileName(viewedReport.client)
                exportAndSharePdf(fileName, viewedReport.report, false, viewingSurveyReadOnly)
            }) else null
        )
        return
    }

    if (showDashboard) {
        DashboardScreen(
            history = vm.history,
            draft = report,
            user = authenticatedUser,
            syncDiagnostic = vm.syncDiagnostic.copy(
                pendingCount = vm.history.count { it.syncStatus != "SYNCED" }
            ),
            deviceStatuses = vm.deviceStatuses,
            isSyncing = vm.isSyncing,
            onSyncNow = vm::synchronizeNow,
            onLogout = logout,
            onChangePassword = { currentPassword, newPassword ->
                authClient.changePassword(currentPassword, newPassword)
            },
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onResumeDraft = { currentStep = 0; showDashboard = false },
            onNewReport = { vm.startNewReport(authenticatedUser.username); currentStep = 0; showDashboard = false },
            onNewSurvey = {
                val id = vm.startNewSurvey(authenticatedUser.username, authenticatedUser.fullName)
                surveyReportId = id
            },
            onOpenReport = {
                viewingSurveyReadOnly = false
                if (!authenticatedUser.isSupervisor && it.stage() == "rei_pendente") {
                    vm.editCompletedReport(it.id, authenticatedUser.username)
                    currentStep = 0
                    showDashboard = false
                } else {
                    viewingReportId = it.id
                }
            },
            onOpenSurvey = {
                if (it.hasCompletedSurvey()) {
                    viewingSurveyReadOnly = true
                    viewingReportId = it.id
                } else {
                    viewingSurveyReadOnly = false
                    surveyReportId = it.id
                }
            },
            onNewClient = { editingClientId = null; showNewClientDialog = true }
        )
        if (showEvaluationReminder && pendingSupervisorEvaluations.isNotEmpty()) {
            DailyEvaluationReminderDialog(
                pending = pendingSupervisorEvaluations,
                onDismiss = { showEvaluationReminder = false },
                onEvaluateNow = {
                    val target = pendingSupervisorEvaluations.firstOrNull()
                    showEvaluationReminder = false
                    if (target != null) viewingReportId = target.id
                }
            )
        }
        if (showNewClientDialog) NewClientDialog(
            initialFields = editingClientId?.let { id -> vm.history.firstOrNull { it.id == id }?.report?.fields }.orEmpty(),
            onDismiss = { showNewClientDialog = false; editingClientId = null },
            onSave = { fields ->
                editingClientId?.let { id ->
                    vm.updateSurveyClient(id, fields, authenticatedUser.username)
                } ?: vm.createSurveyClient(fields, authenticatedUser.username)
                showNewClientDialog = false
                editingClientId = null
                Toast.makeText(context, "Cliente salvo e enviado para levantamento", Toast.LENGTH_LONG).show()
            }
        )
        return
    }

    Scaffold(
        containerColor = appPageColor(),
        topBar = {
            ReiTopBar(
                onHome = { showDashboard = true },
                onNewReport = { confirmClear = true },
                onLogout = logout,
                onChangePassword = { currentPassword, newPassword ->
                    authClient.changePassword(currentPassword, newPassword)
                },
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange
            )
        },
        bottomBar = {
            BottomActions(
                currentStep = currentStep,
                canExport = report.isConcludedDelivery(),
                onBack = { currentStep-- },
                onNext = { currentStep++ },
                onSaveOnly = {
                    if (report.field("cliente").isBlank()) {
                        Toast.makeText(context, "Informe o cliente/projeto antes de salvar", Toast.LENGTH_LONG).show()
                        currentStep = 0
                    } else {
                        vm.archiveCurrentReport()
                        showDashboard = true
                        Toast.makeText(context, "Implantação salva para acompanhamento", Toast.LENGTH_LONG).show()
                    }
                },
                onExport = {
                    val missing = ReportSchema.validateRequiredRequirements(report, ReportSchema.PHASE_REI)
                    if (missing.isNotEmpty()) editorRequirements = missing
                    else {
                        val snapshot = ReportSchema.validationSnapshot(report, ReportSchema.PHASE_REI)
                        vm.setField("_requiredValidationSnapshot", snapshot)
                        exportAndSharePdf(
                            reportPdfFileName(report.field("cliente")),
                            report.copy(fields = report.fields + ("_requiredValidationSnapshot" to snapshot)),
                            true,
                            false
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (editorRequirements.isNotEmpty()) {
            RequiredRequirementsDialog(
                requirements = editorRequirements,
                onDismiss = {
                    currentStep = reiStepForRequirement(editorRequirements.first())
                    editorRequirements = emptyList()
                },
                onGoToFirst = {
                    currentStep = reiStepForRequirement(editorRequirements.first())
                    editorRequirements = emptyList()
                }
            )
        }
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
                    color = appTextColor()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    steps[currentStep].description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = appMutedColor()
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
        icon = { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.primary) },
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
    if (showNewClientDialog) NewClientDialog(
        initialFields = editingClientId?.let { id -> vm.history.firstOrNull { it.id == id }?.report?.fields }.orEmpty(),
        onDismiss = { showNewClientDialog = false; editingClientId = null },
        onSave = { fields ->
            editingClientId?.let { id ->
                vm.updateSurveyClient(id, fields, authenticatedUser.username)
            } ?: vm.createSurveyClient(fields, authenticatedUser.username)
            showNewClientDialog = false
            editingClientId = null
            Toast.makeText(context, "Cliente salvo e enviado para levantamento", Toast.LENGTH_LONG).show()
        }
    )
}

@Composable
private fun DailyEvaluationReminderDialog(
    pending: List<ImplementationSummary>,
    onDismiss: () -> Unit,
    onEvaluateNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.CheckCircle, null, tint = Green) },
        title = { Text("Avaliações pendentes", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Existem ${pending.size} implantação(ões) concluída(s) aguardando sua avaliação.",
                    color = appTextColor()
                )
                Spacer(Modifier.height(12.dp))
                pending.take(3).forEach { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            item.client.ifBlank { "Cliente não informado" },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            fontWeight = FontWeight.Bold,
                            color = appTextColor()
                        )
                    }
                }
                if (pending.size > 3) {
                    Text(
                        "E mais ${pending.size - 3} implantação(ões).",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = appMutedColor()
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Este aviso será apresentado no primeiro acesso de cada dia até que todas sejam avaliadas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = appMutedColor()
                )
            }
        },
        confirmButton = {
            Button(onClick = onEvaluateNow, colors = ButtonDefaults.buttonColors(containerColor = Green)) {
                Text("Avaliar agora", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Lembrar amanhã") }
        }
    )
}

@Composable
private fun LoginScreen(onAuthenticated: (AuthUser) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { AuthClient(context) }
    val authStore = remember { AuthStore(context) }
    val scrollState = rememberScrollState()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var serverUrl by rememberSaveable { mutableStateOf(authStore.serverUrl()) }
    var showConnectionSettings by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(appPageColor(), MaterialTheme.colorScheme.surfaceVariant))
        )
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(scrollState).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        Card(
            Modifier.fillMaxWidth().widthIn(max = 430.dp),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = appSurfaceColor()),
            border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
        ) {
            Column(Modifier.padding(25.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(appLogoResource()),
                    contentDescription = "DuBrasil Soluções",
                    modifier = Modifier.size(108.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                        Text("Acesso ao R.E.I.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Entre com o usuário cadastrado pelo supervisor.", style = MaterialTheme.typography.bodySmall, color = appMutedColor())
                    }
                    Spacer(Modifier.width(10.dp))
                    IconButton(
                        onClick = { showConnectionSettings = !showConnectionSettings },
                        modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Configurar conexão", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (showConnectionSettings) {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = {
                            serverUrl = it
                            authStore.saveServerUrl(it)
                            error = ""
                        },
                        label = { Text("Endereço do servidor") },
                        leadingIcon = { Icon(Icons.Outlined.BusinessCenter, null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(15.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Use apenas para trocar o servidor. Se já acessou antes, o login funciona offline no celular.",
                        style = MaterialTheme.typography.labelSmall,
                        color = appMutedColor()
                    )
                    Spacer(Modifier.height(10.dp))
                }
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
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(17.dp))
                Button(
                    onClick = {
                        if (username.isBlank() || password.isBlank()) {
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
                Text("Servidor do escritório • acesso protegido", style = MaterialTheme.typography.labelMedium, color = appMutedColor())
            }
        }
    }
}
}

/** Dashboard comum aos dois perfis; as permissões alteram ações, não a estrutura visual. */
@Composable
private fun DashboardScreen(
    history: List<ImplementationSummary>,
    draft: ReportData,
    user: AuthUser,
    syncDiagnostic: SyncDiagnostic,
    deviceStatuses: List<DeviceSyncStatus>,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
    onLogout: () -> Unit,
    onChangePassword: (String, String) -> Result<Unit>,
    themeMode: ReiThemeMode,
    onThemeModeChange: (ReiThemeMode) -> Unit,
    onResumeDraft: () -> Unit,
    onNewReport: () -> Unit,
    onNewSurvey: () -> Unit,
    onOpenReport: (ImplementationSummary) -> Unit,
    onOpenSurvey: (ImplementationSummary) -> Unit,
    onNewClient: () -> Unit
) {
    val averageDays = history
        .filter { it.isReadyForSupervisorEvaluation() }
        .mapNotNull { it.implementationDurationDays() }
        .takeIf { it.isNotEmpty() }
        ?.average()
    val hasDraft = draft.fields.any { (key, value) -> key != "_id" && value.isNotBlank() } ||
        draft.checks.isNotEmpty() || draft.attachments.isNotEmpty() || draft.deliveryStatus.isNotBlank()
    val evaluations = history.filter { hasSupervisorEvaluation(it.report) }
    val averageScore = evaluations.mapNotNull { supervisionScore(it.report) }.takeIf { it.isNotEmpty() }?.average()
    var dashboardArea by rememberSaveable(user.username, "dashboardArea") { mutableStateOf("home") }
    var selectedDashboardFilter by rememberSaveable(user.username) { mutableStateOf("levantamentos") }
    val scopedHistory = if (user.isSupervisor) history else history.filter { it.isAssignedTo(user.username) }
    val surveyPending = scopedHistory.filter { it.stage() == "levantamento_pendente" }
    val reiPending = scopedHistory.filter { it.stage() == "rei_pendente" }
    val surveyCompleted = scopedHistory.filter { it.hasCompletedSurvey() }
    val allSurveys = surveyPending + surveyCompleted
    val reiReports = scopedHistory.filterNot { it.isSurveyStage() }
    val inProgress = reiReports.filterNot { it.isReadyForSupervisorEvaluation() }
    val concluded = reiReports.filter { it.isReadyForSupervisorEvaluation() }
    val lastSurveyDate = surveyCompleted.maxByOrNull { it.surveyCompletedAt() }?.let {
        dashboardDate(it.surveyCompletedAt())
    } ?: "—"
    val lastReiDeliveryDate = concluded.maxByOrNull { it.reiDeliveredAt() }?.let {
        dashboardDate(it.reiDeliveredAt())
    } ?: "—"
    val dashboardGroups = if (dashboardArea == "levantamentos") {
        listOf(
            DashboardGroup("levantamentos", "Levantamentos pendentes", surveyPending.size, if (user.isSupervisor) "Clientes aguardando levantamento" else "Disponíveis para preencher", "file", surveyPending, "Nenhum levantamento pendente."),
            DashboardGroup("levantamentos_concluidos", "Levantamentos concluídos", surveyCompleted.size, "Somente visualização e impressão", "calendar", surveyCompleted, "Nenhum levantamento concluído.")
        )
    } else {
        listOf(
            DashboardGroup("pendentes", "Implantações pendentes", reiPending.size, "R.E.I. liberado para iniciar", "briefcase", reiPending, "Nenhuma implantação pendente para iniciar o R.E.I."),
            DashboardGroup("andamento", "Implantações em andamento", inProgress.size, "Iniciadas e ainda não concluídas", "timer", inProgress, "Nenhuma implantação em andamento."),
            DashboardGroup("concluidas", "Implantações concluídas", concluded.size, "Disponíveis para visualização/PDF", "calendar", concluded, "Nenhuma implantação concluída.")
        )
    }
    val activeDashboardGroup = dashboardGroups.firstOrNull { it.key == selectedDashboardFilter } ?: dashboardGroups.first()

    if (dashboardArea == "home") {
        Scaffold(
            containerColor = appPageColor(),
            topBar = { DashboardHeader(user, onLogout, onChangePassword, themeMode, onThemeModeChange) }
        ) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(NavyDark, Navy))).padding(20.dp)
                ) {
                    Text(if (user.isSupervisor) "ÁREA DO SUPERVISOR" else "ÁREA DO IMPLANTADOR", color = Color(0xFFBFC9F5), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Escolha a operação", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Acesse implantações R.E.I. ou levantamentos em telas separadas.", color = Color(0xFFD9DFF6), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(14.dp))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 600.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DashboardAreaButton(
                                modifier = Modifier.fillMaxWidth(),
                                title = "Implantação",
                                subtitle = "Dashboard, relatórios e avaliações.",
                                value = (reiPending.size + reiReports.size).toString(),
                                icon = "briefcase",
                                onClick = {
                                    dashboardArea = "implantacoes"
                                    selectedDashboardFilter = "pendentes"
                                }
                            )
                            DashboardAreaButton(
                                modifier = Modifier.fillMaxWidth(),
                                title = "Levantamentos",
                                subtitle = "Coleta de dados e pendências.",
                                value = allSurveys.size.toString(),
                                icon = "file",
                                onClick = {
                                    dashboardArea = "levantamentos"
                                    selectedDashboardFilter = "levantamentos"
                                }
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DashboardAreaButton(
                                modifier = Modifier.weight(1f),
                                title = "Implantação",
                                subtitle = "Dashboard, relatórios e avaliações.",
                                value = (reiPending.size + reiReports.size).toString(),
                                icon = "briefcase",
                                onClick = {
                                    dashboardArea = "implantacoes"
                                    selectedDashboardFilter = "pendentes"
                                }
                            )
                            DashboardAreaButton(
                                modifier = Modifier.weight(1f),
                                title = "Levantamentos",
                                subtitle = "Coleta de dados e pendências.",
                                value = allSurveys.size.toString(),
                                icon = "file",
                                onClick = {
                                    dashboardArea = "levantamentos"
                                    selectedDashboardFilter = "levantamentos"
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("Resumo rápido", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = appTextColor())
                Spacer(Modifier.height(10.dp))
                val summaryMetrics = listOf(
                    Triple("briefcase", (reiPending.size + reiReports.size).toString(), "Implantações"),
                    Triple("file", allSurveys.size.toString(), "Levantamentos"),
                    Triple("evaluation", evaluations.size.toString(), "Avaliações"),
                    Triple("calendar", lastSurveyDate, "Último levantamento"),
                    Triple("calendar", lastReiDeliveryDate, "Última entrega do R.E.I.")
                )
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 900.dp) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            summaryMetrics.forEach { (icon, value, label) ->
                                DashboardMetricCard(Modifier.weight(1f), icon, value, label)
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            summaryMetrics.take(4).chunked(2).forEach { metricsRow ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    metricsRow.forEach { (icon, value, label) ->
                                        DashboardMetricCard(Modifier.weight(1f), icon, value, label)
                                    }
                                }
                            }
                            val (icon, value, label) = summaryMetrics.last()
                            DashboardMetricCard(Modifier.fillMaxWidth(), icon, value, label)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("Gráficos separados", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = appTextColor())
                Spacer(Modifier.height(10.dp))
                MonthlyDeliveriesChart(reiPending + reiReports, "Implantações por mês")
                Spacer(Modifier.height(12.dp))
                MonthlyDeliveriesChart(surveyPending, "Levantamentos por mês")
                Spacer(Modifier.height(12.dp))
            }
        }
        return
    }

    Scaffold(
        containerColor = appPageColor(),
        topBar = { DashboardHeader(user, onLogout, onChangePassword, themeMode, onThemeModeChange) },
        bottomBar = {
            if (user.isSupervisor && dashboardArea == "levantamentos") {
                Surface(
                    modifier = Modifier.navigationBarsPadding(),
                    color = appSurfaceColor(),
                    shadowElevation = 12.dp
                ) {
                    Button(
                        onClick = onNewClient,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp).height(54.dp),
                        shape = RoundedCornerShape(17.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White)
                    ) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(24.dp))
                        Spacer(Modifier.width(9.dp))
                        Text("Cadastrar cliente", fontWeight = FontWeight.Bold)
                    }
                }
            } else if (!user.isSupervisor) {
                Surface(
                    modifier = Modifier.navigationBarsPadding(),
                    color = appSurfaceColor(),
                    shadowElevation = 12.dp
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                        if (dashboardArea == "levantamentos") {
                            OutlinedButton(
                                onClick = onNewSurvey,
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(17.dp)
                            ) {
                                Icon(Icons.Outlined.Description, null, Modifier.size(21.dp))
                                Spacer(Modifier.width(7.dp))
                                Text("Novo levantamento", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onNewReport,
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(17.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Navy,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Outlined.Add, null, Modifier.size(21.dp))
                                Spacer(Modifier.width(7.dp))
                                Text("Nova implantação", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 20.dp)
        ) {
            OutlinedButton(
                onClick = {
                    dashboardArea = "home"
                    selectedDashboardFilter = "levantamentos"
                },
                shape = RoundedCornerShape(15.dp)
            ) {
                Icon(Icons.Outlined.Home, null, Modifier.size(19.dp))
                Spacer(Modifier.width(7.dp))
                Text("Voltar")
            }
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(NavyDark, Navy))).padding(22.dp)
            ) {
                Text(
                    if (dashboardArea == "levantamentos") "LEVANTAMENTOS" else "IMPLANTAÇÃO",
                    color = Color(0xFFBFC9F5),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    if (dashboardArea == "levantamentos") "Dados dos levantamentos" else "Visão geral das entregas",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    if (dashboardArea == "levantamentos") {
                        "Acompanhe os levantamentos pendentes e a coleta de dados."
                    } else {
                        "Acompanhe o ritmo, histórico e avaliações dos projetos ERP."
                    },
                    color = Color(0xFFD9DFF6),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(18.dp))
            SyncDiagnosticCard(syncDiagnostic, isSyncing, onSyncNow)
            if (user.isSupervisor) {
                Spacer(Modifier.height(14.dp))
                DeviceSyncPanel(deviceStatuses)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(if (dashboardArea == "levantamentos") Icons.Outlined.Description else Icons.Outlined.BusinessCenter, null, tint = MaterialTheme.colorScheme.primary) },
                    value = if (dashboardArea == "levantamentos") surveyPending.size.toString() else (reiPending.size + reiReports.size).toString(),
                    label = if (dashboardArea == "levantamentos") "Pendentes" else "Implantações"
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    icon = { Icon(Icons.Outlined.Timer, null, tint = Green) },
                    value = if (dashboardArea == "levantamentos") surveyCompleted.size.toString() else averageDays?.let { String.format(Locale("pt", "BR"), "%.1f dias", it) } ?: "—",
                    label = if (dashboardArea == "levantamentos") "Concluídos" else "Média de dias gastos"
                )
            }
            if (dashboardArea != "levantamentos") {
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
                        icon = { Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary) },
                        value = evaluations.size.toString(),
                        label = "Avaliacoes"
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                Modifier.fillMaxWidth(),
                color = appSurfaceColor(),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Green)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (dashboardArea == "levantamentos") "Último levantamento" else "Última entrega do R.E.I.", style = MaterialTheme.typography.bodySmall, color = appMutedColor())
                        Text(
                            if (dashboardArea == "levantamentos") {
                                surveyCompleted.maxByOrNull { it.report.field("_surveyCompletedAt").toLongOrNull() ?: it.completedAt }?.let {
                                    val completedAt = it.report.field("_surveyCompletedAt").toLongOrNull() ?: it.completedAt
                                    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(completedAt))
                                } ?: "—"
                            } else lastReiDeliveryDate,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Acompanhamento", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = appTextColor())
            Spacer(Modifier.height(12.dp))
            dashboardGroups.chunked(2).forEach { rowGroups ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowGroups.forEach { group ->
                        WorkflowSummaryCard(
                            modifier = Modifier.weight(1f),
                            group = group,
                            active = group.key == activeDashboardGroup.key,
                            onClick = { selectedDashboardFilter = group.key }
                        )
                    }
                    if (rowGroups.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
            HistorySection(
                title = activeDashboardGroup.title,
                items = activeDashboardGroup.items,
                emptyText = activeDashboardGroup.emptyText,
                onOpenReport = { item ->
                    if (dashboardArea == "levantamentos" && item.hasCompletedSurvey()) onOpenSurvey(item)
                    else if (!user.isSupervisor && item.stage() == "levantamento_pendente") onOpenSurvey(item)
                    else onOpenReport(item)
                }
            )
            Spacer(Modifier.height(18.dp))
            MonthlyDeliveriesChart(if (dashboardArea == "levantamentos") allSurveys else reiPending + reiReports, if (dashboardArea == "levantamentos") "Levantamentos por mês" else "Implantações por mês")
            Spacer(Modifier.height(14.dp))
            StatusDistributionChart(if (dashboardArea == "levantamentos") allSurveys else reiReports, if (dashboardArea == "levantamentos") "Situação dos levantamentos" else "Situação das implantações")
            if (dashboardArea != "levantamentos") {
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
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HistorySection(
    title: String,
    items: List<ImplementationSummary>,
    emptyText: String,
    total: Int = items.size,
    onOpenReport: (ImplementationSummary) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.BusinessCenter, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = appTextColor())
        Spacer(Modifier.weight(1f))
        Text("$total total", style = MaterialTheme.typography.labelMedium, color = appMutedColor())
    }
    Spacer(Modifier.height(12.dp))
    if (items.isEmpty()) {
        EmptyHistoryCard(emptyText)
    } else {
        items.forEach { item ->
            HistoryCard(item) { onOpenReport(item) }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun NewClientDialog(initialFields: Map<String, String> = emptyMap(), onDismiss: () -> Unit, onSave: (Map<String, String>) -> Unit) {
    var cliente by rememberSaveable(initialFields["_id"]) { mutableStateOf(initialFields["cliente"].orEmpty()) }
    var contato by rememberSaveable(initialFields["_id"]) { mutableStateOf(initialFields["contato"].orEmpty()) }
    var telefone by rememberSaveable(initialFields["_id"]) { mutableStateOf(initialFields["telefone"].orEmpty()) }
    var email by rememberSaveable(initialFields["_id"]) { mutableStateOf(initialFields["email"].orEmpty()) }
    var cnpj by rememberSaveable(initialFields["_id"]) { mutableStateOf(initialFields["cnpj"].orEmpty()) }
    var inscricao by rememberSaveable(initialFields["_id"]) { mutableStateOf(initialFields["inscricaoEstadual"].orEmpty()) }
    var implantador by rememberSaveable(initialFields["_id"]) { mutableStateOf(initialFields["_assignedImplantadorUsername"].orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialFields.isEmpty()) "Cadastrar cliente" else "Editar cliente") },
        text = {
            Column {
                OutlinedTextField(cliente, { cliente = it }, label = { Text("Cliente / Projeto") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(implantador, { implantador = it }, label = { Text("Usuário do implantador responsável") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(contato, { contato = it }, label = { Text("Contato") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(telefone, { telefone = it }, label = { Text("Tel/Cel") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cnpj, { cnpj = it }, label = { Text("CNPJ") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(inscricao, { inscricao = it }, label = { Text("Inscrição Estadual") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                if (cliente.isNotBlank() && implantador.isNotBlank()) onSave(
                    mapOf(
                        "cliente" to cliente,
                        "empresa" to cliente,
                        "_assignedImplantadorUsername" to implantador.trim().lowercase(Locale.ROOT),
                        "_assignedImplantadorName" to implantador.trim(),
                        "contato" to contato,
                        "telefone" to telefone,
                        "email" to email,
                        "cnpj" to cnpj,
                        "inscricaoEstadual" to inscricao
                    )
                )
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun SurveyScreen(
    data: ReportData,
    vm: ReportViewModel,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onChangePassword: (String, String) -> Result<Unit>,
    themeMode: ReiThemeMode,
    onThemeModeChange: (ReiThemeMode) -> Unit,
    onSave: () -> Unit,
    onComplete: () -> Unit
) {
    var surveyStep by rememberSaveable(data.field("_id")) { mutableIntStateOf(0) }
    var missingRequirements by remember { mutableStateOf<List<RequiredRequirement>>(emptyList()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val sections = remember(vm.schemaVersion) { activeSurveySections() }
    val currentIndex = surveyStep.coerceIn(0, sections.lastIndex)
    val currentSection = sections[currentIndex]
    val progress = (currentIndex + 1f) / sections.size
    fun persistThen(action: () -> Unit) {
        vm.saveSurveyDraft()
            .onSuccess { action() }
            .onFailure { error ->
                Toast.makeText(
                    context,
                    "Não foi possível salvar antes de trocar de etapa: ${error.message.orEmpty()}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
    fun goToStep(index: Int) {
        val target = index.coerceIn(0, sections.lastIndex)
        if (target != currentIndex) persistThen { surveyStep = target }
    }
    BackHandler { persistThen(onBack) }
    Scaffold(
        containerColor = appPageColor(),
        topBar = {
            Surface(modifier = Modifier.statusBarsPadding(), color = appSurfaceColor(), shadowElevation = 1.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { persistThen(onBack) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar") }
                    Column(Modifier.weight(1f)) {
                        Text("Levantamento", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(data.field("cliente").ifBlank { data.field("empresa").ifBlank { "Cliente não informado" } }, style = MaterialTheme.typography.bodySmall, color = appMutedColor())
                    }
                    AccountSettingsButton(onLogout, onChangePassword, themeMode, onThemeModeChange)
                }
            }
        },
        bottomBar = {
            Surface(modifier = Modifier.navigationBarsPadding(), color = appSurfaceColor(), shadowElevation = 12.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (currentIndex > 0) {
                        OutlinedButton(
                            onClick = { goToStep(currentIndex - 1) },
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Anterior")
                        }
                    }
                    OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Outlined.Save, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Salvar")
                    }
                    if (currentIndex < sections.lastIndex) {
                        Button(
                            onClick = { goToStep(currentIndex + 1) },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White)
                        ) {
                            Text("Próximo")
                            Spacer(Modifier.width(7.dp))
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp))
                        }
                    } else {
                        Button(onClick = {
                            val missing = ReportSchema.validateRequiredRequirements(data, ReportSchema.PHASE_SURVEY)
                            if (missing.isEmpty()) onComplete() else missingRequirements = missing
                        }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Green)) {
                            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Concluir")
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (missingRequirements.isNotEmpty()) {
            RequiredRequirementsDialog(
                requirements = missingRequirements,
                onDismiss = {
                    surveyStep = surveyStepForRequirement(sections, missingRequirements.first())
                    missingRequirements = emptyList()
                },
                onGoToFirst = {
                    surveyStep = surveyStepForRequirement(sections, missingRequirements.first())
                    missingRequirements = emptyList()
                }
            )
        }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(NavyDark, Navy))).padding(20.dp)) {
                Text("LEVANTAMENTO DE DADOS", color = Color(0xFFBFC9F5), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(5.dp))
                Text(data.field("cliente").ifBlank { data.field("empresa").ifBlank { "Novo levantamento" } }, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Preencha as informações levantadas junto ao cliente final.", color = Color(0xFFD9DFF6), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sections.forEachIndexed { index, section ->
                    val active = index == currentIndex
                    Button(
                        onClick = { goToStep(index) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (active) Navy else Color.White,
                            contentColor = if (active) Color.White else Navy
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text(surveyTabTitle(section.title), fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionCard(
                currentSection.title,
                "Etapa ${currentIndex + 1} de ${sections.size} • ${((progress * 100).toInt())}% do levantamento"
            ) {
                Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
                    currentSection.fields
                        .sortedBy { if (it.type == SurveyFieldType.Choice) 0 else 1 }
                        .forEach { field ->
                            SurveyField(field, data, vm)
                        }
                }
            }
        }
    }
}

private fun surveyStepForRequirement(
    sections: List<SurveySectionDef>,
    requirement: RequiredRequirement
): Int = sections.indexOfFirst { it.title == requirement.section }.coerceAtLeast(0)

private fun reiStepForRequirement(requirement: RequiredRequirement): Int {
    val section = requirement.section.lowercase(Locale("pt", "BR"))
    return when {
        "técnico" in section -> 1
        "estoque" in section -> 2
        "financeiro" in section -> 3
        "fiscal" in section -> 4
        "entrega" in section -> 5
        else -> 0
    }
}

private fun requirementDefinition(key: String, explicit: SchemaItem? = null): SchemaItem? =
    explicit ?: ReportSchema.fixedRequirements.values.flatten().firstOrNull { it.key == key }

@Composable
private fun RequiredLabel(label: String, required: Boolean, fulfilled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = appTextColor())
        if (required) {
            Text(" *", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(5.dp))
            RequiredBadge(fulfilled)
        }
    }
}

@Composable
private fun RequiredBadge(fulfilled: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (fulfilled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            if (fulfilled) "Obrigatório · cumprido" else "Obrigatório",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (fulfilled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RequiredRequirementsDialog(
    requirements: List<RequiredRequirement>,
    onDismiss: () -> Unit,
    onGoToFirst: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Itens obrigatórios pendentes") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text("Corrija todos os requisitos abaixo antes de finalizar.", color = appMutedColor())
                Spacer(Modifier.height(12.dp))
                requirements.groupBy { it.section }.forEach { (section, items) ->
                    Text(section, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(5.dp))
                    items.forEach { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp),
                            shape = RoundedCornerShape(13.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = .28f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = .35f))
                        ) {
                            Column(Modifier.padding(11.dp)) {
                                Text(item.label, fontWeight = FontWeight.Bold, color = appTextColor())
                                Text(item.reason, style = MaterialTheme.typography.bodySmall, color = appMutedColor())
                                Text(item.requiredBecause, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                }
            }
        },
        confirmButton = { Button(onClick = onGoToFirst) { Text("Ir para o primeiro item") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun SupervisorManagementDashboard(
    dashboard: SupervisorDashboard,
    filters: SupervisorDashboardFilters,
    loading: Boolean,
    onFiltersChange: (SupervisorDashboardFilters) -> Unit,
    onOpenRecord: (String) -> Unit
) {
    val indicators = dashboard.indicators
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Visão gerencial", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = appTextColor())
                Text("Gargalos, carga, avaliações e sincronização", style = MaterialTheme.typography.bodySmall, color = appMutedColor())
            }
            if (loading) Text("Atualizando...", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(10.dp))
        Surface(
            Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = appSurfaceColor(),
            border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Filtros", fontWeight = FontWeight.Bold, color = appTextColor())
                Spacer(Modifier.height(8.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    val people = listOf("" to "Todos") + dashboard.implantadores.map { it.value to it.label }
                    ManagerFilterButton("Implantador: ${people.firstOrNull { it.first == filters.implantador }?.second ?: "Todos"}") {
                        val index = people.indexOfFirst { it.first == filters.implantador }.coerceAtLeast(0)
                        onFiltersChange(filters.copy(implantador = people[(index + 1) % people.size].first))
                    }
                    val periods = listOf("30" to "30 dias", "90" to "90 dias", "365" to "12 meses", "all" to "Tudo")
                    ManagerFilterButton("Período: ${periods.first { it.first == filters.period }.second}") {
                        val index = periods.indexOfFirst { it.first == filters.period }
                        onFiltersChange(filters.copy(period = periods[(index + 1) % periods.size].first))
                    }
                    val stages = listOf("" to "Todas") + dashboard.stages.map { it.value to it.label }
                    ManagerFilterButton("Etapa: ${stages.firstOrNull { it.first == filters.stage }?.second ?: "Todas"}") {
                        val index = stages.indexOfFirst { it.first == filters.stage }.coerceAtLeast(0)
                        onFiltersChange(filters.copy(stage = stages[(index + 1) % stages.size].first))
                    }
                    val staleOptions = listOf("3", "7", "15", "30")
                    ManagerFilterButton("Parados: ${filters.staleDays} dias") {
                        val index = staleOptions.indexOf(filters.staleDays).coerceAtLeast(0)
                        onFiltersChange(filters.copy(staleDays = staleOptions[(index + 1) % staleOptions.size]))
                    }
                    ManagerFilterButton(if (filters.overdue) "Atrasados: sim" else "Atrasados: todos", filters.overdue) {
                        onFiltersChange(filters.copy(overdue = !filters.overdue))
                    }
                    ManagerFilterButton(if (filters.blockers) "Impedimentos: sim" else "Impedimentos: todos", filters.blockers) {
                        onFiltersChange(filters.copy(blockers = !filters.blockers))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val metrics = listOf(
            "Registros" to indicators.total.toString(), "Atrasados" to indicators.overdue.toString(),
            "Parados" to indicators.stale.toString(), "Avaliar" to indicators.pendingEvaluations.toString(),
            "Impedimentos" to indicators.blockers.toString(), "Concluídos no mês" to indicators.concludedMonth.toString(),
            "Média de duração" to (indicators.averageDurationDays?.let { "$it dias" } ?: "-"),
            "Nota média" to (indicators.averageScore?.let { "$it/10" } ?: "-"),
            "Erros de sync" to indicators.syncErrors.toString()
        )
        metrics.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { metric -> ManagerKpi(metric.first, metric.second, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
        ManagerSectionCard("Total por etapa") {
            dashboard.byStage.forEach { (label, count) -> SyncDiagnosticLine(label, count.toString()) }
        }
        Spacer(Modifier.height(10.dp))
        ManagerSectionCard("Carga por implantador") {
            if (dashboard.workload.isEmpty()) Text("Nenhum implantador encontrado.", color = appMutedColor())
            dashboard.workload.forEachIndexed { index, person ->
                Column(Modifier.padding(vertical = 7.dp)) {
                    Text(person.fullName, fontWeight = FontWeight.Bold, color = appTextColor())
                    Text(
                        "Ativos ${person.active} • Atrasados ${person.overdue} • Parados ${person.stale} • Imped. ${person.blockers}",
                        style = MaterialTheme.typography.bodySmall, color = appMutedColor()
                    )
                    Text(
                        "Avaliar ${person.pendingEvaluations} • Concluídos no mês ${person.concludedMonth}",
                        style = MaterialTheme.typography.bodySmall, color = appMutedColor()
                    )
                    Text(
                        "Último sync: ${person.lastSync?.let(::formatServerSyncTime) ?: "Nunca"} • ${person.pendingSync} pend. • ${person.syncErrors} erro(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (person.syncErrors > 0) MaterialTheme.colorScheme.error else appMutedColor()
                    )
                }
                if (index < dashboard.workload.lastIndex) HorizontalDivider(color = appBorderColor())
            }
        }
        Spacer(Modifier.height(10.dp))
        ManagerRecordList("Atrasados", dashboard.overdue, onOpenRecord)
        Spacer(Modifier.height(8.dp))
        ManagerRecordList("Registros parados", dashboard.stale, onOpenRecord)
        Spacer(Modifier.height(8.dp))
        ManagerRecordList("Avaliações pendentes", dashboard.pendingEvaluations, onOpenRecord)
        Spacer(Modifier.height(8.dp))
        ManagerRecordList("Impedimentos abertos", dashboard.blockers, onOpenRecord)
        Spacer(Modifier.height(8.dp))
        ManagerSectionCard("Falhas de sincronização") {
            if (dashboard.syncErrors.isEmpty()) Text("Nenhuma falha.", color = appMutedColor())
            dashboard.syncErrors.forEachIndexed { index, error ->
                Column(Modifier.padding(vertical = 7.dp)) {
                    Text(error.fullName, fontWeight = FontWeight.Bold, color = appTextColor())
                    Text("App ${error.appVersion} • ${error.pendingCount} pendente(s)", style = MaterialTheme.typography.bodySmall, color = appMutedColor())
                    Text(error.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
                }
                if (index < dashboard.syncErrors.lastIndex) HorizontalDivider(color = appBorderColor())
            }
        }
    }
}

@Composable
private fun ManagerFilterButton(label: String, selected: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
        )
    ) { Text(label, maxLines = 1) }
}

@Composable
private fun ManagerKpi(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(15.dp), color = appSurfaceColor(), border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = appMutedColor(), maxLines = 2)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = appTextColor())
        }
    }
}

@Composable
private fun ManagerSectionCard(title: String, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = appSurfaceColor(), border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = appTextColor())
            Spacer(Modifier.height(7.dp))
            content()
        }
    }
}

@Composable
private fun ManagerRecordList(title: String, items: List<DashboardRecordSummary>, onOpenRecord: (String) -> Unit) {
    var expanded by rememberSaveable(title, items.size) { mutableStateOf(false) }
    val visibleItems = if (expanded) items else items.take(5)
    ManagerSectionCard("$title (${items.size})") {
        if (items.isEmpty()) Text("Nenhum registro.", color = appMutedColor())
        visibleItems.forEachIndexed { index, item ->
            Column(
                Modifier.fillMaxWidth().clickable { onOpenRecord(item.id) }.padding(vertical = 8.dp)
            ) {
                Text(item.client, fontWeight = FontWeight.Bold, color = appTextColor())
                Text("${item.assignedName} • ${item.stageLabel}", style = MaterialTheme.typography.bodySmall, color = appMutedColor())
                val timing = item.deadline?.let { "Prazo: ${formatServerSyncTime(it)}" }
                    ?: item.daysStale?.let { "Sem atualização há $it dia(s)" }
                timing?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = appMutedColor()) }
                item.blocker?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2) }
            }
            if (index < visibleItems.lastIndex) HorizontalDivider(color = appBorderColor())
        }
        if (items.size > 5) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Mostrar menos" else "Ver todos (${items.size})")
            }
        }
    }
}

@Composable
private fun SyncDiagnosticCard(
    diagnostic: SyncDiagnostic,
    isSyncing: Boolean,
    onSyncNow: () -> Unit
) {
    val status = when {
        diagnostic.lastError != null -> "Falha ao sincronizar"
        diagnostic.pendingCount > 0 -> "Aguardando Wi-Fi"
        else -> "Sincronizado"
    }
    val statusColor = when {
        diagnostic.lastError != null -> MaterialTheme.colorScheme.error
        diagnostic.pendingCount > 0 -> Color(0xFFD18A22)
        else -> Green
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = appSurfaceColor(),
        border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(statusColor.copy(alpha = .14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (diagnostic.lastError == null && diagnostic.pendingCount == 0) Icons.Rounded.CloudDone else Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = statusColor
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sincronização", fontWeight = FontWeight.Bold, color = appTextColor())
                    Text(status, style = MaterialTheme.typography.bodySmall, color = statusColor, fontWeight = FontWeight.SemiBold)
                }
                Surface(color = statusColor.copy(alpha = .14f), shape = RoundedCornerShape(50)) {
                    Text(
                        "${diagnostic.pendingCount} pendente${if (diagnostic.pendingCount == 1) "" else "s"}",
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SyncDiagnosticLine("Servidor configurado", if (diagnostic.serverConfigured) diagnostic.serverUrl else "Não")
            SyncDiagnosticLine("Usuário autenticado", diagnostic.username ?: "Não")
            SyncDiagnosticLine("Última tentativa", formatSyncTime(diagnostic.lastAttempt))
            diagnostic.lastError?.let {
                Spacer(Modifier.height(6.dp))
                Text("Último erro", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                Text(it, style = MaterialTheme.typography.bodySmall, color = appMutedColor(), maxLines = 3)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSyncNow,
                enabled = !isSyncing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp)
            ) {
                Icon(Icons.Rounded.CloudDone, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isSyncing) "Sincronizando..." else "Sincronizar agora", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SyncDiagnosticLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = appMutedColor())
        Text(value, style = MaterialTheme.typography.bodySmall, color = appTextColor(), fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun DeviceSyncPanel(devices: List<DeviceSyncStatus>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = appSurfaceColor(),
        border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Sincronização dos implantadores", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = appTextColor())
            Text("Último contato dos aplicativos Android.", style = MaterialTheme.typography.bodySmall, color = appMutedColor())
            Spacer(Modifier.height(6.dp))
            if (devices.isEmpty()) {
                Text("Nenhum dispositivo enviou diagnóstico ainda.", style = MaterialTheme.typography.bodySmall, color = appMutedColor())
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columnCount = if (maxWidth >= 300.dp) 2 else 1
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        devices.chunked(columnCount).forEach { rowDevices ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowDevices.forEach { device ->
                                    DeviceSyncCard(device, Modifier.weight(1f))
                                }
                                repeat(columnCount - rowDevices.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceSyncCard(device: DeviceSyncStatus, modifier: Modifier = Modifier) {
    val failed = !device.lastError.isNullOrBlank()
    val statusColor = if (failed) MaterialTheme.colorScheme.error else if (device.pendingCount > 0) Color(0xFFD18A22) else Green
    val status = if (failed) "Falha" else if (device.pendingCount > 0) "Aguardando" else "Sincronizado"
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = appSurfaceColor(),
        border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 4.dp).size(9.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(7.dp))
                Text(
                    device.fullName.ifBlank { device.username },
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold,
                    color = appTextColor(),
                    maxLines = 2,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(5.dp))
            Text("Última sincronização", style = MaterialTheme.typography.labelSmall, color = appMutedColor())
            Text(formatServerSyncTime(device.lastSeen), style = MaterialTheme.typography.labelMedium, color = appTextColor(), fontWeight = FontWeight.SemiBold)
            device.lastError?.let {
                Spacer(Modifier.height(3.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = appBorderColor())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(status, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${device.pendingCount} pend.", style = MaterialTheme.typography.labelSmall, color = appMutedColor(), maxLines = 1)
            }
        }
    }
}

private fun formatSyncTime(value: Long?): String = value?.let {
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(it))
} ?: "Nunca"

private fun formatServerSyncTime(value: String): String = runCatching {
    formatSyncTime(Instant.parse(value).toEpochMilli())
}.getOrDefault(value.take(16).replace('T', ' '))

@Composable
private fun DashboardHeader(
    user: AuthUser,
    onLogout: () -> Unit,
    onChangePassword: (String, String) -> Result<Unit>,
    themeMode: ReiThemeMode,
    onThemeModeChange: (ReiThemeMode) -> Unit
) {
    Surface(
        modifier = Modifier.statusBarsPadding(),
        color = appSurfaceColor(),
        shadowElevation = 1.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(appLogoResource()),
                contentDescription = "DuBrasil Soluções",
                modifier = Modifier.size(52.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("Dashboard", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                Text(user.fullName, style = MaterialTheme.typography.labelSmall, color = appMutedColor(), maxLines = 1)
            }
            Spacer(Modifier.width(9.dp))
            AccountSettingsButton(onLogout, onChangePassword, themeMode, onThemeModeChange)
        }
    }
}

@Composable
private fun AccountSettingsButton(
    onLogout: () -> Unit,
    onChangePassword: (String, String) -> Result<Unit>,
    themeMode: ReiThemeMode,
    onThemeModeChange: (ReiThemeMode) -> Unit
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    IconButton(
        onClick = { showSettings = true },
        modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Icon(Icons.Outlined.Settings, contentDescription = "Configurações", tint = MaterialTheme.colorScheme.primary)
    }
    if (showSettings) {
        AccountSettingsDialog(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onChangePassword = onChangePassword,
            onLogout = {
                showSettings = false
                onLogout()
            },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun AccountSettingsDialog(
    themeMode: ReiThemeMode,
    onThemeModeChange: (ReiThemeMode) -> Unit,
    onChangePassword: (String, String) -> Result<Unit>,
    onLogout: () -> Unit,
    onDismiss: () -> Unit
) {
    var screen by rememberSaveable { mutableStateOf("main") }
    when (screen) {
        "theme" -> ThemeSettingsDialog(
            selected = themeMode,
            onDismiss = { screen = "main" },
            onSelect = {
                onThemeModeChange(it)
                screen = "main"
            }
        )
        "password" -> ChangePasswordDialog(
            onDismiss = { screen = "main" },
            onChangePassword = onChangePassword
        )
        else -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Configurações da conta", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { screen = "theme" },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Icon(Icons.Outlined.Settings, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Tema: ${themeMode.label}")
                    }
                    OutlinedButton(
                        onClick = { screen = "password" },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Alterar minha senha")
                    }
                    HorizontalDivider(color = appBorderColor())
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Outlined.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Sair do sistema")
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun ThemeSettingsDialog(
    selected: ReiThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ReiThemeMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escolher tema", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Defina a aparência que será utilizada em todas as telas do aplicativo.", color = appMutedColor())
                Spacer(Modifier.height(12.dp))
                ReiThemeMode.entries.forEach { option ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelect(option) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected == option) MaterialTheme.colorScheme.primaryContainer else appSurfaceColor(),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected == option) MaterialTheme.colorScheme.primary else appBorderColor()
                        )
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected == option, onClick = { onSelect(option) })
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(option.label, fontWeight = FontWeight.Bold, color = appTextColor())
                                Text(
                                    when (option) {
                                        ReiThemeMode.System -> "Acompanha a configuração do dispositivo"
                                        ReiThemeMode.Light -> "Mantém o aplicativo com fundo claro"
                                        ReiThemeMode.Dark -> "Reduz o brilho com cores escuras"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = appMutedColor()
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun ChangePasswordDialog(
    onDismiss: () -> Unit,
    onChangePassword: (String, String) -> Result<Unit>
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var currentPassword by rememberSaveable { mutableStateOf("") }
    var newPassword by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Alterar minha senha", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Informe a senha atual e escolha uma nova senha com pelo menos 8 caracteres.", color = appMutedColor())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it; error = "" },
                    label = { Text("Senha atual") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = "" },
                    label = { Text("Nova senha") },
                    supportingText = { Text("Mínimo de 8 caracteres") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it; error = "" },
                    label = { Text("Confirmar nova senha") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    isError = error.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !loading,
                onClick = {
                    when {
                        currentPassword.isBlank() -> error = "Informe a senha atual."
                        newPassword.length < 8 -> error = "A nova senha deve ter ao menos 8 caracteres."
                        newPassword != confirmation -> error = "A confirmação da nova senha não confere."
                        else -> {
                            loading = true
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { onChangePassword(currentPassword, newPassword) }
                                loading = false
                                result.onSuccess {
                                    Toast.makeText(context, "Senha alterada com sucesso", Toast.LENGTH_LONG).show()
                                    onDismiss()
                                }.onFailure { error = it.message ?: "Não foi possível alterar a senha." }
                            }
                        }
                    }
                }
            ) { Text(if (loading) "Alterando..." else "Alterar senha") }
        },
        dismissButton = {
            TextButton(enabled = !loading, onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun DashboardAreaButton(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    value: String,
    icon: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.heightIn(min = 96.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = appSurfaceColor(),
        border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                when (icon) {
                    "briefcase" -> Icon(Icons.Outlined.BusinessCenter, null, tint = MaterialTheme.colorScheme.primary)
                    else -> Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = appTextColor())
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = appMutedColor())
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Navy), contentAlignment = Alignment.Center) {
                Text(value, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
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
        modifier = modifier.height(92.dp),
        color = appSurfaceColor(),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
    ) {
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = MaterialTheme.typography.bodySmall, color = appMutedColor())
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = appTextColor())
            }
        }
    }
}

@Composable
private fun WorkflowSummaryCard(
    modifier: Modifier,
    group: DashboardGroup,
    active: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (active) MaterialTheme.colorScheme.primary else appBorderColor()
    Surface(
        modifier = modifier.heightIn(min = 148.dp).clickable(onClick = onClick),
        color = appSurfaceColor(),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(if (active) 2.dp else 1.dp, borderColor)
    ) {
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                when (group.icon) {
                    "timer" -> Icon(Icons.Outlined.Timer, null, tint = if (active) Color.White else MaterialTheme.colorScheme.primary)
                    "calendar" -> Icon(Icons.Outlined.CalendarMonth, null, tint = if (active) Color.White else MaterialTheme.colorScheme.primary)
                    "briefcase" -> Icon(Icons.Outlined.BusinessCenter, null, tint = if (active) Color.White else MaterialTheme.colorScheme.primary)
                    else -> Icon(Icons.Outlined.Description, null, tint = if (active) Color.White else MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(group.title, style = MaterialTheme.typography.bodySmall, color = appMutedColor(), maxLines = 2)
                Text(group.value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = appTextColor())
                Text(group.subtitle, style = MaterialTheme.typography.labelSmall, color = appMutedColor(), maxLines = 3)
            }
        }
    }
}

@Composable
private fun MonthlyDeliveriesChart(history: List<ImplementationSummary>, title: String = "Entregas por mês") {
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
        color = appSurfaceColor(),
        border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Timer, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Últimos seis meses", style = MaterialTheme.typography.bodySmall, color = appMutedColor())
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                months.forEachIndexed { index, month ->
                    val count = counts[index]
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Box(Modifier.height(92.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier.width(24.dp)
                                    .height(if (count == 0) 5.dp else (18 + (74f * count / max)).dp)
                                    .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                                    .background(if (count == 0) MaterialTheme.colorScheme.surfaceVariant else Navy)
                            )
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(month.format(formatter).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, color = appMutedColor())
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDistributionChart(history: List<ImplementationSummary>, title: String = "Situação das entregas") {
    val concluded = history.count { it.deliveryStatus.startsWith("Concluído") }
    val notConcluded = history.count { it.deliveryStatus == "Não concluído" }
    val unspecified = history.size - concluded - notConcluded
    val total = history.size
    val emptyTrackColor = appBorderColor()
    val segments = listOf(
        Triple("Concluídas", concluded, Green),
        Triple("Não concluídas", notConcluded, Color(0xFFE39A32)),
        Triple("Sem definição", unspecified, Color(0xFFB8BECC))
    )
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = appSurfaceColor(),
        border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.secondary.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(19.dp), tint = Green)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Distribuição dos relatórios finalizados", style = MaterialTheme.typography.bodySmall, color = appMutedColor())
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                        if (total == 0) {
                            drawArc(emptyTrackColor, -90f, 360f, false, style = Stroke(width = 22f))
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
                        Text("total", style = MaterialTheme.typography.labelMedium, color = appMutedColor())
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    segments.forEach { (label, count, color) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                            Spacer(Modifier.width(8.dp))
                            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = appMutedColor())
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
        ReportSchema.supervision.any { group ->
            group.items.any { ReportSchema.isChecked(data, "supervisao", group.title, it) }
        }

private fun ImplementationSummary.isReadyForSupervisorEvaluation(): Boolean =
    deliveryStatus.trim().startsWith("Conclu", ignoreCase = true)

private fun ImplementationSummary.stage(): String =
    report.field("_stage").ifBlank { "rei" }

private fun ImplementationSummary.hasCompletedSurvey(): Boolean =
    report.field("_surveyCompletedAt").isNotBlank()

private fun ImplementationSummary.surveyCompletedAt(): Long =
    report.field("_surveyCompletedAt").toLongOrNull() ?: completedAt

private fun ImplementationSummary.reiDeliveredAt(): Long =
    report.field("termino").toReportDate()
        ?.atStartOfDay(ZoneId.systemDefault())
        ?.toInstant()
        ?.toEpochMilli()
        ?: completedAt

private fun dashboardDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(timestamp))

private fun ImplementationSummary.isSurveyStage(): Boolean =
    stage() in setOf("levantamento_pendente", "rei_pendente")

private fun ImplementationSummary.isAssignedTo(username: String): Boolean {
    val assigned = report.field("_assignedImplantadorUsername").trim()
    return assigned.isBlank() || assigned.equals(username.trim(), ignoreCase = true)
}

private fun ImplementationSummary.implementationDurationDays(): Double? {
    val start = report.field("inicio").toReportDate() ?: return null
    val end = report.field("termino").toReportDate() ?: return null
    if (end.isBefore(start)) return null
    return (ChronoUnit.DAYS.between(start, end) + 1).toDouble()
}

private fun String.toReportDate(): LocalDate? {
    val value = trim()
    if (value.isBlank()) return null
    val datePart = value.take(10)
    return runCatching {
        when {
            Regex("\\d{4}-\\d{2}-\\d{2}").matches(datePart) -> LocalDate.parse(datePart)
            Regex("\\d{2}/\\d{2}/\\d{4}").matches(datePart) -> LocalDate.parse(datePart, DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            else -> null
        }
    }.getOrNull()
}

private fun ReportData.isConcludedDelivery(): Boolean =
    deliveryStatus.trim().startsWith("Conclu", ignoreCase = true)

private fun supervisionScore(data: ReportData): Double? {
    data.field("_supervisionScore").replace(",", ".").toDoubleOrNull()
        ?.coerceIn(0.0, 10.0)
        ?.let { return it }

    val total = ReportSchema.supervisionChecklistItems().size
    if (total == 0) return null
    val done = ReportSchema.supervision.sumOf { group ->
        group.items.count { ReportSchema.isChecked(data, "supervisao", group.title, it) }
    }
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
        color = appSurfaceColor(),
        border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ultimas avaliacoes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = appTextColor())
                Spacer(Modifier.weight(1f))
                Text("${evaluations.size} recentes", style = MaterialTheme.typography.labelMedium, color = appMutedColor())
            }
            Spacer(Modifier.height(5.dp))
            Text("Feedbacks da supervisao sobre suas implantacoes entregues.", style = MaterialTheme.typography.bodySmall, color = appMutedColor())
            Spacer(Modifier.height(13.dp))
            if (evaluations.isEmpty()) {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Nenhuma avaliacao recebida ainda.", style = MaterialTheme.typography.bodySmall, color = appMutedColor())
                }
            } else {
                evaluations.forEachIndexed { index, item ->
                    EvaluationRow(item) { onOpenReport(item) }
                    if (index < evaluations.lastIndex) HorizontalDivider(color = appBorderColor(), modifier = Modifier.padding(vertical = 10.dp))
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
        Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            Text(score?.let { String.format(Locale("pt", "BR"), "%.1f", it) } ?: "-", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.client, fontWeight = FontWeight.Bold, color = appTextColor(), maxLines = 1)
            Text("Avaliado em $date", style = MaterialTheme.typography.bodySmall, color = appMutedColor())
            if (item.report.rating.isNotBlank()) {
                Text(item.report.rating, style = MaterialTheme.typography.bodySmall, color = appMutedColor(), maxLines = 2)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Abrir avaliacao", tint = appMutedColor())
    }
}

@Composable
private fun EmptyHistoryCard(message: String = "Nenhuma implantação registrada") {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = appSurfaceColor(),
        border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
    ) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.BusinessCenter, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(12.dp))
            Text(message, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Gere o primeiro relatório para alimentar suas métricas.", style = MaterialTheme.typography.bodySmall, color = appMutedColor())
        }
    }
}

@Composable
private fun HistoryCard(item: ImplementationSummary, onClick: () -> Unit) {
    val date = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")).format(Date(item.completedAt))
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = appSurfaceColor(),
        border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.BusinessCenter, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.client, fontWeight = FontWeight.Bold, color = appTextColor(), maxLines = 1)
                Text(date, style = MaterialTheme.typography.bodySmall, color = appMutedColor())
                if (item.consultant.isNotBlank()) Text(item.consultant, style = MaterialTheme.typography.bodySmall, color = appMutedColor(), maxLines = 1)
                item.report.field("_assignedImplantadorName").ifBlank { item.report.field("_assignedImplantadorUsername") }.takeIf { it.isNotBlank() }?.let {
                    Text("Responsável: $it", style = MaterialTheme.typography.bodySmall, color = appMutedColor(), maxLines = 1)
                }
                Spacer(Modifier.height(5.dp))
                val syncLabel = when (item.syncStatus) {
                    "SYNCED" -> "Sincronizado"
                    "ERROR" -> "Falha ao sincronizar"
                    else -> "Aguardando Wi-Fi"
                }
                val syncColor = when (item.syncStatus) {
                    "SYNCED" -> Green
                    "ERROR" -> MaterialTheme.colorScheme.error
                    else -> Color(0xFFD18A22)
                }
                Text(syncLabel, style = MaterialTheme.typography.labelMedium, color = syncColor, fontWeight = FontWeight.Bold)
                item.lastSyncAttempt?.let {
                    Text("Última tentativa: ${formatSyncTime(it)}", style = MaterialTheme.typography.labelSmall, color = appMutedColor())
                }
                item.syncError?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
                }
            }
            Surface(color = MaterialTheme.colorScheme.secondary.copy(alpha = .16f), shape = RoundedCornerShape(50)) {
                Text("${item.checkedItems} itens", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun ReportViewerScreen(
    item: ImplementationSummary,
    surveyMode: Boolean,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onChangePassword: (String, String) -> Result<Unit>,
    themeMode: ReiThemeMode,
    onThemeModeChange: (ReiThemeMode) -> Unit,
    onEdit: (() -> Unit)?,
    onEvaluate: ((String, String, Set<String>) -> Unit)?,
    onReprint: (() -> Unit)?
) {
    val data = item.report
    var showEvaluation by remember { mutableStateOf(false) }
    var missingRequirements by remember { mutableStateOf<List<RequiredRequirement>>(emptyList()) }
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
    if (missingRequirements.isNotEmpty()) {
        RequiredRequirementsDialog(
            requirements = missingRequirements,
            onDismiss = { missingRequirements = emptyList(); onEdit?.invoke() },
            onGoToFirst = { missingRequirements = emptyList(); onEdit?.invoke() }
        )
    }
    Scaffold(
        containerColor = appPageColor(),
        topBar = {
            Surface(
                modifier = Modifier.statusBarsPadding(),
                color = appSurfaceColor(),
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
                        Text(if (surveyMode) "Levantamento concluído" else "Relatório concluído", style = MaterialTheme.typography.bodySmall, color = appMutedColor())
                    }
                    Surface(color = MaterialTheme.colorScheme.secondary.copy(alpha = .16f), shape = RoundedCornerShape(50)) {
                        Text(if (surveyMode) "Concluído" else "Entregue", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(Modifier.width(5.dp))
                    AccountSettingsButton(onLogout, onChangePassword, themeMode, onThemeModeChange)
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.navigationBarsPadding(),
                color = appSurfaceColor(),
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
                            onClick = {
                                val phase = if (surveyMode) ReportSchema.PHASE_SURVEY else ReportSchema.PHASE_REI
                                val missing = ReportSchema.validateRequiredRequirements(data, phase)
                                if (missing.isEmpty()) onReprint() else missingRequirements = missing
                            },
                            modifier = Modifier.weight(1.45f).height(54.dp),
                            shape = RoundedCornerShape(17.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White)
                        ) {
                            Icon(Icons.Outlined.PictureAsPdf, null)
                            Spacer(Modifier.width(7.dp))
                            Text(if (surveyMode) "Imprimir relatório" else "Segunda via", fontWeight = FontWeight.Bold)
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
                Text(if (surveyMode) "RELATÓRIO DE LEVANTAMENTO" else "RELATÓRIO DE IMPLANTAÇÃO", color = Color(0xFFBFC9F5), style = MaterialTheme.typography.labelMedium)
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
            if (surveyMode) {
                SurveyCompletedViewer(data)
            } else {
            ViewerSection("Identificação") {
                ViewerValue("Cliente / Projeto", data.field("cliente"))
                ViewerValue("Consultor", data.field("consultor"))
                ViewerValue("Usuários cadastrados", data.field("usuariosTga"))
                ViewerValue("Início", data.field("inicio"))
                ViewerValue("Término", data.field("termino"))
                ViewerValue("Dias contratados", data.field("diasContratados"))
                ViewerValue("Dias utilizados", data.field("diasUtilizados"), divider = false)
            }
            ViewerSelectedChecks(
                "Módulos contratados",
                ReportSchema.contractedModules
                    .filter { ReportSchema.isChecked(data, "dados", "modulos", it) }
                    .map { it.label }
            )
            ViewerChecklistScope("tecnico", ReportSchema.technical, data)
            ViewerChecklistScope("estoque", ReportSchema.stock, data)
            ViewerChecklistScope("financeiro", ReportSchema.finance, data)
            ViewerChecklistScope("fiscal", ReportSchema.fiscalReports, data)
            ViewerSection("Entrega") {
                ViewerValue("Serviços executados", data.field("servicosExecutados"))
                ViewerValue("Posicionamento", data.deliveryStatus)
                ViewerValue("Pendências", data.field("pendencias"), divider = false)
            }
            val supervisionItems = ReportSchema.supervision.flatMap { group ->
                group.items
                    .filter { ReportSchema.isChecked(data, "supervisao", group.title, it) }
                    .map { it.label }
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
                                Text(item, style = MaterialTheme.typography.bodyMedium, color = appTextColor())
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
    val supervisionKeys = remember {
        ReportSchema.supervision.flatMap { group ->
            group.items.flatMap { ReportSchema.itemKeys("supervisao", group.title, it) }
        }.toSet()
    }
    var score by remember(data.field("_supervisionScore")) {
        mutableStateOf(data.field("_supervisionScore").replace(",", ".").toFloatOrNull()?.coerceIn(0f, 10f) ?: 0f)
    }
    var rating by remember(data.rating) { mutableStateOf(data.rating) }
    var selected by remember(data.checks) { mutableStateOf(data.checks.filter { it in supervisionKeys }.toSet()) }
    var missingRequirements by remember { mutableStateOf<List<RequiredRequirement>>(emptyList()) }
    val evaluationData = data.copy(
        fields = data.fields + ("_supervisionScore" to String.format(Locale.US, "%.1f", score)),
        checks = (data.checks - supervisionKeys) + selected,
        rating = rating
    )

    if (missingRequirements.isNotEmpty()) {
        RequiredRequirementsDialog(
            requirements = missingRequirements,
            onDismiss = { missingRequirements = emptyList() },
            onGoToFirst = { missingRequirements = emptyList() }
        )
    }

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
                    color = appMutedColor()
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Nota da supervisão",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = appMutedColor()
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                String.format(Locale("pt", "BR"), "%.1f/10", score),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
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
                    Text(group.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(5.dp))
                    group.items.forEach { item ->
                        val key = ReportSchema.key("supervisao", group.title, item)
                        val aliases = ReportSchema.itemKeys("supervisao", group.title, item)
                        val checked = aliases.any { it in selected }
                        val required = ReportSchema.isRequired(evaluationData, item)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                                .background(if (required && !checked) MaterialTheme.colorScheme.errorContainer.copy(alpha = .32f) else Color.Transparent)
                                .clickable {
                                    selected = if (checked) selected - aliases.toSet() else selected + key
                                }
                                .padding(horizontal = 3.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    selected = if (isChecked) selected + key else selected - aliases.toSet()
                                },
                                colors = CheckboxDefaults.colors(checkedColor = Green, uncheckedColor = Color(0xFF8A91A2))
                            )
                            Column(Modifier.weight(1f)) {
                                RequiredLabel(item.label, required, checked)
                                if (required && item.requiredMode == "conditional") {
                                    Text(ReportSchema.conditionSummary(item.requiredWhen), style = MaterialTheme.typography.labelSmall, color = appMutedColor())
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val missing = ReportSchema.validateRequiredRequirements(evaluationData, ReportSchema.PHASE_SUPERVISION)
                if (missing.isEmpty()) onSave(String.format(Locale.US, "%.1f", score), rating, selected)
                else missingRequirements = missing
            }, colors = ButtonDefaults.buttonColors(containerColor = Green)) {
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
        colors = CardDefaults.cardColors(containerColor = appSurfaceColor()),
        border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
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
        Text(label, style = MaterialTheme.typography.bodySmall, color = appMutedColor())
        Text(value.ifBlank { "Não informado" }, style = MaterialTheme.typography.bodyMedium, color = appTextColor())
    }
    if (divider) HorizontalDivider(color = appBorderColor())
}

@Composable
private fun ViewerChecklistScope(scope: String, groups: List<ChecklistGroup>, data: ReportData) {
    val dynamicGroups = ReportSchema.dynamicFields(scope)
    val groupTitles = (groups.map { it.title } + dynamicGroups.map { it.title }).distinct()

    groupTitles.forEach { title ->
        val standardGroup = groups.firstOrNull { it.title == title }
        val dynamicGroup = dynamicGroups.firstOrNull { it.title == title }
        val groupTitle = standardGroup?.title ?: dynamicGroup?.title ?: title
        val standardItems = standardGroup?.items.orEmpty()
        val dynamicItems = dynamicGroup?.fields.orEmpty()
        val selected = standardItems
            .filter { it.type == "checkbox" && ReportSchema.isChecked(data, scope, groupTitle, it) }
            .map { it.label }
        val values = (standardItems.filter { it.type != "checkbox" } + dynamicItems)
            .distinctBy { ReportSchema.key(scope, groupTitle, it) }
            .mapNotNull { item ->
                ReportSchema.itemValue(data, scope, groupTitle, item)
                    .takeIf { it.isNotBlank() }
                    ?.let { value -> item to value }
            }

        if (selected.isNotEmpty() || values.isNotEmpty()) {
            ViewerSection(groupTitle) {
                selected.forEach { item ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = Green)
                        Spacer(Modifier.width(9.dp))
                        Text(item, style = MaterialTheme.typography.bodyMedium, color = appTextColor())
                    }
                }
                if (selected.isNotEmpty() && values.isNotEmpty()) Spacer(Modifier.height(8.dp))
                values.forEach { (item, value) ->
                    if (item.type == "photo") ViewerImage(item.label, value)
                    else ViewerFieldCard(item.label, value)
                }
            }
        }
    }
}

@Composable
private fun DashboardMetricCard(modifier: Modifier, icon: String, value: String, label: String) {
    MetricCard(
        modifier = modifier,
        icon = {
            when (icon) {
                "briefcase" -> Icon(Icons.Outlined.BusinessCenter, null, tint = MaterialTheme.colorScheme.primary)
                "file" -> Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary)
                "evaluation" -> Icon(Icons.Rounded.CheckCircle, null, tint = Green)
                else -> Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        value = value,
        label = label
    )
}

@Composable
private fun ViewerSelectedChecks(title: String, items: List<String>) {
    ViewerSection(title) {
        if (items.isEmpty()) {
            Text("Nenhum item marcado", style = MaterialTheme.typography.bodySmall, color = appMutedColor())
        } else items.forEach { item ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = Green)
                Spacer(Modifier.width(9.dp))
                Text(item, style = MaterialTheme.typography.bodyMedium, color = appTextColor())
            }
        }
    }
}

@Composable
private fun ViewerImage(label: String, uri: String) {
    Text(label, style = MaterialTheme.typography.bodySmall, color = appMutedColor())
    Spacer(Modifier.height(6.dp))
    Box(
        Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(14.dp))
            .background(appSurfaceColor()).border(1.dp, appBorderColor(), RoundedCornerShape(14.dp))
    ) { AttachmentThumbnail(uri) }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun ViewerAttachment(item: ReportAttachment) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, appBorderColor(), RoundedCornerShape(14.dp)).padding(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(54.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            if (item.mimeType.startsWith("image/")) AttachmentThumbnail(item.uri)
            else Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2)
            Text(if (item.mimeType.startsWith("image/")) "Imagem" else "Documento", style = MaterialTheme.typography.bodySmall, color = appMutedColor())
        }
    }
}

@Composable
private fun ReiTopBar(
    onHome: (() -> Unit)?,
    onNewReport: () -> Unit,
    onLogout: () -> Unit,
    onChangePassword: (String, String) -> Result<Unit>,
    themeMode: ReiThemeMode,
    onThemeModeChange: (ReiThemeMode) -> Unit
) {
    Surface(
        modifier = Modifier.statusBarsPadding(),
        color = appSurfaceColor(),
        shadowElevation = 1.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(appLogoResource()),
                contentDescription = "DuBrasil Soluções",
                modifier = Modifier.size(52.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("R.E.I.", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                Text("Relatório de entrega", style = MaterialTheme.typography.labelSmall, color = Color(0xFF747B8E))
            }
            Spacer(Modifier.width(12.dp))
            if (onHome != null) {
                IconButton(
                    onClick = onHome,
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Outlined.Home, contentDescription = "Dashboard", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(7.dp))
            }
            IconButton(
                onClick = onNewReport,
                modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Novo relatório", tint = Color(0xFF596174))
            }
            Spacer(Modifier.width(7.dp))
            AccountSettingsButton(onLogout, onChangePassword, themeMode, onThemeModeChange)
        }
    }
}

@Composable
private fun ViewerFieldCard(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = appMutedColor())
            Spacer(Modifier.height(7.dp))
            Text(value.ifBlank { "Não informado" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = appTextColor())
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
                color = if (active) Navy else appSurfaceColor(),
                border = if (active) null else androidx.compose.foundation.BorderStroke(1.dp, appBorderColor()),
                shadowElevation = if (active) 4.dp else 0.dp
            ) {
                Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (complete) Icon(Icons.Rounded.CheckCircle, null, Modifier.size(17.dp), tint = Green)
                    else Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .background(if (active) Color.White.copy(alpha = .16f) else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("${index + 1}", color = if (active) Color.White else Navy, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(step.shortTitle, color = if (active) Color.White else appTextColor(), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BottomActions(
    currentStep: Int,
    canExport: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSaveOnly: () -> Unit,
    onExport: () -> Unit
) {
    Surface(
        modifier = Modifier.navigationBarsPadding(),
        color = appSurfaceColor(),
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
                border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                Spacer(Modifier.width(6.dp))
                Text("Anterior")
            }
            if (currentStep < steps.lastIndex) {
                Button(
                    onClick = onNext,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Navy)
                ) {
                    Text("Continuar")
                    Spacer(Modifier.width(7.dp))
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(20.dp))
                }
            } else {
                OutlinedButton(
                    onClick = onSaveOnly,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Navy)
                ) {
                    Icon(Icons.Outlined.Save, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Salvar apenas")
                }
                if (canExport) {
                    Button(
                        onClick = onExport,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy)
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Gerar PDF")
                    }
                }
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
        CheckItems(ReportSchema.contractedModules, data, vm, "dados", "modulos")
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
        val statusRequirement = requirementDefinition("deliveryStatus")
        val statusRequired = statusRequirement?.let { ReportSchema.isRequired(data, it) } == true
        if (statusRequired) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 5.dp)) {
                RequiredLabel("Situação da entrega", true, data.deliveryStatus.startsWith("Conclu", ignoreCase = true))
            }
        }
        RadioOptions(listOf("Concluído", "Concluído, mas deseja novos serviços", "Não concluído"), data.deliveryStatus, vm::setDeliveryStatus)
    }
    FormCard("Assinaturas digitais") {
        SignatureField(
            title = "Analista de implantação – DuBrasil",
            key = "assinaturaAnalistaImagem",
            value = data.field("assinaturaAnalistaImagem"),
            data = data,
            onSaved = vm::setField
        )
        Spacer(Modifier.height(12.dp))
        SignatureField(
            title = "Responsável pelo cliente",
            key = "assinaturaClienteImagem",
            value = data.field("assinaturaClienteImagem"),
            data = data,
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
        colors = CardDefaults.cardColors(containerColor = appSurfaceColor()),
        border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.PhotoCamera, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Evidências e anexos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (attachments.isEmpty()) "Inclua fotos das telas do sistema" else "${attachments.size} arquivo(s) adicionado(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = appMutedColor()
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
                    border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
                ) {
                    Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Galeria")
                }
                OutlinedButton(
                    onClick = onFiles,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(15.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, appBorderColor(), RoundedCornerShape(15.dp)).padding(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(11.dp)).background(appSurfaceColor()),
            contentAlignment = Alignment.Center
        ) {
            if (item.mimeType.startsWith("image/")) {
                AttachmentThumbnail(item.uri)
            } else {
                Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, maxLines = 2, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                if (item.mimeType.startsWith("image/")) "Imagem" else "Documento",
                style = MaterialTheme.typography.bodySmall,
                color = appMutedColor()
            )
        }
        IconButton(onClick = { onRemove(item.uri) }) {
            Icon(Icons.Outlined.Close, contentDescription = "Remover anexo", tint = appMutedColor())
        }
    }
}

@Composable
private fun AttachmentThumbnail(uri: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val image by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (uri.startsWith("data:image", ignoreCase = true)) {
                    val base64 = uri.substringAfter("base64,", "")
                    if (base64.isNotBlank()) {
                        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } else null
                } else {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }
            }.getOrNull()
        }
    }
    if (image != null) Image(
        bitmap = image!!,
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop
    ) else Icon(Icons.Outlined.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ChecklistStep(scope: String, groups: List<ChecklistGroup>, data: ReportData, vm: ReportViewModel) {
    val dynamicGroups = ReportSchema.dynamicFields(scope)
    val titles = (groups.map { it.title } + dynamicGroups.map { it.title }).distinctBy { it.lowercase() }
    titles.forEach { title ->
        val group = groups.firstOrNull { it.title.equals(title, ignoreCase = true) }
        val dynamic = dynamicGroups.firstOrNull { it.title.equals(title, ignoreCase = true) }
        val checklistItems = group?.items.orEmpty()
        val fields = dynamic?.fields.orEmpty()
        val done = checklistItems.count { ReportSchema.isChecked(data, scope, title, it) }
        val subtitle = buildList {
            if (checklistItems.isNotEmpty()) add("$done de ${checklistItems.size} concluídos")
            if (fields.isNotEmpty()) add("${fields.size} campo(s) personalizado(s)")
        }.joinToString(" • ")
        SectionCard(title, subtitle) {
            if (checklistItems.isNotEmpty()) {
                CheckItems(checklistItems, data, vm, scope, title)
            }
            fields.forEach { field ->
                SurveyField(
                    SurveyFieldDef(
                        key = ReportSchema.fieldKey(scope, title, field),
                        label = field.label,
                        type = when (field.type) {
                            "choice" -> SurveyFieldType.Choice
                            "textarea" -> SurveyFieldType.TextArea
                            "date", "datetime-local" -> SurveyFieldType.DateTime
                            "photo" -> SurveyFieldType.Photo
                            else -> SurveyFieldType.Text
                        },
                        options = field.options.ifEmpty { if (field.type == "choice") yesNoOptions else emptyList() },
                        minLines = if (field.type == "textarea") 3 else 1,
                        definition = field
                    ),
                    data,
                    vm
                )
            }
        }
    }
}

@Composable
private fun SurveyCompletedViewer(data: ReportData) {
    activeSurveySections().forEach { section ->
        val informedFields = section.fields.filter { field ->
            data.field(field.key).isNotBlank() || (field.key == "empresa" && data.field("cliente").isNotBlank())
        }
        if (informedFields.isNotEmpty()) {
            ViewerSection(section.title) {
                informedFields.forEach { field ->
                    val value = data.field(field.key).ifBlank {
                        if (field.key == "empresa") data.field("cliente") else ""
                    }
                    if (field.type == SurveyFieldType.Photo) {
                        ViewerImage(field.label, value)
                    } else {
                        ViewerFieldCard(field.label, value)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = appSurfaceColor()),
        border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = appTextColor())
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
        colors = CardDefaults.cardColors(containerColor = appSurfaceColor()),
        border = androidx.compose.foundation.BorderStroke(1.dp, appBorderColor())
    ) {
        Column {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 17.dp, bottom = 13.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = appTextColor())
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = appMutedColor())
            }
            HorizontalDivider(color = appBorderColor())
            Column(Modifier.padding(vertical = 7.dp)) { content() }
        }
    }
}

@Composable
private fun CheckItems(
    items: List<SchemaItem>,
    data: ReportData,
    vm: ReportViewModel,
    scope: String,
    group: String
) {
    items.forEach { item ->
        val key = ReportSchema.key(scope, group, item)
        val aliases = ReportSchema.itemKeys(scope, group, item)
        val checked = aliases.any { it in data.checks }
        val required = ReportSchema.isRequired(data, item)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    when {
                        required && !checked -> MaterialTheme.colorScheme.errorContainer.copy(alpha = .32f)
                        checked -> MaterialTheme.colorScheme.secondary.copy(alpha = .16f)
                        else -> Color.Transparent
                    }
                )
                .clickable { vm.toggle(key, aliases) }
                .padding(horizontal = 5.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(checkedColor = Green, uncheckedColor = Color(0xFF8A91A2))
            )
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                RequiredLabel(item.label, required, checked)
                if (required && item.requiredMode == "conditional") {
                    Text(
                        ReportSchema.conditionSummary(item.requiredWhen),
                        style = MaterialTheme.typography.labelSmall,
                        color = appMutedColor()
                    )
                }
            }
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
                .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else Color.Transparent)
                .clickable { onSelect(option) }.padding(horizontal = 5.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = active, onClick = null)
            Text(option, color = appTextColor())
        }
    }
}

@Composable
private fun SurveyField(field: SurveyFieldDef, data: ReportData, vm: ReportViewModel) {
    if (field.key == "_surveyScheduledAt") {
        DateTimeField(field.label, field.key, data, vm, field.definition)
        return
    }
    when (field.type) {
        SurveyFieldType.Choice -> ChoiceField(field, data, vm)
        SurveyFieldType.TextArea -> FormField(field.label, field.key, data, vm, minLines = field.minLines, definition = field.definition)
        SurveyFieldType.DateTime -> DateTimeField(field.label, field.key, data, vm, field.definition)
        SurveyFieldType.Photo -> PhotoField(field.label, field.key, data, vm, field.definition)
        SurveyFieldType.Text -> FormField(field.label, field.key, data, vm, definition = field.definition)
    }
}

@Composable
private fun PhotoField(
    label: String,
    key: String,
    data: ReportData,
    vm: ReportViewModel,
    definition: SchemaItem? = null
) {
    val item = requirementDefinition(key, definition)
    val required = item?.let { ReportSchema.isRequired(data, it) } == true
    val fulfilled = data.field(key).isNotBlank()
    val context = androidx.compose.ui.platform.LocalContext.current
    val cameraTarget = remember(key) { createSurveyPhotoTarget(context, key) }
    val cameraUri = cameraTarget.second
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) vm.setField(key, cameraUri.toString())
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            vm.setField(key, uri.toString())
        }
    }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        if (required) RequiredLabel(label, true, fulfilled)
        else Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = appMutedColor())
        if (required && item?.requiredMode == "conditional") {
            Text(ReportSchema.conditionSummary(item.requiredWhen), style = MaterialTheme.typography.labelSmall, color = appMutedColor())
        }
        Spacer(Modifier.height(7.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { },
            shape = RoundedCornerShape(16.dp),
            color = appSurfaceColor(),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (required && !fulfilled) MaterialTheme.colorScheme.error else appBorderColor()
            )
        ) {
            Column(Modifier.padding(12.dp)) {
                if (data.field(key).isNotBlank()) {
                    AttachmentThumbnail(data.field(key))
                    Spacer(Modifier.height(10.dp))
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.PhotoCamera, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            Text("Adicione uma foto", color = appMutedColor())
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { cameraLauncher.launch(cameraUri) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.PhotoCamera, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Câmera")
                    }
                    OutlinedButton(onClick = { galleryLauncher.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Galeria")
                    }
                }
                if (data.field(key).isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.setField(key, "") }) { Text("Remover foto") }
                }
            }
        }
    }
}

@Composable
private fun ChoiceField(field: SurveyFieldDef, data: ReportData, vm: ReportViewModel) {
    val item = requirementDefinition(field.key, field.definition)
    val required = item?.let { ReportSchema.isRequired(data, it) } == true
    val fulfilled = item?.let { ReportSchema.isFulfilled(data, it) } ?: data.field(field.key).isNotBlank()
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        if (required) RequiredLabel(field.label, true, fulfilled)
        else Text(field.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = appMutedColor())
        if (required && item?.requiredMode == "conditional") {
            Text(ReportSchema.conditionSummary(item.requiredWhen), style = MaterialTheme.typography.labelSmall, color = appMutedColor())
        }
        Spacer(Modifier.height(7.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            field.options.forEach { option ->
                val selected = data.field(field.key) == option
                Surface(
                    modifier = Modifier.clickable { vm.setField(field.key, option) },
                    shape = RoundedCornerShape(50),
                    color = if (selected) MaterialTheme.colorScheme.secondary.copy(alpha = .16f) else appSurfaceColor(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Green else appBorderColor())
                ) {
                    Row(Modifier.padding(end = 12.dp).height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = { vm.setField(field.key, option) })
                        Text(option, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FormField(
    label: String,
    key: String,
    data: ReportData,
    vm: ReportViewModel,
    minLines: Int = 1,
    definition: SchemaItem? = null
) {
    val item = requirementDefinition(key, definition)
    val required = item?.let { ReportSchema.isRequired(data, it) } == true
    val fulfilled = item?.let { ReportSchema.isFulfilled(data, it) } ?: data.field(key).isNotBlank()
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        if (required) {
            RequiredLabel(label.removeSuffix(" *"), true, fulfilled)
            if (item?.requiredMode == "conditional") {
                Text(ReportSchema.conditionSummary(item.requiredWhen), style = MaterialTheme.typography.labelSmall, color = appMutedColor())
            }
            Spacer(Modifier.height(5.dp))
        }
        OutlinedTextField(
            value = data.field(key),
            onValueChange = { vm.setField(key, it) },
            label = { Text(label.removeSuffix(" *")) },
            minLines = minLines,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (required && !fulfilled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = if (required && !fulfilled) MaterialTheme.colorScheme.error else appBorderColor(),
                focusedContainerColor = appSurfaceColor(),
                unfocusedContainerColor = appSurfaceColor(),
                focusedTextColor = appTextColor(),
                unfocusedTextColor = appTextColor(),
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = appMutedColor()
            )
        )
    }
}

internal fun createSurveyPhotoTarget(
    context: Context,
    key: String,
    timestamp: Long = System.currentTimeMillis()
): Pair<File, Uri> {
    val directory = File(context.filesDir, "report_photos")
    check(directory.isDirectory || directory.mkdirs()) { "Não foi possível preparar o diretório de fotos." }
    val safeKey = key.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').ifBlank { "campo" }.take(64)
    val file = File(directory, "survey_${safeKey}_$timestamp.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return file to uri
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeField(
    label: String,
    key: String,
    data: ReportData,
    vm: ReportViewModel,
    definition: SchemaItem? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showCalendar by rememberSaveable { mutableStateOf(false) }
    val dateState = androidx.compose.material3.rememberDatePickerState()
    val item = requirementDefinition(key, definition)
    val required = item?.let { ReportSchema.isRequired(data, it) } == true
    val fulfilled = data.field(key).isNotBlank()

    Surface(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(bottom = 12.dp).clickable { showCalendar = true },
        shape = RoundedCornerShape(15.dp),
        color = appSurfaceColor(),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (required && !fulfilled) MaterialTheme.colorScheme.error else appBorderColor()
        )
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (data.field(key).isNotBlank()) {
                    Text(if (required) "$label *" else label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(data.field(key), style = MaterialTheme.typography.bodyMedium, color = appTextColor())
                } else {
                    Text(if (required) "$label *" else label, style = MaterialTheme.typography.bodyMedium, color = if (required) MaterialTheme.colorScheme.error else appMutedColor())
                }
            }
            if (required) {
                Text("Obrigatório", style = MaterialTheme.typography.labelSmall, color = if (fulfilled) Green else MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(6.dp))
            }
            Icon(Icons.Outlined.CalendarMonth, contentDescription = "Abrir calendário", tint = MaterialTheme.colorScheme.primary)
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
    data: ReportData,
    onSaved: (String, String) -> Unit
) {
    var showPad by rememberSaveable { mutableStateOf(false) }
    val item = requirementDefinition(key)
    val required = item?.let { ReportSchema.isRequired(data, it) } == true
    val fulfilled = value.isNotBlank()
    Column {
        if (required) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = appTextColor()
                )
                Spacer(Modifier.width(4.dp))
                Text("*", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(5.dp))
            RequiredBadge(fulfilled)
        } else {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = appTextColor())
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().height(132.dp).clickable { showPad = true },
            shape = RoundedCornerShape(16.dp),
            color = appSurfaceColor(),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (required && !fulfilled) MaterialTheme.colorScheme.error else appBorderColor()
            )
        ) {
            if (value.isNotBlank()) {
                Box {
                    AttachmentThumbnail(value)
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        shape = CircleShape,
                        color = appSurfaceColor().copy(alpha = .92f),
                        shadowElevation = 2.dp
                    ) {
                        IconButton(onClick = { onSaved(key, "") }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = "Remover assinatura", tint = appMutedColor())
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.Draw, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.height(7.dp))
                    Text("Toque para assinar na tela", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
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
        icon = { Icon(Icons.Outlined.Draw, null, tint = MaterialTheme.colorScheme.primary) },
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
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = .13f))
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = .32f), RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Green), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CloudDone, null, tint = Color.White)
        }
        Spacer(Modifier.width(13.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, color = appTextColor())
            Text(email, style = MaterialTheme.typography.bodySmall, color = appMutedColor())
            Text(phone, style = MaterialTheme.typography.bodySmall, color = appMutedColor())
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

private fun reportPdfFileName(clientName: String): String {
    val safeClient = clientName
        .replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('.')
        .ifBlank { "Cliente" }
    return "Relatorio de Entrega - $safeClient.pdf"
}

private fun surveyPdfFileName(clientName: String): String {
    val safeClient = clientName
        .replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('.')
        .ifBlank { "Cliente" }
    return "Levantamento de Dados - $safeClient.pdf"
}

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
