const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const app = $("#app");
const S = window.REI_SCHEMA;
const state = { token: localStorage.reiToken || "", user: null, reports: [], users: [], view: "login", editing: null, step: 0, surveyStep: 0, dashboardFilter: "levantamentos" };
const steps = [
  ["Identificação", "ident"], ["Técnico", "technical"], ["Estoque", "stock"],
  ["Financeiro", "finance"], ["Fiscal", "fiscal"], ["Entrega", "delivery"]
];
const yesNo = ["Sim", "Não"];
const surveySections = [
  ["Levantamento de dados – Implantação TGA", [
    ["empresa", "Empresa", "text"], ["contato", "Contato", "text"], ["telefone", "Tel/Cel", "text"],
    ["email", "E-mail", "email"], ["cnpj", "CNPJ", "text"], ["inscricaoEstadual", "Insc. Estadual", "text"],
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
    file: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6"/><path d="M8 13h8"/><path d="M8 17h5"/>',
    check: '<path d="M20 6L9 17l-5-5"/>'
  };
  return `<svg class="ico" viewBox="0 0 24 24" aria-hidden="true">${paths[name] || paths.file}</svg>`;
}
function fmtDate(ms) { return ms ? new Date(ms).toLocaleDateString("pt-BR") : "-"; }
function api(path, options = {}) {
  const headers = { "Accept": "application/json", ...(options.headers || {}) };
  if (state.token) headers.Authorization = `Bearer ${state.token}`;
  if (options.body && !(options.body instanceof FormData)) headers["Content-Type"] = "application/json; charset=utf-8";
  return fetch(path, { ...options, headers }).then(async r => {
    const text = await r.text();
    const data = text ? JSON.parse(text) : {};
    if (!r.ok) throw new Error(data.error || `HTTP ${r.status}`);
    return data;
  });
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
function reportPdfTitle(report) {
  const client = String(report?.client || field(report, "cliente") || "Cliente")
    .replace(/[\\/:*?"<>|\r\n]+/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/\.+$/g, "") || "Cliente";
  return `Relatorio de Entrega - ${client}`;
}
function score(report) {
  const explicit = Number(String(field(report, "_supervisionScore")).replace(",", "."));
  if (!Number.isNaN(explicit)) return Math.max(0, Math.min(10, explicit));
  const keys = S.supervisionKeys(), checks = new Set(report?.report?.checks || []);
  const done = keys.filter(k => checks.has(k)).length;
  return done ? done * 10 / keys.length : null;
}
function hasEvaluation(report) {
  const checks = new Set(report?.report?.checks || []);
  return !!(report?.report?.rating || field(report, "_supervisionScore") || S.supervisionKeys().some(k => checks.has(k)));
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
function deliveryCount(report) {
  const checks = new Set(report?.report?.checks || []);
  return S.allDeliveryKeys().filter(k => checks.has(k)).length;
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
  app.innerHTML = `<div class="app">
    <header class="topbar no-print">
            <div class=\"brand\"><img src=\"/web/assets/logo_dbs_app.png\" alt=\"DuBrasil Soluções\"></div>
      <div class="spacer"></div>
      ${state.user ? `<span class="pill">${esc(role)} · ${esc(state.user.fullName || state.user.username)}</span>
      ${state.user.role === "supervisor" ? `<a class="btn secondary" href="/admin">${icon("users")}Usuários</a>` : ""}
      <button class="btn danger" data-action="logout">${icon("logout")}Sair</button>` : ""}
    </header>
    <main class="container">${content}</main>
  </div>`;
}

function renderLogin(error = "") {
  app.innerHTML = `<main class="login">
    <section class="card">
            <div class=\"brand login-brand\"><img src=\"/web/assets/logo_dbs_app.png\" alt=\"DuBrasil Soluções\"></div>
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
      await loadUsers(); await loadReports(); renderDashboard();
    } catch (err) { renderLogin(err.message); }
  };
}

async function loadMe() {
  if (!state.token) return false;
  try { state.user = (await api("/api/auth/me")).user; return true; }
  catch { localStorage.removeItem("reiToken"); state.token = ""; return false; }
}
async function loadReports() {
  state.reports = await api("/api/reports?full=1&limit=500");
}
async function loadUsers() {
  state.users = state.user?.role === "supervisor" ? await api("/api/users?role=implantador") : [];
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

function renderDashboard() {
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
    ${state.user.role !== "supervisor" ? `<div class="footer-actions no-print"><button class="btn" data-action="new">${icon("plus")}Nova implantação</button></div>` : ""}`);
}
function metric(label, value, iconName = "file") { return `<div class="card metric"><span class="metric-icon">${icon(iconName)}</span><span>${esc(label)}</span><b>${esc(value)}</b></div>`; }
function workflowCard(group, active) {
  return `<button type="button" class="card metric workflow-card ${active ? "active" : ""}" data-action="dashboard-filter" data-filter="${esc(group.key)}">
    <span class="metric-icon">${icon(group.icon)}</span>
    <span>${esc(group.title)}</span>
    <b>${esc(group.value)}</b>
    <small>${esc(group.subtitle)}</small>
  </button>`;
}
function empty(text) { return `<div class="card muted">${esc(text)}</div>`; }
function reportSection(title, items, emptyText, subtitle = "") {
  return `<div class="section-title"><div><h2>${esc(title)}</h2>${subtitle ? `<p class="muted section-subtitle">${esc(subtitle)}</p>` : ""}</div><div class="spacer"></div><span class="muted">${items.length} total</span></div>
    <section class="list">${items.length ? items.map(reportRow).join("") : empty(emptyText)}</section>`;
}
function reportRow(r) {
  const responsible = assignedName(r);
  return `<article class="card report-row" data-action="open" data-id="${esc(r.id)}">
    <div class="row"><span class="row-icon">${icon("file")}</span><div><h3>${esc(r.client || "Cliente não informado")}</h3>
    <p class="muted">${fmtDate(r.completed_at)} · ${esc(r.consultant || "Sem consultor")} · ${deliveryCount(r)} itens${responsible ? ` · Responsável: ${esc(responsible)}` : ""}</p></div>
    <div class="spacer"></div><span class="pill">${esc(r.delivery_status || "Sem status")}</span></div>
  </article>`;
}
function monthlyChart(reports) {
  const now = new Date(), months = Array.from({length:6},(_,i)=>new Date(now.getFullYear(), now.getMonth()-5+i, 1));
  const counts = months.map(m => reports.filter(r => { const d = new Date(r.completed_at || 0); return d.getMonth()===m.getMonth() && d.getFullYear()===m.getFullYear(); }).length);
  const max = Math.max(1, ...counts);
  return `<div class="card chart-card"><div class="chart-title"><h3>${icon("bar")}Entregas por mês</h3><span>${counts.reduce((a,b)=>a+b,0)} no período</span></div>
    <div class="month-bars">${months.map((m,i)=>{
      const pct = Math.max(4, counts[i] / max * 100);
      const label = m.toLocaleDateString("pt-BR",{month:"short"}).replace(".","");
      return `<div class="month-row"><span>${label}</span><div class="month-track"><i style="width:${pct}%"></i></div><b>${counts[i]}</b></div>`;
    }).join("")}</div></div>`;
}
function statusChart(reports) {
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
  return `<div class="card chart-card"><div class="chart-title"><h3>${icon("pie")}Situação</h3><span>${total} relatório${total === 1 ? "" : "s"}</span></div>
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
function renderClientForm(payload = blankClientPayload()) {
  state.editing = cloneData(payload.payload ? payload.payload : payload);
  state.editing.reportId = payload.id || payload.reportId || state.editing.reportId;
  const f = state.editing.report.fields;
  const editingExisting = Boolean(payload.id || payload.reportId);
  shell(`<section class="hero editor-hero">
      <span>${editingExisting ? "EDITAR CLIENTE" : "NOVO CLIENTE"}</span>
      <h1>${esc(f.cliente || "Cliente para levantamento")}</h1>
      <p>Cadastro inicial feito pela supervisão antes do levantamento.</p>
    </section>
    <div class="section-title"><h2>Dados básicos do cliente</h2><div class="spacer"></div><button class="btn secondary" data-action="dashboard">Voltar</button></div>
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
  shell(`<div class="section-title survey-title">
      <h2>${esc(clientName)}</h2>
      <div class="spacer"></div>
      <button class="btn secondary" data-action="dashboard">Voltar</button>
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
  const value = f[key] || f[key === "empresa" ? "cliente" : key] || "";
  if (type === "choice") return choice(key, label, value, extra);
  if (type === "textarea") return textarea(key, label, value, extra || 3);
  return input(key, label, value, false, type || "text");
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
  shell(`<section class="hero editor-hero">
      <span>RELATÓRIO EM PREENCHIMENTO</span>
      <h1>${esc(f.cliente || "Novo relatório")}</h1>
      <p>Etapa ${state.step + 1} de ${steps.length} · ${esc(steps[state.step][0])}</p>
      <div class="progress"><i style="width:${((state.step + 1) / steps.length) * 100}%"></i></div>
    </section>
    <div class="section-title"><h2>${esc(f.cliente || "Novo relatório")}</h2><div class="spacer"></div><button class="btn secondary" data-action="dashboard">Voltar</button></div>
    <nav class="steps">${steps.map((s,i)=>`<button class="step ${i===state.step?"active":""}" data-action="step" data-step="${i}">${s[0]}</button>`).join("")}</nav>
    <form id="reportForm" class="card">${stepHtml(steps[state.step][1], p)}</form>
    <div class="footer-actions no-print">
      ${footerActions}
    </div>`);
  bindInputs();
}
function stepHtml(name, p) {
  const f = p.report.fields;
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
    <div class="field"><label>Status</label><select data-field="deliveryStatus"><option></option><option ${p.report.deliveryStatus==="Concluído"?"selected":""}>Concluído</option><option ${p.report.deliveryStatus==="Concluído, mas deseja novos serviços"?"selected":""}>Concluído, mas deseja novos serviços</option><option ${p.report.deliveryStatus==="Não concluído"?"selected":""}>Não concluído</option></select></div>
    ${textarea("pendencias","Pendências",f.pendencias)}
    <div class="form-grid"><div>${signature("assinaturaAnalistaImagem","Assinatura do técnico",f.assinaturaAnalistaImagem)}</div><div>${signature("assinaturaClienteImagem","Assinatura do cliente",f.assinaturaClienteImagem)}</div></div>
    <div class="field"><label>Anexos / fotos</label><input type="file" id="files" multiple accept="image/*,.pdf"><input type="file" id="camera" accept="image/*" capture="environment"></div>
    <div class="attachments">${(p.report.attachments||[]).map(a=>`<div class="thumb">${a.uri?.startsWith("data:image")?`<img src="${a.uri}">`:""}<small>${esc(a.name)}</small></div>`).join("")}</div>`;
}
function input(key,label,value="",required=false,type="text"){return `<div class="field"><label>${esc(label)}</label><input data-field="${key}" value="${esc(value)}" type="${type}" ${required?"required":""}></div>`}
function userSelect(key,label,value=""){
  const options = state.users.map(user => `<option value="${esc(user.username)}" ${user.username === value ? "selected" : ""}>${esc(user.full_name || user.fullName || user.username)} (${esc(user.username)})</option>`).join("");
  return `<div class="field"><label>${esc(label)}</label><select data-field="${key}" required><option value="">Selecione o implantador</option>${options}</select></div>`;
}
function choice(key,label,value="",options=[]){return `<div class="field survey-choice"><label>${esc(label)}</label><div class="choice-row">${options.map(option=>`<label><input type="radio" name="${esc(key)}" data-field="${esc(key)}" value="${esc(option)}" ${value===option?"checked":""}>${esc(option)}</label>`).join("")}</div></div>`}
function textarea(key,label,value="",minLines=3){return `<div class="field"><label>${esc(label)}</label><textarea data-field="${key}" rows="${minLines}">${esc(value)}</textarea></div>`}
function groups(scope, groups, p){return groups.map(([g,items])=>`<div class="group"><h3>${esc(g)}</h3>${checks(items.map(i=>[scope,g,i]),p)}</div>`).join("")}
function checks(items, p){const set=new Set(p.report.checks||[]);return `<div class="check-grid">${items.map(([s,g,i])=>{const k=S.key(s,g,i);return `<label class="check"><input type="checkbox" data-check="${esc(k)}" ${set.has(k)?"checked":""}>${esc(i)}</label>`}).join("")}</div>`}
function signature(key,label,value){return `<div class="field"><label>${esc(label)}</label><canvas class="signature" data-signature="${key}" data-value="${esc(value||"")}"></canvas><button type="button" class="btn secondary" data-action="clear-signature" data-key="${key}">Limpar</button></div>`}
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
    el.checked ? set.add(el.dataset.check) : set.delete(el.dataset.check);
    state.editing.report.checks = [...set];
  });
  $$("#files,#camera").forEach(el => el && (el.onchange = async () => {
    const files = await Promise.all([...el.files].map(fileToAttachment));
    state.editing.report.attachments = [...(state.editing.report.attachments||[]), ...files];
    drawEditor();
  }));
  $$("canvas.signature").forEach(setupSignature);
}
function fileToAttachment(file) {
  return new Promise(resolve => { const rd = new FileReader(); rd.onload = () => resolve({ name:file.name, mimeType:file.type||"application/octet-stream", uri:rd.result }); rd.readAsDataURL(file); });
}
function setupSignature(canvas) {
  const key = canvas.dataset.signature, ctx = canvas.getContext("2d"); let drawing = false;
  const resize = () => { const old = canvas.dataset.value; canvas.width = canvas.clientWidth * devicePixelRatio; canvas.height = canvas.clientHeight * devicePixelRatio; ctx.scale(devicePixelRatio, devicePixelRatio); ctx.lineWidth=2; ctx.lineCap="round"; if(old){const img=new Image();img.onload=()=>ctx.drawImage(img,0,0,canvas.clientWidth,canvas.clientHeight);img.src=old;} };
  resize(); const pos=e=>{const r=canvas.getBoundingClientRect(),t=e.touches?.[0]||e;return{x:t.clientX-r.left,y:t.clientY-r.top}};
  const start=e=>{drawing=true; const p=pos(e); ctx.beginPath(); ctx.moveTo(p.x,p.y); e.preventDefault();};
  const move=e=>{if(!drawing)return; const p=pos(e); ctx.lineTo(p.x,p.y); ctx.stroke(); state.editing.report.fields[key]=canvas.toDataURL("image/png"); e.preventDefault();};
  const end=()=>drawing=false;
  canvas.onmousedown=canvas.ontouchstart=start; canvas.onmousemove=canvas.ontouchmove=move; canvas.onmouseup=canvas.onmouseleave=canvas.ontouchend=end;
}

function renderViewer(r) {
  state.viewing = r;
  const f = r.report.fields, checks = new Set(r.report.checks||[]);
  const evaluationScore = score(r);
  const actions = [
    `<button class="btn secondary" data-action="dashboard">Voltar</button>`,
    state.user.role === "supervisor" && stage(r) === "levantamento_pendente" ? `<button class="btn" data-action="edit-client" data-id="${esc(r.id)}">Editar cadastro</button>` : "",
    state.user.role !== "supervisor" ? `<button class="btn" data-action="edit" data-id="${esc(r.id)}">Editar</button>` : "",
    state.user.role === "supervisor" && isReadyForSupervisorEvaluation(r) ? `<button class="btn green" data-action="evaluate" data-id="${esc(r.id)}">Avaliar</button>` : "",
    isReadyForSupervisorEvaluation(r) ? `<button class="btn" data-action="print">Reimprimir PDF</button>` : ""
  ].filter(Boolean).join("");
  shell(`<section class="hero viewer"><h1>${esc(r.client || f.cliente)}</h1><p>${fmtDate(r.completed_at)} · ${esc(r.consultant || f.consultor || "")}</p></section>
    <section class="card viewer">${dl([["Cliente / Projeto",f.cliente],["Implantador responsável",assignedName(r)],["Contato",f.contato],["Tel/Cel",f.telefone],["E-mail",f.email],["CNPJ",f.cnpj],["Consultor",f.consultor],["Início",f.inicio],["Término",f.termino],["Status",r.report.deliveryStatus],["Serviços executados",f.servicosExecutados],["Pendências",f.pendencias]])}</section>
    ${hasEvaluation(r)?supervisionEvaluationCard(r, evaluationScore, checks):""}
    <section class="card"><h2>Checklists marcados</h2>${selected(S.technical,"tecnico",checks)}${selected(S.stock,"estoque",checks)}${selected(S.finance,"financeiro",checks)}${selected(S.fiscal,"fiscal",checks)}</section>
    ${printReportHtml(r)}
    <div class="footer-actions no-print">${actions}</div>`);
}
function dl(items){return `<dl>${items.map(([k,v])=>`<dt>${esc(k)}</dt><dd>${esc(v||"Não informado")}</dd>`).join("")}</dl>`}
function selected(groups, scope, checks){return groups.map(([g,items])=>items.filter(i=>checks.has(S.key(scope,g,i))).map(i=>`<span class="pill">${esc(i)}</span>`).join(" ")).filter(Boolean).join("<br>") || "<p class='muted'>Nenhum item marcado.</p>"}
function selectedCards(groups, scope, checks) {
  const items = groups.flatMap(([g, list]) => list.filter(i => checks.has(S.key(scope, g, i))).map(i => [g, i]));
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
      <div class="print-brand"><img src="/web/assets/logo_dubrasil.png" alt="DuBrasil Soluções"></div>
      <div><h1>RELATÓRIO DE ENTREGA DE IMPLANTAÇÃO</h1><p>Sistema de Gestão TGA • R.E.I.</p></div>
    </header>
    ${pSection("MÓDULOS CONTRATADOS")}
    ${pChecklist(S.modules, item => S.moduleKey(item), checks, 3)}
    ${pInfoTable([
      ["Cliente / Projeto", f.cliente], ["Consultor de implantação", f.consultor],
      ["Usuários cadastrados no TGA", f.usuariosTga], ["Início", f.inicio],
      ["Término", f.termino], ["Dias contratados", f.diasContratados],
      ["Dias utilizados", f.diasUtilizados]
    ])}
    <div class="print-support"><b>CONTATOS COM O SUPORTE TÉCNICO</b><span>suportetga@dubrasilsolucoes.com.br • (34) 3322-8500</span></div>
    ${pSection("PREENCHIMENTO TÉCNICO")}
    ${pGroups("tecnico", S.technical, checks)}
    ${pInfoTable([["Tipo do certificado digital", f.tipoCertificado], ["Quantidade de usuários no Workflow", f.qtdWorkflow]])}
    ${pParagraph("Observações técnicas", f.observacoesTecnicas)}
    ${pSection("MÓDULO ESTOQUE")}${pGroups("estoque", S.stock, checks)}
    ${pSection("MÓDULO FINANCEIRO")}${pGroups("financeiro", S.finance, checks)}
    ${pSection("MÓDULO FISCAL E RELATÓRIOS")}${pGroups("fiscal", S.fiscal, checks)}
    ${pSection("ENTREGA DA IMPLANTAÇÃO")}
    ${pParagraph("Descritivo dos serviços executados", f.servicosExecutados)}
    ${pStatus(data.deliveryStatus)}
    ${pParagraph("Pendências pós-implantação", f.pendencias)}
    ${pSection("ASSINATURAS")}
    ${pSignatures(f)}
    ${hasEvaluation(r) ? `${pSection("AVALIAÇÃO DA SUPERVISÃO")}${pInfoTable([["Supervisor", f._supervisorName], ["Nota", evScore == null ? f._supervisionScore : `${evScore.toFixed(1)}/10`], ["Parecer / observação", data.rating]])}${pGroups("supervisao", S.supervision, checks)}` : ""}
    ${(data.attachments || []).length ? `${pSection("EVIDÊNCIAS E ANEXOS")}${pAttachments(data.attachments)}` : ""}
    <footer class="print-footer">DuBrasil Soluções • suporte: (34) 3322-8500</footer>
  </article>`;
}
function pSection(title) { return `<h2 class="print-section">${esc(title)}</h2>`; }
function pInfoTable(items) {
  return `<div class="print-info">${items.map(([k,v]) => `<div><small>${esc(k).toUpperCase()}</small><b>${esc(v || "—")}</b></div>`).join("")}</div>`;
}
function pChecklist(items, keyFor, checks, cols = 2) {
  return `<div class="print-checks cols-${cols}">${items.map(item => `<div><span class="${checks.has(keyFor(item)) ? "on" : ""}"></span>${esc(item)}</div>`).join("")}</div>`;
}
function pGroups(scope, groups, checks) {
  return groups.map(([group, items]) => `<h3 class="print-subsection">${esc(group).toUpperCase()}</h3>${pChecklist(items, item => S.key(scope, group, item), checks, 2)}`).join("");
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
  const previousTitle = document.title;
  document.title = reportPdfTitle(state.viewing);
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
  $$("[data-check]", modal).forEach(el => el.onchange = () => { el.checked ? checks.add(el.dataset.check) : checks.delete(el.dataset.check); r.report.checks=[...checks]; });
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
  const a = e.target.closest("[data-action]"); if (!a) return;
  e.preventDefault();
  const action = a.dataset.action, id = a.dataset.id;
  try {
    if (action === "logout") { await api("/api/auth/logout",{method:"POST"}).catch(()=>{}); localStorage.removeItem("reiToken"); state.token=""; state.user=null; renderLogin(); }
    if (action === "dashboard") { await loadUsers(); await loadReports(); renderDashboard(); }
    if (action === "new" && state.user.role !== "supervisor") renderEditor(blankReport());
    if (action === "new-client" && state.user.role === "supervisor") renderClientForm();
    if (action === "open") {
      const report = state.reports.find(r => r.id === id);
      if (stage(report) === "levantamento_pendente" && state.user.role !== "supervisor") renderSurvey(report);
      else if (stage(report) === "rei_pendente" && state.user.role !== "supervisor") renderEditor(report);
      else renderViewer(report);
    }
    if (action === "dashboard-filter") { state.dashboardFilter = a.dataset.filter; renderDashboard(); }
    if (action === "edit-client" && state.user.role === "supervisor") renderClientForm(state.reports.find(r => r.id === id));
    if (action === "edit") renderEditor(state.reports.find(r => r.id === id));
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
      if(!field(state.editing,"cliente") && !field(state.editing,"empresa")) return alert("Informe a empresa/cliente.");
      state.editing.report.fields._stage = "rei_pendente";
      if (!state.editing.report.fields.cliente) state.editing.report.fields.cliente = state.editing.report.fields.empresa;
      await savePayload(state.editing);
      renderDashboard();
    }
    if (action === "save-print") {
      if(!field(state.editing,"cliente")) return alert("Informe o cliente/projeto.");
      if (!isConcludedDeliveryStatus(state.editing.report.deliveryStatus)) return alert("Sinalize a implantação como concluída antes de gerar o PDF.");
      state.editing.report.fields._stage = "rei";
      const savedId = await savePayload(state.editing);
      renderViewer(state.reports.find(r => r.id === savedId));
      await printCurrentReport();
    }
    if (action === "print" && isReadyForSupervisorEvaluation(state.viewing)) { await printCurrentReport(); }
    if (action === "evaluate") {
      const report = state.reports.find(r => r.id === id);
      if (state.user.role === "supervisor" && isReadyForSupervisorEvaluation(report)) renderEvaluation(report);
    }
    if (action === "close-modal") $(".modal")?.remove();
    if (action === "save-evaluation") { const r = state.reports.find(x => x.id === id); r.report.fields._supervisorName = state.user.fullName || state.user.username; r.report.fields._supervisionReviewedAt = String(Date.now()); await savePayload(r); $(".modal")?.remove(); renderViewer(state.reports.find(x => x.id === id)); }
    if (action === "clear-signature") { state.editing.report.fields[a.dataset.key] = ""; drawEditor(); }
  } catch (error) {
    alert(error.message || "Não foi possível executar esta ação.");
  }
});

(async function init() {
  if (await loadMe()) { await loadUsers(); await loadReports(); renderDashboard(); }
  else renderLogin();
})();
