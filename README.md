# TV Launcher

Launcher nativo e minimalista para Android TV, com uma prévia HTML sem dados pessoais.

## Recursos

- Banners obtidos dos aplicativos instalados; nenhuma arte extraída de terceiros acompanha o repositório.
- Carrosséis circulares: a seleção percorre os cartões visíveis antes de a fileira rolar.
- Degradê que acompanha o aplicativo selecionado.
- Ordem dos apps ajustada por contagem local de aberturas.
- Até dez itens de Watch Next, incluindo CONTINUE e NEXT, ordenados pela atividade publicada pelo app.
- Capas proporcionais, play central, links de retomada e atualização quando o provider muda.
- Nome de perfil editável e acesso às configurações da TV.

## Compilar no Windows

Requisitos: JDK 17 ou 21 e Android SDK com plataforma android-35 e Build Tools 35.0.0. O aplicativo usa minSdk 26 e targetSdk 28, testado em Android TV 9. Não é uma configuração validada para publicação na Play Store.

Defina JAVA_HOME e ANDROID_SDK_ROOT, depois execute:

```powershell
./scripts/build.ps1
```

O APK de desenvolvimento fica em .build/tv-launcher-debug.apk. Uma chave de desenvolvimento é gerada em .local/ e não deve ser versionada. O pacote genérico org.example.tvlauncher pode coexistir com outras versões locais; este código não inclui a chave de assinatura de nenhuma instalação anterior.

## Instalação

Autorize ADB no seu dispositivo e use o endereço da sua própria TV:

```text
adb connect TV_IP:5555
adb install -r .build/tv-launcher-debug.apk
adb shell pm grant org.example.tvlauncher android.permission.READ_TV_LISTINGS
adb shell am start -n org.example.tvlauncher/.MainActivity
```

Para definir o launcher como tela inicial, use a seleção de app padrão do Android ou, se suportado:

```text
adb shell cmd package set-home-activity org.example.tvlauncher/.MainActivity
```

## Limites de Continuar assistindo

Watch Next não é o histórico privado completo das contas. Cada app decide o que publica e quando atualiza. Registros podem estar antigos; posição e progresso só são exibidos quando disponibilizados. Alguns links exigem login ou seleção de perfil. Sem dados publicados, a seção permanece visível em estado vazio. O projeto não acessa cookies, tokens, senhas ou bancos privados dos apps.

## Privacidade

Esta cópia não inclui nomes pessoais, endereços de rede, histórico real, capas de conteúdos assistidos, capturas da TV, logs de diagnóstico, APKs, chaves ou caminhos locais. Diagnósticos e exportadores de histórico usados durante o desenvolvimento foram removidos do código. O perfil e as contagens de uso são salvos apenas nas preferências locais do aplicativo. As imagens remotas publicadas pelos serviços são baixadas para memória; não há telemetria.

## Prévia

Abra preview/index.html. Os dados são fictícios e os cartões usam nomes e cores de demonstração. O launcher real busca a arte dos apps instalados e os dados publicados no Android.
