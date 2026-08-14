const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const app = $("#app");
const S = window.REI_SCHEMA;
// Estado de sessão e navegação. Os dados definitivos continuam no servidor.
const state = {
  token: localStorage.reiToken || "",
  user: null,
  reports: [],
  users: [],
  deviceHeartbeats: [],
  supervisorDashboard: null,
  managerFilters: { implantador: "", period: "90", stage: "", overdue: false, blockers: false, staleDays: "7" },
  view: "login",
  editing: null,
  step: 0,
  surveyStep: 0,
  dashboardFilter: "levantamentos",
  dashboardArea: "home",
  areaSearch: "",
  areaStatus: "todos",
  chartPeriod: "6m",
  chartStart: "",
  chartEnd: "",
  viewingSurveyReadOnly: false,
  schemaLoaded: false,
  schemaVersion: "",
  fixedRequirements: {},
  validationTarget: null,
  chat: null
};
const THEME_MODES = new Set(["system", "light", "dark"]);

function storedThemeMode() {
  const userKey = state.user?.username ? `reiTheme:${state.user.username}` : "";
  const value = (userKey && localStorage.getItem(userKey)) || localStorage.getItem("reiTheme") || "system";
  return THEME_MODES.has(value) ? value : "system";
}

function applyTheme(mode = storedThemeMode(), persist = false) {
  const selected = THEME_MODES.has(mode) ? mode : "system";
  if (persist) {
    localStorage.setItem("reiTheme", selected);
    if (state.user?.username) localStorage.setItem(`reiTheme:${state.user.username}`, selected);
  }
  const dark = selected === "dark" || (selected === "system" && matchMedia("(prefers-color-scheme: dark)").matches);
  document.documentElement.dataset.theme = dark ? "dark" : "light";
  document.documentElement.dataset.themeMode = selected;
  document.documentElement.style.colorScheme = dark ? "dark" : "light";
}

const systemThemeQuery = matchMedia("(prefers-color-scheme: dark)");
systemThemeQuery.addEventListener?.("change", () => {
  if (storedThemeMode() === "system") applyTheme("system");
});
applyTheme();
const steps = [
  ["Identificação", "ident"], ["Técnico", "technical"], ["Estoque", "stock"],
  ["Financeiro", "finance"], ["Fiscal", "fiscal"], ["Entrega", "delivery"]
];
const yesNo = ["Sim", "Não"];
const surveySections = [
  ["Levantamento de dados – Implantação TGA", [
    ["empresa", "Empresa", "text"], ["contato", "Contato", "text"], ["telefone", "Tel/Cel", "text"],
    ["email", "E-mail", "email"], ["cnpj", "CNPJ", "text"], ["inscricaoEstadual", "Insc. Estadual", "text"],
    ["_surveyScheduledAt", "Data e hora do levantamento", "datetime-local"],
    ["analistaLevantamento", "Analista responsável pelo levantamento", "text"],
    ["presentesReuniao", "Presentes na reunião", "textarea", 2]
  ]],
  ["Financeiro", [
    ["financeiroCentroCusto", "Centro de custo", "choice", ["Importar", "Usar padrão"]],
    ["financeiroFormasPagamento", "Formas de pagamento", "textarea", 3],
    ["financeiroContasPagarReceber", "Gerencia Contas a pagar/receber?", "choice", yesNo],
    ["financeiroFluxoCaixa", "Utiliza Fluxo de caixa?", "choice", yesNo],
    ["financeiroConciliacao", "Utiliza Conciliação bancária?", "choice", yesNo],
    ["financeiroCartao", "Utiliza Controle de cartão?", "choice", yesNo],
    ["financeiroCartaoMaquina", "Qual máquina utilizada?", "text"],
    ["financeiroTipoIntegracaoBoleto", "Tipo de integração do boleto", "choice", ["Arquivo de remessa e retorno", "API", "Ambos"]],
    ["financeiroCheque", "Utiliza Controle de cheque?", "choice", yesNo],
    ["financeiroDescontoTitulo", "Utiliza Desconto de Título?", "choice", yesNo],
    ["financeiroPrevisaoFutura", "Utiliza Previsão futura de Contas a Pagar?", "choice", yesNo],
    ["financeiroParticularidades", "Particularidades perfil financeiro", "textarea", 4]
  ]],
  ["Estoque", [
    ["estoquePdv", "Utiliza PDV?", "choice", ["Online", "Offline"]],
    ["estoqueDevolucao", "Utiliza devolução de compra e venda?", "choice", yesNo],
    ["estoqueSerieNf", "Série da Nota Fiscal", "text"],
    ["estoqueTiposNotas", "Quais tipos de notas emitidas sem ser venda", "textarea", 3],
    ["estoqueParticularidades", "Particularidades perfil estoque", "textarea", 4],
    ["estoqueComissao", "Utiliza comissão?", "choice", yesNo],
    ["estoqueComissaoPagamento", "Se SIM, pagamento sobre?", "choice", ["Recebimento", "Faturamento"]],
    ["estoqueOrdemServico", "Utiliza Ordem de serviço?", "choice", yesNo],
    ["estoqueControlaEstoque", "Controla Estoque?", "choice", yesNo],
    ["estoqueDetalhes", "Detalhes", "textarea", 5],
    ["estoqueFormacaoPreco", "Utiliza formação de preço?", "choice", yesNo],
    ["estoqueCertificado", "Utiliza qual certificado?", "choice", ["A1", "A3"]],
    ["estoqueEmailNf", "Qual e-mail para envio NF?", "email"],
    ["estoqueBalanca", "Utiliza balança?", "choice", yesNo],
    ["estoqueLote", "Utiliza controle de lote?", "choice", yesNo],
    ["estoqueComposicao", "Utiliza composição?", "choice", yesNo],
    ["estoqueSimilar", "Utiliza similar?", "choice", yesNo],
    ["estoqueSerieProduto", "Utiliza controle de série cadastro produto?", "choice", yesNo]
  ]],
  ["Gerais", [
    ["geralAgendamento", "A implantação pode ser agendada em qualquer período?", "choice", yesNo],
    ["geralRelatorios", "Relatórios: Quais relatórios são utilizados ao longo do mês?", "textarea", 5],
    ["geralWorkflow", "Workflow", "textarea", 5],
    ["geralCustomizacao", "Customização", "textarea", 3]
  ]],
  ["Movimentos de entrada", [["movimentosEntrada", "Movimentos de entrada", "textarea", 5]]],
  ["Movimentos de saída", [["movimentosSaida", "Movimentos de saída", "textarea", 5]]],
  ["Anotações", [["anotacoes", "Anotações", "textarea", 5]]],
  ["Fluxograma inicial", [["fluxogramaInicial", "Fluxograma inicial", "textarea", 5]]]
];

function esc(v) {
  return String(v ?? "").replace(/[&<>"']/g, s => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[s]));
}
function icon(name) {
  const paths = {
    login: '<path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><path d="M10 17l5-5-5-5"/><path d="M15 12H3"/>',
    logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="M16 17l5-5-5-5"/><path d="M21 12H9"/>',
    users: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
    plus: '<path d="M5 12h14"/><path d="M12 5v14"/>',
    briefcase: '<path d="M16 20V4a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/><rect x="2" y="6" width="20" height="14" rx="2"/>',
    timer: '<path d="M10 2h4"/><path d="M12 14l3-3"/><circle cx="12" cy="14" r="8"/>',
    star: '<path d="M11.5 2.7l2.8 5.7 6.3.9-4.6 4.5 1.1 6.3-5.6-3-5.6 3 1.1-6.3-4.6-4.5 6.3-.9z"/>',
    calendar: '<path d="M8 2v4"/><path d="M16 2v4"/><rect x="3" y="4" width="18" height="18" rx="2"/><path d="M3 10h18"/>',
    bar: '<path d="M3 3v18h18"/><rect x="7" y="12" width="3" height="5"/><rect x="12" y="8" width="3" height="9"/><rect x="17" y="5" width="3" height="12"/>',
    pie: '<path d="M21 12a9 9 0 1 1-9-9v9z"/><path d="M12 3a9 9 0 0 1 9 9h-9z"/>',
    clipboard: '<rect x="8" y="2" width="8" height="4" rx="1"/><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><path d="M8 13h8"/><path d="M8 17h5"/>',
    search: '<circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>',
    file: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/><path d="M8 13h8"/><path d="M8 17h5"/>',
    check: '<path d="M20 6L9 17l-5-5"/>',
    settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.83 2.83-.06-.06a1.7 1.7 0 0 0-1.88-.34 1.7 1.7 0 0 0-1.03 1.55V21h-4v-.08A1.7 1.7 0 0 0 9 19.37a1.7 1.7 0 0 0-1.88.34l-.06.06-2.83-2.83.06-.06A1.7 1.7 0 0 0 4.63 15 1.7 1.7 0 0 0 3.08 14H3v-4h.08A1.7 1.7 0 0 0 4.63 9a1.7 1.7 0 0 0-.34-1.88l-.06-.06 2.83-2.83.06.06A1.7 1.7 0 0 0 9 4.63h.01A1.7 1.7 0 0 0 10 3.08V3h4v.08A1.7 1.7 0 0 0 15 4.63a1.7 1.7 0 0 0 1.88-.34l.06-.06 2.83 2.83-.06.06A1.7 1.7 0 0 0 19.37 9c.2.61.77 1.02 1.55 1.02H21v4h-.08A1.7 1.7 0 0 0 19.4 15z"/>'
  };
  return `<svg class="ico" viewBox="0 0 24 24" aria-hidden="true">${paths[name] || paths.file}</svg>`;
}
function fmtDate(ms) {
  return ms ? new Date(ms).toLocaleDateString("pt-BR") : "-";
}
function api(path, options = {}) {
  // Centraliza autenticação e converte os erros JSON em mensagens da interface.
  const headers = { "Accept": "application/json", ...(options.headers || {}) };
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  if (options.body && !(options.body instanceof FormData)) headers["Content-Type"] = "application/json; charset=utf-8";
  return fetch(path, { ...options, headers }).then(async r => {
    const text = await r.text();
    const data = text ? JSON.parse(text) : {};
    if (!r.ok) {
      const error = new Error(data.error || `HTTP ${r.status}`);
      error.status = r.status;
      error.code = data.code || "";
      error.requirements = Array.isArray(data.requirements) ? data.requirements : [];
      throw error;
    }
    return data;
  });
}
function sameText(a, b) {
  return schemaItemLabel(a).toLowerCase() === schemaItemLabel(b).toLowerCase();
}
function schemaItemLabel(item) { return String(item && typeof item === "object" ? item.label : item || "").trim(); }
function schemaItemType(item) { return item && typeof item === "object" ? String(item.type || "checkbox") : "checkbox"; }
function schemaItemOptions(item) { return item && typeof item === "object" && Array.isArray(item.options) ? item.options : []; }
function schemaItemKey(scope, group, item) { return S.key(scope, group, item); }
function schemaFieldKey(scope, group, item) { return schemaItemKey(scope, group, item); }
function schemaItemKeys(scope, group, item) {
  const definition = item && typeof item === "object" ? item : S.normalizeItem(scope, group, item);
  return [...new Set([schemaItemKey(scope, group, definition), ...(Array.isArray(definition.legacyKeys) ? definition.legacyKeys : []), S.legacyKey(scope, group, definition)])];
}
function schemaItemRequiredMode(item) { return String(item && typeof item === "object" ? item.requiredMode || "never" : "never"); }
function schemaItemRequiredWhen(item) { return item && typeof item === "object" && item.requiredWhen ? item.requiredWhen : null; }
function appendUniqueText(list, value, scope, group, forcedType = "") {
  const clean = schemaItemLabel(value);
  if (!clean) return;
  const normalized = S.normalizeItem(scope, group, value, forcedType);
  const existingIndex = list.findIndex(item => schemaItemKey(scope, group, item) === normalized.key || sameText(item, clean));
  if (existingIndex < 0) {
    list.push(normalized);
    return;
  }
  list[existingIndex] = normalized;
}
function appendSchemaGroup(areaList, scope, title, items = []) {
  const cleanTitle = String(title || "").trim();
  if (!cleanTitle) return;
  let group = areaList.find(([groupTitle]) => sameText(groupTitle, cleanTitle));
  if (!group) {
    group = [cleanTitle, []];
    areaList.push(group);
  }
  items.forEach(item => appendUniqueText(group[1], item, scope, cleanTitle));
}
function appendSurveyField(sectionFields, field) {
  const key = String(field?.key || "").trim();
  const label = String(field?.label || "").trim();
  const type = String(field?.type || "text").trim();
  if (!key || !label) return;
  let normalized;
  const metadata = {
    key, label, type,
    options: Array.isArray(field.options) ? field.options : [],
    requiredMode: String(field.requiredMode || "never"),
    requiredWhen: field.requiredWhen || null,
    legacyKeys: Array.isArray(field.legacyKeys) ? field.legacyKeys : []
  };
  if (type === "choice") normalized = [key, label, "choice", metadata.options.length ? metadata.options : ["Sim", "Não"], metadata];
  else if (type === "textarea") normalized = [key, label, "textarea", 3, metadata];
  else normalized = [key, label, type, null, metadata];
  const existingIndex = sectionFields.findIndex(([existingKey]) => existingKey === key);
  if (existingIndex >= 0) sectionFields[existingIndex] = normalized;
  else sectionFields.push(normalized);
}
function applySchemaOverrides(overrides) {
  if (!overrides || typeof overrides !== "object") return;
  const rei = overrides.rei || {};
  (rei.modules || []).forEach(item => appendUniqueText(S.modules, item, "dados", "modulos", "checkbox"));
  [["technical", "tecnico"], ["stock", "estoque"], ["finance", "financeiro"], ["fiscal", "fiscal"], ["supervision", "supervisao"]].forEach(([area, scope]) => {
    (rei[area] || []).forEach(group => appendSchemaGroup(
      S[area],
      scope,
      group.title,
      group.items || []
    ));
  });
  (overrides.levantamento || []).forEach(section => {
    const title = String(section?.title || "").trim();
    if (!title) return;
    let target = surveySections.find(([sectionTitle]) => sameText(sectionTitle, title));
    if (!target) {
      target = [title, []];
      surveySections.push(target);
    }
    (section.fields || []).forEach(field => appendSurveyField(target[1], field));
  });
  state.schemaVersion = String(overrides.schemaVersion || "");
  state.fixedRequirements = overrides.validation?.fixed || {};
}
async function loadSchemaOverrides() {
  if (state.schemaLoaded) return;
  const overrides = await api("/api/schema-overrides").catch(() => null);
  applySchemaOverrides(overrides);
  state.schemaLoaded = true;
}

function normalizedComparison(value) {
  return String(value ?? "").normalize("NFKC").trim().replace(/\s+/g, " ").toLocaleLowerCase("pt-BR");
}
function surveyDefinition(item) {
  return item?.[4] || { key: item?.[0] || "", label: item?.[1] || "", type: item?.[2] || "text", options: Array.isArray(item?.[3]) ? item[3] : [], requiredMode: "never", requiredWhen: null, legacyKeys: [] };
}
function allSchemaDefinitions() {
  const definitions = [];
  S.modules.forEach(item => definitions.push(item));
  [[S.technical, "tecnico"], [S.stock, "estoque"], [S.finance, "financeiro"], [S.fiscal, "fiscal"], [S.supervision, "supervisao"]].forEach(([groups]) => {
    groups.forEach(([, items]) => items.forEach(item => definitions.push(item)));
  });
  surveySections.forEach(([, fields]) => fields.forEach(item => definitions.push(surveyDefinition(item))));
  Object.values(state.fixedRequirements || {}).flat().forEach(item => definitions.push(item));
  return definitions;
}
function definitionKeys(definition) {
  return [...new Set([String(definition?.key || ""), ...(Array.isArray(definition?.legacyKeys) ? definition.legacyKeys.map(String) : [])].filter(Boolean))];
}
function schemaDefinitionByKey(key) {
  return allSchemaDefinitions().find(definition => definitionKeys(definition).includes(String(key))) || null;
}
function checkedRequirement(reportData, key) {
  const checks = new Set(reportData?.checks || []);
  const definition = schemaDefinitionByKey(key);
  const keys = definition ? definitionKeys(definition) : [String(key)];
  return keys.some(candidate => checks.has(candidate));
}
function valueForRequirement(reportData, key) {
  if (key === "deliveryStatus") return reportData?.deliveryStatus || "";
  if (key === "rating") return reportData?.rating || "";
  return reportData?.fields?.[key] || "";
}
function evaluateRuleCondition(reportData, item) {
  if (item?.match && !item?.source) return evaluateRequiredWhen(reportData, item);
  const source = String(item?.source || "");
  const operator = String(item?.operator || "");
  if (source === "module" || source === "checklist") {
    const checked = checkedRequirement(reportData, item.key);
    return operator === "checked" ? checked : !checked;
  }
  const value = valueForRequirement(reportData, item?.key);
  const expected = item?.value ?? "";
  if (operator === "equals") return normalizedComparison(value) === normalizedComparison(expected);
  if (operator === "not_equals") return normalizedComparison(value) !== normalizedComparison(expected);
  if (operator === "not_blank") return String(value).trim() !== "";
  if (operator === "blank") return String(value).trim() === "";
  if (operator === "greater_than") {
    const actualNumber = Number(String(value).replace(",", "."));
    const expectedNumber = Number(String(expected).replace(",", "."));
    return Number.isFinite(actualNumber) && Number.isFinite(expectedNumber) && actualNumber > expectedNumber;
  }
  return false;
}
function evaluateRequiredWhen(reportData, rule) {
  const results = (rule?.conditions || []).filter(item => item && typeof item === "object").map(item => evaluateRuleCondition(reportData, item));
  if (!results.length) return false;
  return rule?.match === "all" ? results.every(Boolean) : results.some(Boolean);
}
function conditionSummaryText(rule) {
  const labels = new Map(allSchemaDefinitions().map(definition => [String(definition.key || ""), definition.label || definition.key]));
  const operators = { checked: "está marcado", not_checked: "não está marcado", equals: "é igual a", not_equals: "é diferente de", not_blank: "está preenchido", blank: "está vazio", greater_than: "é maior que" };
  const parts = (rule?.conditions || []).map(item => {
    if (item?.match && !item?.source) return `(${conditionSummaryText(item)})`;
    const suffix = ["equals", "not_equals", "greater_than"].includes(item?.operator) ? ` ${item?.value ?? ""}` : "";
    return `${labels.get(String(item?.key || "")) || item?.key || "Campo"} ${operators[item?.operator] || ""}${suffix}`.trim();
  });
  return parts.join(rule?.match === "all" ? " e " : " ou ") || "Condição configurada";
}
function requirementActive(reportData, definition) {
  const mode = String(definition?.requiredMode || "never");
  return mode === "always" || (mode === "conditional" && evaluateRequiredWhen(reportData, definition?.requiredWhen));
}
function validImageOrUri(value) {
  const text = String(value || "").trim();
  return /^data:image\//i.test(text) || /^(content|file|https?):\/\//i.test(text);
}
function requirementFulfilled(reportData, definition) {
  const type = String(definition?.type || "text");
  if (type === "checkbox") return definitionKeys(definition).some(key => checkedRequirement(reportData, key));
  const values = definitionKeys(definition).map(key => valueForRequirement(reportData, key));
  if (definition?.key === "empresa") values.push(valueForRequirement(reportData, "cliente"));
  const value = values.find(candidate => String(candidate || "").trim()) || "";
  if (type === "choice") {
    const options = new Set((definition?.options || []).map(normalizedComparison));
    return !!String(value).trim() && (!options.size || options.has(normalizedComparison(value)));
  }
  if (type === "photo") return validImageOrUri(value);
  return String(value).trim() !== "";
}
function phaseRequirementDefinitions(phase) {
  const result = [];
  if (phase === "survey_completion" || phase === "rei_completion") {
    surveySections.forEach(([section, fields]) => fields.forEach(item => result.push({ section, definition: surveyDefinition(item), target: "survey" })));
  }
  if (phase === "rei_completion") {
    [["Técnico", "tecnico", S.technical], ["Estoque", "estoque", S.stock], ["Financeiro", "financeiro", S.finance], ["Fiscal", "fiscal", S.fiscal]].forEach(([area, scope, groups]) => {
      groups.forEach(([group, items]) => items.forEach(item => result.push({ section: `${area} · ${group}`, definition: item, target: scope })));
    });
  }
  if (phase === "supervision_submission") {
    S.supervision.forEach(([group, items]) => items.forEach(item => result.push({ section: `Supervisão · ${group}`, definition: item, target: "supervisao" })));
  }
  (state.fixedRequirements?.[phase] || []).forEach(definition => result.push({ section: definition.section || "Relatório", definition, target: "fixed" }));
  return result;
}
function validateRequiredRequirements(reportData, phase) {
  const seen = new Set();
  return phaseRequirementDefinitions(phase).flatMap(({ section, definition, target }) => {
    const key = String(definition?.key || "");
    if (!key || seen.has(key) || !requirementActive(reportData, definition)) return [];
    seen.add(key);
    if (requirementFulfilled(reportData, definition)) return [];
    return [{
      key,
      label: definition.label || key,
      section,
      phase,
      target,
      reason: definition.type === "checkbox" ? "Marque este item obrigatório." : "Preencha este campo obrigatório.",
      requiredBecause: definition.requiredMode === "conditional" ? conditionSummaryText(definition.requiredWhen) : "Obrigatório para concluir esta etapa."
    }];
  });
}
function validationSnapshot(reportData, phase) {
  const active = phaseRequirementDefinitions(phase).map(item => item.definition).filter(definition => requirementActive(reportData, definition));
  return JSON.stringify({ schemaVersion: state.schemaVersion || "cached", validatedAt: new Date().toISOString(), phase, requiredKeys: [...new Set(active.map(item => item.key))], fulfilledKeys: [...new Set(active.filter(item => requirementFulfilled(reportData, item)).map(item => item.key))] });
}
function requirementUi(definition, reportData) {
  const active = requirementActive(reportData, definition);
  const fulfilled = active && requirementFulfilled(reportData, definition);
  return {
    active,
    fulfilled,
    className: active ? ` required-active ${fulfilled ? "required-fulfilled" : "required-pending"}` : "",
    label: active ? `<span class="required-star" aria-hidden="true">*</span><span class="required-chip">${fulfilled ? "Obrigatório · cumprido" : "Obrigatório"}</span>` : "",
    reason: active && definition?.requiredMode === "conditional" ? `<small class="required-reason">${esc(conditionSummaryText(definition.requiredWhen))}</small>` : ""
  };
}
function fixedRequirement(key) {
  return Object.values(state.fixedRequirements || {}).flat().find(item => String(item?.key || "") === String(key)) || null;
}
function firstRequirementTarget(item) {
  if (!item) return null;
  if (item.phase === "survey_completion") {
    const index = surveySections.findIndex(([title]) => title === item.section);
    return { kind: "survey", index: Math.max(0, index) };
  }
  if (item.phase === "supervision_submission") return { kind: "supervision", index: 0 };
  const section = normalizedComparison(item.section);
  const index = section.includes("técnico") ? 1 : section.includes("estoque") ? 2 : section.includes("financeiro") ? 3 : section.includes("fiscal") ? 4 : section.includes("entrega") ? 5 : 0;
  return { kind: "rei", index };
}
function showRequiredRequirements(requirements) {
  const items = Array.isArray(requirements) ? requirements : [];
  if (!items.length) return;
  state.validationTarget = firstRequirementTarget(items[0]);
  const grouped = items.reduce((acc, item) => { (acc[item.section || "Relatório"] ||= []).push(item); return acc; }, {});
  document.body.insertAdjacentHTML("beforeend", `<div class="modal requirements-modal no-print" role="alertdialog" aria-modal="true" aria-labelledby="requiredRequirementsTitle">
    <section class="card requirements-card"><h2 id="requiredRequirementsTitle">Itens obrigatórios pendentes</h2><p class="muted">Corrija todos os requisitos abaixo antes de finalizar.</p>
      <div class="requirements-list">${Object.entries(grouped).map(([section, sectionItems]) => `<section><h3>${esc(section)}</h3>${sectionItems.map(item => `<article><b>${esc(item.label)}</b><span>${esc(item.reason)}</span><small>${esc(item.requiredBecause)}</small></article>`).join("")}</section>`).join("")}</div>
      <div class="row"><button class="btn secondary" data-action="close-requirements">Fechar</button><button class="btn" data-action="go-first-requirement">Ir para o primeiro item</button></div>
    </section></div>`);
}
function goToFirstRequirement() {
  const target = state.validationTarget;
  $(".requirements-modal")?.remove();
  if (!target) return;
  if (target.kind === "survey") { state.surveyStep = target.index; drawSurvey(); }
  if (target.kind === "rei") { state.step = target.index; drawEditor(); }
}
function validateBeforeAction(payload, phase) {
  const missing = validateRequiredRequirements(payload.report, phase);
  if (missing.length) {
    showRequiredRequirements(missing);
    return false;
  }
  if (phase === "survey_completion" || phase === "rei_completion") payload.report.fields._requiredValidationSnapshot = validationSnapshot(payload.report, phase);
  return true;
}
function newId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  const part = () => Math.floor((1 + Math.random()) * 0x10000).toString(16).slice(1);
  return `${part()}${part()}-${part()}-${part()}-${part()}-${part()}${part()}${part()}`;
}
function cloneData(value) {
  if (globalThis.structuredClone) return globalThis.structuredClone(value);
  return JSON.parse(JSON.stringify(value));
}
function blankReport() {
  return { reportId: newId(), completedAt: Date.now(), report: { fields: {}, checks: [], deliveryStatus: "", rating: "", attachments: [] } };
}
function field(report, key) { return report?.report?.fields?.[key] || ""; }
function stage(report) { return field(report, "_stage") || "rei"; }
function hasCompletedSurvey(report) { return Boolean(String(field(report, "_surveyCompletedAt") || "").trim()); }
function surveyCompletedAt(report) { return Number(field(report, "_surveyCompletedAt")) || report?.completed_at || report?.completedAt || 0; }
function reiDeliveredAt(report) {
  const endDate = reportDate(field(report, "termino"));
  return endDate ? endDate.getTime() : Number(report?.completed_at || report?.completedAt || 0);
}
function reportPdfTitle(report, surveyReport = false) {
  const prefix = surveyReport || ["levantamento_pendente", "rei_pendente"].includes(stage(report)) ? "Levantamento de Dados" : "Relatorio de Entrega";
  const client = String(report?.client || field(report, "cliente") || "Cliente")
    .replace(/[\\/:*?"<>|\r\n]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/\.+$/g, "") || "Cliente";
  return `${prefix} - ${client}`;
}
function score(report) {
  const explicit = Number(String(field(report, "_supervisionScore")).replace(",", "."));
  if (!Number.isNaN(explicit)) return Math.max(0, Math.min(10, explicit));
  const items = S.supervision.flatMap(([group, values]) => values.map(item => [group, item]));
  const done = items.filter(([group, item]) => schemaItemKeys("supervisao", group, item).some(key => checkedRequirement(report?.report, key))).length;
  return done ? done * 10 / items.length : null;
}
function hasEvaluation(report) {
  return !!(report?.report?.rating || field(report, "_supervisionScore") || S.supervision.some(([group, items]) => items.some(item => schemaItemKeys("supervisao", group, item).some(key => checkedRequirement(report?.report, key)))));
}
function pendingSupervisorEvaluations() {
  if (state.user?.role !== "supervisor") return [];
  return state.reports.filter(report =>
    !["levantamento_pendente", "rei_pendente"].includes(stage(report)) &&
    isReadyForSupervisorEvaluation(report) &&
    !hasEvaluation(report)
  );
}
function localDayStamp(date = new Date()) {
  const part = value => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${part(date.getMonth() + 1)}-${part(date.getDate())}`;
}
function showDailyEvaluationReminder() {
  const pending = pendingSupervisorEvaluations();
  if (!pending.length) return;
  const username = String(state.user?.username || "supervisor").toLowerCase();
  const storageKey = `reiEvaluationReminder:${username}`;
  const today = localDayStamp();
  if (localStorage.getItem(storageKey) === today) return;
  localStorage.setItem(storageKey, today);
  const first = pending[0];
  const clients = pending.slice(0, 3).map(report => `<li>${esc(report.client || field(report, "cliente") || "Cliente não informado")}</li>`).join("");
  const remaining = pending.length > 3 ? `<li>e mais ${pending.length - 3} implantação(ões)</li>` : "";
  document.body.insertAdjacentHTML("beforeend", `<div class="modal no-print evaluation-reminder" role="alertdialog" aria-modal="true" aria-labelledby="evaluationReminderTitle">
    <section class="card evaluation-reminder-card">
      <div class="evaluation-reminder-icon">${icon("star")}</div>
      <h2 id="evaluationReminderTitle">Avaliações pendentes</h2>
      <p>Existem <strong>${pending.length}</strong> implantação(ões) concluída(s) aguardando sua avaliação.</p>
      <ul>${clients}${remaining}</ul>
      <p class="muted">Este aviso será apresentado no primeiro acesso de cada dia até que todas sejam avaliadas.</p>
      <div class="row evaluation-reminder-actions">
        <button class="btn secondary" data-action="close-modal">Lembrar amanhã</button>
        <button class="btn green" data-action="open-evaluation-reminder" data-id="${esc(first.id)}">${icon("star")}Avaliar agora</button>
      </div>
    </section>
  </div>`);
}
function isReadyForSupervisorEvaluation(report) {
  return String(report?.delivery_status || report?.report?.deliveryStatus || "")
    .trim()
    .toLowerCase()
    .startsWith("conclu");
}
function isConcludedDeliveryStatus(status) {
  return String(status || "").trim().toLowerCase().startsWith("conclu");
}
function definitionApplicable(reportData, definition) {
  const mode = schemaItemRequiredMode(definition);
  return mode !== "conditional" || evaluateRequiredWhen(reportData, schemaItemRequiredWhen(definition));
}
function deliveryDefinitions() {
  const definitions = [];
  S.modules.forEach(item => {
    if (schemaItemType(item) === "checkbox") definitions.push({ scope: "dados", group: "modulos", definition: item });
  });
  [[S.technical, "tecnico"], [S.stock, "estoque"], [S.finance, "financeiro"], [S.fiscal, "fiscal"]].forEach(([groups, scope]) => {
    groups.forEach(([group, items]) => items.forEach(item => {
      if (schemaItemType(item) === "checkbox") definitions.push({ scope, group, definition: item });
    }));
  });
  return definitions;
}
function applicableDeliveryDefinitions(report) {
  const data = report?.report || report || {};
  return deliveryDefinitions().filter(item => definitionApplicable(data, item.definition));
}
function deliveryCount(report) {
  const data = report?.report || {};
  return applicableDeliveryDefinitions(report).filter(({ definition }) => checkedRequirement(data, definition.key)).length;
}
function deliveryTotal(report) {
  return Math.max(1, applicableDeliveryDefinitions(report).length);
}
function reportDate(value) {
  const text = String(value || "").trim();
  if (!text) return null;
  let year, month, day;
  const iso = text.match(/^(\d{4})-(\d{2})-(\d{2})/);
  const br = text.match(/^(\d{2})\/(\d{2})\/(\d{4})/);
  if (iso) [, year, month, day] = iso;
  else if (br) { day = br[1]; month = br[2]; year = br[3]; }
  else return null;
  const date = new Date(Number(year), Number(month) - 1, Number(day));
  return Number.isNaN(date.getTime()) ? null : date;
}
function implementationDurationDays(report) {
  const start = reportDate(report?.report?.fields?.inicio);
  const end = reportDate(report?.report?.fields?.termino);
  if (!start || !end || end < start) return null;
  return Math.floor((end - start) / 86400000) + 1;
}
function shell(content) {
  const role = state.user?.role === "supervisor" ? "Supervisor" : "Implantador";
  const fullName = state.user?.fullName || state.user?.full_name || state.user?.username || "Usuário";
  const initials = fullName.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]).join("").toUpperCase();
  const avatar = profileAvatar(state.user, initials);
  app.innerHTML = `<div class="app">
    <header class="topbar no-print">
            <div class=\"brand\"><img class=\"theme-logo-light\" src=\"/web/assets/logo_dubrasil_blue.png\" alt=\"DuBrasil Soluções\"><img class=\"theme-logo-dark\" src=\"/web/assets/logo_dubrasil_white.png\" alt=\"\" aria-hidden=\"true\"></div>
      <div class="spacer"></div>
      ${state.user ? `
      ${state.user.role === "supervisor" ? `<a class="btn secondary" href="/admin">${icon("users")}Usuários</a>` : ""}
      ${state.user.role === "supervisor" ? `<a class="btn secondary topbar-btn" href="/admin/items">${icon("clipboard")}Itens dos relatórios</a>` : ""}
      <button class="profile-trigger" data-action="settings" title="Abrir perfil e configurações" aria-label="Abrir perfil de ${esc(fullName)}">${avatar}<span class="profile-copy"><strong>${esc(fullName)}</strong><small>${esc(role)}</small></span><span class="profile-chevron">⌄</span></button>` : ""}
    </header>
    <main class="container">${content}</main>
  </div>`;
}

function profileAvatar(user, initials, large = false) {
  const photo = String(user?.photoData || user?.photo_data || "");
  const className = `profile-avatar${large ? " large" : ""}`;
  return /^data:image\/(?:jpeg|png|webp);base64,/i.test(photo)
    ? `<img class="${className} has-photo" src="${esc(photo)}" alt="Foto de perfil">`
    : `<span class="${className}">${esc(initials || "?")}</span>`;
}

async function compactProfilePhoto(file) {
  if (!file || !/^image\/(?:jpeg|png|webp)$/i.test(file.type)) throw new Error("Selecione uma imagem JPG, PNG ou WEBP.");
  const bitmap = await createImageBitmap(file), max = 512;
  const scale = Math.min(1, max / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement("canvas");
  canvas.width = Math.max(1, Math.round(bitmap.width * scale));
  canvas.height = Math.max(1, Math.round(bitmap.height * scale));
  canvas.getContext("2d").drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close?.();
  return canvas.toDataURL("image/jpeg", .84);
}

function renderAccountSettings() {
  const currentTheme = storedThemeMode();
  const role = state.user?.role === "supervisor" ? "Supervisor" : "Implantador";
  const fullName = state.user?.fullName || state.user?.full_name || state.user?.username || "Usuário";
  const initials = fullName.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]).join("").toUpperCase();
  document.body.insertAdjacentHTML("beforeend", `<div class="modal no-print" role="dialog" aria-modal="true" aria-labelledby="accountSettingsTitle">
    <section class="card account-settings-card">
      <div class="row"><div><h2 id="accountSettingsTitle">Configurações da conta</h2><p class="muted">Personalize a aparência e altere sua senha pessoal.</p></div><div class="spacer"></div><button class="btn secondary" data-action="close-modal" title="Fechar configurações" aria-label="Fechar">Fechar</button></div>
      <div class="profile-summary">${profileAvatar(state.user, initials, true)}<span><strong>${esc(fullName)}</strong><small>${esc(role)} · @${esc(state.user?.username || "")}</small></span><div class="spacer"></div><label class="btn secondary profile-photo-button">${icon("image")}Alterar foto<input type="file" hidden data-profile-photo accept="image/jpeg,image/png,image/webp"></label></div>
      <div class="theme-settings">
        <h3>Tema do sistema</h3><p class="muted">Escolha a aparência que deseja utilizar em todas as telas.</p>
        <div class="theme-options" role="radiogroup" aria-label="Tema do sistema">
          ${[["system","Sistema"],["light","Claro"],["dark","Escuro"]].map(([value,label]) => `<button type="button" class="theme-option ${currentTheme === value ? "active" : ""}" data-action="theme" data-theme-mode="${value}" role="radio" aria-checked="${currentTheme === value}">${label}</button>`).join("")}
        </div>
      </div>
      <form id="changePasswordForm">
        <div class="field"><label>Senha atual</label><input name="currentPassword" type="password" autocomplete="current-password" required></div>
        <div class="form-grid">
          <div class="field"><label>Nova senha</label><input name="newPassword" type="password" minlength="8" autocomplete="new-password" required><small class="muted">Mínimo de 8 caracteres.</small></div>
          <div class="field"><label>Confirmar nova senha</label><input name="confirmation" type="password" minlength="8" autocomplete="new-password" required></div>
        </div>
        <div class="row"><button class="btn" type="submit">Alterar minha senha</button><div class="spacer"></div><button class="btn danger" type="button" data-action="logout" title="Sair do sistema" aria-label="Sair do sistema">${icon("logout")}Sair do sistema</button></div>
      </form>
    </section>
  </div>`);
  $("#changePasswordForm").onsubmit = async event => {
    event.preventDefault();
    const payload = Object.fromEntries(new FormData(event.currentTarget));
    if (payload.newPassword !== payload.confirmation) return alert("A confirmação da nova senha não confere.");
    try {
      await api("/api/auth/change-password", { method: "POST", body: JSON.stringify(payload) });
      $(".modal")?.remove();
      alert("Senha alterada com sucesso.");
    } catch (error) {
      alert(error.message || "Não foi possível alterar a senha.");
    }
  };
  $("[data-profile-photo]").onchange = async event => {
    try {
      const photoData = await compactProfilePhoto(event.target.files[0]);
      const response = await api("/api/auth/photo", { method: "POST", body: JSON.stringify({ photoData }) });
      state.user = { ...state.user, ...response.user, photoData: response.user.photoData || "" };
      $(".modal")?.remove();
      renderDashboard();
    } catch (error) {
      alert(error.message || "Não foi possível alterar a foto.");
    }
  };
}

function renderLogin(error = "") {
  app.innerHTML = `<main class="login">
    <section class="card">
            <div class=\"brand login-brand\"><img class=\"theme-logo-light\" src=\"/web/assets/logo_dubrasil_blue.png\" alt=\"DuBrasil Soluções\"><img class=\"theme-logo-dark\" src=\"/web/assets/logo_dubrasil_white.png\" alt=\"\" aria-hidden=\"true\"></div>
      <h1>Acesso web</h1>
      <p class="muted">Entre com o usuário e senha para acessar o sistema.</p>
      ${error ? `<div class="error">${esc(error)}</div>` : ""}
      <form id="loginForm">
        <div class="field"><label>Usuário</label><input name="username" autocomplete="username" required></div>
        <div class="field"><label>Senha</label><input name="password" type="password" autocomplete="current-password" required></div>
        <button class="btn block">${icon("login")}Entrar</button>
      </form>
    </section>
  </main>`;
  $("#loginForm").onsubmit = async e => {
    e.preventDefault();
    const form = Object.fromEntries(new FormData(e.currentTarget));
    try {
      const res = await api("/api/auth/login", { method: "POST", body: JSON.stringify(form) });
      state.token = res.token; state.user = res.user; localStorage.reiToken = res.token;
      applyTheme(storedThemeMode());
      state.dashboardArea = "home";
      state.dashboardFilter = "levantamentos";
      await loadSchemaOverrides(); await loadUsers(); await loadReports(); await loadDeviceHeartbeats(); renderDashboard(); showDailyEvaluationReminder();
    } catch (err) { renderLogin(err.message); }
  };
}

async function loadMe() {
  try { state.user = (await api("/api/auth/me")).user; return true; }
  catch { localStorage.removeItem("reiToken"); state.token = ""; return false; }
}
async function loadReports() {
  state.reports = await api("/api/reports?full=1&limit=500");
}
async function loadUsers() {
  state.users = state.user?.role === "supervisor" ? await api("/api/users?role=implantador") : [];
}
async function loadDeviceHeartbeats() {
  const devices = await api("/api/device-heartbeats").catch(() => []);
  const latestByUser = new Map();
  devices.forEach(device => {
    const key = String(device.username || device.deviceId || "").trim().toLocaleLowerCase("pt-BR");
    const current = latestByUser.get(key);
    const lastSeen = Date.parse(device.lastSeen || "") || 0;
    const currentLastSeen = Date.parse(current?.lastSeen || "") || 0;
    if (!current || lastSeen > currentLastSeen) latestByUser.set(key, device);
  });
  state.deviceHeartbeats = [...latestByUser.values()].sort((a, b) =>
    (Date.parse(b.lastSeen || "") || 0) - (Date.parse(a.lastSeen || "") || 0)
  );
}
async function loadSupervisorDashboard() {
  if (state.user?.role !== "supervisor") { state.supervisorDashboard = null; return; }
  const filters = state.managerFilters;
  const params = new URLSearchParams({ period: filters.period, staleDays: filters.staleDays });
  if (filters.implantador) params.set("implantador", filters.implantador);
  if (filters.stage) params.set("stage", filters.stage);
  if (filters.overdue) params.set("overdue", "1");
  if (filters.blockers) params.set("blockers", "1");
  state.supervisorDashboard = await api(`/api/dashboard/supervisor?${params}`).catch(error => ({ error: error.message }));
}
function assignedUsername(r) {
  return String(r?.report?.fields?._assignedImplantadorUsername || r?.payload?.report?.fields?._assignedImplantadorUsername || "").trim().toLowerCase();
}
function assignedName(r) {
  const f = r?.report?.fields || r?.payload?.report?.fields || {};
  return f._assignedImplantadorName || f._assignedImplantadorUsername || "";
}
function isAssignedToCurrentUser(r) {
  const assigned = assignedUsername(r);
  return !assigned || assigned === String(state.user?.username || "").trim().toLowerCase();
}

function reportScope() {
  const reports = [...state.reports].sort((a, b) => (b.completed_at || 0) - (a.completed_at || 0));
  if (state.user?.role === "supervisor") return reports;
  return reports.filter(isAssignedToCurrentUser);
}

function renderDashboardHome() {
  // A página inicial é deliberadamente objetiva: operações, resumo e gráficos.
  const reports = state.reports;
  const scoped = reportScope();
  const surveys = scoped.filter(r => stage(r) === "levantamento_pendente" || hasCompletedSurvey(r));
  const implementations = scoped.filter(r => stage(r) !== "levantamento_pendente");
  const completedSurveys = scoped.filter(hasCompletedSurvey).sort((a, b) => surveyCompletedAt(b) - surveyCompletedAt(a));
  const deliveredReports = implementations.filter(isReadyForSupervisorEvaluation).sort((a, b) => reiDeliveredAt(b) - reiDeliveredAt(a));
  const roleTitle = state.user?.role === "supervisor" ? "Área do supervisor" : "Área do implantador";
  shell(`<section class="hero hub-hero">
      <h1>${roleTitle}</h1>
      <p>Escolha abaixo se deseja trabalhar com implantações R.E.I. ou levantamentos de dados.</p>
    </section>
    <section class="hub-actions">
      <button class="card hub-card" data-action="dashboard-area" data-area="implantacoes">
        <span class="metric-icon">${icon("briefcase")}</span>
        <strong>Implantação</strong>
        <small>Dashboard, informações das implantações, relatórios e avaliações recebidas.</small>
        <b>${implementations.length}</b>
      </button>
      <button class="card hub-card" data-action="dashboard-area" data-area="levantamentos">
        <span class="metric-icon">${icon("file")}</span>
        <strong>Levantamentos</strong>
        <small>Tela dedicada para visualizar, iniciar e preencher levantamentos pendentes.</small>
        <b>${surveys.length}</b>
      </button>
    </section>
    <div class="section-title"><h2>Resumo rápido</h2></div>
    <section class="grid metrics home-summary-metrics">
      ${metric("Implantações", implementations.length, "briefcase")}
      ${metric("Levantamentos", surveys.length, "file")}
      ${metric("Avaliações", reports.filter(hasEvaluation).length, "star")}
      ${metric("Último levantamento", completedSurveys[0] ? fmtDate(surveyCompletedAt(completedSurveys[0])) : "-", "calendar")}
      ${metric("Última entrega do R.E.I.", deliveredReports[0] ? fmtDate(reiDeliveredAt(deliveredReports[0])) : "-", "calendar")}
    </section>
    <div class="section-title"><h2>Tendências de atividade</h2></div>
    ${trendAreaChart(implementations, surveys, "Implantações vs. levantamentos")}`);
}

function renderDashboardArea() {
  const reports = reportScope();
  const isSurveyArea = state.dashboardArea === "levantamentos";
  const surveys = reports.filter(r => stage(r) === "levantamento_pendente");
  const completedSurveys = reports.filter(hasCompletedSurvey).sort((a, b) => surveyCompletedAt(b) - surveyCompletedAt(a));
  const allSurveys = surveys.concat(completedSurveys);
  const reiPending = reports.filter(r => stage(r) === "rei_pendente");
  const reiReports = reports.filter(r => !["levantamento_pendente", "rei_pendente"].includes(stage(r)));
  const inProgress = reiReports.filter(r => !isReadyForSupervisorEvaluation(r));
  const concluded = reiReports.filter(isReadyForSupervisorEvaluation);
  const deliveredReports = [...concluded].sort((a, b) => reiDeliveredAt(b) - reiDeliveredAt(a));
  const evaluations = reiReports.filter(hasEvaluation);
  const avgDays = concluded.map(implementationDurationDays).filter(v => v !== null);
  const avgScore = evaluations.map(score).filter(v => v !== null);
  const groups = isSurveyArea
    ? [
        { key: "levantamentos", title: "Levantamentos pendentes", value: surveys.length, subtitle: "Disponíveis para preencher", icon: "file", items: surveys, emptyText: "Nenhum levantamento pendente." },
        { key: "levantamentos_concluidos", title: "Levantamentos concluídos", value: completedSurveys.length, subtitle: "Somente visualização e impressão", icon: "calendar", items: completedSurveys, emptyText: "Nenhum levantamento concluído." }
      ]
    : [
        { key: "pendentes", title: "Implantações pendentes", value: reiPending.length, subtitle: "R.E.I. liberado para iniciar", icon: "briefcase", items: reiPending, emptyText: "Nenhuma implantação pendente para iniciar o R.E.I." },
        { key: "andamento", title: "Implantações em andamento", value: inProgress.length, subtitle: "Iniciadas e ainda não concluídas", icon: "timer", items: inProgress, emptyText: "Nenhuma implantação em andamento." },
        { key: "concluidas", title: "Implantações concluídas", value: concluded.length, subtitle: "Disponíveis para visualização/PDF", icon: "calendar", items: concluded, emptyText: "Nenhuma implantação concluída." }
      ];
  if (!groups.some(group => group.key === state.dashboardFilter)) state.dashboardFilter = groups[0].key;
  const activeGroup = groups.find(group => group.key === state.dashboardFilter) || groups[0];
  shell(`${isSurveyArea ? surveyDashboard(allSurveys) : implementationDashboard(reiPending.concat(inProgress, concluded))}
    ${isSurveyArea
      ? surveyWorkspace(allSurveys)
      : implementationWorkspace(reiPending.concat(inProgress, concluded))}
    ${isSurveyArea ? `${trendAreaChart([], allSurveys, "Tendência dos levantamentos")}${deviceSyncPanel()}` : `<div class="section-title"><h2>Tendências de atividade</h2></div>${trendAreaChart(reiPending.concat(reiReports), [], "Tendência das implantações")}`}
    ${!isSurveyArea ? deviceSyncPanel() : ""}
    ${!isSurveyArea ? `<div class="section-title"><h2>Avaliação das implantações</h2></div>${latestEvaluations(evaluations.slice(0,3))}` : ""}
    `);
  queueMicrotask(applyAreaListFilters);
}

function renderDashboard() {
  if (state.dashboardArea === "home") return renderDashboardHome();
  return renderDashboardArea();
  const reports = state.reports;
  const sorted = [...reports].sort((a, b) => (b.completed_at || 0) - (a.completed_at || 0));
  const allSurveyPending = sorted.filter(r => stage(r) === "levantamento_pendente");
  const surveyPending = state.user.role === "supervisor" ? allSurveyPending : allSurveyPending.filter(isAssignedToCurrentUser);
  const reiPending = state.user.role === "supervisor"
    ? sorted.filter(r => stage(r) === "rei_pendente")
    : sorted.filter(r => stage(r) === "rei_pendente" && isAssignedToCurrentUser(r));
  const reiReports = sorted.filter(r => !["levantamento_pendente", "rei_pendente"].includes(stage(r)));
  const inProgress = reiReports.filter(r => !isReadyForSupervisorEvaluation(r));
  const concluded = reiReports.filter(isReadyForSupervisorEvaluation);
  const groups = [
    { key: "levantamentos", title: "Levantamentos pendentes", value: surveyPending.length, subtitle: state.user.role === "supervisor" ? "Clientes aguardando levantamento" : "Disponíveis para preencher", icon: "file", items: surveyPending, emptyText: "Nenhum levantamento pendente." },
    { key: "pendentes", title: "Implantações pendentes", value: reiPending.length, subtitle: "R.E.I. liberado para iniciar", icon: "briefcase", items: reiPending, emptyText: "Nenhuma implantação pendente para iniciar o R.E.I." },
    { key: "andamento", title: "Implantações em andamento", value: inProgress.length, subtitle: "Iniciadas e ainda não concluídas", icon: "timer", items: inProgress, emptyText: "Nenhuma implantação em andamento." },
    { key: "concluidas", title: "Implantações concluídas", value: concluded.length, subtitle: "Disponíveis para visualização/PDF", icon: "calendar", items: concluded, emptyText: "Nenhuma implantação concluída." }
  ];
  if (!groups.some(group => group.key === state.dashboardFilter)) state.dashboardFilter = groups[0].key;
  const activeGroup = groups.find(group => group.key === state.dashboardFilter) || groups[0];
  const avgDays = sorted
    .filter(isReadyForSupervisorEvaluation)
    .map(implementationDurationDays)
    .filter(v => v !== null);
  const evaluations = sorted.filter(hasEvaluation);
  const avgScore = evaluations.map(score).filter(v => v !== null);
  const averageScoreMetric = state.user.role !== "supervisor"
    ? metric("Nota média", avgScore.length ? `${(avgScore.reduce((a,b)=>a+b,0)/avgScore.length).toFixed(1)}/10` : "-", "star")
    : "";
  shell(`<section class="hero">
      <h1>Painel de implantações</h1>
      <p>Acompanhe entregas, avaliações e relatórios R.E.I. direto pelo navegador.</p>
    </section>
    <section class="grid metrics">
      ${metric("Implantações", reports.length, "briefcase")}
      ${metric("Média de dias gastos", avgDays.length ? `${(avgDays.reduce((a,b)=>a+b,0)/avgDays.length).toFixed(1)} dias` : "-", "timer")}
      ${averageScoreMetric}
      ${metric("Última entrega", reports[0] ? fmtDate(reports[0].completed_at) : "-", "calendar")}
    </section>
    <div class="section-title"><h2>Acompanhamento</h2></div>
    <section class="grid workflow-cards">
      ${groups.map(group => workflowCard(group, group.key === activeGroup.key)).join("")}
    </section>
    ${reportSection(activeGroup.title, activeGroup.items, activeGroup.emptyText, activeGroup.subtitle)}
    <div class="section-title"><h2>Gráficos</h2></div>
    <section class="grid charts-grid">
      ${monthlyChart(reports)}
      ${statusChart(reports)}
    </section>
    ${state.user.role !== "supervisor" ? latestEvaluations(evaluations.slice(0,3)) : ""}
    ${state.user.role === "supervisor" ? `<div class="footer-actions no-print"><button class="btn" data-action="new-client">${icon("plus")}Cadastrar cliente</button></div>` : ""}
    ${state.user.role !== "supervisor" ? `<div class="footer-actions no-print"><button class="btn secondary" data-action="new-survey">${icon("file")}Novo levantamento</button><button class="btn" data-action="new">${icon("plus")}Nova implantação</button></div>` : ""}`);
}
function metric(label, value, iconName = "file") { return `<div class="card metric"><span class="metric-icon">${icon(iconName)}</span><span>${esc(label)}</span><b>${esc(value)}</b></div>`; }
function deviceSyncPanel() {
  if (state.user?.role !== "supervisor") return "";
  const devices = state.deviceHeartbeats || [];
  const rows = devices.map(device => {
    const registeredUser = state.users.find(user => String(user.username || "").toLocaleLowerCase("pt-BR") === String(device.username || "").toLocaleLowerCase("pt-BR"));
    const fullName = device.fullName || registeredUser?.full_name || registeredUser?.fullName || device.username || "Usuário";
    const failed = Boolean(device.lastError);
    const status = failed ? "Falha ao sincronizar" : Number(device.pendingCount) > 0 ? "Aguardando sincronização" : "Sincronizado";
    const statusClass = failed ? "failed" : Number(device.pendingCount) > 0 ? "pending" : "synced";
    const seen = device.lastSeen ? new Date(device.lastSeen).toLocaleString("pt-BR", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit"
    }) : "Nunca";
    return `<article class="sync-device ${statusClass}">
      <span class="sync-dot" aria-hidden="true"></span>
      <div class="sync-device-main">
        <strong>${esc(fullName)}</strong>
        <small>Última sincronização</small>
        <time>${esc(seen)}</time>
        ${device.lastError ? `<p>${esc(device.lastError)}</p>` : ""}
      </div>
      <div class="sync-device-state"><b>${esc(status)}</b><span>${Number(device.pendingCount) || 0} pendente(s)</span></div>
    </article>`;
  }).join("");
  return `<section class="card sync-panel">
    <div class="sync-panel-title"><div><h2>Sincronização dos implantadores</h2><p>Último contato dos aplicativos Android da equipe.</p></div><span>${devices.length} registro(s)</span></div>
    <div class="sync-device-list">${rows || `<p class="muted">Nenhum dispositivo enviou diagnóstico ainda.</p>`}</div>
  </section>`;
}
function managerDashboardHtml() {
  if (state.user?.role !== "supervisor") return "";
  const dashboard = state.supervisorDashboard;
  if (!dashboard) return `<section class="card manager-fallback"><h2>Visão gerencial</h2><p class="muted">O painel gerencial será carregado quando o servidor estiver disponível. O dashboard operacional permanece acessível abaixo.</p></section>`;
  if (dashboard.error) return `<section class="card manager-fallback"><h2>Visão gerencial indisponível</h2><p class="error">${esc(dashboard.error)}</p><p class="muted">O dashboard operacional permanece disponível abaixo.</p></section>`;
  const indicators = dashboard.indicators || {};
  const options = dashboard.filterOptions || { implantadores: [], stages: [] };
  const filters = state.managerFilters;
  const indicatorCards = [
    ["Registros", indicators.total ?? 0, ""], ["Atrasados", indicators.overdue ?? 0, "danger"],
    [`Parados há ${filters.staleDays} dias`, indicators.stale ?? 0, "warn"],
    ["Avaliações pendentes", indicators.pendingEvaluations ?? 0, "warn"],
    ["Impedimentos", indicators.blockers ?? 0, "danger"], ["Concluídos no mês", indicators.concludedMonth ?? 0, "ok"],
    ["Média de duração", indicators.averageDurationDays == null ? "-" : `${indicators.averageDurationDays} dias`, ""],
    ["Nota média", indicators.averageScore == null ? "-" : `${indicators.averageScore}/10`, "ok"],
    ["Erros de sincronização", indicators.syncErrors ?? 0, "danger"]
  ].map(([label, value, type]) => `<div class="manager-kpi ${type}"><span>${esc(label)}</span><b>${esc(value)}</b></div>`).join("");
  const workload = (dashboard.workload || []).map(person => `<tr>
    <td><strong>${esc(person.fullName)}</strong><small>@${esc(person.username || "sem atribuição")}</small></td>
    <td>${person.active}</td><td class="danger-text">${person.overdue}</td><td>${person.stale}</td>
    <td>${person.blockers}</td><td>${person.pendingEvaluations}</td><td>${person.concludedMonth}</td>
    <td><span>${esc(formatManagerDate(person.lastSync))}</span><small>${person.pendingSync} pend. · ${person.syncErrors} erro(s)</small></td>
  </tr>`).join("");
  return `<section class="manager-dashboard">
    <div class="section-title"><div><h2>Visão gerencial</h2><p class="muted section-subtitle">Gargalos, carga da equipe, avaliações e sincronização.</p></div><span class="muted">Atualizado em ${esc(formatManagerDate(dashboard.generatedAt))}</span></div>
    <section class="card manager-filters">
      <label>Implantador<select data-manager-filter="implantador"><option value="">Todos</option>${(options.implantadores || []).map(user => `<option value="${esc(user.username)}" ${filters.implantador === user.username ? "selected" : ""}>${esc(user.full_name)}</option>`).join("")}</select></label>
      <label>Período<select data-manager-filter="period"><option value="30" ${filters.period === "30" ? "selected" : ""}>30 dias</option><option value="90" ${filters.period === "90" ? "selected" : ""}>90 dias</option><option value="365" ${filters.period === "365" ? "selected" : ""}>12 meses</option><option value="all" ${filters.period === "all" ? "selected" : ""}>Todo o histórico</option></select></label>
      <label>Etapa<select data-manager-filter="stage"><option value="">Todas</option>${(options.stages || []).map(item => `<option value="${esc(item.value)}" ${filters.stage === item.value ? "selected" : ""}>${esc(item.label)}</option>`).join("")}</select></label>
      <label>Parado há<select data-manager-filter="staleDays"><option value="3" ${filters.staleDays === "3" ? "selected" : ""}>3 dias</option><option value="7" ${filters.staleDays === "7" ? "selected" : ""}>7 dias</option><option value="15" ${filters.staleDays === "15" ? "selected" : ""}>15 dias</option><option value="30" ${filters.staleDays === "30" ? "selected" : ""}>30 dias</option></select></label>
      <label class="manager-check"><input type="checkbox" data-manager-filter="overdue" ${filters.overdue ? "checked" : ""}>Somente atrasados</label>
      <label class="manager-check"><input type="checkbox" data-manager-filter="blockers" ${filters.blockers ? "checked" : ""}>Com impedimentos</label>
    </section>
    <section class="manager-kpis">${indicatorCards}</section>
    <section class="card manager-stage"><h3>Total por etapa</h3><div>${(dashboard.byStage || []).map(item => `<span><b>${item.count}</b>${esc(item.label)}</span>`).join("")}</div></section>
    <section class="card manager-workload"><h3>Carga por implantador</h3><div class="manager-table-wrap"><table><thead><tr><th>Implantador</th><th>Ativos</th><th>Atras.</th><th>Parados</th><th>Imped.</th><th>Avaliar</th><th>Concl. mês</th><th>Último sync</th></tr></thead><tbody>${workload || `<tr><td colspan="8">Nenhum implantador encontrado.</td></tr>`}</tbody></table></div></section>
    <section class="manager-lists">
      ${managerRecordList("Atrasados", dashboard.lists?.overdue, "danger")}
      ${managerRecordList("Registros parados", dashboard.lists?.stale, "warn")}
      ${managerRecordList("Avaliações pendentes", dashboard.lists?.pendingEvaluations, "warn")}
      ${managerRecordList("Impedimentos abertos", dashboard.lists?.blockers, "danger")}
      ${managerSyncErrorList(dashboard.lists?.syncErrors)}
    </section>
  </section>`;
}
function managerRecordList(title, items = [], type = "") {
  const rows = (items || []).map(item => `<button type="button" class="manager-list-row" data-action="manager-open" data-id="${esc(item.id)}"><span><strong>${esc(item.client)}</strong><small>${esc(item.assignedName)} · ${esc(item.stageLabel)}</small>${item.blocker ? `<small class="danger-text">${esc(item.blocker)}</small>` : ""}</span><span>${item.deadline ? `Prazo ${esc(formatManagerDate(item.deadline))}` : `${item.daysStale || 0} dia(s) sem atualizar`}</span></button>`).join("");
  return `<article class="card manager-list ${type}"><h3>${esc(title)} <span>${(items || []).length}</span></h3>${rows || `<p class="muted">Nenhum registro.</p>`}</article>`;
}
function managerSyncErrorList(items = []) {
  const rows = (items || []).map(item => `<div class="manager-list-row static"><span><strong>${esc(item.fullName)}</strong><small>App ${esc(item.appVersion)} · ${item.pendingCount} pendente(s)</small><small class="danger-text">${esc(item.error)}</small></span><span>${esc(formatManagerDate(item.lastSeen))}</span></div>`).join("");
  return `<article class="card manager-list danger"><h3>Falhas de sincronização <span>${(items || []).length}</span></h3>${rows || `<p class="muted">Nenhuma falha.</p>`}</article>`;
}
function formatManagerDate(value) {
  if (!value) return "Nunca";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}
function workflowCard(group, active) {
  return `<button type="button" class="card metric workflow-card ${active ? "active" : ""}" data-action="dashboard-filter" data-filter="${esc(group.key)}">
    <span class="metric-icon">${icon(group.icon)}</span>
    <span>${esc(group.title)}</span>
    <b>${esc(group.value)}</b>
    <small>${esc(group.subtitle)}</small>
  </button>`;
}
function empty(text) { return `<div class="card muted">${esc(text)}</div>`; }
function reportSection(title, items, emptyText, subtitle = "", openAction = "open") {
  return `<div class="section-title"><div><h2>${esc(title)}</h2>${subtitle ? `<p class="muted section-subtitle">${esc(subtitle)}</p>` : ""}</div><div class="spacer"></div><span class="muted">${items.length} total</span></div>
    <section class="list">${items.length ? items.map(item => reportRow(item, openAction)).join("") : empty(emptyText)}</section>`;
}
function reportRow(r, openAction = "open") {
  const responsible = assignedName(r);
  const statusLabel = openAction === "open-survey-completed" ? "Concluído" : (r.delivery_status || "Sem status");
  return `<article class="card report-row" data-action="${esc(openAction)}" data-id="${esc(r.id)}">
    <div class="row"><span class="row-icon">${icon("file")}</span><div><h3>${esc(r.client || "Cliente não informado")}</h3>
    <p class="muted">${fmtDate(r.completed_at)} · ${esc(r.consultant || "Sem consultor")} · ${deliveryCount(r)} itens${responsible ? ` · Responsável: ${esc(responsible)}` : ""}</p></div>
    <div class="spacer"></div><span class="pill">${esc(statusLabel)}</span></div>
  </article>`;
}
function surveyProgress(report) {
  if (hasCompletedSurvey(report)) return 100;
  const fields = report?.report?.fields || {};
  const definitions = surveySections.flatMap(([, items]) => items.map(surveyDefinition));
  const filled = definitions.filter(definition => String(fields[definition.key] || "").trim()).length;
  return definitions.length ? Math.round(filled * 100 / definitions.length) : 0;
}
function surveyDashboard(items) {
  const surveys = [...new Map(items.map(item => [item.id, item])).values()];
  const completed = surveys.filter(hasCompletedSurvey);
  const progresses = surveys.map(surveyProgress);
  const average = progresses.length ? Math.round(progresses.reduce((sum, value) => sum + value, 0) / progresses.length) : 0;
  const waiting = progresses.filter(value => value === 0).length;
  const inProgress = surveys.length - completed.length;
  const byResponsible = new Map();
  surveys.forEach(report => {
    const name = assignedName(report) || report.consultant || "Não definido";
    const current = byResponsible.get(name) || { name, reports: [], progress: 0 };
    current.reports.push(report);
    current.progress += surveyProgress(report);
    byResponsible.set(name, current);
  });
  const responsibleRows = [...byResponsible.values()]
    .map(item => ({ ...item, average: Math.round(item.progress / item.reports.length) }))
    .sort((a, b) => b.average - a.average || a.name.localeCompare(b.name, "pt-BR"))
    .map(item => {
      const initials = item.name.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]).join("").toUpperCase() || "?";
      const profile = [...state.users, state.user].find(user => String(user?.full_name || user?.fullName || "").toLocaleLowerCase("pt-BR") === item.name.toLocaleLowerCase("pt-BR"));
      return `<article class="survey-person-row">${profileAvatar(profile, initials)}<span class="survey-person-name"><strong>${esc(item.name)}</strong><small>${item.reports.length} levantamento(s) atribuído(s)</small></span><span class="survey-person-progress"><small>Progresso médio <b>${item.average}%</b></small><i><u style="width:${item.average}%"></u></i></span></article>`;
    }).join("");
  const recentRows = [...surveys]
    .sort((a, b) => surveyCompletedAt(b) - surveyCompletedAt(a))
    .slice(0, 4)
    .map(report => {
      const progress = surveyProgress(report);
      const completedAt = hasCompletedSurvey(report) ? surveyCompletedAt(report) : 0;
      const scheduled = field(report, "_surveyScheduledAt");
      const date = completedAt ? fmtDate(completedAt) : (scheduled ? formatSurveyDateTime(scheduled).split(" ")[0] : "Sem data");
      const action = hasCompletedSurvey(report) ? "open-survey-completed" : "open";
      return `<button class="survey-activity" data-action="${action}" data-id="${esc(report.id)}"><span><strong>${esc(report.client || "Cliente não informado")}</strong><small>${hasCompletedSurvey(report) ? "Concluído" : "Entrega"}: ${esc(date)} • ${progress}%</small></span><b>↗</b></button>`;
    }).join("");
  const primaryAction = state.user.role === "supervisor"
    ? `<button class="btn" data-action="new-client">${icon("plus")}Cadastrar cliente</button>`
    : `<button class="btn" data-action="new-survey">${icon("plus")}Novo levantamento</button>`;
  return `<section class="survey-dashboard">
    <header class="survey-dashboard-heading"><div><h1>Dashboard de Levantamentos</h1><p>Visão consolidada de desempenho, prazos e progresso dos levantamentos ativos.</p></div><div class="survey-dashboard-actions"><button class="btn secondary" data-action="dashboard-home">${icon("home")}Voltar</button>${primaryAction}</div></header>
    <section class="survey-kpis">
      <article class="survey-kpi"><span class="metric-icon">${icon("clipboard")}</span><small>Total de levantamentos</small><strong>${surveys.length}</strong><em>${completed.length} concluído(s)</em></article>
      <article class="survey-kpi"><span class="metric-icon success">${icon("bar")}</span><small>Média de progresso</small><strong>${average}%</strong><i><u style="width:${average}%"></u></i></article>
      <article class="survey-kpi"><span class="metric-icon warning">${icon("timer")}</span><small>Em andamento</small><strong>${inProgress}</strong><em>${waiting} pendente(s) de início</em></article>
      <article class="survey-kpi"><span class="metric-icon success">${icon("check")}</span><small>Concluídos</small><strong>${completed.length}</strong><em class="success-text">Disponíveis para impressão</em></article>
    </section>
    <section class="survey-dashboard-grid">
      <article class="survey-performance"><h2>Desempenho por responsável</h2><div>${responsibleRows || empty("Nenhum levantamento atribuído.")}</div></article>
      <article class="survey-recent"><h2>Atividades recentes</h2><div>${recentRows || empty("Nenhuma atividade recente.")}</div></article>
    </section>
  </section>`;
}
function implementationDashboard(items) {
  const implementations = [...new Map(items.map(item => [item.id, item])).values()];
  const completed = implementations.filter(isReadyForSupervisorEvaluation);
  const progresses = implementations.map(implementationProgress);
  const average = progresses.length ? Math.round(progresses.reduce((sum, value) => sum + value, 0) / progresses.length) : 0;
  const waiting = implementations.filter(report => stage(report) === "rei_pendente").length;
  const inProgress = implementations.length - completed.length;
  const byResponsible = new Map();
  implementations.forEach(report => {
    const name = assignedName(report) || report.consultant || "Não definido";
    const current = byResponsible.get(name) || { name, reports: [], progress: 0 };
    current.reports.push(report);
    current.progress += implementationProgress(report);
    byResponsible.set(name, current);
  });
  const responsibleRows = [...byResponsible.values()]
    .map(item => ({ ...item, average: Math.round(item.progress / item.reports.length) }))
    .sort((a, b) => b.average - a.average || a.name.localeCompare(b.name, "pt-BR"))
    .map(item => {
      const initials = item.name.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]).join("").toUpperCase() || "?";
      const profile = [...state.users, state.user].find(user => String(user?.full_name || user?.fullName || "").toLocaleLowerCase("pt-BR") === item.name.toLocaleLowerCase("pt-BR"));
      return `<article class="survey-person-row">${profileAvatar(profile, initials)}<span class="survey-person-name"><strong>${esc(item.name)}</strong><small>${item.reports.length} implantação(ões) atribuída(s)</small></span><span class="survey-person-progress"><small>Progresso médio <b>${item.average}%</b></small><i><u style="width:${item.average}%"></u></i></span></article>`;
    }).join("");
  const recentRows = [...implementations]
    .sort((a, b) => (reiDeliveredAt(b) || b.completed_at || 0) - (reiDeliveredAt(a) || a.completed_at || 0))
    .slice(0, 4)
    .map(report => `<button class="survey-activity" data-action="open" data-id="${esc(report.id)}"><span><strong>${esc(report.client || "Cliente não informado")}</strong><small>${isReadyForSupervisorEvaluation(report) ? "Concluído" : "Em andamento"} • ${implementationProgress(report)}%</small></span><b>↗</b></button>`)
    .join("");
  const primaryAction = state.user.role !== "supervisor" ? `<button class="btn" data-action="new">${icon("plus")}Nova implantação</button>` : "";
  return `<section class="survey-dashboard implementation-dashboard">
    <header class="survey-dashboard-heading"><div><h1>Dashboard de Implantações</h1><p>Visão consolidada de desempenho, prazos e progresso das implantações.</p></div><div class="survey-dashboard-actions"><button class="btn secondary" data-action="dashboard-home">${icon("home")}Voltar</button>${primaryAction}</div></header>
    <section class="survey-kpis">
      <article class="survey-kpi"><span class="metric-icon">${icon("briefcase")}</span><small>Total de implantações</small><strong>${implementations.length}</strong><em>${completed.length} concluída(s)</em></article>
      <article class="survey-kpi"><span class="metric-icon success">${icon("bar")}</span><small>Média de progresso</small><strong>${average}%</strong><i><u style="width:${average}%"></u></i></article>
      <article class="survey-kpi"><span class="metric-icon warning">${icon("timer")}</span><small>Em andamento</small><strong>${inProgress}</strong><em>${waiting} pendente(s) de início</em></article>
      <article class="survey-kpi"><span class="metric-icon success">${icon("check")}</span><small>Concluídas</small><strong>${completed.length}</strong><em class="success-text">Disponíveis para impressão</em></article>
    </section>
    <section class="survey-dashboard-grid">
      <article class="survey-performance"><h2>Desempenho por responsável</h2><div>${responsibleRows || empty("Nenhuma implantação atribuída.")}</div></article>
      <article class="survey-recent"><h2>Atividades recentes</h2><div>${recentRows || empty("Nenhuma atividade recente.")}</div></article>
    </section>
  </section>`;
}
function implementationProgress(report) {
  if (isReadyForSupervisorEvaluation(report)) return 100;
  if (stage(report) === "rei_pendente") return 0;
  const total = deliveryTotal(report);
  return Math.min(95, Math.round(deliveryCount(report) * 100 / total));
}
function areaStatus(report, survey) {
  if (survey) return hasCompletedSurvey(report) ? "concluido" : "pendente";
  if (stage(report) === "rei_pendente") return "pendente";
  return isReadyForSupervisorEvaluation(report) ? "concluido" : "andamento";
}
function areaListToolbar(survey) {
  const options = survey
    ? [["todos", "Todos"], ["pendente", "Pendentes"], ["concluido", "Concluídos"]]
    : [["todos", "Todos os status"], ["pendente", "Pendentes"], ["andamento", "Em andamento"], ["concluido", "Concluídos"]];
  return `<div class="area-list-toolbar no-print">
    <label class="area-search">${icon("search")}<input type="search" data-area-search placeholder="Pesquisar ${survey ? "levantamentos" : "implantações"}..." value="${esc(state.areaSearch)}"></label>
    <select data-area-status aria-label="Filtrar status">${options.map(([value,label]) => `<option value="${value}" ${state.areaStatus === value ? "selected" : ""}>${label}</option>`).join("")}</select>
  </div>`;
}
function surveyWorkspace(items) {
  const sorted = [...new Map(items.map(item => [item.id, item])).values()].sort((a,b) => surveyCompletedAt(b) - surveyCompletedAt(a));
  const rows = sorted.map(report => {
    const completed = hasCompletedSurvey(report), progress = surveyProgress(report);
    const status = areaStatus(report, true), responsible = assignedName(report) || report.consultant || "Não definido";
    const scheduled = field(report, "_surveyScheduledAt");
    const date = completed ? fmtDate(surveyCompletedAt(report)) : (scheduled ? formatSurveyDateTime(scheduled).split(" ")[0] : "Não agendado");
    const action = completed ? "open-survey-completed" : "open";
    return `<article class="survey-list-card" data-area-row data-area-status="${status}" data-area-search-value="${esc(`${report.client} ${responsible}`).toLowerCase()}">
      <button type="button" class="survey-open" data-action="${action}" data-id="${esc(report.id)}" aria-label="Abrir levantamento de ${esc(report.client)}">
        <span class="row-icon">${icon(completed ? "check" : "file")}</span>
        <span class="survey-main"><small>LEVANTAMENTO</small><strong>${esc(report.client || "Cliente não informado")}</strong><em>Responsável: ${esc(responsible)}</em></span>
        <span class="survey-progress"><small>Progresso <b>${progress}%</b></small><i><u style="width:${progress}%"></u></i></span>
        <span class="survey-date"><small>${completed ? "Concluído em" : "Data do levantamento"}</small><strong>${esc(date)}</strong></span>
        <span class="survey-more">›</span>
      </button>
    </article>`;
  }).join("");
  return `<section class="area-workspace"><div class="area-workspace-title"><div><h2>Levantamentos</h2><p>Gestão e execução de levantamentos de dados pendentes e concluídos.</p></div><span data-area-count>${sorted.length} registro(s)</span></div>${areaListToolbar(true)}<div class="survey-list">${rows || empty("Nenhum levantamento encontrado.")}</div></section>`;
}
function implementationWorkspace(items) {
  const sorted = [...items].sort((a,b) => (b.completed_at || 0) - (a.completed_at || 0));
  const rows = sorted.map(report => {
    const status = areaStatus(report, false), progress = implementationProgress(report);
    const labels = { pendente: "Pendente", andamento: "Em andamento", concluido: "Concluído" };
    const responsible = assignedName(report) || report.consultant || "Não definido";
    return `<div class="implementation-row" data-area-row data-area-status="${status}" data-area-search-value="${esc(`${report.client} ${responsible}`).toLowerCase()}">
      <strong data-label="Projeto">${esc(report.client || "Cliente não informado")}</strong>
      <span data-label="Implantador">${esc(responsible)}</span>
      <span data-label="Status"><i class="status-badge ${status}">${labels[status]}</i></span>
      <span data-label="Progresso">${progress}%</span>
      <span data-label="Data de início">${esc(field(report,"inicio") || "—")}</span>
      <button type="button" class="btn secondary compact" data-action="open" data-id="${esc(report.id)}">${icon("settings")}Ações</button>
    </div>`;
  }).join("");
  return `<section class="area-workspace"><div class="area-workspace-title"><div><h2>Lista de implantações</h2><p>Gerencie e acompanhe o status dos projetos de implantação ativos e concluídos.</p></div><span data-area-count>${sorted.length} registro(s)</span></div>${areaListToolbar(false)}<div class="implementation-table"><div class="implementation-head"><span>Projeto</span><span>Implantador</span><span>Status</span><span>Progresso</span><span>Data de início</span><span>Ações</span></div>${rows || empty("Nenhuma implantação encontrada.")}</div></section>`;
}
function applyAreaListFilters() {
  const search = String(state.areaSearch || "").trim().toLocaleLowerCase("pt-BR");
  let visible = 0;
  $$('[data-area-row]').forEach(row => {
    const matchesSearch = !search || String(row.dataset.areaSearchValue || "").includes(search);
    const matchesStatus = state.areaStatus === "todos" || row.dataset.areaStatus === state.areaStatus;
    row.hidden = !(matchesSearch && matchesStatus);
    if (!row.hidden) visible++;
  });
  const count = $('[data-area-count]');
  if (count) count.textContent = `${visible} registro(s)`;
}
function trendAreaChart(implementations = [], surveys = [], title = "Tendências de atividade") {
  const now = new Date();
  const startOfMonth = date => new Date(date.getFullYear(), date.getMonth(), 1);
  let start, end = startOfMonth(now), periodLabel = "últimos 6 meses";
  if (state.chartPeriod === "year") {
    start = new Date(now.getFullYear(), 0, 1);
    end = new Date(now.getFullYear(), 11, 1);
    periodLabel = `ano de ${now.getFullYear()}`;
  } else if (state.chartPeriod === "custom" && state.chartStart && state.chartEnd) {
    const selectedStart = reportDate(state.chartStart), selectedEnd = reportDate(state.chartEnd);
    if (selectedStart && selectedEnd && selectedStart <= selectedEnd) {
      start = startOfMonth(selectedStart); end = startOfMonth(selectedEnd);
      periodLabel = `${fmtDate(selectedStart.getTime())} a ${fmtDate(selectedEnd.getTime())}`;
    }
  }
  if (!start) start = new Date(now.getFullYear(), now.getMonth() - 5, 1);
  const months = [];
  for (let cursor = new Date(start); cursor <= end && months.length < 36; cursor.setMonth(cursor.getMonth() + 1)) months.push(new Date(cursor));
  if (!months.length) months.push(startOfMonth(now));
  const countByMonth = (reports, surveyMode) => months.map(month => reports.filter(report => {
    const timestamp = surveyMode ? surveyCompletedAt(report) : (report.completed_at || report.completedAt || 0);
    const date = new Date(timestamp);
    return timestamp && date.getMonth() === month.getMonth() && date.getFullYear() === month.getFullYear();
  }).length);
  const implementationCounts = countByMonth(implementations, false);
  const surveyCounts = countByMonth(surveys, true);
  const maxValue = Math.max(1, ...implementationCounts, ...surveyCounts);
  const pointStep = months.length <= 1 ? 0 : 510 / (months.length - 1);
  const points = values => values.map((value, index) => ({ x: 48 + index * pointStep, y: 202 - (value / maxValue) * 154, value }));
  const implementationPoints = points(implementationCounts);
  const surveyPoints = points(surveyCounts);
  const smoothPath = data => data.reduce((path, point, index) => {
    if (!index) return `M ${point.x} ${point.y}`;
    const previous = data[index - 1], middle = (previous.x + point.x) / 2;
    return `${path} C ${middle} ${previous.y}, ${middle} ${point.y}, ${point.x} ${point.y}`;
  }, "");
  const areaPath = data => `${smoothPath(data)} L ${data[data.length - 1].x} 202 L ${data[0].x} 202 Z`;
  const chartId = `trend-${Math.random().toString(36).slice(2, 9)}`;
  const series = [];
  if (implementations.length || !surveys.length) series.push({ label: "Implantações", color: "#0b63c9", points: implementationPoints, area: `${chartId}-blue` });
  if (surveys.length || !implementations.length) series.push({ label: "Levantamentos", color: "#00a875", points: surveyPoints, area: `${chartId}-green` });
  const labelEvery = Math.max(1, Math.ceil(months.length / 8));
  const analyzed = implementations.filter(report => {
    const date = new Date(report.completed_at || report.completedAt || 0); return date >= start && date < new Date(end.getFullYear(), end.getMonth() + 1, 1);
  }).length + surveys.filter(report => {
    const date = new Date(surveyCompletedAt(report)); return date >= start && date < new Date(end.getFullYear(), end.getMonth() + 1, 1);
  }).length;
  return `<section class="trend-chart-card">
    <header><div><h3>${esc(title)}</h3><p>Movimentação registrada no período selecionado</p></div><div class="trend-legend">${series.map(item => `<span><i style="background:${item.color}"></i>${item.label}</span>`).join("")}</div></header>
    <div class="chart-period-controls no-print" role="group" aria-label="Período do gráfico">
      ${[["6m","Últimos 6 meses"],["year","Ano"],["custom","Personalizado"]].map(([value,label]) => `<button type="button" class="btn secondary compact ${state.chartPeriod === value ? "active" : ""}" data-action="chart-period" data-period="${value}">${label}</button>`).join("")}
      ${state.chartPeriod === "custom" ? `<label>Início<input type="date" data-chart-date="start" value="${esc(state.chartStart)}"></label><label>Fim<input type="date" data-chart-date="end" value="${esc(state.chartEnd)}"></label>` : ""}
    </div>
    <div class="trend-chart-scroll"><svg class="trend-chart" viewBox="0 0 600 242" role="img" aria-label="${esc(title)}">
      <defs><linearGradient id="${chartId}-blue" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#0b63c9" stop-opacity=".38"/><stop offset="1" stop-color="#0b63c9" stop-opacity=".02"/></linearGradient><linearGradient id="${chartId}-green" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#00a875" stop-opacity=".34"/><stop offset="1" stop-color="#00a875" stop-opacity=".02"/></linearGradient></defs>
      ${[0, .25, .5, .75, 1].map(factor => { const y = 202 - factor * 154; return `<line x1="48" y1="${y}" x2="558" y2="${y}" class="trend-grid-line"/><text x="35" y="${y + 4}" class="trend-axis-value">${Math.round(maxValue * factor)}</text>`; }).join("")}
      ${series.map(item => `<path d="${areaPath(item.points)}" fill="url(#${item.area})"/><path d="${smoothPath(item.points)}" fill="none" stroke="${item.color}" stroke-width="3" stroke-linecap="round"/>${item.points.map((point, index) => `<g><title>${item.label}: ${point.value} em ${months[index].toLocaleDateString("pt-BR", {month:"long"})}</title><circle cx="${point.x}" cy="${point.y}" r="4" fill="${item.color}" stroke="white" stroke-width="2"/></g>`).join("")}`).join("")}
      ${months.map((month, index) => index % labelEvery === 0 || index === months.length - 1 ? `<text x="${48 + index * pointStep}" y="228" text-anchor="middle" class="trend-axis-label">${month.toLocaleDateString("pt-BR", {month:"short", year: months.length > 12 ? "2-digit" : undefined}).replace(".", "")}</text>` : "").join("")}
    </svg></div>
    <footer><span>Período: ${esc(periodLabel)}</span><span>${analyzed} registro(s) analisado(s)</span></footer>
  </section>`;
}
function monthlyChart(reports, title = "Entregas por mês") {
  return trendAreaChart(reports, [], title);
}
function statusChart(reports, title = "Situação") {
  const total = reports.length;
  const divisor = Math.max(1, total);
  const ok = reports.filter(r => isConcludedDeliveryStatus(r.delivery_status)).length;
  const no = reports.filter(r => String(r.delivery_status) === "Não concluído").length;
  const other = total - ok - no;
  const items = [
    ["Concluídas", ok, "ok"],
    ["Não concluídas", no, "no"],
    ["Sem definição", other, "other"]
  ];
  return `<div class="card chart-card"><div class="chart-title"><h3>${icon("pie")}${esc(title)}</h3><span>${total} relatório${total === 1 ? "" : "s"}</span></div>
    <div class="status-stack">
      <i class="ok" style="width:${ok/divisor*100}%"></i>
      <i class="no" style="width:${no/divisor*100}%"></i>
      <i class="other" style="width:${other/divisor*100}%"></i>
    </div>
    <div class="status-cards">${items.map(([label,value,type])=>`<div class="status-card ${type}"><small>${label}</small><b>${value}</b><span>${Math.round(value/divisor*100)}%</span></div>`).join("")}</div>
  </div>`;
}
function latestEvaluations(items) {
  return `<div class="section-title"><h2>${icon("star")}Últimas avaliações</h2></div><section class="list">${items.length ? items.map(r => `<article class="card report-row" data-action="open" data-id="${esc(r.id)}"><div class="row"><span class="row-icon score">${icon("star")}</span><b>${score(r)?.toFixed(1) || "-"}/10</b><div><h3>${esc(r.client)}</h3><p class="muted">${esc(r.report.rating || "Sem parecer escrito.")}</p></div></div></article>`).join("") : empty("Nenhuma avaliação recebida ainda.")}</section>`;
}

function renderEditor(payload = blankReport()) {
  // Edita uma cópia para que cancelar a tela não altere o histórico em memória.
  if (payload.payload) {
    state.editing = cloneData(payload.payload);
    state.editing.reportId = payload.id;
    state.editing.completedAt = payload.completed_at || payload.payload.completedAt || Date.now();
  } else {
    state.editing = cloneData(payload);
  }
  state.step = 0;
  drawEditor();
}
function blankClientPayload() {
  const payload = blankReport();
  payload.report.fields._stage = "levantamento_pendente";
  payload.report.fields._createdBy = state.user?.username || "";
  return payload;
}
function blankSurveyPayload() {
  const payload = blankReport();
  const username = state.user?.username || "";
  const name = state.user?.fullName || state.user?.full_name || username;
  payload.report.fields._stage = "levantamento_pendente";
  payload.report.fields._createdBy = username;
  payload.report.fields._ownerUsername = username;
  payload.report.fields._assignedImplantadorUsername = username;
  payload.report.fields._assignedImplantadorName = name;
  payload.report.fields.analistaLevantamento = name;
  return payload;
}
function renderClientForm(payload = blankClientPayload()) {
  state.editing = cloneData(payload.payload ? payload.payload : payload);
  state.editing.reportId = payload.id || payload.reportId || state.editing.reportId;
  const f = state.editing.report.fields;
  const editingExisting = Boolean(payload.id || payload.reportId);
  shell(`<section class="hero editor-hero has-top-action">
      <button class="btn secondary page-back" data-action="dashboard">Voltar</button>
      <span>${editingExisting ? "EDITAR CLIENTE" : "NOVO CLIENTE"}</span>
      <h1>${esc(f.cliente || "Cliente para levantamento")}</h1>
      <p>Cadastro inicial feito pela supervisão antes do levantamento.</p>
    </section>
    <div class="section-title"><h2>Dados básicos do cliente</h2></div>
    <form id="reportForm" class="card">
      <div class="form-grid">
        ${input("cliente","Cliente / Projeto",f.cliente,true)}
        ${input("contato","Contato",f.contato)}
        ${input("telefone","Tel/Cel",f.telefone)}
        ${input("email","E-mail",f.email,false,"email")}
        ${input("cnpj","CNPJ",f.cnpj)}
        ${input("inscricaoEstadual","Inscrição Estadual",f.inscricaoEstadual)}
        ${userSelect("_assignedImplantadorUsername","Implantador responsável",f._assignedImplantadorUsername)}
      </div>
    </form>
    <div class="footer-actions no-print">
      <button class="btn green" data-action="save-client">${editingExisting ? "Salvar alterações" : "Salvar e enviar para levantamento"}</button>
    </div>`);
  bindInputs();
}
function renderSurvey(payload) {
  state.editing = cloneData(payload.payload ? payload.payload : payload);
  state.editing.reportId = payload.id || payload.reportId || state.editing.reportId;
  state.surveyStep = 0;
  drawSurvey();
}
function surveyTabTitle(title) {
  if (title.includes("Levantamento")) return "Identificação";
  if (title.includes("entrada")) return "Entrada";
  if (title.includes("saída")) return "Saída";
  return title;
}
function drawSurvey() {
  const p = state.editing, f = p.report.fields;
  const current = Math.min(Math.max(state.surveyStep || 0, 0), surveySections.length - 1);
  state.surveyStep = current;
  const [title, fields] = surveySections[current];
  const progress = ((current + 1) / surveySections.length) * 100;
  const tabs = surveySections.map(([sectionTitle], index) =>
    `<button type="button" class="step ${index === current ? "active" : ""}" data-action="survey-step" data-step="${index}">${esc(surveyTabTitle(sectionTitle))}</button>`
  ).join("");
  const footerActions = [
    current > 0 ? `<button class="btn secondary" data-action="survey-prev">Anterior</button>` : "",
    `<button class="btn secondary" data-action="save-survey-draft">Salvar levantamento</button>`,
    current < surveySections.length - 1
      ? `<button class="btn" data-action="survey-next">Próximo</button>`
      : `<button class="btn green" data-action="complete-survey">Concluir levantamento</button>`
  ].filter(Boolean).join("");
  const clientName = f.cliente || f.empresa || "Levantamento de dados";
  shell(`<div class="section-title survey-title page-heading">
      <h2>${esc(clientName)}</h2>
      <div class="spacer"></div>
      <button class="btn secondary page-back" data-action="dashboard">Voltar</button>
    </div>
    <nav class="steps survey-steps">${tabs}</nav>
    <section class="card survey-step-card">
      <div class="survey-step-heading">
        <div>
          <span>LEVANTAMENTO</span>
          <h3>${esc(title)}</h3>
          <p>Etapa ${current + 1} de ${surveySections.length} · campos de múltipla escolha primeiro.</p>
        </div>
        <strong>${Math.round(progress)}%</strong>
      </div>
      <div class="progress survey-progress"><i style="width:${progress}%"></i></div>
      <form id="reportForm" class="form-grid survey-grid">
        ${orderedSurveyFields(fields).map(item => surveyField(item, f)).join("")}
      </form>
    </section>
    <div class="footer-actions no-print">
      ${footerActions}
    </div>`);
  bindInputs();
}
function orderedSurveyFields(fields) {
  return [...fields].sort((a, b) => (a[2] === "choice" ? 0 : 1) - (b[2] === "choice" ? 0 : 1));
}
function surveyField(item, f) {
  const [key, label, type, extra] = item;
  const definition = surveyDefinition(item);
  const ui = requirementUi(definition, state.editing.report);
  const value = f[key] || f[key === "empresa" ? "cliente" : key] || "";
  if (type === "choice") return choice(key, label, value, extra, definition);
  if (type === "textarea") return textarea(key, label, value, extra || 3, definition);
  if (type === "photo") return `<div class="field dynamic-photo${ui.className}"><label>${esc(label)} ${ui.label}</label>${ui.reason}${value ? `<img src="${esc(value)}" alt="${esc(label)}">` : ""}<input type="file" accept="image/*" capture="environment" data-survey-photo-field="${esc(key)}"></div>`;
  return input(key, label, value, false, type || "text", definition);
}
function drawEditor() {
  const p = state.editing, r = p.report, f = r.fields;
  const canGeneratePdf = isConcludedDeliveryStatus(r.deliveryStatus);
  const footerActions = [
    state.step > 0 ? `<button class="btn secondary" data-action="prev">Anterior</button>` : "",
    state.step < steps.length - 1 ? `<button class="btn secondary" data-action="next">Próximo</button>` : "",
    state.step === steps.length - 1 ? `<button class="btn secondary" data-action="save-only">Salvar apenas</button>` : "",
    state.step === steps.length - 1 && canGeneratePdf ? `<button class="btn green" data-action="save-print">Gerar relatório PDF</button>` : ""
  ].filter(Boolean).join("");
  shell(`<section class="hero editor-hero has-top-action">
      <button class="btn secondary page-back" data-action="dashboard">Voltar</button>
      <span>RELATÓRIO EM PREENCHIMENTO</span>
      <h1>${esc(f.cliente || "Novo relatório")}</h1>
      <p>Etapa ${state.step + 1} de ${steps.length} · ${esc(steps[state.step][0])}</p>
      <div class="progress"><i style="width:${((state.step + 1) / steps.length) * 100}%"></i></div>
    </section>
    <div class="section-title"><h2>${esc(f.cliente || "Novo relatório")}</h2></div>
    <nav class="steps">${steps.map((s,i)=>`<button class="step ${i===state.step?"active":""}" data-action="step" data-step="${i}">${s[0]}</button>`).join("")}</nav>
    <form id="reportForm" class="card">${stepHtml(steps[state.step][1], p)}</form>
    <div class="footer-actions no-print">
      ${footerActions}
    </div>`);
  bindInputs();
}
function stepHtml(name, p) {
  const f = p.report.fields;
  const statusUi = requirementUi(fixedRequirement("deliveryStatus"), p.report);
  if (name === "ident") return `<div class="form-grid">
    ${input("cliente","Cliente / Projeto",f.cliente,true)}${input("consultor","Consultor",f.consultor)}
    ${input("usuariosTga","Usuários cadastrados",f.usuariosTga)}${input("inicio","Início",f.inicio,false,"date")}
    ${input("termino","Término",f.termino,false,"date")}${input("diasContratados","Dias contratados",f.diasContratados,false,"number")}
    ${input("diasUtilizados","Dias utilizados",f.diasUtilizados,false,"number")}</div><h3>Módulos contratados</h3>${checks(S.modules.map(i=>["dados","modulos",i]), p)}`;
  if (name === "technical") return groups("tecnico", S.technical, p) + `<div class="form-grid">${input("tipoCertificado","Tipo do certificado",f.tipoCertificado)}${input("qtdWorkflow","Qtd. Workflow",f.qtdWorkflow)}</div>${textarea("observacoesTecnicas","Observações técnicas",f.observacoesTecnicas)}`;
  if (name === "stock") return groups("estoque", S.stock, p);
  if (name === "finance") return groups("financeiro", S.finance, p);
  if (name === "fiscal") return groups("fiscal", S.fiscal, p);
  return `${textarea("servicosExecutados","Serviços executados",f.servicosExecutados)}
    <div class="field${statusUi.className}"><label>Status ${statusUi.label}</label>${statusUi.reason}<select data-field="deliveryStatus" required><option></option><option ${p.report.deliveryStatus==="Concluído"?"selected":""}>Concluído</option><option ${p.report.deliveryStatus==="Concluído, mas deseja novos serviços"?"selected":""}>Concluído, mas deseja novos serviços</option><option ${p.report.deliveryStatus==="Não concluído"?"selected":""}>Não concluído</option></select></div>
    ${textarea("pendencias","Pendências",f.pendencias)}
    <div class="form-grid signature-grid"><div>${signature("assinaturaAnalistaImagem","Assinatura do técnico",f.assinaturaAnalistaImagem)}</div><div>${signature("assinaturaClienteImagem","Assinatura do cliente",f.assinaturaClienteImagem)}</div></div>
    <div class="field"><label>Anexos / fotos</label><input type="file" id="files" multiple accept="image/*,.pdf"><input type="file" id="camera" accept="image/*" capture="environment"></div>
    <div class="attachments">${(p.report.attachments||[]).map(a=>`<div class="thumb">${a.uri?.startsWith("data:image")?`<img src="${a.uri}">`:""}<small>${esc(a.name)}</small></div>`).join("")}</div>`;
}
function input(key,label,value="",required=false,type="text",definition=null,reportData=null){const ui=requirementUi(definition||fixedRequirement(key),reportData||state.editing?.report||{});return `<div class="field${ui.className}"><label>${esc(label)} ${ui.label}</label>${ui.reason}<input data-field="${esc(key)}" value="${esc(value)}" type="${esc(type)}" ${(required||ui.active)?"required":""}></div>`}
function userSelect(key,label,value=""){
  const options = state.users.map(user => `<option value="${esc(user.username)}" ${user.username === value ? "selected" : ""}>${esc(user.full_name || user.fullName || user.username)} (${esc(user.username)})</option>`).join("");
  return `<div class="field"><label>${esc(label)}</label><select data-field="${key}" required><option value="">Selecione o implantador</option>${options}</select></div>`;
}
function choice(key,label,value="",options=[],definition=null,reportData=null){const ui=requirementUi(definition||fixedRequirement(key),reportData||state.editing?.report||{});return `<div class="field survey-choice${ui.className}"><label>${esc(label)} ${ui.label}</label>${ui.reason}<div class="choice-row">${options.map(option=>`<label><input type="radio" name="${esc(key)}" data-field="${esc(key)}" value="${esc(option)}" ${value===option?"checked":""}>${esc(option)}</label>`).join("")}</div></div>`}
function textarea(key,label,value="",minLines=3,definition=null,reportData=null){const ui=requirementUi(definition||fixedRequirement(key),reportData||state.editing?.report||{});return `<div class="field${ui.className}"><label>${esc(label)} ${ui.label}</label>${ui.reason}<textarea data-field="${key}" rows="${minLines}" ${ui.active?"required":""}>${esc(value)}</textarea></div>`}
function dynamicReiField(scope, group, item, p) {
  const label = schemaItemLabel(item), type = schemaItemType(item), key = schemaFieldKey(scope, group, item);
  const value = schemaItemKeys(scope, group, item).map(candidate => p.report.fields?.[candidate] || "").find(Boolean) || "";
  const ui = requirementUi(item, p.report);
  if (type === "choice") return choice(key, label, value, schemaItemOptions(item).length ? schemaItemOptions(item) : ["Sim", "Não"], item, p.report);
  if (type === "textarea") return textarea(key, label, value, 3, item, p.report);
  if (type === "photo") return `<div class="field dynamic-photo${ui.className}"><label>${esc(label)} ${ui.label}</label>${ui.reason}${value ? `<img src="${esc(value)}" alt="${esc(label)}">` : ""}<input type="file" accept="image/*" capture="environment" data-photo-field="${esc(key)}"></div>`;
  return input(key, label, value, false, type || "text", item, p.report);
}
function groups(scope, groupList, p){return groupList.map(([g,items])=>{
  const activeItems = items.filter(item => definitionApplicable(p.report, item));
  const checklist = activeItems.filter(item => schemaItemType(item) === "checkbox");
  const fields = activeItems.filter(item => schemaItemType(item) !== "checkbox");
  return `<div class="group"><h3>${esc(g)}</h3>${checklist.length ? checks(checklist.map(i=>[scope,g,i]),p) : ""}${fields.length ? `<div class="form-grid dynamic-rei-fields">${fields.map(item => dynamicReiField(scope,g,item,p)).join("")}</div>` : ""}</div>`;
}).join("")}
function checks(items, p){const set=new Set(p.report.checks||[]);return `<div class="check-grid">${items.map(([s,g,i])=>{const label=schemaItemLabel(i),k=S.key(s,g,i),aliases=schemaItemKeys(s,g,i),checked=aliases.some(key=>set.has(key)),ui=requirementUi(i,p.report);return `<label class="check${ui.className}"><input type="checkbox" data-check="${esc(k)}" data-check-aliases="${esc(encodeURIComponent(JSON.stringify(aliases)))}" ${checked?"checked":""}><span>${esc(label)} ${ui.label}${ui.reason}</span></label>`}).join("")}</div>`}
function signature(key,label,value){const definition=fixedRequirement(key),ui=requirementUi(definition,state.editing?.report||{});return `<div class="field signature-field${ui.className}"><div class="signature-heading"><label>${esc(label)}</label>${ui.label}</div>${ui.reason}<canvas class="signature" data-signature="${key}" data-value="${esc(value||"")}" aria-label="${esc(label)}"></canvas><button type="button" class="btn secondary signature-clear" data-action="clear-signature" data-key="${key}">Limpar assinatura</button></div>`}
function bindInputs() {
  $$("[data-field]").forEach(el => {
    const updateField = () => {
    const k = el.dataset.field;
    if (k === "deliveryStatus") state.editing.report.deliveryStatus = el.value;
      else {
        state.editing.report.fields[k] = el.value;
        if (k === "_assignedImplantadorUsername") {
          const selected = state.users.find(user => user.username === el.value);
          state.editing.report.fields._assignedImplantadorName = selected ? (selected.full_name || selected.fullName || selected.username) : "";
        }
      }
    };
    el.oninput = updateField;
    el.onchange = updateField;
  });
  $$("[data-check]").forEach(el => el.onchange = () => {
    const set = new Set(state.editing.report.checks || []);
    const aliases = (() => {
      try { return JSON.parse(decodeURIComponent(el.dataset.checkAliases || "")); }
      catch { return [el.dataset.check]; }
    })();
    if (el.checked) set.add(el.dataset.check); else aliases.forEach(key => set.delete(key));
    state.editing.report.checks = [...set];
  });
  $$("[data-photo-field]").forEach(el => el.onchange = async () => {
    const file = el.files?.[0];
    if (!file) return;
    state.editing.report.fields[el.dataset.photoField] = (await fileToAttachment(file)).uri;
    drawEditor();
  });
  $$("[data-survey-photo-field]").forEach(el => el.onchange = async () => {
    const file = el.files?.[0];
    if (!file) return;
    state.editing.report.fields[el.dataset.surveyPhotoField] = (await fileToAttachment(file)).uri;
    drawSurvey();
  });
  $$("#files,#camera").forEach(el => el && (el.onchange = async () => {
    const files = await Promise.all([...el.files].map(fileToAttachment));
    state.editing.report.attachments = [...(state.editing.report.attachments||[]), ...files];
    drawEditor();
  }));
  $$("canvas.signature").forEach(setupSignature);
}
function fileToAttachment(file) {
  return new Promise(resolve => {
    const reader = new FileReader();
    reader.onload = () => resolve({
      name: file.name,
      mimeType: file.type || "application/octet-stream",
      uri: reader.result
    });
    reader.readAsDataURL(file);
  });
}

function setupSignature(canvas) {
  const key = canvas.dataset.signature;
  const context = canvas.getContext("2d");
  let drawing = false;

  const resize = () => {
    const previousValue = canvas.dataset.value;
    canvas.width = canvas.clientWidth * devicePixelRatio;
    canvas.height = canvas.clientHeight * devicePixelRatio;
    context.scale(devicePixelRatio, devicePixelRatio);
    context.lineWidth = 2;
    context.lineCap = "round";
    if (previousValue) {
      const image = new Image();
      image.onload = () => context.drawImage(image, 0, 0, canvas.clientWidth, canvas.clientHeight);
      image.src = previousValue;
    }
  };
  const position = event => {
    const bounds = canvas.getBoundingClientRect();
    const pointer = event.touches?.[0] || event;
    return { x: pointer.clientX - bounds.left, y: pointer.clientY - bounds.top };
  };
  const start = event => {
    drawing = true;
    const point = position(event);
    context.beginPath();
    context.moveTo(point.x, point.y);
    event.preventDefault();
  };
  const move = event => {
    if (!drawing) return;
    const point = position(event);
    context.lineTo(point.x, point.y);
    context.stroke();
    state.editing.report.fields[key] = canvas.toDataURL("image/png");
    event.preventDefault();
  };
  const end = () => {
    drawing = false;
  };

  resize();
  canvas.onmousedown = canvas.ontouchstart = start;
  canvas.onmousemove = canvas.ontouchmove = move;
  canvas.onmouseup = canvas.onmouseleave = canvas.ontouchend = end;
}

function renderViewer(r, options = {}) {
  state.viewing = r;
  state.viewingSurveyReadOnly = options.surveyReadOnly === true;
  const f = r.report.fields, checks = new Set(r.report.checks||[]);
  const evaluationScore = score(r);
  const surveyLike = state.viewingSurveyReadOnly || stage(r) === "levantamento_pendente";
  const actions = [
    surveyLike ? `<button class="btn secondary" data-action="chat" data-id="${esc(r.id)}">Assistente</button>` : "",
    !state.viewingSurveyReadOnly && state.user.role === "supervisor" && stage(r) === "levantamento_pendente" ? `<button class="btn" data-action="edit-client" data-id="${esc(r.id)}">Editar cadastro</button>` : "",
    !state.viewingSurveyReadOnly && state.user.role !== "supervisor" ? `<button class="btn" data-action="edit" data-id="${esc(r.id)}">Editar</button>` : "",
    !state.viewingSurveyReadOnly && state.user.role === "supervisor" && isReadyForSupervisorEvaluation(r) && !hasEvaluation(r) ? `<button class="btn green" data-action="evaluate" data-id="${esc(r.id)}">Avaliar</button>` : "",
    (state.viewingSurveyReadOnly || (!surveyLike && isReadyForSupervisorEvaluation(r))) ? `<button class="btn" data-action="print">${state.viewingSurveyReadOnly ? "Imprimir relatório" : "Reimprimir PDF"}</button>` : ""
  ].filter(Boolean).join("");
  shell(`<section class="hero viewer has-top-action"><button class="btn secondary page-back" data-action="dashboard">Voltar</button><h1>${esc(r.client || f.cliente)}</h1><p>${fmtDate(r.completed_at)} · ${esc(r.consultant || f.consultor || "")}</p></section>
    <section class="card viewer">${dl([["Cliente / Projeto",f.cliente],["Implantador responsável",assignedName(r)],["Contato",f.contato],["Tel/Cel",f.telefone],["E-mail",f.email],["CNPJ",f.cnpj],["Consultor",f.consultor],["Início",f.inicio],["Término",f.termino],["Status",r.report.deliveryStatus],["Serviços executados",f.servicosExecutados],["Pendências",f.pendencias]])}</section>
    ${hasEvaluation(r)?supervisionEvaluationCard(r, evaluationScore, checks):""}
    ${state.viewingSurveyReadOnly ? surveyViewerHtml(f) : selectedReportData(checks, f, r.report)}
    ${surveyLike ? printSurveyHtml(r) : printReportHtml(r)}
    ${actions ? `<div class="footer-actions no-print">${actions}</div>` : ""}`);
}

const CHAT_SKILLS = [
  ["erp-levantamento-diagnostico", "Levantamento e diagnóstico", "Entrevistas, processos, requisitos e riscos."],
  ["erp-conversao-auditoria", "Conversão e auditoria", "Excel, CSV, XML, SPED e divergências."],
  ["erp-parametrizacao-brasil", "Fiscal e estoque", "Regime, CFOP, CST/CSOSN, compras e estoque."],
  ["erp-testes-go-live-suporte", "Testes e go-live", "UAT, cutover, rollback e suporte assistido."]
];

async function openChat(report) {
  if (!report) return;
  const skillCode = state.chat?.reportId === report.id ? state.chat.skillCode : CHAT_SKILLS[0][0];
  try {
    const session = await api(`/api/levantamentos/${encodeURIComponent(report.id)}/chat/sessoes`, { method: "POST", body: JSON.stringify({ skill_code: skillCode }) });
    state.chat = { reportId: report.id, sessionId: session.id, skillCode: session.skillCode, messages: [] };
    renderChatModal(report);
    await refreshChatMessages(report);
  } catch (error) {
    state.chat = { reportId: report.id, sessionId: "", skillCode, messages: [], error: error.message };
    renderChatModal(report);
  }
}

async function refreshChatMessages(report) {
  if (!state.chat?.sessionId) return;
  const messages = await api(`/api/levantamentos/${encodeURIComponent(report.id)}/chat/mensagens?sessionId=${encodeURIComponent(state.chat.sessionId)}`);
  state.chat.messages = messages;
  renderChatModal(report);
}

function renderChatModal(report) {
  const selected = state.chat?.skillCode || CHAT_SKILLS[0][0];
  const content = (message) => {
    if (message.role !== "assistant") return String(message.content || "");
    const value = typeof message.content === "object" ? message.content : (() => { try { return JSON.parse(message.content); } catch { return { answer: message.content }; } })();
    const block = (title, items) => Array.isArray(items) && items.length ? `<div class="chat-structured"><strong>${title}</strong><ul>${items.map(item => `<li>${esc(typeof item === "object" ? (item.label || item.description || JSON.stringify(item)) : item)}</li>`).join("")}</ul></div>` : "";
    return `<p>${esc(value.answer || "")}</p>${block("Pendências", value.pending_items)}${block("Riscos", value.risks)}${block("Perguntas", value.questions)}`;
  };
  const messages = (state.chat?.messages || []).map(message => `<article class="chat-message ${message.role === "user" ? "mine" : "assistant"}"><small>${message.role === "user" ? "Você" : "Assistente"}</small><div>${content(message)}</div>${message.status === "PENDING" ? `<em>Aguardando sincronização</em>` : ""}${message.status === "FAILED" ? `<em class="error-text">${esc(message.errorMessage || "Falha ao enviar")}</em>` : ""}</article>`).join("");
  document.querySelector(".chat-modal")?.remove();
  document.body.insertAdjacentHTML("beforeend", `<div class="modal no-print chat-modal" role="dialog" aria-modal="true"><section class="card chat-card"><header class="row"><div><h2>Assistente contextual</h2><p class="muted">${esc(report.client || "Levantamento")}</p></div><button class="btn secondary" data-action="close-chat">Fechar</button></header><div class="chat-skills">${CHAT_SKILLS.map(([code, title, description]) => `<button class="btn secondary ${code === selected ? "active" : ""}" data-action="chat-skill" data-skill="${code}" title="${esc(description)}">${esc(title)}</button>`).join("")}</div><p class="chat-note">O assistente usa somente o contexto deste levantamento. Mensagens sem conexão ficam pendentes no dispositivo Android.</p><div class="chat-history">${messages || `<p class="muted">Inicie a conversa com uma pergunta sobre este cliente.</p>`}</div><form class="chat-compose" data-chat-form><textarea name="content" rows="2" maxlength="12000" placeholder="Digite sua dúvida..." required></textarea><button class="btn" type="submit">Enviar</button></form>${state.chat?.error ? `<p class="error chat-error">${esc(state.chat.error)}</p>` : ""}</section></div>`);
}

async function sendWebChat(report) {
  const form = $("[data-chat-form]");
  const content = form?.content?.value?.trim();
  if (!content || !state.chat?.sessionId) return;
  const button = form.querySelector("button");
  button.disabled = true;
  try {
    const localIdempotencyKey = crypto.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`;
    await api(`/api/levantamentos/${encodeURIComponent(report.id)}/chat/mensagens`, { method: "POST", body: JSON.stringify({ sessionId: state.chat.sessionId, localIdempotencyKey, content }) });
    await refreshChatMessages(report);
  } catch (error) {
    state.chat.error = error.message;
    renderChatModal(report);
  }
}
function surveyViewerHtml(fields) {
  return surveySections.map(([title, sectionFields]) => {
    const values = sectionFields.map(([key, label, type]) => {
      const value = fields[key] || (key === "empresa" ? fields.cliente : "");
      if (!value) return "";
      return `<div class="dynamic-value"><small>${esc(label)}</small>${type === "photo" ? `<img src="${esc(value)}" alt="${esc(label)}">` : `<b class="preserve-newlines">${esc(value)}</b>`}</div>`;
    }).filter(Boolean).join("");
    return values ? `<section class="card viewer-survey-section"><h2>${esc(title)}</h2><div class="dynamic-values">${values}</div></section>` : "";
  }).join("");
}
function dl(items){return `<dl>${items.map(([k,v])=>`<dt>${esc(k)}</dt><dd class="preserve-newlines">${esc(v||"Não informado")}</dd>`).join("")}</dl>`}
function selectedReportData(checks, fields, reportData = null) {
  const topics = [
    selectedModules(checks),
    selected(S.technical, "tecnico", checks, fields, reportData),
    selected(S.stock, "estoque", checks, fields, reportData),
    selected(S.finance, "financeiro", checks, fields, reportData),
    selected(S.fiscal, "fiscal", checks, fields, reportData)
  ].join("");
  return `<section class="card filled-report-card"><h2>Dados preenchidos no R.E.I.</h2>${topics ? `<div class="viewer-topic-grid">${topics}</div>` : `<p class="muted viewer-empty">Nenhum dado informado.</p>`}</section>`;
}
function selectedModules(checks) {
  const modules = S.modules.filter(item => schemaItemKeys("dados", "modulos", item).some(key => checks.has(key)))
    .map(item => `<span class="pill">${esc(schemaItemLabel(item))}</span>`).join("");
  return modules ? `<article class="viewer-topic-card viewer-modules-card"><h3>Módulos contratados</h3><div class="viewer-topic-items">${modules}</div></article>` : "";
}
function selected(groups, scope, checks, fields = {}, reportData = null) {
  const content = groups.map(([g,items]) => {
    const activeItems = reportData ? items.filter(i => definitionApplicable(reportData, i)) : items;
    const marked = activeItems.filter(i => schemaItemType(i) === "checkbox" && schemaItemKeys(scope,g,i).some(key => checks.has(key))).map(i => `<span class="pill">${esc(schemaItemLabel(i))}</span>`).join(" ");
    const values = activeItems.filter(i => schemaItemType(i) !== "checkbox").map(i => {
      const value = schemaItemKeys(scope,g,i).map(key => fields[key]).find(Boolean);
      if (!value) return "";
      return `<div class="dynamic-value"><small>${esc(schemaItemLabel(i))}</small>${schemaItemType(i) === "photo" ? `<img src="${esc(value)}" alt="${esc(schemaItemLabel(i))}">` : `<b class="preserve-newlines">${esc(value)}</b>`}</div>`;
    }).filter(Boolean).join("");
    return marked || values ? `<article class="viewer-topic-card"><h3>${esc(g)}</h3>${marked ? `<div class="viewer-topic-items">${marked}</div>` : ""}${values ? `<div class="dynamic-values">${values}</div>` : ""}</article>` : "";
  }).filter(Boolean).join("");
  return content;
}
function selectedCards(groups, scope, checks) {
  const items = groups.flatMap(([g, list]) => list.filter(i => schemaItemType(i) === "checkbox" && schemaItemKeys(scope,g,i).some(key => checks.has(key))).map(i => [g, schemaItemLabel(i)]));
  if (!items.length) return `<p class="muted">Nenhum item marcado.</p>`;
  return `<div class="evaluation-checks">${items.map(([g, i]) => `<div class="evaluation-check"><small>${esc(g)}</small><span>${esc(i)}</span></div>`).join("")}</div>`;
}
function supervisionEvaluationCard(r, evaluationScore, checks) {
  const f = r.report.fields || {};
  return `<section class="card evaluation-card">
    <div class="evaluation-title">
      <div>
        <h2>Avaliação da supervisão</h2>
        <p class="muted">Resultado da análise feita pelo supervisor responsável.</p>
      </div>
      <div class="evaluation-score">
        <strong>${evaluationScore == null ? "-" : evaluationScore.toFixed(1)}</strong>
        <span>/10</span>
      </div>
    </div>
    <div class="evaluation-summary">
      <div><small>Supervisor</small><b>${esc(f._supervisorName || "Não informado")}</b></div>
      <div><small>Parecer</small><b>${esc(r.report.rating || "Não informado")}</b></div>
    </div>
    <h3>Checklist avaliado</h3>
    ${selectedCards(S.supervision, "supervisao", checks)}
  </section>`;
}

function printReportHtml(r) {
  const data = r.report || {}, f = data.fields || {}, checks = new Set(data.checks || []);
  const evScore = score(r);
  return `<article class="print-report">
    <header class="print-header">
      <div class="print-brand"><img src="/web/assets/logo_dubrasil_blue.png" alt="DuBrasil Soluções"></div>
      <div><h1>RELATÓRIO DE ENTREGA DE IMPLANTAÇÃO</h1><p>Sistema de Gestão TGA • R.E.I.</p></div>
    </header>
    ${pSection("MÓDULOS CONTRATADOS")}
    ${pChecklist(S.modules, item => schemaItemKeys("dados", "modulos", item), checks, 3)}
    ${pInfoTable([
      ["Cliente / Projeto", f.cliente], ["Consultor de implantação", f.consultor],
      ["Usuários cadastrados no TGA", f.usuariosTga], ["Início", f.inicio],
      ["Término", f.termino], ["Dias contratados", f.diasContratados],
      ["Dias utilizados", f.diasUtilizados]
    ])}
    <div class="print-support"><b>CONTATOS COM O SUPORTE TÉCNICO</b><span>suportetga@dubrasilsolucoes.com.br • (34) 3322-8500</span></div>
    ${pSection("PREENCHIMENTO TÉCNICO")}
    ${pGroups("tecnico", S.technical, checks, f, data)}
    ${pInfoTable([["Tipo do certificado digital", f.tipoCertificado], ["Quantidade de usuários no Workflow", f.qtdWorkflow]])}
    ${pParagraph("Observações técnicas", f.observacoesTecnicas)}
    ${pSection("MÓDULO ESTOQUE")}${pGroups("estoque", S.stock, checks, f, data)}
    ${pSection("MÓDULO FINANCEIRO")}${pGroups("financeiro", S.finance, checks, f, data)}
    ${pSection("MÓDULO FISCAL E RELATÓRIOS")}${pGroups("fiscal", S.fiscal, checks, f, data)}
    ${pSection("ENTREGA DA IMPLANTAÇÃO")}
    ${pParagraph("Descritivo dos serviços executados", f.servicosExecutados)}
    ${pStatus(data.deliveryStatus)}
    ${pParagraph("Pendências pós-implantação", f.pendencias)}
    ${pSection("ASSINATURAS")}
    ${pSignatures(f)}
    ${hasEvaluation(r) ? `${pSection("AVALIAÇÃO DA SUPERVISÃO")}${pInfoTable([["Supervisor", f._supervisorName], ["Nota", evScore == null ? f._supervisionScore : `${evScore.toFixed(1)}/10`], ["Parecer / observação", data.rating]])}${pGroups("supervisao", S.supervision, checks, f, data)}` : ""}
    ${(data.attachments || []).length ? `${pSection("EVIDÊNCIAS E ANEXOS")}${pAttachments(data.attachments)}` : ""}
    <footer class="print-footer">DuBrasil Soluções • suporte: (34) 3322-8500</footer>
  </article>`;
}
function printSurveyHtml(r) {
  const data = r.report || {}, f = data.fields || {};
  return `<article class="print-report">
    <header class="print-header">
      <div class="print-brand"><img src="/web/assets/logo_dubrasil_blue.png" alt="DuBrasil Soluções"></div>
      <div><h1>LEVANTAMENTO DE DADOS</h1><p>Sistema de Gestão TGA • Pré-implantação</p></div>
    </header>
    ${pSection("IDENTIFICAÇÃO DO CLIENTE")}
    ${pInfoTable([
      ["Cliente / Projeto", f.cliente || f.empresa], ["Contato", f.contato],
      ["Tel/Cel", f.telefone], ["E-mail", f.email],
      ["CNPJ", f.cnpj], ["Inscrição Estadual", f.inscricaoEstadual],
      ["Data e hora do levantamento", formatSurveyDateTime(f._surveyScheduledAt)], ["Implantador responsável", assignedName(r) || f.analistaLevantamento]
    ])}
    ${surveySections.slice(1).map(([title, fields]) => `${pSection(title.toUpperCase())}${pSurveyFields(fields, f)}`).join("")}
    ${pSection("ANOTAÇÕES GERAIS")}
    ${pParagraph("Presentes na reunião", f.presentesReuniao)}
    <footer class="print-footer">DuBrasil Soluções • suporte: (34) 3322-8500</footer>
  </article>`;
}
function pSurveyFields(fields, f) {
  const typedFields = fields.map(item => ({ item, definition: surveyDefinition(item) }));
  const choices = typedFields.filter(({ definition }) => definition.type === "choice");
  const photos = typedFields.filter(({ definition }) => definition.type === "photo");
  const texts = typedFields.filter(({ definition }) => !["choice", "photo"].includes(definition.type));
  return `${choices.length ? `<div class="print-info">${choices.map(({ definition }) => `<div><small>${esc(definition.label).toUpperCase()}</small><b class="preserve-newlines">${esc(f[definition.key] || "—")}</b></div>`).join("")}</div>` : ""}
    ${photos.map(({ definition }) => `<div class="print-photo"><small>${esc(definition.label).toUpperCase()}</small>${f[definition.key] ? imageTag(f[definition.key], definition.label) : `<b>—</b>`}</div>`).join("")}
    ${texts.map(({ definition }) => definition.type === "textarea" ? pParagraph(definition.label, f[definition.key]) : pInfoTable([[definition.label, f[definition.key]]])).join("")}`;
}
function formatSurveyDateTime(value) {
  if (!value) return "";
  const match = String(value).match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/);
  return match ? `${match[3]}/${match[2]}/${match[1]} ${match[4]}:${match[5]}` : value;
}
function pSection(title) { return `<h2 class="print-section">${esc(title)}</h2>`; }
function pInfoTable(items) {
  return `<div class="print-info">${items.map(([k,v]) => `<div><small>${esc(k).toUpperCase()}</small><b class="preserve-newlines">${esc(v || "—")}</b></div>`).join("")}</div>`;
}
function pChecklist(items, keyFor, checks, cols = 2) {
  return `<div class="print-checks cols-${cols}">${items.map(item => { const value=keyFor(item),keys=Array.isArray(value)?value:[value];return `<div><span class="${keys.some(key=>checks.has(key)) ? "on" : ""}"></span>${esc(schemaItemLabel(item))}</div>`; }).join("")}</div>`;
}
function pGroups(scope, groups, checks, fields = {}, reportData = null) {
  return groups.map(([group, items]) => {
    const effectiveReport = reportData || state.viewing?.report || state.editing?.report || null;
    const activeItems = effectiveReport ? items.filter(item => definitionApplicable(effectiveReport, item)) : items;
    const checklist = activeItems.filter(item => schemaItemType(item) === "checkbox");
    const typed = activeItems.filter(item => schemaItemType(item) !== "checkbox");
    const typedValues = typed.filter(item => schemaItemType(item) !== "photo").map(item => {
      const value = schemaItemKeys(scope,group,item).map(key => fields[key]).find(Boolean);
      return [schemaItemLabel(item), value];
    });
    const photos = typed.filter(item => schemaItemType(item) === "photo").map(item => {
      const value = schemaItemKeys(scope,group,item).map(key => fields[key]).find(Boolean);
      return value ? `<figure><figcaption>${esc(schemaItemLabel(item))}</figcaption>${imageTag(value, schemaItemLabel(item))}</figure>` : "";
    }).filter(Boolean).join("");
    return `<h3 class="print-subsection">${esc(group).toUpperCase()}</h3>${checklist.length ? pChecklist(checklist, item => schemaItemKeys(scope, group, item), checks, 2) : ""}${typedValues.length ? pInfoTable(typedValues) : ""}${photos ? `<div class="print-attachments">${photos}</div>` : ""}`;
  }).join("");
}
function pParagraph(label, value) {
  return `<div class="print-paragraph"><small>${esc(label).toUpperCase()}</small><p>${esc(value || "Não informado")}</p></div>`;
}
function pStatus(selected) {
  const options = ["Concluído", "Concluído, mas deseja novos serviços", "Não concluído"];
  return `<div class="print-status"><small>POSICIONAMENTO DA ENTREGA</small><div>${options.map(option => `<span><i class="${selected === option ? "on" : ""}"></i>${esc(option)}</span>`).join("")}</div></div>`;
}
function pSignatures(f) {
  const sig = (uri, label, detail) => `<div class="print-signature">${imageTag(uri, label)}<hr><b>${esc(label)}</b><small>${esc(detail || " ")}</small></div>`;
  return `<div class="print-signatures">${sig(f.assinaturaAnalistaImagem, "TÉCNICO DE IMPLANTAÇÃO", "DUBRASIL SOLUÇÕES")}${sig(f.assinaturaClienteImagem, "RESPONSÁVEL PELO CLIENTE", f.cliente)}</div><p class="print-note">Ao assinar, as partes confirmam o recebimento das informações e o posicionamento descrito neste relatório.</p>`;
}
function pAttachments(items) {
  return `<div class="print-attachments">${items.map((item, index) => isPrintableImage(item)
    ? `<figure><figcaption>Evidência ${index + 1} • ${esc(item.name)}</figcaption>${imageTag(item.uri, item.name)}</figure>`
    : `<div class="print-file"><b>ARQUIVO ANEXADO</b> ${esc(item.name)}</div>`).join("")}</div>`;
}
function isPrintableImage(item) {
  const uri = String(item?.uri || "");
  const mime = String(item?.mimeType || "");
  return mime.startsWith("image/") || uri.startsWith("data:image") || /\.(png|jpe?g|webp|gif)$/i.test(uri);
}
function imageTag(uri, alt = "") {
  return uri ? `<img src="${esc(uri)}" alt="${esc(alt)}" loading="eager">` : "";
}
function waitForPrintImages() {
  const images = [...document.querySelectorAll(".print-report img")];
  return Promise.all(images.map(img => {
    if (img.complete && img.naturalWidth > 0) return Promise.resolve();
    return new Promise(resolve => {
      img.onload = resolve;
      img.onerror = resolve;
      setTimeout(resolve, 1200);
    });
  }));
}
async function printCurrentReport() {
  // Aguarda imagens e assinaturas antes de abrir o diálogo nativo de impressão.
  const previousTitle = document.title;
  document.title = reportPdfTitle(state.viewing, state.viewingSurveyReadOnly);
  await waitForPrintImages();
  const restoreTitle = () => {
    document.title = previousTitle;
    window.removeEventListener("afterprint", restoreTitle);
  };
  window.addEventListener("afterprint", restoreTitle);
  window.print();
  setTimeout(restoreTitle, 3000);
}

function renderEvaluation(r) {
  const checks = new Set(r.report.checks||[]), f = r.report.fields;
  const scoreValue = Math.max(0, Math.min(10, Number(String(f._supervisionScore || "0").replace(",", ".")) || 0));
  f._supervisionScore = scoreValue.toFixed(1);
  app.insertAdjacentHTML("beforeend", `<div class="modal"><section class="card"><h2>Avaliar implantação</h2>
    <div class="field score-slider">
      <label>Nota da supervisão</label>
      <div class="score-head"><span>0</span><strong data-score-output>${scoreValue.toFixed(1)}/10</strong><span>10</span></div>
      <input type="range" min="0" max="10" step="0.5" value="${scoreValue.toFixed(1)}" data-field="_supervisionScore">
    </div>
    ${textarea("_rating","Parecer / observação",r.report.rating||"")}
    ${groups("supervisao", S.supervision, r)}
    <div class="row"><button class="btn secondary" data-action="close-modal">Cancelar</button><button class="btn green" data-action="save-evaluation" data-id="${esc(r.id)}">Salvar avaliação</button></div>
  </section></div>`);
  const modal = $(".modal");
  $$("[data-field]", modal).forEach(el => el.oninput = () => {
    if (el.dataset.field === "_rating") r.report.rating = el.value; else r.report.fields[el.dataset.field] = el.value;
    if (el.dataset.field === "_supervisionScore") {
      $("[data-score-output]", modal).textContent = `${Number(el.value).toFixed(1)}/10`;
    }
  });
  $$("[data-check]", modal).forEach(el => el.onchange = () => {
    let aliases = [el.dataset.check];
    try { aliases = JSON.parse(decodeURIComponent(el.dataset.checkAliases || "")); } catch {}
    if (el.checked) checks.add(el.dataset.check); else aliases.forEach(key => checks.delete(key));
    r.report.checks = [...checks];
  });
}
async function savePayload(payload) {
  const normalized = {
    reportId: payload.reportId || payload.id || field(payload, "_id") || newId(),
    completedAt: payload.completedAt || payload.completed_at || Date.now(),
    report: payload.report || payload.payload?.report || blankReport().report
  };
  normalized.report.fields = normalized.report.fields || {};
  normalized.report.checks = normalized.report.checks || [];
  normalized.report.attachments = normalized.report.attachments || [];
  normalized.report.deliveryStatus = normalized.report.deliveryStatus || "";
  normalized.report.rating = normalized.report.rating || "";
  normalized.report.fields._id = normalized.reportId;
  if (normalized.report.fields.empresa && !normalized.report.fields.cliente) normalized.report.fields.cliente = normalized.report.fields.empresa;
  await api("/api/reports", { method:"POST", body: JSON.stringify(normalized) });
  await loadReports();
  return normalized.reportId;
}

document.addEventListener("click", async e => {
  // A delegação mantém as ações após cada reconstrução de tela feita por shell().
  const a = e.target.closest("[data-action]"); if (!a) return;
  e.preventDefault();
  const action = a.dataset.action, id = a.dataset.id;
  try {
    if (action === "logout") { await api("/api/auth/logout",{method:"POST"}).catch(()=>{}); localStorage.removeItem("reiToken"); state.token=""; state.user=null; window.location.replace("/login"); }
    if (action === "settings") renderAccountSettings();
    if (action === "theme") {
      applyTheme(a.dataset.themeMode, true);
      $$(".theme-option").forEach(option => {
        const selected = option.dataset.themeMode === a.dataset.themeMode;
        option.classList.toggle("active", selected);
        option.setAttribute("aria-checked", String(selected));
      });
    }
    if (action === "chart-period") {
      state.chartPeriod = a.dataset.period || "6m";
      if (state.chartPeriod === "custom" && (!state.chartStart || !state.chartEnd)) {
        const now = new Date(), start = new Date(now.getFullYear(), now.getMonth() - 5, 1);
        state.chartStart = start.toISOString().slice(0, 10);
        state.chartEnd = now.toISOString().slice(0, 10);
      }
      renderDashboard();
    }
    if (action === "dashboard") { await loadUsers(); await loadReports(); await loadDeviceHeartbeats(); renderDashboard(); }
    if (action === "dashboard-home") { state.dashboardArea = "home"; state.dashboardFilter = "levantamentos"; await loadDeviceHeartbeats(); renderDashboard(); }
    if (action === "dashboard-area") { state.dashboardArea = a.dataset.area || "implantacoes"; state.dashboardFilter = state.dashboardArea === "levantamentos" ? "levantamentos" : "pendentes"; state.areaSearch = ""; state.areaStatus = "todos"; renderDashboard(); }
    if (action === "new" && state.user.role !== "supervisor") renderEditor(blankReport());
    if (action === "new-survey" && state.user.role !== "supervisor") renderSurvey(blankSurveyPayload());
    if (action === "new-client" && state.user.role === "supervisor") renderClientForm();
    if (action === "manager-open" && state.user.role === "supervisor") {
      let report = state.reports.find(item => item.id === id);
      if (!report) {
        state.reports = await api("/api/reports?full=1&limit=1000");
        report = state.reports.find(item => item.id === id);
      }
      if (report) renderViewer(report); else alert("Relatório não encontrado no histórico.");
    }
    if (action === "chat") {
      const report = state.reports.find(r => r.id === id) || state.viewing;
      await openChat(report);
    }
    if (action === "close-chat") { document.querySelector(".chat-modal")?.remove(); state.chat = null; }
    if (action === "chat-skill") {
      const report = state.reports.find(r => r.id === state.chat?.reportId) || state.viewing;
      if (report) {
        const skillCode = a.dataset.skill || CHAT_SKILLS[0][0];
        const session = await api(`/api/levantamentos/${encodeURIComponent(report.id)}/chat/sessoes`, { method: "POST", body: JSON.stringify({ skill_code: skillCode }) });
        state.chat = { reportId: report.id, sessionId: session.id, skillCode: session.skillCode, messages: [] };
        state.chat.sessionId = session.id;
        renderChatModal(report);
        await refreshChatMessages(report);
      }
    }
    if (action === "chat-send") {
      const report = state.reports.find(r => r.id === state.chat?.reportId) || state.viewing;
      if (report) await sendWebChat(report);
    }
    if (action === "open") {
      const report = state.reports.find(r => r.id === id);
      state.viewingSurveyReadOnly = false;
      if (stage(report) === "levantamento_pendente" && state.user.role !== "supervisor") renderSurvey(report);
      else if (stage(report) === "rei_pendente" && state.user.role !== "supervisor") renderEditor(report);
      else renderViewer(report);
    }
    if (action === "open-survey-completed") {
      const report = state.reports.find(r => r.id === id);
      if (report && hasCompletedSurvey(report)) renderViewer(report, { surveyReadOnly: true });
    }
    if (action === "dashboard-filter") { state.dashboardFilter = a.dataset.filter; renderDashboard(); }
    if (action === "edit-client" && state.user.role === "supervisor") renderClientForm(state.reports.find(r => r.id === id));
    if (action === "edit" && state.user.role !== "supervisor" && !state.viewingSurveyReadOnly) renderEditor(state.reports.find(r => r.id === id));
    if (action === "step") { state.step = Number(a.dataset.step); drawEditor(); }
    if (action === "prev") { state.step = Math.max(0, state.step - 1); drawEditor(); }
    if (action === "next") { state.step = Math.min(steps.length - 1, state.step + 1); drawEditor(); }
    if (action === "survey-step") { state.surveyStep = Number(a.dataset.step); drawSurvey(); }
    if (action === "survey-prev") { state.surveyStep = Math.max(0, state.surveyStep - 1); drawSurvey(); }
    if (action === "survey-next") { state.surveyStep = Math.min(surveySections.length - 1, state.surveyStep + 1); drawSurvey(); }
    if (action === "save-only") {
      if(!field(state.editing,"cliente")) return alert("Informe o cliente/projeto.");
      state.editing.report.fields._stage = "rei";
      await savePayload(state.editing);
      renderDashboard();
    }
    if (action === "save-client") {
      if(!field(state.editing,"cliente")) return alert("Informe o cliente/projeto.");
      if(!field(state.editing,"_assignedImplantadorUsername")) return alert("Selecione o implantador responsável pelo levantamento.");
      const selected = state.users.find(user => user.username === field(state.editing,"_assignedImplantadorUsername"));
      state.editing.report.fields._assignedImplantadorName = selected ? (selected.full_name || selected.fullName || selected.username) : field(state.editing,"_assignedImplantadorUsername");
      state.editing.report.fields._stage = "levantamento_pendente";
      await savePayload(state.editing);
      renderDashboard();
    }
    if (action === "save-survey-draft") {
      if(!field(state.editing,"cliente") && !field(state.editing,"empresa")) return alert("Informe a empresa/cliente.");
      state.editing.report.fields._stage = "levantamento_pendente";
      if (!state.editing.report.fields.cliente) state.editing.report.fields.cliente = state.editing.report.fields.empresa;
      await savePayload(state.editing);
      renderDashboard();
    }
    if (action === "complete-survey") {
      if (!validateBeforeAction(state.editing, "survey_completion")) return;
      state.editing.report.fields._stage = "rei_pendente";
      state.editing.report.fields._surveyCompletedAt = String(Date.now());
      if (!state.editing.report.fields.cliente) state.editing.report.fields.cliente = state.editing.report.fields.empresa;
      await savePayload(state.editing);
      renderDashboard();
    }
    if (action === "save-print") {
      if (!validateBeforeAction(state.editing, "rei_completion")) return;
      state.editing.report.fields._stage = "rei";
      const savedId = await savePayload(state.editing);
      renderViewer(state.reports.find(r => r.id === savedId));
      await printCurrentReport();
    }
    if (action === "print" && ((state.viewingSurveyReadOnly && hasCompletedSurvey(state.viewing)) || isReadyForSupervisorEvaluation(state.viewing))) {
      const phase = state.viewingSurveyReadOnly ? "survey_completion" : "rei_completion";
      if (validateBeforeAction(state.viewing, phase)) await printCurrentReport();
    }
    if (action === "evaluate") {
      const report = state.reports.find(r => r.id === id);
      if (state.user.role === "supervisor" && isReadyForSupervisorEvaluation(report)) renderEvaluation(report);
    }
    if (action === "open-evaluation-reminder") {
      const report = state.reports.find(item => item.id === id);
      $(".evaluation-reminder")?.remove();
      if (report) renderViewer(report);
    }
    if (action === "close-modal") $(".modal")?.remove();
    if (action === "close-requirements") { $(".requirements-modal")?.remove(); goToFirstRequirement(); }
    if (action === "go-first-requirement") goToFirstRequirement();
    if (action === "save-evaluation") {
      const r = state.reports.find(x => x.id === id);
      if (!validateBeforeAction(r, "supervision_submission")) return;
      r.report.fields._supervisorName = state.user.fullName || state.user.username;
      r.report.fields._supervisionReviewedAt = String(Date.now());
      await savePayload(r); $(".modal")?.remove(); renderViewer(state.reports.find(x => x.id === id));
    }
    if (action === "clear-signature") { state.editing.report.fields[a.dataset.key] = ""; drawEditor(); }
  } catch (error) {
    if (error.code === "required_items_missing" && error.requirements?.length) {
      showRequiredRequirements(error.requirements);
      return;
    }
    alert(error.message || "Não foi possível executar esta ação.");
  }
});

document.addEventListener("change", async event => {
  if (event.target.matches("[data-chart-date]")) {
    if (event.target.dataset.chartDate === "start") state.chartStart = event.target.value;
    else state.chartEnd = event.target.value;
    renderDashboard();
    return;
  }
  if (event.target.matches("[data-area-status]")) {
    state.areaStatus = event.target.value || "todos";
    applyAreaListFilters();
    return;
  }
  const control = event.target.closest("[data-manager-filter]");
  if (!control || state.user?.role !== "supervisor") return;
  const key = control.dataset.managerFilter;
  state.managerFilters[key] = control.type === "checkbox" ? control.checked : control.value;
  await loadSupervisorDashboard();
  renderDashboard();
});

document.addEventListener("submit", async event => {
  const form = event.target.closest("[data-chat-form]");
  if (!form) return;
  event.preventDefault();
  const report = state.reports.find(r => r.id === state.chat?.reportId) || state.viewing;
  if (report) await sendWebChat(report);
});

document.addEventListener("input", event => {
  if (!event.target.matches("[data-area-search]")) return;
  state.areaSearch = event.target.value || "";
  applyAreaListFilters();
});

(async function init() {
  if (await loadMe()) {
    applyTheme(storedThemeMode()); await loadSchemaOverrides(); await loadUsers(); await loadReports(); await loadDeviceHeartbeats(); renderDashboard();
    if (window.location.hash === "#settings") {
      history.replaceState(null, "", window.location.pathname + window.location.search);
      renderAccountSettings();
    }
    showDailyEvaluationReminder();
  }
  else window.location.replace("/login");
})();
