<p align="center">
  <img src="docs/icon.png" width="160" alt="Ícone da Rainha Carmesim">
</p>

<h1 align="center">Rainha Carmesim</h1>

<p align="center">
  <b>Descubra, monitore e diagnostique a sua rede local direto do celular.</b><br>
  App Android nativo, sem anúncios, sem conta e sem servidor: tudo roda no aparelho.
</p>

<p align="center">
  <a href="https://github.com/MatheusTogni/RainhaCarmesim/releases/latest"><img src="https://img.shields.io/github/v/release/MatheusTogni/RainhaCarmesim?label=baixar%20APK&color=DC143C&style=for-the-badge" alt="Baixar a versão mais recente"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-6D0F1F?style=for-the-badge&logo=android&logoColor=white" alt="Android 8.0+">
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-6D0F1F?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin + Jetpack Compose">
</p>

---

## O que ela faz

**🏠 Painel da rede.** Mostra IP, máscara, gateway, DNS, nome do Wi‑Fi, sinal e velocidade atual, com indicador de conexão com a internet.

**📡 Scanner de dispositivos.** Encontra os aparelhos conectados à mesma rede, com nome, fabricante (quando o Android permite), latência e status online/offline. Lembra os dispositivos já vistos e destaca os novos.

**📈 Monitor de tráfego.** Gráfico ao vivo de download e upload do celular e histórico de consumo de 24 h, 7 dias e 30 dias.

**🛠️ Ferramentas.** Ping com latência e perda de pacotes, e teste de velocidade real (download, upload, latência e jitter) com gauge e histórico.

**🔔 Alertas.** Avisa quando entra um dispositivo novo na rede, quando um conhecido volta ou quando algum se desconecta, enquanto o app está aberto.

**⬆️ Atualização pelo próprio app.** Verifica novas versões no GitHub, baixa, confere a integridade (SHA‑256) e abre o instalador do Android.

## Telas

<p align="center">
  <img src="docs/screenshots/home.png" width="200" alt="Home">
  <img src="docs/screenshots/devices.png" width="200" alt="Dispositivos">
  <img src="docs/screenshots/monitor.png" width="200" alt="Monitor">
  <img src="docs/screenshots/tools.png" width="200" alt="Ferramentas">
</p>

## Instalar

1. Baixe o `RainhaCarmesim-X.Y.Z.apk` na [última release](https://github.com/MatheusTogni/RainhaCarmesim/releases/latest).
2. Abra o arquivo no celular e autorize a instalação de apps desta fonte, se o Android pedir.
3. As próximas versões podem ser instaladas pelo próprio app, em **Settings › Verificar atualizações**.

## Feito com

- **Kotlin** e **Jetpack Compose** (Material 3), com tema escuro próprio e gráficos desenhados à mão em Canvas
- **Room** e **DataStore** para guardar dados no aparelho
- **Coroutines/Flow** para varreduras paralelas sem travar a interface
- **GitHub Actions** para testar, compilar, assinar e publicar cada versão no GitHub Releases

Praticamente sem bibliotecas além do AndroidX: o HTTP usa a API padrão e os protocolos DNS, mDNS e NetBIOS foram implementados no próprio projeto.

## Privacidade

O app não pede conta, não tem anúncios e não envia dados a servidores. As únicas conexões externas são o teste de velocidade, que o usuário inicia, e a verificação de atualizações no GitHub. A localização só é pedida porque o Android a exige para mostrar o nome da rede Wi‑Fi.

## Limitações do Android

Algumas informações simplesmente não são acessíveis a apps:
- **MAC e fabricante** de outros aparelhos quase nunca estão disponíveis desde o Android 10.
- O **tráfego de outros dispositivos** da rede não é visível, só o do próprio celular.
- Os **alertas** funcionam apenas com o app aberto.

A lista completa está em [docs/TECNICO.md](docs/TECNICO.md#limitações-conhecidas).

## Para desenvolvedores

```bash
./gradlew testDebugUnitTest   # testes
./gradlew assembleRelease     # APK em app/build/outputs/apk/release/
```

Requer JDK 17 e Android SDK 35, ou o Android Studio. Sem a chave oficial configurada, o build local é assinado com a chave de debug.

- [docs/TECNICO.md](docs/TECNICO.md): arquitetura, funcionamento de cada módulo, permissões e limitações
- [docs/RELEASING.md](docs/RELEASING.md): como publicar uma nova versão

## Licença

[MIT](LICENSE)
