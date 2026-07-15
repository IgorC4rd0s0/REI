# R.E.I. — Documentação técnica

Sistema de levantamento de dados e emissão do Relatório de Entrega de Implantação do ERP TGA. O projeto é composto por aplicativo Android offline, interface web responsiva, API Python e banco SQLite central.

Para uma apresentação simples do sistema, dos perfis e dos benefícios para a empresa, consulte o [Guia do Sistema R.E.I.](Guia_REI.pdf).

Documentos visuais:

- [Fluxograma técnico](Fluxograma_Tecnico_REI.pdf);
- [Fluxograma operacional do Guia R.E.I.](Fluxograma_Guia_REI.pdf).

## Arquitetura

| Componente | Tecnologias | Responsabilidade |
|---|---|---|
| Android | Kotlin, Jetpack Compose, Room e WorkManager | Formulários móveis, armazenamento offline, câmera, assinaturas, PDFs, notificações e sincronização |
| Web | HTML, CSS e JavaScript | Dashboards, formulários, avaliação, administração e impressão pelo navegador |
| Servidor | Python `ThreadingHTTPServer` | API, autenticação, páginas administrativas e distribuição do frontend |
| Banco central | SQLite em modo WAL | Usuários, sessões e relatórios sincronizados |
| Esquemas dinâmicos | JSON e API | Tópicos, tipos e itens personalizados compartilhados entre web e Android |

Fluxo de dados:

```text
Android/Room ── sincronização Wi-Fi ──┐
                                      ├── API Python ── SQLite ── CSV/BI
Navegador ─────────── HTTP/HTTPS ─────┘
```

## Requisitos

### Servidor e web

- Windows com Python 3;
- porta TCP `8765` disponível;
- navegador moderno;
- acesso à rede local do escritório.

O servidor utiliza somente a biblioteca padrão do Python.

### Android

- Android Studio com JDK 17;
- Android SDK 35;
- Android 8.0/API 26 ou superior;
- Gradle Wrapper incluído no repositório.

Versões principais:

- Android Gradle Plugin `8.7.3`;
- Kotlin `2.0.21`;
- Compose BOM `2024.12.01`;
- Room `2.6.1`;
- WorkManager `2.10.0`.

## Estrutura do repositório

```text
Rei/
├── app/
│   ├── src/main/java/br/com/dubrasil/rei/
│   │   ├── MainActivity.kt          Telas e navegação Compose
│   │   ├── ReportViewModel.kt       Estado e transições dos relatórios
│   │   ├── data/                    Room, autenticação e sincronização
│   │   ├── model/                   Dados e esquema dos formulários
│   │   ├── pdf/                     Exportação dos PDFs Android
│   │   └── ui/theme/                Temas claro e escuro
│   └── src/main/res/                Ícones, logos e recursos Android
├── web/
│   ├── index.html                   Entrada do frontend
│   ├── app.js                       Estado, API, telas e impressão
│   ├── schema.js                    Campos padrão da versão web
│   ├── styles.css                   Responsividade, impressão e temas
│   └── assets/                      Logos e favicons
├── server/
│   ├── rei_server.py                API e servidor HTTP
│   ├── config.example.json          Exemplo de configuração
│   ├── config.json                  Configuração local não versionável
│   ├── data/rei_central.db          Banco central
│   ├── data/schema_items.json       Personalizações dos formulários
│   └── *.ps1                        Inicialização e configuração do Windows
├── docs/Guia_REI.html               Fonte do guia institucional
├── docs/Fluxograma_Tecnico_REI.html  Fonte do fluxograma técnico
├── docs/Fluxograma_Guia_REI.html     Fonte do fluxo operacional
├── Guia_REI.pdf                     Guia paginado para usuários
├── Fluxograma_Tecnico_REI.pdf       Arquitetura e sincronização
├── Fluxograma_Guia_REI.pdf          Processo de implantação
└── README.md                        Documentação técnica
```

## Configuração do servidor

Copie ou adapte `server/config.example.json` para `server/config.json`:

```json
{
  "host": "0.0.0.0",
  "port": 8765,
  "api_key": "troque-por-uma-chave-segura",
  "database": "data/rei_central.db"
}
```

Não publique `server/config.json`, bancos, anexos ou backups. A chave de API deve ser diferente do exemplo.

Iniciar manualmente:

```powershell
.\server\start-server.ps1
```

Instalar inicialização automática no login do Windows:

```powershell
.\server\install-autostart.ps1
```

Remover a inicialização automática:

```powershell
.\server\uninstall-autostart.ps1
```

Endereços principais:

| Rota | Acesso | Finalidade |
|---|---|---|
| `/login` | Público | Autenticação única |
| `/web` | Autenticado | Aplicação web |
| `/admin` | Supervisor | Gestão de usuários |
| `/admin/items` | Supervisor | Gestão dos itens dos relatórios |
| `/health` | Rede | Verificação do servidor |
| `/api/auth/login` | Público | Login da web e do Android |
| `/api/auth/me` | Autenticado | Sessão atual |
| `/api/auth/change-password` | Autenticado | Alteração da própria senha |
| `/api/reports` | Autenticado | Consulta e gravação dos relatórios |
| `/api/schema-overrides` | Autenticado | Sincronização dos campos dinâmicos |
| `/api/bi/reports.csv` | Protegido | Exportação para BI |

Na primeira abertura de `/login`, se o banco não possuir usuários, o servidor apresenta o cadastro do supervisor inicial.

## Autenticação e permissões

Existem dois perfis:

- `supervisor`: visualiza todos os registros, gerencia usuários e itens, cadastra clientes, avalia implantações concluídas e imprime relatórios;
- `implantador`: trabalha nos registros sob sua responsabilidade, cria e preenche levantamentos, preenche o R.E.I., conclui e imprime seus relatórios.

Regras relevantes:

- avaliação somente após a conclusão da implantação;
- avaliação enviada não pode ser editada;
- levantamento concluído é somente leitura e impressão;
- PDF do R.E.I. somente após status concluído;
- tópicos não são excluídos pela interface;
- itens personalizados podem ser excluídos;
- campos padrão do levantamento podem ter nome e tipo ajustados;
- senha mínima de oito caracteres.

O servidor armazena senhas usando PBKDF2-HMAC-SHA256 com salt e `210.000` iterações. Tokens de sessão são armazenados pelo hash SHA-256, têm validade de 30 dias e são invalidados no logout.

## Estados dos relatórios

O campo interno `_stage` controla o fluxo:

| Estado | Significado | Próxima ação |
|---|---|---|
| `levantamento_pendente` | Cliente aguardando coleta de dados | Preencher e concluir levantamento |
| `rei_pendente` | Levantamento concluído | Iniciar o R.E.I. |
| R.E.I. em edição | Implantação iniciada e ainda não concluída | Salvar ou concluir |
| R.E.I. concluído | Entrega finalizada | Imprimir e aguardar avaliação |

Ao concluir o levantamento, o mesmo registro recebe `_surveyCompletedAt` e passa para `rei_pendente`. A versão concluída do levantamento continua disponível em modo somente leitura.

## Campos dinâmicos

Os esquemas padrão ficam em `ReportSchema.kt` e `web/schema.js`. As personalizações cadastradas pelo supervisor são persistidas em `server/data/schema_items.json` e disponibilizadas por `/api/schema-overrides`.

Tipos aceitos:

- `text`: texto curto;
- `textarea`: texto longo;
- `choice`: múltipla escolha;
- `date`: data;
- `datetime-local`: data e hora;
- `photo`: foto/imagem;
- `email`: e-mail;
- `number`: número.

Android e web devem interpretar o objeto completo do item. Não converta um item dinâmico diretamente com `String(item)`, pois isso resulta em `[object Object]` na web ou representação incorreta no Android.

## Banco local Android e sincronização

O Room cria `rei_database.db` com as entidades de relatórios e usuários autenticados em cache. O banco possui versão `3` e migrações para situação de sincronização e autenticação offline.

Fluxo de sincronização:

1. o login online grava sessão, perfil, hash local da credencial e URL do servidor;
2. o aplicativo baixa as personalizações dos formulários;
3. alterações locais ficam com sincronização pendente;
4. `CentralSyncWorker` é agendado pelo WorkManager;
5. o worker exige `NetworkType.UNMETERED`, normalmente Wi-Fi;
6. pendências são enviadas e registros remotos são atualizados localmente;
7. falhas utilizam repetição com backoff exponencial.

O login offline só funciona depois de um login online no mesmo aparelho. Alteração de senha exige uma sessão online e atualiza a credencial local após o sucesso.

A URL do servidor pode ser informada na engrenagem do aplicativo sem recompilar o APK. O valor padrão de compilação pode ser definido em `local.properties`:

```properties
CENTRAL_API_URL=http://IP-DO-SERVIDOR:8765
CENTRAL_API_KEY=chave-configurada-no-servidor
```

`local.properties` é específico da máquina e não deve ser publicado.

## Notificações Android

`ReiNotifications.kt` configura:

- aviso de novo levantamento sincronizado;
- aviso de R.E.I. liberado;
- lembrete de levantamento 30 minutos antes do agendamento;
- lembrete de R.E.I. pendente às 10h30 e 16h30;
- aviso diário de avaliação pendente no primeiro login do supervisor.

Android 13 ou superior exige a permissão `POST_NOTIFICATIONS`. O horário real pode variar conforme as otimizações de bateria do fabricante.

## PDFs

O Android utiliza `PdfExporter.kt` e compartilha o arquivo por `FileProvider`. A web monta a versão de impressão em `app.js` e usa o diálogo do navegador.

Nomes:

```text
Relatorio de Entrega - Nome do cliente.pdf
Levantamento de Dados - Nome do cliente.pdf
```

O PDF do R.E.I. deve preservar dados, checklists, avaliação, fotos e assinaturas. O PDF do levantamento usa o mesmo padrão visual e imprime os campos conforme o tipo configurado.

## Compilação Android

Gerar e testar a versão debug:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest
```

APK gerado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Instalar em dispositivo conectado:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

A versão web não possui etapa de compilação: os arquivos estáticos são servidos diretamente por `rei_server.py`.

## Validação

Validação mínima antes de disponibilizar uma versão:

```powershell
python -m py_compile .\server\rei_server.py
.\gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest
```

Roteiro manual:

1. testar login e troca de senha nos dois perfis;
2. testar temas claro e escuro;
3. cadastrar um campo de cada tipo e sincronizar com Android;
4. concluir e imprimir um levantamento;
5. confirmar a transição para `rei_pendente`;
6. preencher e concluir o R.E.I.;
7. validar fotos e assinaturas nos PDFs web e Android;
8. avaliar como supervisor e conferir a nota como implantador;
9. preencher offline e sincronizar ao voltar ao Wi-Fi;
10. validar responsividade no Samsung S22 ou tela equivalente.

Se `testDebugUnitTest` retornar `NO-SOURCE`, o módulo ainda não possui testes unitários automatizados; a compilação não substitui o roteiro manual.

## Banco central, BI e backup

Banco principal:

```text
server/data/rei_central.db
```

Para backup consistente:

1. interrompa o servidor;
2. copie toda a pasta `server/data`;
3. copie `server/config.json` para local protegido;
4. reinicie o servidor;
5. teste periodicamente a restauração em ambiente separado.

Integrações de BI devem consumir `/api/bi/reports.csv` ou uma cópia controlada do banco. Não escreva diretamente no SQLite durante a operação.

## Rede e acesso externo

Na rede local, use:

```text
http://IP-DO-SERVIDOR:8765/login
```

O servidor escuta em `0.0.0.0`, mas endereços privados não são alcançáveis pelo 5G. O modo previsto é trabalhar offline e sincronizar no Wi-Fi do escritório.

Se houver necessidade de acesso externo, utilize VPN ou túnel HTTPS administrado. Não exponha a porta 8765 diretamente sem uma camada segura. Consulte [server/EXTERNAL_ACCESS.md](server/EXTERNAL_ACCESS.md).

## Problemas frequentes

| Sintoma | Verificação |
|---|---|
| Web não abre | Processo Python, `/health`, IP e firewall |
| Login offline falha | Realizar primeiro login online no aparelho |
| Item não aparece no Android | URL, Wi-Fi, sessão e `/api/schema-overrides` |
| Registro não sincroniza | Rede não medida, servidor e situação no Room |
| Foto/assinatura ausente | Salvar antes de gerar e confirmar arquivo acessível |
| Caracteres inválidos | Manter fontes Kotlin, Python, HTML, JS, CSS e Markdown em UTF-8 |
| PDF web diferente | Conferir CSS `@media print`, imagens e opção de imprimir fundos |

## Limitações atuais

- o servidor central precisa permanecer ligado para web e sincronização;
- não há serviço em nuvem integrado por padrão;
- cada usuário precisa autenticar online uma vez por aparelho;
- SQLite é adequado ao cenário atual, mas pode exigir migração conforme volume e concorrência;
- notificações dependem das permissões e da política de bateria do Android;
- o módulo Android ainda não possui testes unitários automatizados.
