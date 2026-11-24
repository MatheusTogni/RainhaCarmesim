# Como publicar uma nova versão

**Todo push na `main` publica uma versão.** O workflow **Build APK** (`.github/workflows/build.yml`) compila o APK assinado e substitui a release `latest` do GitHub, que contém:

- `RainhaCarmesim.apk`: o APK assinado com a chave do projeto;
- `latest.json`: os metadados lidos pelo atualizador do app, incluindo o SHA-256 do APK.

O workflow não roda testes nem lint, para ser rápido. Rode-os localmente antes de subir: `./gradlew testDebugUnitTest lintDebug`.

## Configuração única: chave de assinatura (GitHub Secrets)

A chave de release **não fica no repositório**. Ela é cadastrada uma vez em
**Settings › Secrets and variables › Actions › New repository secret**:

| Secret | Valor |
|---|---|
| `RC_KEYSTORE_BASE64` | conteúdo do keystore `.p12` em base64, numa linha só |
| `RC_KEYSTORE_PASSWORD` | senha do keystore |
| `RC_KEY_ALIAS` | alias da chave |
| `RC_KEY_PASSWORD` | senha da chave (no PKCS12 é igual à do keystore) |

Para gerar o base64 de um keystore: `base64 -w0 chave.p12 > chave.b64.txt` no Linux/Git Bash, ou `[Convert]::ToBase64String([IO.File]::ReadAllBytes("chave.p12"))` no PowerShell.

Sem esses secrets, o APK sai assinado com a chave de debug do runner e **não instala por cima** da versão oficial. Confira os secrets antes de mexer no workflow.

Para assinar localmente com a chave oficial, defina as variáveis de ambiente (ou coloque as entradas em `~/.gradle/gradle.properties`, **fora** do projeto):
```
RC_KEYSTORE_PATH=C:/caminho/rainha-carmesim-release.p12
RC_KEYSTORE_PASSWORD=...
RC_KEY_ALIAS=rainhacarmesim
RC_KEY_PASSWORD=...
```

## Esquema de versões

A versão sai do número do run do workflow (`N`): `versionName` = `1.0.N` e `versionCode` = `10000 + N`. O número sempre cresce, então cada push gera uma versão maior que a anterior, e o app nunca oferece downgrade.

As notas da versão exibidas no app são a mensagem do commit. Para publicar de novo sem mudar código: **Actions › Build APK › Run workflow**.

O app consulta `releases/latest/download/latest.json`:
```json
{
  "versionName": "1.0.7",
  "versionCode": 10007,
  "releaseNotes": "Mensagem do commit",
  "apkUrl": "https://github.com/MatheusTogni/RainhaCarmesim/releases/download/latest/RainhaCarmesim.apk",
  "sha256": "…",
  "publishedAt": "2026-11-24T12:00:00Z"
}
```

## Cuidados

- **Nunca perca nem troque a chave de release, e nunca mude o `applicationId`.** Sem a mesma chave, o Android recusa instalar a nova versão por cima, e o usuário teria que desinstalar e perder os dados. Guarde o `.p12` e a senha num cofre, com backup.
- **Nunca coloque a chave no repositório.** O `.gitignore` já bloqueia `*.p12`, `*.jks` e `*.keystore`.
- Mudanças no banco Room exigem incrementar a versão do `AppDatabase` e escrever uma `Migration`, para preservar os dados na atualização.
- Todo push na `main` vira release e chega aos usuários pelo atualizador. Trabalho incompleto deve ir para outra branch.
- Não apague a release `latest` à mão: o app perderia a referência de versão até a próxima publicação.
