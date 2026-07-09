package br.com.dubrasil.rei.model

import org.json.JSONArray
import org.json.JSONObject

data class ChecklistGroup(val title: String, val items: List<String>)
data class SurveyFieldSchema(
    val key: String,
    val label: String,
    val type: String = "text",
    val options: List<String> = emptyList(),
    val minLines: Int = 1
)
data class SurveySectionSchema(val title: String, val fields: List<SurveyFieldSchema>)

data class SchemaOverrides(
    val contractedModules: List<String> = emptyList(),
    val technical: List<ChecklistGroup> = emptyList(),
    val stock: List<ChecklistGroup> = emptyList(),
    val finance: List<ChecklistGroup> = emptyList(),
    val fiscalReports: List<ChecklistGroup> = emptyList(),
    val supervision: List<ChecklistGroup> = emptyList(),
    val surveySections: List<SurveySectionSchema> = emptyList()
) {
    companion object {
        val Empty = SchemaOverrides()

        fun fromJson(root: JSONObject): SchemaOverrides {
            val rei = root.optJSONObject("rei") ?: JSONObject()
            return SchemaOverrides(
                contractedModules = rei.optStringList("modules"),
                technical = rei.optGroups("technical"),
                stock = rei.optGroups("stock"),
                finance = rei.optGroups("finance"),
                fiscalReports = rei.optGroups("fiscal"),
                supervision = rei.optGroups("supervision"),
                surveySections = root.optSurveySections("levantamento")
            )
        }

        private fun JSONObject.optStringList(key: String): List<String> {
            val array = optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                array.optString(index).trim().takeIf { it.isNotBlank() }
            }
        }

        private fun JSONObject.optGroups(key: String): List<ChecklistGroup> {
            val array = optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val group = array.optJSONObject(index) ?: return@mapNotNull null
                val title = group.optString("title").trim()
                if (title.isBlank()) return@mapNotNull null
                ChecklistGroup(title, group.optStringList("items"))
            }
        }

        private fun JSONObject.optSurveySections(key: String): List<SurveySectionSchema> {
            val array = optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val section = array.optJSONObject(index) ?: return@mapNotNull null
                val title = section.optString("title").trim()
                if (title.isBlank()) return@mapNotNull null
                val fieldsArray = section.optJSONArray("fields") ?: JSONArray()
                val fields = (0 until fieldsArray.length()).mapNotNull { fieldIndex ->
                    val field = fieldsArray.optJSONObject(fieldIndex) ?: return@mapNotNull null
                    val fieldKey = field.optString("key").trim()
                    val label = field.optString("label").trim()
                    val type = field.optString("type", "text").ifBlank { "text" }
                    if (fieldKey.isBlank() || label.isBlank()) return@mapNotNull null
                    SurveyFieldSchema(
                        key = fieldKey,
                        label = label,
                        type = type,
                        options = field.optStringList("options"),
                        minLines = if (type == "textarea") 3 else 1
                    )
                }
                SurveySectionSchema(title, fields)
            }
        }
    }
}

object ReportSchema {
    @Volatile private var overrides: SchemaOverrides = SchemaOverrides.Empty

    fun configure(custom: SchemaOverrides) {
        overrides = custom
    }

    private val baseContractedModules = listOf(
        "Manifesto", "Financeiro", "Nota Fiscal Eletrônica", "Emissão de NFC-e",
        "Compras", "Custos", "Nota Fiscal Eletrônica de Serviço", "Ordem de Serviço",
        "Estoque", "Customização", "Sintegra", "Boleto", "Faturamento",
        "SPED Fiscal / PIS-COFINS", "PDV – Ponto de Venda"
    )
    val contractedModules: List<String> get() = mergeItems(baseContractedModules, overrides.contractedModules)

    private val baseTechnical = listOf(
        ChecklistGroup("Instalação e ambiente", listOf(
            "Instalação do TGA", "Conferir cadastro da empresa", "Conferir cadastro da filial",
            "Cadastro de login e senha do cliente", "Instalação do IBExpert",
            "Configuração de segurança e energia", "Liberação de porta no firewall",
            "Compartilhamento da pasta TGA", "IP fixo no servidor", "Workflow de trava de usuário",
            "Print da tela de registro da empresa", "Pasta Instaladores no servidor",
            "Certificado digital na pasta Instaladores", "Logotipo na pasta Instaladores",
            "Relatórios desenvolvidos na pasta Instaladores", "Print da tela de registro da filial"
        )),
        ChecklistGroup("Emissão de NF-e", listOf(
            "Instalação do certificado no TGA", "Certificado A1 inserido no banco de dados",
            "Conferir série da NF-e", "Conferir local do PDF/XML da NF-e na filial",
            "Parametrizar os CFOPs", "Configurar regime tributário",
            "Confirmar alíquotas com o contador (PIS, COFINS etc.)"
        )),
        ChecklistGroup("Configuração e cadastros", listOf(
            "Configurar backup", "Verificar Workflow de trava de usuários", "Cadastrar usuários",
            "Cadastrar funcionários", "Criar fluxograma", "Conferir versão",
            "Configurar e explicar Liberação Online"
        ))
    )
    val technical: List<ChecklistGroup> get() = mergeGroups(baseTechnical, overrides.technical)

    private val baseStock = listOf(
        ChecklistGroup("Cadastros", listOf(
            "Cadastro de cliente/fornecedor", "Cadastro de grupo", "Tributação do produto",
            "Tipo do item", "Produto ou serviço", "Ajuste de saldo"
        )),
        ChecklistGroup("Entradas", listOf(
            "Manifesto", "Pedido de compra", "NF-e com financeiro", "NF-e sem financeiro",
            "Extrato de compra", "Energia", "Telecomunicação", "Conhecimento de frete",
            "Importação de CT-e", "Devolução de compra com NF-e", "Devolução de compra sem NF-e"
        )),
        ChecklistGroup("Saídas", listOf(
            "Cupom fiscal", "NF-e de venda", "NFS-e", "Extrato de venda",
            "Devolução de venda com NF-e", "Devolução de venda sem NF-e",
            "NF-e referente a cupom fiscal", "NF-e de outras saídas"
        )),
        ChecklistGroup("Outros", listOf(
            "Configuração de etiquetas", "Configuração de e-mail", "Movimentos de ajuste de saldo (F10)",
            "Treinamento de perfil de usuário"
        ))
    )
    val stock: List<ChecklistGroup> get() = mergeGroups(baseStock, overrides.stock)

    private val baseFinance = listOf(
        ChecklistGroup("Cadastros", listOf(
            "Cadastrar conta/caixa", "Cadastrar forma de pagamento", "Cadastro de contas a pagar/receber (F7)"
        )),
        ChecklistGroup("Manutenção de lançamentos (F8)", listOf(
            "Baixa normal", "Baixa agrupada", "Baixa parcial", "Baixa com duas ou mais formas de pagamento",
            "Gerar fatura", "Estorno", "Imprimir boleto", "Devolução", "Adiantamento"
        )),
        ChecklistGroup("Extratos e documentos", listOf(
            "Cadastro de depósito/saque/transferência (F9)", "Compensação", "Devolução de cheque",
            "Transferência de documentos (F3)"
        )),
        ChecklistGroup("Boletos e cartão", listOf(
            "Remessa", "Retorno", "Cartão", "Conciliação de cartão", "Homologação de boleto", "Homologação de API"
        ))
    )
    val finance: List<ChecklistGroup> get() = mergeGroups(baseFinance, overrides.finance)

    private val baseFiscalReports = listOf(
        ChecklistGroup("Módulo Fiscal", listOf(
            "Gerar Sintegra", "Envio de XML para a contabilidade", "Gerar SPED", "Relatório de entradas e saídas"
        )),
        ChecklistGroup("Relatórios financeiros", listOf(
            "Fechamento de caixa", "Contas a pagar/receber", "Recibo"
        )),
        ChecklistGroup("Relatórios de estoque", listOf(
            "Relatório de venda", "Relatório de compra", "Estoque e movimentação"
        ))
    )
    val fiscalReports: List<ChecklistGroup> get() = mergeGroups(baseFiscalReports, overrides.fiscalReports)

    private val baseSupervision = listOf(
        ChecklistGroup("Planejamento e preparação", listOf(
            "Cronograma e etapas definidos antes do início", "Requisitos e dados validados com o cliente",
            "Levantamento executado e anotado", "Ambiente de testes configurado corretamente"
        )),
        ChecklistGroup("Execução técnica", listOf(
            "Configurações realizadas corretamente", "Migração de dados concluída sem erros",
            "Treinamento do cliente realizado"
        )),
        ChecklistGroup("Comunicação e relacionamento", listOf(
            "Contato frequente e claro com o cliente", "Atendimento formal e profissional",
            "Trabalho em equipe e cooperação", "Fluxograma da base entregue ao Helpdesk",
            "R.E.I. preenchido diariamente", "Registro de ponto efetuado diariamente"
        )),
        ChecklistGroup("Prazos e qualidade", listOf(
            "Implantação entregue no prazo", "Sem pendências críticas após a finalização",
            "Cliente satisfeito com o resultado geral"
        )),
        ChecklistGroup("Aprimoramento e postura", listOf(
            "Proatividade e iniciativa", "Pontualidade e compromisso",
            "Busca constante por aprendizado técnico"
        ))
    )
    val supervision: List<ChecklistGroup> get() = mergeGroups(baseSupervision, overrides.supervision)
    val surveySections: List<SurveySectionSchema> get() = overrides.surveySections

    fun key(scope: String, group: String, item: String) = "$scope::$group::$item"
    fun contractedKey(item: String) = key("dados", "modulos", item)

    fun allChecklistItems(): List<String> =
        contractedModules.map(::contractedKey) +
            scopedKeys("tecnico", technical) + scopedKeys("estoque", stock) +
            scopedKeys("financeiro", finance) + scopedKeys("fiscal", fiscalReports)

    fun supervisionChecklistItems(): List<String> = scopedKeys("supervisao", supervision)

    private fun scopedKeys(scope: String, groups: List<ChecklistGroup>) =
        groups.flatMap { group -> group.items.map { key(scope, group.title, it) } }

    private fun mergeItems(base: List<String>, extra: List<String>): List<String> =
        base + extra.filterNot { custom -> base.any { it.equals(custom, ignoreCase = true) } }

    private fun mergeGroups(base: List<ChecklistGroup>, extra: List<ChecklistGroup>): List<ChecklistGroup> {
        val result = base.map { ChecklistGroup(it.title, it.items) }.toMutableList()
        extra.forEach { custom ->
            val index = result.indexOfFirst { it.title.equals(custom.title, ignoreCase = true) }
            if (index >= 0) {
                val current = result[index]
                result[index] = current.copy(items = mergeItems(current.items, custom.items))
            } else {
                result.add(custom)
            }
        }
        return result
    }
}
