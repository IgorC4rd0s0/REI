package br.com.dubrasil.rei.data

data class ChatSkillOption(val code: String, val title: String, val description: String)

val allowedChatSkills = listOf(
    ChatSkillOption("erp-levantamento-diagnostico", "Levantamento e diagnóstico", "Entrevistas, processos, requisitos, escopo e riscos."),
    ChatSkillOption("erp-conversao-auditoria", "Conversão e auditoria", "Excel, CSV, XML, SPED, saldos, amostras e divergências."),
    ChatSkillOption("erp-parametrizacao-brasil", "Fiscal e estoque", "Regime, CFOP, CST/CSOSN, compras, vendas e estoque."),
    ChatSkillOption("erp-testes-go-live-suporte", "Testes e go-live", "UAT, treinamento, cutover, rollback e suporte assistido.")
)

data class ChatSessionRemote(
    val id: String,
    val reportId: String,
    val skillCode: String,
    val status: String
)

data class ChatAssistantResponse(
    val answer: String,
    val questions: List<String> = emptyList(),
    val facts: List<String> = emptyList(),
    val pendingItems: List<String> = emptyList(),
    val risks: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val evidenceIds: List<String> = emptyList(),
    val requiresConfirmation: Boolean = false,
    val confidence: String = "medium",
    val skillCode: String = ""
)

data class ChatRemoteMessage(
    val id: String,
    val localIdempotencyKey: String,
    val role: String,
    val content: String,
    val status: String,
    val createdAt: String,
    val errorMessage: String = ""
)

data class ChatSendResult(
    val messageId: String,
    val sessionId: String,
    val status: String,
    val response: ChatAssistantResponse?
)

class ChatApiException(val statusCode: Int, val code: String, message: String) : IllegalStateException(message)
