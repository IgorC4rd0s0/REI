# Aplicação web do R.E.I.

Frontend responsivo servido diretamente pelo servidor Python. Não existe uma etapa separada de compilação.

## Acesso

Com o servidor iniciado:

```text
http://localhost:8765/login
```

Na rede do escritório:

```text
http://IP-DO-SERVIDOR:8765/login
```

O login identifica o perfil e abre automaticamente a área de supervisor ou implantador.

## Responsabilidades dos arquivos

- `index.html`: entrada e metadados da aplicação;
- `app.js`: estado, API, dashboards, formulários, avaliações e impressão;
- `schema.js`: estrutura padrão dos relatórios;
- `styles.css`: layout responsivo, temas e estilos de impressão;
- `assets/`: logos e favicons utilizados pelo navegador e pelos PDFs.

## Recursos

- dashboards separados para levantamentos e implantações;
- formulários em etapas e campos dinâmicos;
- fotos, assinaturas e PDFs;
- avaliações da supervisão;
- temas claro e escuro;
- administração de usuários e itens dos relatórios;
- permissões específicas para supervisor e implantador.

As personalizações são carregadas de `/api/schema-overrides`; os relatórios são consultados e gravados por `/api/reports`.
