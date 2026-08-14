// Esquema padrão da web. As chaves devem permanecer compatíveis com ReportSchema.kt.
window.REI_SCHEMA = {
  modules: [
    "Manifesto", "Financeiro", "Nota Fiscal Eletrônica", "Emissão de NFC-e",
    "Compras", "Custos", "Nota Fiscal Eletrônica de Serviço", "Ordem de Serviço",
    "Estoque", "Customização", "Sintegra", "Boleto", "Faturamento",
    "SPED Fiscal / PIS-COFINS", "PDV – Ponto de Venda"
  ],
  technical: [
    ["Instalação e ambiente", [
      "Instalação do TGA", "Conferir cadastro da empresa", "Conferir cadastro da filial",
      "Cadastro de login e senha do cliente", "Instalação do IBExpert",
      "Configuração de segurança e energia", "Liberação de porta no firewall",
      "Compartilhamento da pasta TGA", "IP fixo no servidor", "Workflow de trava de usuário",
      "Print da tela de registro da empresa", "Pasta Instaladores no servidor",
      "Certificado digital na pasta Instaladores", "Logotipo na pasta Instaladores",
      "Relatórios desenvolvidos na pasta Instaladores", "Print da tela de registro da filial"
    ]],
    ["Emissão de NF-e", [
      "Instalação do certificado no TGA", "Certificado A1 inserido no banco de dados",
      "Conferir série da NF-e", "Conferir local do PDF/XML da NF-e na filial",
      "Parametrizar os CFOPs", "Configurar regime tributário",
      "Confirmar alíquotas com o contador (PIS, COFINS etc.)"
    ]],
    ["Configuração e cadastros", [
      "Configurar backup", "Verificar Workflow de trava de usuários", "Cadastrar usuários",
      "Cadastrar funcionários", "Criar fluxograma", "Conferir versão",
      "Configurar e explicar Liberação Online"
    ]]
  ],
  stock: [
    ["Cadastros", ["Cadastro de cliente/fornecedor", "Cadastro de grupo", "Tributação do produto", "Tipo do item", "Produto ou serviço", "Ajuste de saldo"]],
    ["Entradas", ["Manifesto", "Pedido de compra", "NF-e com financeiro", "NF-e sem financeiro", "Extrato de compra", "Energia", "Telecomunicação", "Conhecimento de frete", "Importação de CT-e", "Devolução de compra com NF-e", "Devolução de compra sem NF-e"]],
    ["Saídas", ["Cupom fiscal", "NF-e de venda", "NFS-e", "Extrato de venda", "Devolução de venda com NF-e", "Devolução de venda sem NF-e", "NF-e referente a cupom fiscal", "NF-e de outras saídas"]],
    ["Outros", [
      "Configuração de etiquetas", "Configuração de e-mail", "Movimentos de ajuste de saldo (F10)",
      "Treinamento de perfil de usuário", "Configurar e testar o fluxo de Ordem de Serviço",
      "Validar separação de produtos e serviços no faturamento", "Configurar e testar balança",
      "Configurar e testar controle de lote", "Configurar composição de produtos",
      "Configurar produtos similares", "Configurar controle de série do produto",
      "Configurar e testar comissão", "Configurar formação de preço e custos",
      "Configurar e testar PDV online", "Configurar PDV offline e sincronização",
      "Validar e testar customizações contratadas"
    ]]
  ],
  finance: [
    ["Cadastros", ["Cadastrar conta/caixa", "Cadastrar forma de pagamento", "Cadastro de contas a pagar/receber (F7)"]],
    ["Manutenção de lançamentos (F8)", ["Baixa normal", "Baixa agrupada", "Baixa parcial", "Baixa com duas ou mais formas de pagamento", "Gerar fatura", "Estorno", "Imprimir boleto", "Devolução", "Adiantamento"]],
    ["Extratos e documentos", ["Cadastro de depósito/saque/transferência (F9)", "Compensação", "Devolução de cheque", "Transferência de documentos (F3)"]],
    ["Boletos e cartão", ["Remessa", "Retorno", "Cartão", "Conciliação de cartão", "Homologação de boleto", "Homologação de API"]]
  ],
  fiscal: [
    ["Módulo Fiscal", ["Gerar Sintegra", "Envio de XML para a contabilidade", "Gerar SPED", "Relatório de entradas e saídas"]],
    ["Relatórios financeiros", ["Fechamento de caixa", "Contas a pagar/receber", "Recibo"]],
    ["Relatórios de estoque", ["Relatório de venda", "Relatório de compra", "Estoque e movimentação"]]
  ],
  supervision: [
    ["Planejamento e preparação", ["Cronograma e etapas definidos antes do início", "Requisitos e dados validados com o cliente", "Levantamento executado e anotado", "Ambiente de testes configurado corretamente"]],
    ["Execução técnica", ["Configurações realizadas corretamente", "Migração de dados concluída sem erros", "Treinamento do cliente realizado"]],
    ["Comunicação e relacionamento", ["Contato frequente e claro com o cliente", "Atendimento formal e profissional", "Trabalho em equipe e cooperação", "Fluxograma da base entregue ao Helpdesk", "R.E.I. preenchido diariamente", "Registro de ponto efetuado diariamente"]],
    ["Prazos e qualidade", ["Implantação entregue no prazo", "Sem pendências críticas após a finalização", "Cliente satisfeito com o resultado geral"]],
    ["Aprimoramento e postura", ["Proatividade e iniciativa", "Pontualidade e compromisso", "Busca constante por aprendizado técnico"]]
  ],
  itemLabel(item) { return String(item && typeof item === "object" ? item.label : item || "").trim(); },
  itemType(item) { return item && typeof item === "object" ? String(item.type || "checkbox") : "checkbox"; },
  legacyKey(scope, group, item) {
    const prefix = this.itemType(item) === "checkbox" ? "" : "reiField::";
    return `${prefix}${scope}::${group}::${this.itemLabel(item)}`;
  },
  key(scope, group, item) {
    return String(item && typeof item === "object" && item.key ? item.key : this.legacyKey(scope, group, item));
  },
  normalizeItem(scope, group, raw, forcedType = "") {
    const label = this.itemLabel(raw);
    const type = forcedType || this.itemType(raw);
    const legacyKey = `${type === "checkbox" ? "" : "reiField::"}${scope}::${group}::${label}`;
    const source = raw && typeof raw === "object" ? raw : {};
    return {
      key: String(source.key || legacyKey),
      label,
      type,
      options: Array.isArray(source.options) ? [...source.options] : [],
      requiredMode: String(source.requiredMode || "never"),
      requiredWhen: source.requiredWhen || null,
      legacyKeys: [...new Set([...(Array.isArray(source.legacyKeys) ? source.legacyKeys : []), ...(source.key && source.key !== legacyKey ? [legacyKey] : [])])]
    };
  },
  normalize() {
    this.modules = this.modules.map(item => this.normalizeItem("dados", "modulos", item, "checkbox"));
    [["technical", "tecnico"], ["stock", "estoque"], ["finance", "financeiro"], ["fiscal", "fiscal"], ["supervision", "supervisao"]].forEach(([area, scope]) => {
      this[area] = this[area].map(([group, items]) => [group, items.map(item => this.normalizeItem(scope, group, item))]);
    });
  },
  moduleKey(item) { return this.key("dados", "modulos", item); },
  allDeliveryKeys() {
    return [
      ...this.modules.map(item => this.moduleKey(item)),
      ...["technical:tecnico", "stock:estoque", "finance:financeiro", "fiscal:fiscal"].flatMap(pair => {
        const [name, scope] = pair.split(":");
        return this[name].flatMap(([group, items]) => items.filter(item => !(item && typeof item === "object") || (item.type || "text") === "checkbox").map(item => this.key(scope, group, item)));
      })
    ];
  },
  supervisionKeys() {
    return this.supervision.flatMap(([group, items]) => items.filter(item => !(item && typeof item === "object") || (item.type || "text") === "checkbox").map(item => this.key("supervisao", group, item)));
  }
};

window.REI_SCHEMA.normalize();
