package br.com.dubrasil.rei.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import br.com.dubrasil.rei.R
import org.json.JSONObject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object ReiNotifier {
    private const val CHANNEL_ID = "rei_alertas"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alertas R.E.I.",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisos de levantamentos, R.E.I. pendentes e lembretes de agenda."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notify(context: Context, id: Int, title: String, message: String) {
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}

class ReiDailyReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val reports = ReiDatabase.getInstance(applicationContext).reportDao().getCompleted()
        val reiPending = reports.count { entity ->
            runCatching {
                JSONObject(entity.payloadJson).optJSONObject("fields")?.optString("_stage") == "rei_pendente"
            }.getOrDefault(false)
        }
        if (reiPending > 0) {
            ReiNotifier.notify(
                applicationContext,
                31030 + inputData.getInt("hour", 0),
                "R.E.I. pendente",
                "Você possui $reiPending implantação(ões) aguardando preenchimento do relatório R.E.I."
            )
        }
        ReiReminderScheduler.scheduleDailyReminder(applicationContext, inputData.getInt("hour", 10), inputData.getInt("minute", 30))
        return Result.success()
    }
}

class SurveyReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        ReiNotifier.notify(
            applicationContext,
            inputData.getString("reportId").orEmpty().hashCode(),
            "Levantamento em 30 minutos",
            "Levantamento agendado para ${inputData.getString("client").orEmpty().ifBlank { "cliente não informado" }}."
        )
        return Result.success()
    }
}

object ReiReminderScheduler {
    private val brDateTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun scheduleDailyReminders(context: Context) {
        ReiNotifier.ensureChannel(context)
        scheduleDailyReminder(context, 10, 30)
        scheduleDailyReminder(context, 16, 30)
    }

    fun scheduleDailyReminder(context: Context, hour: Int, minute: Int) {
        val now = LocalDateTime.now()
        var target = LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minute))
        if (!target.isAfter(now)) target = target.plusDays(1)
        val delay = Duration.between(now, target).toMillis().coerceAtLeast(1_000L)
        val request = OneTimeWorkRequestBuilder<ReiDailyReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putInt("hour", hour).putInt("minute", minute).build())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork("rei_daily_${hour}_${minute}", ExistingWorkPolicy.REPLACE, request)
    }

    fun scheduleSurveyReminder(context: Context, reportId: String, client: String, scheduledAt: String) {
        val target = parseDateTime(scheduledAt) ?: return
        val notifyAt = target.minusMinutes(30)
        val delay = Duration.between(LocalDateTime.now(), notifyAt).toMillis()
        if (delay <= 0) return
        val request = OneTimeWorkRequestBuilder<SurveyReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString("reportId", reportId).putString("client", client).build())
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork("rei_survey_reminder_$reportId", ExistingWorkPolicy.REPLACE, request)
    }

    private fun parseDateTime(value: String): LocalDateTime? {
        val text = value.trim()
        if (text.isBlank()) return null
        return runCatching { LocalDateTime.parse(text, brDateTime) }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text) }.getOrNull()
            ?: runCatching {
                java.time.Instant.ofEpochMilli(text.toLong()).atZone(ZoneId.systemDefault()).toLocalDateTime()
            }.getOrNull()
    }
}
