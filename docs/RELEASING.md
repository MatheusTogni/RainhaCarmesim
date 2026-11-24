# Como publicar uma nova versão

O atualizador do app lê **apenas** o GitHub Releases. Cada release precisa conter:

- `RainhaCarmesim-X.Y.Z.apk`: o APK assinado com a chave do projeto;
- `latest.json`: os metadados lidos pelo app;
- `RainhaCarmesim-X.Y.Z.apk.sha256`: o hash, para conferência manual.

Tudo isso é gerado automaticamente pelo workflow **Build** (`.github/workflows/build.yml`) **a cada push na `main`**. Pull requests só testam e compilam, sem publicar.

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

Sem esses secrets:
- o push na `main` falha de propósito e não publica nada;
- um pull request gera um APK assinado com a chave de debug, que serve só para teste e não instala por cima da versão oficial.

Antes de publicar, o workflow também confere, com o `apksigner`, que o APK não saiu com a chave de debug antes de publicar.

Para assinar localmente com a chave oficial, defina as variáveis de ambiente (ou coloque as entradas em `~/.gradle/gradle.properties`, **fora** do projeto):
```
RC_KEYSTORE_PATH=C:/caminho/rainha-carmesim-release.p12
RC_KEYSTORE_PASSWORD=...
RC_KEY_ALIAS=rainhacarmesim
RC_KEY_PASSWORD=...
```

## Esquema de versões

A versão sai do número do run do workflow (`N`), sem nada manual:

- `versionCode` = `10000 + N`;
- `versionName` = `1.Y.Z`, com `Y*100 + Z = N`. Por exemplo, o run 7 vira `1.0.7` (`10007`) e o run 123 vira `1.1.23` (`10123`).

Isso segue o esquema `X*10000 + Y*100 + Z` do app e sempre cresce, então cada push gera uma versão maior que a anterior. O app nunca oferece downgrade.

As notas da versão exibidas no app são a mensagem do commit que disparou o build. Para uma nota caprichada, capriche na mensagem do commit.

Para publicar de novo sem mudar código: **Actions › Build › Run workflow** na `main`.

## O que o workflow faz

1. Calcula a versão e o `versionCode` a partir do número do run.
2. Valida o Gradle wrapper, roda os testes unitários e o lint e compila o `assembleRelease` com a versão.
3. Confere com o `apksigner` que o APK não saiu com a chave de debug.
4. Calcula o SHA‑256 do APK e gera o `latest.json`:
   ```json
   {
     "versionName": "1.0.7",
     "versionCode": 10007,
     "releaseNotes": "Mensagem do commit",
     "apkUrl": "https://github.com/MatheusTogni/RainhaCarmesim/releases/download/v1.0.7/RainhaCarmesim-1.0.7.apk",
     "sha256": "…",
     "publishedAt": "2026-11-24T12:00:00Z"
   }
   ```
5. Guarda o APK como artefato do workflow e cria a release `v1.Y.Z`, marcada como *latest*.

O app consulta `releases/latest/download/latest.json`, que sempre aponta para a release mais recente que não seja rascunho nem pre-release.

## Primeira publicação

1. Cadastre os 4 secrets acima.
2. Faça um push na `main` (ou rode **Actions › Build › Run workflow**) e confira se a release apareceu.
3. Instale o APK dessa release no celular.
4. Para testar o atualizador, faça outro push. Depois, no app, vá em **Settings › Verificar atualizações**.

## Cuidados

- **Nunca perca nem troque a chave de release, e nunca mude o `applicationId`.** Sem a mesma chave, o Android recusa instalar a nova versão por cima, e o usuário teria que desinstalar e perder os dados. Guarde o `.p12` e a senha num cofre, com backup.
- **Nunca coloque a chave no repositório.** O `.gitignore` já bloqueia `*.p12`, `*.jks` e `*.keystore`.
- Mudanças no banco Room exigem incrementar a versão do `AppDatabase` e escrever uma `Migration`, para preservar os dados na atualização.
- Todo push na `main` vira release e chega aos usuários pelo atualizador. Trabalho incompleto deve ir para outra branch e entrar na `main` por pull request.
- Não apague a release mais recente: o app perderia a referência de versão até a próxima publicação.
