# TV Launcher

Launcher nativo e minimalista para Android TV, com uma prévia HTML sem dados pessoais.

Projeto experimental, feito para navegação com controle remoto. É um aplicativo de tela inicial: não substitui o firmware da TV. O código pode ser usado, modificado e distribuído conforme a [licença MIT](LICENSE).

**Estado atual:** compilação e assinatura verificadas; desenvolvido em Android TV 9. A compatibilidade com outras TVs ainda precisa de validação. Houve uma falha de desligamento pelo controle no dispositivo de desenvolvimento, ainda sem causa determinada, inclusive ao retornar à tela inicial original. Não há correção de energia comprovada neste projeto.

## Recursos

- Banners obtidos dos aplicativos instalados; nenhuma arte extraída de terceiros acompanha o repositório.
- Carrosséis com início e fim: a seleção percorre os cartões visíveis antes de a fileira rolar e para nas bordas.
- Som de navegação do sistema ao mover a seleção e ao confirmar.
- Cada fileira lembra a posição selecionada ao subir e descer.
- Cartão "Todos os apps" no fim da fileira, com todos os apps de TV instalados.
- Lista atualizada quando um app é instalado, removido ou desativado.
- Degradê que acompanha o aplicativo selecionado.
- Ordem dos apps ajustada por contagem local de aberturas.
- Até dez itens de Watch Next, incluindo CONTINUE e NEXT, ordenados pela atividade publicada pelo app.
- Capas proporcionais, play central, links de retomada e atualização quando o provider muda.
- Nome de perfil editável e acesso às configurações da TV.

A lista de aplicativos é uma seleção de serviços de entretenimento: somente os pacotes dessa seleção encontrados na TV aparecem na fileira; os demais ficam em "Todos os apps". Ela não instala serviços de streaming nem inclui assinaturas. Para adaptar a seleção, edite `catalog` em [MainActivity.java](app/src/main/java/org/example/tvlauncher/MainActivity.java) e confira o identificador do pacote instalado.

## Compilar no Windows

Requisitos: JDK 17 ou 21 e Android SDK com plataforma android-35 e Build Tools 35.0.0. O aplicativo usa minSdk 26 e targetSdk 28, testado em Android TV 9. Não é uma configuração validada para publicação na Play Store.

Defina JAVA_HOME e ANDROID_SDK_ROOT, depois execute:

```powershell
./scripts/build.ps1
```

O APK de desenvolvimento fica em .build/tv-launcher-debug.apk. Uma chave de desenvolvimento é gerada em .local/ e não deve ser versionada. O pacote genérico org.example.tvlauncher pode coexistir com outras versões locais; este código não inclui a chave de assinatura de nenhuma instalação anterior.

## Instalação

Autorize ADB no seu dispositivo e use o endereço da sua própria TV:

Antes de trocar a tela inicial, registre a atividade HOME original:

```text
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Guarde o componente retornado, no formato `pacote/atividade`, para conseguir voltar. Execute a instalação a seguir e teste o aplicativo antes de torná-lo padrão.

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

Não é necessário remover ou desabilitar componentes da fabricante. Mantenha o launcher original instalado. Depois de configurar, teste Home, Voltar, volume, desligar e ligar pelo controle físico. Desconecte o ADB quando terminar e desative a depuração se não precisar mais dela.

### Voltar à tela inicial anterior

Substitua `ORIGINAL_PACKAGE/ORIGINAL_ACTIVITY` pelo componente que você registrou:

```text
adb shell cmd package set-home-activity ORIGINAL_PACKAGE/ORIGINAL_ACTIVITY
adb shell am start -n ORIGINAL_PACKAGE/ORIGINAL_ACTIVITY
adb uninstall org.example.tvlauncher
```

A desinstalação remove as preferências locais deste launcher. Ela não remove suas contas dos serviços de streaming.

## Navegação

- Direita/esquerda: percorrem o carrossel; a fileira rola quando a seleção alcança a borda e para no primeiro e no último cartão.
- Cima/baixo: alternam entre as áreas da tela.
- OK: abre o app, tenta retomar o conteúdo ou ativa o controle selecionado.
- Configurações: abre os ajustes do Android; perfil: permite editar o nome local.

O nome de perfil do launcher é apenas visual. Ele não seleciona automaticamente o perfil dentro de Netflix, Globoplay ou outros serviços.

## Limites de Continuar assistindo

Watch Next não é o histórico privado completo das contas. Cada app decide o que publica e quando atualiza. Registros podem estar antigos; posição e progresso só são exibidos quando disponibilizados. Alguns links exigem login ou seleção de perfil. Sem dados publicados, a seção permanece visível em estado vazio. O projeto não acessa cookies, tokens, senhas ou bancos privados dos apps.

## Privacidade

Esta cópia não inclui nomes pessoais, endereços de rede, histórico real, capas de conteúdos assistidos, capturas da TV, logs de diagnóstico, APKs, chaves ou caminhos locais. Diagnósticos e exportadores de histórico usados durante o desenvolvimento foram removidos do código. O perfil e as contagens de uso são salvos apenas nas preferências locais do aplicativo. As imagens remotas publicadas pelos serviços são baixadas para memória; não há telemetria.

Esses downloads se conectam aos servidores que hospedam as imagens. As permissões de leitura de TV permitem consultar os conteúdos publicados no provider Android; `INTERNET` permite carregar as capas. `allowBackup=false` desativa o backup Android dos dados do aplicativo. Ao compartilhar relatos de erro, remova nomes de perfil, títulos assistidos, endereços de rede e URLs de retomada.

## Prévia

Abra preview/index.html. Os dados são fictícios e os cartões usam nomes e cores de demonstração. O launcher real busca a arte dos apps instalados e os dados publicados no Android.

## Estrutura e contribuição

```text
app/src/main/AndroidManifest.xml                 Permissões e atividade HOME
app/src/main/java/org/example/tvlauncher/         Implementação nativa em Java
preview/index.html                              Demonstração independente
scripts/build.ps1                               Compilação e assinatura local
```

Não há dependências Java externas, Gradle nem serviço de backend. Veja [CONTRIBUTING.md](CONTRIBUTING.md) para validar mudanças e enviar relatos sem dados pessoais. Marcas e imagens obtidas dos aplicativos pertencem aos respectivos titulares; a licença cobre o código deste repositório.
