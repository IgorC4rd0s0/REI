# Servidor central do R.E.I.

Servidor local responsável pela autenticação, API, aplicação web e armazenamento central dos relatórios.

## Execução

Na raiz do projeto:

```powershell
.\server\start-server.ps1
```

O servidor escuta a porta `8765` em todas as interfaces. Acesse no próprio computador:

```text
http://localhost:8765/login
```

Em outro dispositivo da rede, use `http://IP-DO-SERVIDOR:8765/login`.

## Inicialização automática

```powershell
.\server\install-autostart.ps1
```

Para remover:

```powershell
.\server\uninstall-autostart.ps1
```

## Dados e configuração

- configuração: `server/config.json`;
- exemplo seguro: `server/config.example.json`;
- banco central: `server/data/rei_central.db`;
- campos personalizados: `server/data/schema_items.json`;
- exportação para BI: `/api/bi/reports.csv`;
- verificação do servidor: `/health`.

## Assistente contextual

O chat do levantamento usa somente quatro Skills versionadas em `server/skills/` e persiste sessões,
mensagens e sugestões no SQLite central. Rotas autenticadas:

- `POST /api/levantamentos/{id}/chat/sessoes`;
- `POST /api/levantamentos/{id}/chat/mensagens`;
- `GET /api/levantamentos/{id}/chat/mensagens?sessionId=...`;
- `POST /api/chat/sugestoes/{id}/confirmar` e `/rejeitar`.

A chave do provedor nunca fica no Android. Para habilitar respostas reais, configure no ambiente do
servidor `OPENAI_API_KEY`, opcionalmente `OPENAI_MODEL`, `OPENAI_TIMEOUT_SECONDS` e
`OPENAI_MAX_OUTPUT_TOKENS`. Sem a chave, mensagens são preservadas localmente e a API informa
`ai_not_configured`; os testes usam mock e não fazem chamadas externas.

`config.json` e `data/` são locais e ignorados pelo Git. Não publique bancos, chaves ou backups.

## Perfis

- **supervisor:** gerencia usuários e itens dos relatórios, acompanha registros, cadastra clientes, avalia implantações concluídas e reimprime PDFs;
- **implantador:** cria ou preenche levantamentos, executa o R.E.I., salva rascunhos, conclui e imprime seus documentos.

O primeiro acesso cria o supervisor inicial. Depois, os usuários são administrados em `/admin` e os campos em `/admin/items`.

## Rede

O modo recomendado é sincronizar o Android no Wi-Fi do escritório. Fora da rede, o aplicativo trabalha offline e envia as pendências ao retornar.

Se o IP do computador mudar, altere o endereço na engrenagem do aplicativo; não é necessário recompilar o APK. Para acesso externo, consulte [EXTERNAL_ACCESS.md](EXTERNAL_ACCESS.md) e utilize preferencialmente VPN ou túnel HTTPS.
