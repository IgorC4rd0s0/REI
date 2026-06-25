# R.E.I. Web

Versão web do Relatório de Entrega de Implantação.

## Como acessar

Com o servidor Python iniciado:

```powershell
python server/rei_server.py
```

Abra no navegador:

```text
http://localhost:8765/web
```

Na rede do escritório, use o IP do computador servidor:

```text
http://192.168.1.123:8765/web
```

## Recursos

- login com os mesmos usuários do app Android;
- dashboard para supervisor e implantador;
- criação e edição de relatórios;
- checklists técnico, estoque, financeiro, fiscal e entrega;
- anexos/fotos pelo navegador;
- assinatura digital em canvas;
- visualização e reimpressão via `Imprimir / Salvar como PDF`;
- avaliação de supervisão com checklist, nota e parecer;
- implantador visualiza nota média e últimas avaliações recebidas.

## Arquivos

- `index.html`: entrada da aplicação;
- `styles.css`: layout responsivo e impressão;
- `schema.js`: checklists e módulos;
- `app.js`: telas, chamadas de API e regras da web app.
