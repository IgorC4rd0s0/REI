package br.com.dubrasil.rei.model

data class ChecklistGroup(val title: String, val items: List<String>)

object ReportSchema {
    val contractedModules = listOf(
        "Manifesto", "Financeiro", "Nota Fiscal Eletrônica", "Emissão de NFC-e",
        "Compras", "Custos", "Nota Fiscal Eletrônica de Serviço", "Ordem de Serviço",
        "Estoque", "Customização", "Sintegra", "Boleto", "Faturamento",
        "SPED Fiscal / PIS-COFINS", "PDV – Ponto de Venda"
    )

    val technical = listOf(
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

    val stock = listOf(
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

    val finance = listOf(
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

    val fiscalReports = listOf(
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

    val supervision = listOf(
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

    fun key(scope: String, group: String, item: String) = "$scope::$group::$item"
    fun contractedKey(item: String) = key("dados", "modulos", item)

    fun allChecklistItems(): List<String> =
        contractedModules.map(::contractedKey) +
            scopedKeys("tecnico", technical) + scopedKeys("estoque", stock) +
            scopedKeys("financeiro", finance) + scopedKeys("fiscal", fiscalReports)

    private fun scopedKeys(scope: String, groups: List<ChecklistGroup>) =
        groups.flatMap { group -> group.items.map { key(scope, group.title, it) } }
}
