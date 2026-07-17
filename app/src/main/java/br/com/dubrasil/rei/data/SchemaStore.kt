package br.com.dubrasil.rei.data

import android.content.Context
import br.com.dubrasil.rei.model.ReportSchema
import br.com.dubrasil.rei.model.SchemaOverrides
import org.json.JSONObject

/** Persiste e aplica os tópicos personalizados recebidos do servidor. */
class SchemaStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rei_schema", Context.MODE_PRIVATE)

    fun load(): SchemaOverrides {
        val json = prefs.getString("schema_overrides", "").orEmpty()
        return if (json.isBlank()) SchemaOverrides.Empty else runCatching {
            SchemaOverrides.fromJson(JSONObject(json))
        }.getOrDefault(SchemaOverrides.Empty)
    }

    fun applyCached(): SchemaOverrides {
        val schema = load()
        ReportSchema.configure(schema)
        return schema
    }

    fun save(rawJson: String): SchemaOverrides {
        val schema = SchemaOverrides.fromJson(JSONObject(rawJson))
        prefs.edit()
            .putString("schema_overrides", rawJson)
            .putLong("schema_synced_at", System.currentTimeMillis())
            .apply()
        ReportSchema.configure(schema)
        return schema
    }

    fun lastSyncAt(): Long = prefs.getLong("schema_synced_at", 0L)
}
