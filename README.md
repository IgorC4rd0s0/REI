# R.E.I. Android

Aplicativo Android nativo para preencher o Relatório de Entrega de Implantação do TGA.

## Recursos

- formulário organizado em etapas;
- salvamento automático e funcionamento offline;
- checklists técnico, de módulos e de supervisão;
- exportação do relatório preenchido em PDF;
- validação dos dados essenciais antes da conclusão.
- banco de dados SQLite/Room para rascunhos e implantações concluídas;
- migração automática do armazenamento legado para o banco local.

## Armazenamento

Os dados são mantidos offline no banco `rei_database.db`. A tabela `reports` diferencia rascunhos e relatórios concluídos e possui índices por cliente, situação e data de conclusão. Fotos e assinaturas ficam nos arquivos privados do aplicativo; o banco guarda suas referências junto ao relatório.

## Banco central do escritório

Ao finalizar um relatório, o app mantém a cópia local e agenda o envio para a API do escritório. Se o PC ou a rede estiver indisponível, o WorkManager conserva a pendência e tenta novamente quando houver conexão.

- servidor: `server/rei_server.py`;
- banco central: `server/data/rei_central.db`;
- endereço atual: `http://192.168.1.123:8765`;
- arquivo para BI: `http://192.168.1.123:8765/api/bi/reports.csv`;
- cadastro de usuários: `http://192.168.1.123:8765/admin`;
- configuração do Android: `CENTRAL_API_URL` e `CENTRAL_API_KEY` no `local.properties`.

O primeiro acesso ao painel web cria o supervisor inicial. Os perfis **supervisor** e **implantador** acessam o dashboard, podem retomar o rascunho salvo e editar relatórios entregues. A reimpressão de PDFs permanece exclusiva do supervisor.

Para iniciar manualmente o servidor, execute `server/start-server.ps1`. Consulte `server/README.md` para detalhes da rede e autenticação.

## Abrir e executar

1. Abra esta pasta no Android Studio.
2. Aguarde a sincronização do Gradle.
3. Execute em um dispositivo ou emulador Android 8.0 (API 26) ou superior.

O Android Studio precisa utilizar um JDK 17 ou superior (o JDK embutido é recomendado).
