# Como publicar uma nova versão

O atualizador do app lê **apenas** o GitHub Releases. Cada release precisa conter:

- `RainhaCarmesim-X.Y.Z.apk`: o APK assinado com a chave do projeto;
- `latest.json`: os metadados lidos pelo app;
- `RainhaCarmesim-X.Y.Z.apk.sha256`: o hash, para conferência manual.

Tudo isso é gerado automaticamente pelo workflow **Release** (`.github/workflows/release.yml`).

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
- o workflow **Release** falha de propósito;
- o workflow **Build** gera um APK assinado com a chave de debug, que serve só para teste e não instala por cima da versão oficial.

O workflow **Release** também confere, com o `apksigner`, que o APK não saiu com a chave de debug antes de publicar.

Para assinar localmente com a chave oficial, defina as variáveis de ambiente (ou coloque as entradas em `~/.gradle/gradle.properties`, **fora** do projeto):
```
RC_KEYSTORE_PATH=C:/caminho/rainha-carmesim-release.p12
RC_KEYSTORE_PASSWORD=...
RC_KEY_ALIAS=rainhacarmesim
RC_KEY_PASSWORD=...
```

## Esquema de versões

- `versionName` = `X.Y.Z`, com `Y` e `Z` entre 0 e 99.
- `versionCode` = `X*10000 + Y*100 + Z`. Por exemplo, `1.2.3` vira `10203`.
- O workflow recusa publicar uma versão cujo `versionCode` não seja maior que o da release atual. O app também nunca oferece downgrade.

## Opção A — por tag (recomendado)

```bash
git checkout main
git pull
# anote o que mudou na mensagem da tag: ela vira as notas da versão exibidas no app
git tag -a v1.0.1 -m "Correção no scanner e melhorias no monitor"
git push origin v1.0.1
```

## Opção B — pela interface do GitHub

**Actions › Release › Run workflow**. Informe a versão (ex.: `1.0.1`) e, se quiser, as notas. O workflow cria a tag no commit escolhido.

## O que o workflow faz

1. Valida a versão e calcula o `versionCode`.
2. Confere se a versão é maior que a publicada e se a release ainda não existe.
3. Valida o Gradle wrapper, roda os testes unitários e compila o `assembleRelease` com a versão.
4. Calcula o SHA‑256 do APK e gera o `latest.json`:
   ```json
   {
     "versionName": "1.0.1",
     "versionCode": 10001,
     "releaseNotes": "Correção no scanner e melhorias no monitor",
     "apkUrl": "https://github.com/MatheusTogni/RainhaCarmesim/releases/download/v1.0.1/RainhaCarmesim-1.0.1.apk",
     "sha256": "…",
     "publishedAt": "2026-11-24T12:00:00Z"
   }
   ```
5. Guarda o APK como artefato do workflow e cria a release marcada como *latest*.

O app consulta `releases/latest/download/latest.json`, que sempre aponta para a release mais recente que não seja rascunho nem pre-release.

## Primeira publicação

1. Crie o repositório `MatheusTogni/RainhaCarmesim` no GitHub, cadastre os 4 secrets acima e envie o código para a `main`.
2. Confira se o workflow **Build** passou.
3. Publique a `v1.0.0` (Opção A ou B) e instale o APK dessa release no celular.
4. Para testar o atualizador, publique a `v1.0.1`. Depois, no app, vá em **Settings › Verificar atualizações**.

## Cuidados

- **Nunca perca nem troque a chave de release, e nunca mude o `applicationId`.** Sem a mesma chave, o Android recusa instalar a nova versão por cima, e o usuário teria que desinstalar e perder os dados. Guarde o `.p12` e a senha num cofre, com backup.
- **Nunca coloque a chave no repositório.** O `.gitignore` já bloqueia `*.p12`, `*.jks` e `*.keystore`.
- Mudanças no banco Room exigem incrementar a versão do `AppDatabase` e escrever uma `Migration`, para preservar os dados na atualização.
- Os APKs do workflow **Build** usam versão `0.0.N` com `versionCode` igual ao número do run. Servem só para teste e não são oferecidos pelo atualizador.
- Não apague a release mais recente: o app perderia a referência de versão até a próxima publicação.
