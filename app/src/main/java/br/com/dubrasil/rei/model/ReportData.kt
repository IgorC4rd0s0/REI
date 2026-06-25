package br.com.dubrasil.rei.model

data class ReportData(
    val fields: Map<String, String> = emptyMap(),
    val checks: Set<String> = emptySet(),
    val deliveryStatus: String = "",
    val rating: String = "",
    val attachments: List<ReportAttachment> = emptyList()
) {
    fun field(key: String) = fields[key].orEmpty()
}

data class ReportAttachment(
    val uri: String,
    val name: String,
    val mimeType: String
)

data class ImplementationSummary(
    val id: String,
    val client: String,
    val consultant: String,
    val completedAt: Long,
    val deliveryStatus: String,
    val checkedItems: Int,
    val report: ReportData = ReportData()
)
