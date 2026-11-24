# Rainha Carmesim — detalhes técnicos

## Arquitetura

O app é um único módulo Android, em Kotlin e Jetpack Compose. A organização é por camadas e as dependências são criadas manualmente em `AppContainer`, sem framework de DI.

```
app/src/main/java/com/rainhacarmesim/app/
  RainhaApp, MainActivity, AppContainer   ponto de entrada e DI manual
  ui/          tema (cores, tipografia), componentes (cards, gráficos em Canvas), navegação
  feature/     uma pasta por tela: dashboard, devices, monitor, tools, alerts, settings (Screen + ViewModel)
  network/     acesso à rede: NetworkInfoProvider, DeviceScanner, HostProbe, HostnameResolver,
               DnsCodec, ArpTable, VendorLookup, PingRunner, SpeedTestClient, TrafficSampler, SystemUsageHistory
  data/db/     Room: entidades e DAOs (devices, scans, alerts, traffic_minutes, speed_tests)
  data/repo/   repositórios, SettingsStore (DataStore), ScanCoordinator (varreduras + alertas), TrafficMonitor
  domain/      lógica pura e testável: ScanMerger, SubnetCalculator, AlertPolicy, Formatters
  update/      UpdateManager, metadados/validação, ApkDownloader (retomada + SHA-256), ApkInstaller, receiver
```

Stack:
- Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9 (wrapper incluído).
- Compose BOM 2024.12.01 com Material 3, Navigation Compose, Room 2.6.1 (KSP), DataStore e Coroutines/Flow.
- Sem bibliotecas de rede ou de gráficos: HTTP via `HttpURLConnection` e gráficos desenhados em Canvas.
- `minSdk 26` (Android 8.0), `targetSdk 35`, `applicationId com.rainhacarmesim.app`.

## Como cada módulo funciona

- **Dashboard.** Lê IP, prefixo, gateway e DNS via `ConnectivityManager`/`LinkProperties`, e o SSID via `WifiInfo`. Atualiza a cada mudança de rede com `NetworkCallback`. A velocidade atual vem do `TrafficStats`.
- **Scanner.** Varre a sub-rede (até uma /22) com 32 sondagens em paralelo. Cada sondagem tenta ICMP (`isReachable`) e conexão TCP em portas comuns; conexão recusada (RST) também conta como host ativo.
  - Hostname: DNS reverso no roteador, depois mDNS unicast e depois NetBIOS.
  - MAC: lido da tabela ARP quando o Android permite. Fabricante: base OUI embarcada (`assets/oui.txt.gz`, derivada da lista pública da IEEE via Wireshark).
  - O `ScanMerger` compara cada varredura com os dispositivos conhecidos: a primeira varredura de uma rede vira a referência e um dispositivo só fica offline após 2 varreduras seguidas sem resposta.
- **Monitor.** O `TrafficStats` é amostrado a cada 1 s com o app aberto e agregado por minuto no Room (retenção de 90 dias). O histórico usa o `NetworkStatsManager`, com Wi‑Fi e dados móveis separados, quando o usuário concede Acesso de uso.
- **Ping.** Usa o binário `/system/bin/ping` e faz parse da saída. Sem ICMP disponível, mede conexões TCP e informa que mudou de método.
- **Speed test.** Usa `speed.cloudflare.com` via HTTPS:
  - latência: mediana de 10 requisições vazias, com jitter;
  - download e upload: 4 conexões paralelas por até 10 s, com teto de 250 MB e 120 MB, descartando o 1º segundo.
- **Mapa de sinal Wi-Fi** (`SignalMapper` + `domain/SignalHeatmap`). Lê o RSSI do Wi-Fi conectado a cada 0,7 s, sem exigir permissão. A posição é estimada a partir do ponto de partida: os passos vêm do acelerômetro, por detecção de picos, sem a permissão de atividade física, e a direção vem do `GAME_ROTATION_VECTOR`, que ignora o magnetômetro, distorcido dentro de casa. As leituras são agrupadas por célula de 0,5 m. O mapa de calor é interpolado por inverso da distância (IDW) num raio de 3 m. Fica só em memória e é apagado ao fechar a tela.
- **Alertas.** Com o app em primeiro plano (`repeatOnLifecycle(STARTED)`), uma varredura automática roda no intervalo configurado. As transições geram alertas no Room, com cooldown de 10 min por dispositivo e tipo.

## Sistema de atualização

1. O app consulta `https://github.com/MatheusTogni/RainhaCarmesim/releases/latest/download/latest.json`. Faz isso ao abrir, no máximo a cada 6 h, e sempre que o usuário pede.
2. Valida os metadados: a URL do APK precisa ser HTTPS e apontar para as releases deste repositório, e o SHA‑256 precisa ser válido.
3. Só oferece a atualização se o `versionCode` for **maior** que o instalado, o que impede downgrade. O usuário pode escolher "Depois".
4. Baixa o APK com progresso e continua de onde parou se o download for interrompido (`Range`).
5. Confere o **SHA‑256** e verifica que o APK é deste app e mais novo que o instalado.
6. Pede a permissão "instalar apps desconhecidos" **apenas** nesse momento.
7. Abre o instalador oficial (`PackageInstaller`, com `FileProvider` como alternativa). A confirmação é sempre do usuário e os dados são preservados.

As releases são publicadas pelo workflow `build.yml` a cada push na `main` (release `latest`); veja [RELEASING.md](RELEASING.md).

## Permissões Android

| Permissão | Por quê |
|---|---|
| `INTERNET` | Varredura, ping, speed test e atualizações |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | Ler IP, gateway, DNS, tipo de conexão, sinal |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Exigidas pelo Android **apenas** para revelar o nome (SSID) do Wi‑Fi. Pedidas em tempo de execução e opcionais. |
| `NEARBY_WIFI_DEVICES` (Android 13+, `neverForLocation`) | Informações de Wi‑Fi sem uso de localização |
| `CHANGE_WIFI_MULTICAST_STATE` | Consultas mDNS para nomes `.local` |
| `REQUEST_INSTALL_PACKAGES` | Abrir o instalador oficial para atualizar o app |
| `PACKAGE_USAGE_STATS` | Opcional. O usuário concede em "Acesso de uso" para o histórico de dados do sistema. |

O app não usa notificações do sistema, serviços em segundo plano nem WorkManager.

## Limitações conhecidas

- **MAC e fabricante** de outros aparelhos: desde o Android 10, apps não conseguem ler a tabela ARP. Nesses aparelhos o MAC aparece como indisponível e o dispositivo é identificado pelo **IP**. Se o DHCP trocar o IP de um aparelho, ele pode aparecer como novo e o antigo como desconectado.
- O **MAC deste celular** é ocultado pelo Android. O nome e o fabricante exibidos vêm do sistema.
- **Dispositivos silenciosos**: aparelhos que ignoram ICMP e não têm portas TCP comuns abertas podem não ser detectados numa varredura.
- **Hostname** depende do roteador responder DNS reverso ou do dispositivo responder a mDNS ou NetBIOS.
- **Redes grandes**: sub-redes maiores que /22 são varridas só na /22 que contém o celular (até 1022 hosts).
- **Tráfego de outros dispositivos** não é visível: o Android só expõe os contadores do próprio aparelho.
- **Histórico de dados**: sem "Acesso de uso", cobre só os períodos com o app aberto.
- **Speed test**: depende do serviço público da Cloudflare, consome dados (até cerca de 370 MB por teste) e mede a vazão até esse servidor.
- **Mapa de sinal**: a posição por passos desvia com o tempo, então o usuário pode tocar no mapa para corrigi-la. O mapa mede só a rede conectada. O RSSI informado pelo Android pode levar alguns segundos para atualizar.
- **SSID**: requer permissão de localização e a localização do aparelho ativada.
- **Identificação da rede**: dispositivos conhecidos são agrupados por sub-rede e gateway, então duas redes com o mesmo endereçamento compartilham a lista.
- **Alertas** só existem com o app aberto. É uma escolha de projeto: não há monitoramento em segundo plano.

## Segurança e privacidade

- Nenhuma senha, credencial ou dado pessoal é coletado. O backup automático está desativado e tudo fica no aparelho.
- Todo tráfego HTTP é HTTPS (`cleartextTrafficPermitted=false`).
- As entradas do usuário são validadas. O alvo do ping não pode começar com `-` e nunca passa por shell.
- A chave de assinatura **não está no repositório**: fica nos GitHub Secrets e é usada pelo CI para assinar o APK. Veja [RELEASING.md](RELEASING.md).
