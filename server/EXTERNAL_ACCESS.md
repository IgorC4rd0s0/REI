# Acesso externo ao R.E.I.

O servidor já escuta em `0.0.0.0:8765`, então ele aceita conexões fora do próprio computador. Para acessar pelo 5G, falta publicar esse serviço fora da rede local.

## Endereços

- Web local: `http://192.168.1.123:8765/web`
- Web externa, se usar IP público/porta: `http://SEU_IP_PUBLICO:8765/web`
- App Android: informe o mesmo endereço base na tela de login, sem `/web`.
  - exemplo: `http://SEU_IP_PUBLICO:8765`
  - exemplo com domínio/túnel: `https://rei.suaempresa.com.br`

## Opção 1: liberar porta no roteador

1. No computador servidor, execute como Administrador:

```powershell
server\enable-firewall-8765-admin.ps1
```

2. No roteador/modem do escritório, crie um redirecionamento de porta:

```text
Porta externa: 8765 TCP
IP interno: 192.168.1.123
Porta interna: 8765 TCP
```

3. Acesse pelo 5G:

```text
http://SEU_IP_PUBLICO:8765/web
```

Observação: se o provedor usar CGNAT, o redirecionamento de porta não funcionará. Nesse caso use a opção 2.

## Opção 2: túnel seguro

Use um serviço de túnel, por exemplo Cloudflare Tunnel, Tailscale Funnel, ngrok ou similar.

O túnel deve apontar para:

```text
http://localhost:8765
```

Depois use a URL pública gerada:

```text
https://sua-url-publica/web
```

No app Android, informe somente:

```text
https://sua-url-publica
```

## Importante

- Prefira `https` para acesso externo.
- Use senhas fortes para supervisor e implantadores.
- Não exponha o arquivo `server/config.json`.
- O painel `/admin` ficará acessível externamente se a URL pública for aberta, então mantenha o acesso protegido por senha.
