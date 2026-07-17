package br.com.dubrasil.rei

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import androidx.room.Room
import br.com.dubrasil.rei.data.ReiDatabase
import br.com.dubrasil.rei.data.ReportEntity
import br.com.dubrasil.rei.model.ReportData
import br.com.dubrasil.rei.model.ReportSchema
import br.com.dubrasil.rei.pdf.PdfExporter
import java.io.ByteArrayOutputStream

/** Regressão instrumentada: conclusão e impressão do levantamento não dependem da rede. */
class OfflinePersistenceInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val cases = listOf(
            "surveyDraftIsPersistedBeforeNavigation" to ::verifySurveyDraftPersistence,
            "completedSurveyRemainsInOfflineQueue" to ::verifyOfflinePersistence,
            "surveyPdfIsGeneratedWithoutNetwork" to ::verifyOfflineSurveyPdf,
            "surveyPhotoUsesConfiguredProviderRoot" to ::verifySurveyPhotoTarget
        )
        var failures = 0
        cases.forEachIndexed { index, (name, test) ->
            sendStatus(STATUS_START, statusBundle(name, index + 1, cases.size))
            runCatching(test).onSuccess {
                sendStatus(STATUS_OK, statusBundle(name, index + 1, cases.size))
            }.onFailure { error ->
                failures++
                sendStatus(
                    STATUS_FAILURE,
                    statusBundle(name, index + 1, cases.size).apply {
                        putString("stack", error.stackTraceToString())
                        putString("stream", "\n$name: ${error.message.orEmpty()}")
                    }
                )
            }
        }
        finish(
            if (failures == 0) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply {
                putString("stream", if (failures == 0) "\nOK (${cases.size} tests)" else "\nFAILURES: $failures")
            }
        )
    }

    private fun statusBundle(name: String, current: Int, total: Int) = Bundle().apply {
        putString("id", "OfflinePersistenceInstrumentation")
        putInt("numtests", total)
        putString("class", OfflinePersistenceInstrumentation::class.java.name)
        putString("test", name)
        putInt("current", current)
        putString("stream", "")
    }

    private fun verifyOfflinePersistence() {
        val database = Room.inMemoryDatabaseBuilder(targetContext, ReiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.reportDao()
            val now = System.currentTimeMillis()
            val synced = reportEntity(
                id = "already-synced",
                payload = "{\"fields\":{\"cliente\":\"Cliente sincronizado\"}}",
                updatedAt = now - 1_000,
                syncStatus = ReportEntity.SYNC_SYNCED
            )
            val draft = ReportEntity(
                dbId = "${ReportEntity.STATUS_DRAFT}:offline-survey",
                reportId = "offline-survey",
                status = ReportEntity.STATUS_DRAFT,
                client = "Cliente Offline",
                consultant = "Implantador",
                deliveryStatus = "",
                checkedItems = 0,
                completedAt = null,
                updatedAt = now,
                payloadJson = "{\"fields\":{\"_stage\":\"levantamento_pendente\"}}"
            )
            val completed = reportEntity(
                id = "offline-survey",
                payload = "{\"fields\":{\"_stage\":\"rei_pendente\",\"cliente\":\"Cliente Offline\"}}",
                updatedAt = now,
                syncStatus = ReportEntity.SYNC_PENDING
            )

            dao.upsert(synced)
            dao.upsert(draft)
            dao.upsertHistoryAndDeleteDraft(completed)

            check(dao.getDraft() == null) { "O rascunho não foi removido na transação de conclusão." }
            check(dao.getCompletedByReportId(completed.reportId)?.payloadJson == completed.payloadJson) {
                "O levantamento concluído não foi gravado no histórico local."
            }
            check(dao.getCompletedByReportId(synced.reportId)?.syncStatus == ReportEntity.SYNC_SYNCED) {
                "Um relatório não alterado perdeu a situação sincronizada."
            }
            check(dao.getPendingSync().map { it.reportId } == listOf(completed.reportId)) {
                "A fila offline deve conter somente o levantamento alterado."
            }
        } finally {
            database.close()
        }
    }

    private fun verifySurveyDraftPersistence() {
        val database = Room.inMemoryDatabaseBuilder(targetContext, ReiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.reportDao()
            val now = System.currentTimeMillis()
            val history = reportEntity(
                id = "survey-navigation",
                payload = "{\"fields\":{\"_stage\":\"levantamento_pendente\",\"anotacoes\":\"Texto preservado\"}}",
                updatedAt = now,
                syncStatus = ReportEntity.SYNC_PENDING
            )
            val draft = ReportEntity(
                dbId = "${ReportEntity.STATUS_DRAFT}:survey-navigation",
                reportId = "survey-navigation",
                status = ReportEntity.STATUS_DRAFT,
                client = "Cliente Offline",
                consultant = "Implantador",
                deliveryStatus = "",
                checkedItems = 0,
                completedAt = null,
                updatedAt = now,
                payloadJson = history.payloadJson
            )

            dao.upsertHistoryAndDraft(history, draft)

            check(dao.getDraft()?.payloadJson == history.payloadJson) {
                "O conteúdo digitado não foi confirmado antes da navegação."
            }
            check(dao.getCompletedByReportId(history.reportId)?.payloadJson == history.payloadJson) {
                "O histórico local não recebeu o levantamento salvo automaticamente."
            }
        } finally {
            database.close()
        }
    }

    private fun verifyOfflineSurveyPdf() {
        val report = ReportData(
            fields = mapOf(
                "_id" to "offline-survey",
                "_stage" to "rei_pendente",
                "cliente" to "Cliente Offline"
            )
        )
        check(ReportSchema.validateRequiredRequirements(report, ReportSchema.PHASE_SURVEY).isEmpty()) {
            "O relatório de teste não atende ao esquema local do levantamento."
        }
        val output = ByteArrayOutputStream()
        PdfExporter.write(targetContext, output, report)
        check(output.toByteArray().take(4).toByteArray().decodeToString() == "%PDF") {
            "O PDF do levantamento não foi produzido localmente."
        }
    }

    private fun verifySurveyPhotoTarget() {
        val (file, uri) = createSurveyPhotoTarget(targetContext, "fluxograma inicial/teste", 123L)
        val expectedDirectory = java.io.File(targetContext.filesDir, "report_photos").canonicalFile
        check(file.parentFile?.canonicalFile == expectedDirectory) {
            "A foto do levantamento não está no diretório autorizado pelo FileProvider."
        }
        check(uri.scheme == "content" && uri.authority == "${targetContext.packageName}.fileprovider") {
            "A URI do fluxograma não foi criada pelo FileProvider do aplicativo."
        }
    }

    private fun reportEntity(id: String, payload: String, updatedAt: Long, syncStatus: String) = ReportEntity(
        dbId = "${ReportEntity.STATUS_COMPLETED}:$id",
        reportId = id,
        status = ReportEntity.STATUS_COMPLETED,
        client = id,
        consultant = "Implantador",
        deliveryStatus = "",
        checkedItems = 0,
        completedAt = updatedAt,
        updatedAt = updatedAt,
        payloadJson = payload,
        syncStatus = syncStatus
    )

    private companion object {
        const val STATUS_START = 1
        const val STATUS_OK = 0
        const val STATUS_FAILURE = -2
    }
}
