# Servidor central R.E.I.

Servidor local do escritório que recebe os relatórios Android e grava os dados em SQLite.

## Iniciar

Execute `start-server.ps1` no PowerShell. O servidor escuta a porta `8765` em todas as interfaces. Para sincronizar no modo recomendado, o celular deve estar no Wi-Fi do escrit?rio. Fora da rede, o app Android trabalha offline e envia os relat?rios pendentes quando voltar para uma rede Wi-Fi com acesso ao servidor.

Para iniciar o servidor automaticamente ao entrar no Windows, execute uma vez `install-autostart.ps1`. Para desfazer, use `uninstall-autostart.ps1`.

Endereços atuais:

- administração de usuários: `http://192.168.1.123:8765/admin`
- saúde: `http://192.168.1.123:8765/health`
- relatórios: `http://192.168.1.123:8765/api/reports`
- CSV para BI: `http://192.168.1.123:8765/api/bi/reports.csv`

No primeiro acesso a `/admin`, cadastre o supervisor inicial. Depois, use esse painel para criar e ativar/desativar usuários dos tipos:

- **supervisor**: acessa o dashboard, edita implantações e reimprime relatórios em PDF;
- **implantador**: acessa o dashboard, retoma rascunhos, edita implantações entregues e envia relatórios concluídos.

O aplicativo autentica pela sessão do usuário. A rota CSV para BI também aceita o cabeçalho `X-API-Key` configurado em `config.json`. O banco fica em `data/rei_central.db`, utiliza modo WAL e registra quem enviou cada relatório.

Se o IP do computador mudar, informe o novo endere?o na tela de login do app Android. N?o ? necess?rio recompilar o APK apenas para trocar o servidor.
