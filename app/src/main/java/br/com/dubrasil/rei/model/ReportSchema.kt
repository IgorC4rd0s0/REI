package br.com.dubrasil.rei.model

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.time.Instant
import java.util.Locale

data class RequirementCondition(
    val source: String = "",
    val key: String = "",
    val operator: String = "",
    val value: String = "",
    val match: String = "",
    val conditions: List<RequirementCondition> = emptyList()
)

data class RequiredWhen(
    val match: String = "any",
    val conditions: List<RequirementCondition> = emptyList()
)

data class SchemaItem(
    val key: String,
    val label: String,
    val type: String = "checkbox",
    val options: List<String> = emptyList(),
    val requiredMode: String = "never",
    val requiredWhen: RequiredWhen? = null,
    val legacyKeys: List<String> = emptyList(),
    val section: String = "",
    val valueSource: String = ""
)

data class ChecklistGroup(val title: String, val items: List<SchemaItem>)
data class DynamicReiGroup(val title: String, val fields: List<SchemaItem>)
data class SurveyFieldSchema(
    val key: String,
    val label: String,
    val type: String = "text",
    val options: List<String> = emptyList(),
    val minLines: Int = 1,
    val requiredMode: String = "never",
    val requiredWhen: RequiredWhen? = null,
    val legacyKeys: List<String> = emptyList()
) {
    fun asSchemaItem() = SchemaItem(
        key = key,
        label = label,
        type = type,
        options = options,
        requiredMode = requiredMode,
        requiredWhen = requiredWhen,
        legacyKeys = legacyKeys
    )
}
data class SurveySectionSchema(val title: String, val fields: List<SurveyFieldSchema>)

data class SchemaOverrides(
    val schemaVersion: String = "",
    val contractedModules: List<SchemaItem> = emptyList(),
    val technical: List<ChecklistGroup> = emptyList(),
    val stock: List<ChecklistGroup> = emptyList(),
    val finance: List<ChecklistGroup> = emptyList(),
    val fiscalReports: List<ChecklistGroup> = emptyList(),
    val supervision: List<ChecklistGroup> = emptyList(),
    val reiFields: Map<String, List<DynamicReiGroup>> = emptyMap(),
    val surveySections: List<SurveySectionSchema> = emptyList(),
    val fixedRequirements: Map<String, List<SchemaItem>> = emptyMap()
) {
    companion object {
        val Empty = SchemaOverrides()

        fun fromJson(root: JSONObject): SchemaOverrides {
            val rei = root.optJSONObject("rei") ?: JSONObject()
            return SchemaOverrides(
                schemaVersion = root.optString("schemaVersion"),
                contractedModules = rei.optItems("modules", "dados", "modulos", "checkbox"),
                technical = rei.optGroups("technical", "tecnico"),
                stock = rei.optGroups("stock", "estoque"),
                finance = rei.optGroups("finance", "financeiro"),
                fiscalReports = rei.optGroups("fiscal", "fiscal"),
                supervision = rei.optGroups("supervision", "supervisao"),
                reiFields = mapOf(
                    "tecnico" to rei.optDynamicGroups("technical", "tecnico"),
                    "estoque" to rei.optDynamicGroups("stock", "estoque"),
                    "financeiro" to rei.optDynamicGroups("finance", "financeiro"),
                    "fiscal" to rei.optDynamicGroups("fiscal", "fiscal"),
                    "supervisao" to rei.optDynamicGroups("supervision", "supervisao")
                ),
                surveySections = root.optSurveySections("levantamento"),
                fixedRequirements = root.optFixedRequirements()
            )
        }

        private fun JSONObject.optItems(
            name: String,
            scope: String,
            group: String,
            forcedType: String = ""
        ): List<SchemaItem> {
            val array = optJSONArray(name) ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                array.opt(index).toSchemaItem(scope, group, forcedType)
            }
        }

        private fun JSONObject.optGroups(name: String, scope: String): List<ChecklistGroup> {
            val array = optJSONArray(name) ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val group = array.optJSONObject(index) ?: return@mapNotNull null
                val title = group.optString("title").trim()
                if (title.isBlank()) return@mapNotNull null
                ChecklistGroup(
                    title,
                    group.optItems("items", scope, title).filter { it.type.equals("checkbox", true) }
                )
            }
        }

        private fun JSONObject.optDynamicGroups(name: String, scope: String): List<DynamicReiGroup> {
            val array = optJSONArray(name) ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val group = array.optJSONObject(index) ?: return@mapNotNull null
                val title = group.optString("title").trim()
                if (title.isBlank()) return@mapNotNull null
                val fields = group.optItems("items", scope, title)
                    .filterNot { it.type.equals("checkbox", true) }
                DynamicReiGroup(title, fields)
            }
        }

        private fun JSONObject.optSurveySections(name: String): List<SurveySectionSchema> {
            val array = optJSONArray(name) ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val section = array.optJSONObject(index) ?: return@mapNotNull null
                val title = section.optString("title").trim()
                if (title.isBlank()) return@mapNotNull null
                val fieldsArray = section.optJSONArray("fields") ?: JSONArray()
                val fields = (0 until fieldsArray.length()).mapNotNull { fieldIndex ->
                    val field = fieldsArray.optJSONObject(fieldIndex) ?: return@mapNotNull null
                    val item = field.toSchemaItem("levantamento", title)
                        ?: return@mapNotNull null
                    SurveyFieldSchema(
                        key = item.key,
                        label = item.label,
                        type = item.type,
                        options = item.options,
                        minLines = if (item.type == "textarea") 3 else 1,
                        requiredMode = item.requiredMode,
                        requiredWhen = item.requiredWhen,
                        legacyKeys = item.legacyKeys
                    )
                }
                SurveySectionSchema(title, fields)
            }
        }

        private fun JSONObject.optFixedRequirements(): Map<String, List<SchemaItem>> {
            val fixed = optJSONObject("validation")?.optJSONObject("fixed") ?: return emptyMap()
            return fixed.keys().asSequence().associateWith { phase ->
                val array = fixed.optJSONArray(phase) ?: JSONArray()
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.toSchemaItem("fixed", phase)
                }
            }
        }

        private fun Any?.toSchemaItem(
            scope: String,
            group: String,
            forcedType: String = ""
        ): SchemaItem? {
            val source = this as? JSONObject
            val label = (source?.optString("label") ?: this?.toString().orEmpty()).trim()
            if (label.isBlank()) return null
            val type = forcedType.ifBlank {
                source?.optString("type", "text")?.ifBlank { "text" } ?: "text"
            }
            val legacyKey = if (type.equals("checkbox", true)) {
                "$scope::$group::$label"
            } else {
                "reiField::$scope::$group::$label"
            }
            val key = source?.optString("key")?.trim().orEmpty().ifBlank { legacyKey }
            val legacy = source?.optStringList("legacyKeys").orEmpty().toMutableList()
            if (key != legacyKey && legacyKey !in legacy) legacy += legacyKey
            return SchemaItem(
                key = key,
                label = label,
                type = type,
                options = source?.optStringList("options").orEmpty(),
                requiredMode = source?.optString("requiredMode", "never") ?: "never",
                requiredWhen = source?.optJSONObject("requiredWhen")?.toRequiredWhen(),
                legacyKeys = legacy.distinct(),
                section = source?.optString("section").orEmpty(),
                valueSource = source?.optString("valueSource").orEmpty()
            )
        }

        private fun JSONObject.toRequiredWhen() = RequiredWhen(
            match = optString("match", "any"),
            conditions = (optJSONArray("conditions") ?: JSONArray()).let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.toRequirementCondition()
                }
            }
        )

        private fun JSONObject.toRequirementCondition(): RequirementCondition {
            val nested = optJSONArray("conditions")
            return RequirementCondition(
                source = optString("source"),
                key = optString("key"),
                operator = optString("operator"),
                value = optString("value"),
                match = optString("match"),
                conditions = if (nested == null) emptyList() else (0 until nested.length()).mapNotNull { index ->
                    nested.optJSONObject(index)?.toRequirementCondition()
                }
            )
        }

        private fun JSONObject.optStringList(name: String): List<String> {
            val array = optJSONArray(name) ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                array.opt(index)?.toString()?.trim()?.takeIf { it.isNotBlank() }
            }
        }
    }
}

data class RequiredRequirement(
    val key: String,
    val label: String,
    val section: String,
    val phase: String,
    val reason: String,
    val requiredBecause: String
)

object ReportSchema {
    const val PHASE_SURVEY = "survey_completion"
    const val PHASE_REI = "rei_completion"
    const val PHASE_SUPERVISION = "supervision_submission"

    @Volatile private var overrides: SchemaOverrides = SchemaOverrides.Empty

    fun configure(custom: SchemaOverrides) {
        overrides = custom
    }

    val schemaVersion: String get() = overrides.schemaVersion

    private fun checkboxItems(scope: String, group: String, labels: List<String>) =
        labels.map { label -> SchemaItem("$scope::$group::$label", label) }

    private val baseContractedModules = checkboxItems("dados", "modulos", listOf(
        "Manifesto", "Financeiro", "Nota Fiscal Eletrônica", "Emissão de NFC-e",
        "Compras", "Custos", "Nota Fiscal Eletrônica de Serviço", "Ordem de Serviço",
        "Estoque", "Customização", "Sintegra", "Boleto", "Faturamento",
        "SPED Fiscal / PIS-COFINS", "PDV – Ponto de Venda"
    ))
    val contractedModules: List<SchemaItem> get() = mergeItems(baseContractedModules, overrides.contractedModules)

    private fun group(scope: String, title: String, vararg labels: String) =
        ChecklistGroup(title, checkboxItems(scope, title, labels.toList()))

    private val baseTechnical = listOf(
        group("tecnico", "Instalação e ambiente",
            "Instalação do TGA", "Conferir cadastro da empresa", "Conferir cadastro da filial",
            "Cadastro de login e senha do cliente", "Instalação do IBExpert",
            "Configuração de segurança e energia", "Liberação de porta no firewall",
            "Compartilhamento da pasta TGA", "IP fixo no servidor", "Workflow de trava de usuário",
            "Print da tela de registro da empresa", "Pasta Instaladores no servidor",
            "Certificado digital na pasta Instaladores", "Logotipo na pasta Instaladores",
            "Relatórios desenvolvidos na pasta Instaladores", "Print da tela de registro da filial"),
        group("tecnico", "Emissão de NF-e",
            "Instalação do certificado no TGA", "Certificado A1 inserido no banco de dados",
            "Conferir série da NF-e", "Conferir local do PDF/XML da NF-e na filial",
            "Parametrizar os CFOPs", "Configurar regime tributário",
            "Confirmar alíquotas com o contador (PIS, COFINS etc.)"),
        group("tecnico", "Configuração e cadastros",
            "Configurar backup", "Verificar Workflow de trava de usuários", "Cadastrar usuários",
            "Cadastrar funcionários", "Criar fluxograma", "Conferir versão",
            "Configurar e explicar Liberação Online")
    )
    val technical: List<ChecklistGroup> get() = mergeGroups(baseTechnical, overrides.technical)

    private val baseStock = listOf(
        group("estoque", "Cadastros", "Cadastro de cliente/fornecedor", "Cadastro de grupo", "Tributação do produto", "Tipo do item", "Produto ou serviço", "Ajuste de saldo"),
        group("estoque", "Entradas", "Manifesto", "Pedido de compra", "NF-e com financeiro", "NF-e sem financeiro", "Extrato de compra", "Energia", "Telecomunicação", "Conhecimento de frete", "Importação de CT-e", "Devolução de compra com NF-e", "Devolução de compra sem NF-e"),
        group("estoque", "Saídas", "Cupom fiscal", "NF-e de venda", "NFS-e", "Extrato de venda", "Devolução de venda com NF-e", "Devolução de venda sem NF-e", "NF-e referente a cupom fiscal", "NF-e de outras saídas"),
        group("estoque", "Outros",
            "Configuração de etiquetas", "Configuração de e-mail", "Movimentos de ajuste de saldo (F10)", "Treinamento de perfil de usuário",
            "Configurar e testar o fluxo de Ordem de Serviço", "Validar separação de produtos e serviços no faturamento",
            "Configurar e testar balança", "Configurar e testar controle de lote", "Configurar composição de produtos",
            "Configurar produtos similares", "Configurar controle de série do produto", "Configurar e testar comissão",
            "Configurar formação de preço e custos", "Configurar e testar PDV online",
            "Configurar PDV offline e sincronização", "Validar e testar customizações contratadas")
    )
    val stock: List<ChecklistGroup> get() = mergeGroups(baseStock, overrides.stock)

    private val baseFinance = listOf(
        group("financeiro", "Cadastros", "Cadastrar conta/caixa", "Cadastrar forma de pagamento", "Cadastro de contas a pagar/receber (F7)"),
        group("financeiro", "Manutenção de lançamentos (F8)", "Baixa normal", "Baixa agrupada", "Baixa parcial", "Baixa com duas ou mais formas de pagamento", "Gerar fatura", "Estorno", "Imprimir boleto", "Devolução", "Adiantamento"),
        group("financeiro", "Extratos e documentos", "Cadastro de depósito/saque/transferência (F9)", "Compensação", "Devolução de cheque", "Transferência de documentos (F3)"),
        group("financeiro", "Boletos e cartão", "Remessa", "Retorno", "Cartão", "Conciliação de cartão", "Homologação de boleto", "Homologação de API")
    )
    val finance: List<ChecklistGroup> get() = mergeGroups(baseFinance, overrides.finance)

    private val baseFiscalReports = listOf(
        group("fiscal", "Módulo Fiscal", "Gerar Sintegra", "Envio de XML para a contabilidade", "Gerar SPED", "Relatório de entradas e saídas"),
        group("fiscal", "Relatórios financeiros", "Fechamento de caixa", "Contas a pagar/receber", "Recibo"),
        group("fiscal", "Relatórios de estoque", "Relatório de venda", "Relatório de compra", "Estoque e movimentação")
    )
    val fiscalReports: List<ChecklistGroup> get() = mergeGroups(baseFiscalReports, overrides.fiscalReports)

    private val baseSupervision = listOf(
        group("supervisao", "Planejamento e preparação", "Cronograma e etapas definidos antes do início", "Requisitos e dados validados com o cliente", "Levantamento executado e anotado", "Ambiente de testes configurado corretamente"),
        group("supervisao", "Execução técnica", "Configurações realizadas corretamente", "Migração de dados concluída sem erros", "Treinamento do cliente realizado"),
        group("supervisao", "Comunicação e relacionamento", "Contato frequente e claro com o cliente", "Atendimento formal e profissional", "Trabalho em equipe e cooperação", "Fluxograma da base entregue ao Helpdesk", "R.E.I. preenchido diariamente", "Registro de ponto efetuado diariamente"),
        group("supervisao", "Prazos e qualidade", "Implantação entregue no prazo", "Sem pendências críticas após a finalização", "Cliente satisfeito com o resultado geral"),
        group("supervisao", "Aprimoramento e postura", "Proatividade e iniciativa", "Pontualidade e compromisso", "Busca constante por aprendizado técnico")
    )
    val supervision: List<ChecklistGroup> get() = mergeGroups(baseSupervision, overrides.supervision)
    val surveySections: List<SurveySectionSchema> get() = overrides.surveySections
    val fixedRequirements: Map<String, List<SchemaItem>> get() =
        overrides.fixedRequirements.ifEmpty { baseFixedRequirements }
    fun dynamicFields(scope: String): List<DynamicReiGroup> = overrides.reiFields[scope].orEmpty()

    private val baseFixedRequirements = mapOf(
        PHASE_REI to listOf(
            SchemaItem("cliente", "Cliente / Projeto", "text", requiredMode = "always", section = "Identificação"),
            SchemaItem("consultor", "Consultor", "text", requiredMode = "always", section = "Identificação"),
            SchemaItem("inicio", "Início", "date", requiredMode = "always", section = "Identificação"),
            SchemaItem("termino", "Término", "date", requiredMode = "always", section = "Identificação"),
            SchemaItem("servicosExecutados", "Serviços executados", "textarea", requiredMode = "always", section = "Entrega"),
            SchemaItem("deliveryStatus", "Posicionamento da entrega", "choice", listOf("Concluído", "Concluído, mas deseja novos serviços"), "always", section = "Entrega"),
            SchemaItem("assinaturaAnalistaImagem", "Assinatura do técnico", "signature", requiredMode = "always", section = "Entrega"),
            SchemaItem("assinaturaClienteImagem", "Assinatura do cliente", "signature", requiredMode = "always", section = "Entrega"),
            SchemaItem(
                "pendencias", "Pendências", "textarea", requiredMode = "conditional",
                requiredWhen = RequiredWhen("all", listOf(RequirementCondition("report_field", "deliveryStatus", "equals", "Concluído, mas deseja novos serviços"))),
                section = "Entrega"
            )
        )
    )

    fun key(scope: String, group: String, item: SchemaItem) = item.key.ifBlank { legacyKey(scope, group, item) }
    fun key(scope: String, group: String, label: String) = "$scope::$group::$label"
    fun legacyKey(scope: String, group: String, item: SchemaItem): String =
        if (item.type == "checkbox") "$scope::$group::${item.label}" else "reiField::$scope::$group::${item.label}"
    fun itemKeys(scope: String, group: String, item: SchemaItem): List<String> =
        (listOf(key(scope, group, item)) + item.legacyKeys + legacyKey(scope, group, item))
            .distinct().filter(String::isNotBlank)
    fun fieldKey(scope: String, group: String, item: SchemaItem) = key(scope, group, item)
    fun fieldKey(scope: String, group: String, label: String) = "reiField::$scope::$group::$label"
    fun contractedKey(item: SchemaItem) = key("dados", "modulos", item)
    fun contractedKey(label: String) = key("dados", "modulos", label)

    fun isChecked(data: ReportData, scope: String, group: String, item: SchemaItem): Boolean =
        itemKeys(scope, group, item).any { it in data.checks }
    fun itemValue(data: ReportData, scope: String, group: String, item: SchemaItem): String =
        itemKeys(scope, group, item).firstNotNullOfOrNull { key -> data.fields[key]?.takeIf(String::isNotBlank) }.orEmpty()

    fun allChecklistItems(): List<String> =
        contractedModules.map(::contractedKey) + scopedKeys("tecnico", technical) +
            scopedKeys("estoque", stock) + scopedKeys("financeiro", finance) + scopedKeys("fiscal", fiscalReports)
    fun supervisionChecklistItems(): List<String> = scopedKeys("supervisao", supervision)

    private fun scopedKeys(scope: String, groups: List<ChecklistGroup>) =
        groups.flatMap { group -> group.items.map { key(scope, group.title, it) } }

    private fun mergeItems(base: List<SchemaItem>, incoming: List<SchemaItem>): List<SchemaItem> {
        val result = base.toMutableList()
        incoming.forEach { custom ->
            val index = result.indexOfFirst { it.key == custom.key || (it.key.isBlank() && it.label.equals(custom.label, true)) }
            if (index >= 0) result[index] = custom else result += custom
        }
        return result
    }

    private fun mergeGroups(base: List<ChecklistGroup>, incoming: List<ChecklistGroup>): List<ChecklistGroup> {
        val result = base.toMutableList()
        incoming.forEach { custom ->
            val index = result.indexOfFirst { it.title.equals(custom.title, true) }
            if (index >= 0) result[index] = result[index].copy(items = mergeItems(result[index].items, custom.items))
            else result += custom
        }
        return result
    }

    private fun comparison(value: String) = Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
        .replace(Regex("\\s+"), " ").lowercase(Locale("pt", "BR"))

    private fun reportValue(data: ReportData, key: String): String = when (key) {
        "deliveryStatus" -> data.deliveryStatus
        "rating" -> data.rating
        else -> data.field(key)
    }

    private fun allDefinitions(): List<SchemaItem> = buildList {
        addAll(contractedModules)
        listOf(technical, stock, finance, fiscalReports, supervision).flatten().forEach { addAll(it.items) }
        dynamicFields("tecnico").forEach { addAll(it.fields) }
        dynamicFields("estoque").forEach { addAll(it.fields) }
        dynamicFields("financeiro").forEach { addAll(it.fields) }
        dynamicFields("fiscal").forEach { addAll(it.fields) }
        dynamicFields("supervisao").forEach { addAll(it.fields) }
        surveySections.forEach { section -> addAll(section.fields.map { it.asSchemaItem() }) }
        fixedRequirements.values.forEach { addAll(it) }
    }

    private fun definitionByKey(key: String) = allDefinitions().firstOrNull { key in (listOf(it.key) + it.legacyKeys) }
    private fun checked(data: ReportData, key: String): Boolean {
        val definition = definitionByKey(key)
        val keys = if (definition == null) listOf(key) else listOf(definition.key) + definition.legacyKeys
        return keys.any { it in data.checks }
    }

    private fun evaluateCondition(data: ReportData, condition: RequirementCondition): Boolean {
        if (condition.match.isNotBlank() && condition.source.isBlank()) {
            val values = condition.conditions.map { evaluateCondition(data, it) }
            return values.isNotEmpty() && if (condition.match == "all") values.all { it } else values.any { it }
        }
        if (condition.source in setOf("module", "checklist")) {
            val value = checked(data, condition.key)
            return if (condition.operator == "checked") value else !value
        }
        val value = reportValue(data, condition.key)
        return when (condition.operator) {
            "equals" -> comparison(value) == comparison(condition.value)
            "not_equals" -> comparison(value) != comparison(condition.value)
            "not_blank" -> value.isNotBlank()
            "blank" -> value.isBlank()
            "greater_than" -> value.replace(',', '.').toBigDecimalOrNull()
                ?.let { actual -> condition.value.replace(',', '.').toBigDecimalOrNull()?.let { actual > it } } == true
            else -> false
        }
    }

    private fun active(data: ReportData, item: SchemaItem): Boolean = when (item.requiredMode) {
        "always" -> true
        "conditional" -> item.requiredWhen?.conditions?.map { evaluateCondition(data, it) }
            ?.let { values -> values.isNotEmpty() && if (item.requiredWhen.match == "all") values.all { it } else values.any { it } } == true
        else -> false
    }

    fun isRequired(data: ReportData, item: SchemaItem): Boolean = active(data, item)

    private fun fulfilled(data: ReportData, item: SchemaItem): Boolean {
        if (item.type == "checkbox") return (listOf(item.key) + item.legacyKeys).any { checked(data, it) }
        val values = (listOf(item.key) + item.legacyKeys).map(data::field).toMutableList()
        if (item.key == "empresa") values += data.field("cliente")
        val value = values.firstOrNull(String::isNotBlank).orEmpty().ifBlank { reportValue(data, item.key) }
        return when (item.type) {
            "choice" -> value.isNotBlank() && (item.options.isEmpty() || item.options.any { comparison(it) == comparison(value) })
            "photo" -> value.startsWith("data:image/", true) || Regex("^(content|file|https?)://", RegexOption.IGNORE_CASE).containsMatchIn(value)
            else -> value.isNotBlank()
        }
    }

    fun isFulfilled(data: ReportData, item: SchemaItem): Boolean = fulfilled(data, item)

    fun conditionSummary(rule: RequiredWhen?): String {
        if (rule == null) return "Condição configurada"
        val operators = mapOf(
            "checked" to "está marcado", "not_checked" to "não está marcado",
            "equals" to "é igual a", "not_equals" to "é diferente de",
            "not_blank" to "está preenchido", "blank" to "está vazio", "greater_than" to "é maior que"
        )
        fun summarize(condition: RequirementCondition): String {
            if (condition.match.isNotBlank() && condition.source.isBlank()) {
                val connector = if (condition.match == "all") " e " else " ou "
                return "(" + condition.conditions.joinToString(connector, transform = ::summarize) + ")"
            }
            val label = definitionByKey(condition.key)?.label ?: condition.key
            val suffix = if (condition.operator in setOf("equals", "not_equals", "greater_than")) " ${condition.value}" else ""
            return "$label ${operators[condition.operator].orEmpty()}$suffix".trim()
        }
        return rule.conditions.joinToString(if (rule.match == "all") " e " else " ou ", transform = ::summarize)
            .ifBlank { "Condição configurada" }
    }

    private fun requirements(phase: String): List<Pair<String, SchemaItem>> = buildList {
        if (phase in setOf(PHASE_SURVEY, PHASE_REI)) {
            surveySections.forEach { section -> section.fields.forEach { add(section.title to it.asSchemaItem()) } }
        }
        if (phase == PHASE_REI) {
            listOf(
                Triple("Técnico", "tecnico", technical), Triple("Estoque", "estoque", stock),
                Triple("Financeiro", "financeiro", finance), Triple("Fiscal", "fiscal", fiscalReports)
            ).forEach { (area, scope, groups) ->
                groups.forEach { group -> group.items.forEach { add("$area · ${group.title}" to it) } }
                dynamicFields(scope).forEach { group -> group.fields.forEach { add("$area · ${group.title}" to it) } }
            }
        }
        if (phase == PHASE_SUPERVISION) {
            supervision.forEach { group -> group.items.forEach { add("Supervisão · ${group.title}" to it) } }
            dynamicFields("supervisao").forEach { group -> group.fields.forEach { add("Supervisão · ${group.title}" to it) } }
        }
        fixedRequirements[phase].orEmpty().forEach { add(it.section.ifBlank { "Relatório" } to it) }
    }

    fun validateRequiredRequirements(data: ReportData, phase: String): List<RequiredRequirement> {
        val seen = mutableSetOf<String>()
        return requirements(phase).mapNotNull { (section, item) ->
            if (!seen.add(item.key) || !active(data, item) || fulfilled(data, item)) return@mapNotNull null
            RequiredRequirement(
                key = item.key,
                label = item.label,
                section = section,
                phase = phase,
                reason = if (item.type == "checkbox") "Marque este item obrigatório." else "Preencha este campo obrigatório.",
                requiredBecause = if (item.requiredMode == "conditional") conditionSummary(item.requiredWhen) else "Obrigatório para concluir esta etapa."
            )
        }
    }

    fun validationSnapshot(data: ReportData, phase: String): String {
        val active = requirements(phase).map { it.second }.filter { active(data, it) }.distinctBy { it.key }
        return JSONObject()
            .put("schemaVersion", schemaVersion.ifBlank { "cached" })
            .put("validatedAt", Instant.now().toString())
            .put("phase", phase)
            .put("requiredKeys", JSONArray(active.map { it.key }))
            .put("fulfilledKeys", JSONArray(active.filter { fulfilled(data, it) }.map { it.key }))
            .toString()
    }
}
