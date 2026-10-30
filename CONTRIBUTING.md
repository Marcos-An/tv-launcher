# Como contribuir

Relatos de bugs, melhorias de navegação e suporte a outras TVs são bem-vindos.

## Relatar um problema

Informe a versão do Android, a versão do projeto, o comportamento esperado e os passos para reproduzir. Para problemas de Watch Next, diga qual serviço não publica ou não retoma o conteúdo; não é necessário enviar seu histórico.

Revise qualquer captura ou log antes de anexar. Não envie IP da TV, número de série, identificadores de conta, nomes de perfis, tokens, cookies, URLs privadas ou chaves de assinatura.

## Desenvolver e validar

1. Compile com `./scripts/build.ps1`, usando os requisitos do README.
2. Abra `preview/index.html` para mudanças na demonstração. Ela usa dados fictícios e não acessa sua TV.
3. Para mudanças nativas, teste em um dispositivo autorizado: navegação por controle, rolagem nas bordas, abertura de apps e retorno com Home.
4. Confira Watch Next tanto com dados publicados quanto vazio ou sem permissão. Conteúdos e progresso não devem ser inventados quando ausentes.
5. Se alterar o ciclo de vida ou teclas, teste desligar e ligar pelo controle físico. Uma tecla injetada via ADB não comprova o funcionamento do controle.

Descreva no pull request o problema, a mudança e o que foi testado. Separe ajustes do HTML e do Android quando não representarem o mesmo comportamento.

## Arquivos locais

`.build/` e `.local/` são ignorados pelo Git. A chave gerada localmente é de desenvolvimento, usa a senha pública padrão do Android e não serve como chave de produção. Nunca publique uma chave privada de uma instalação real.

Antes de enviar, revise `git diff --cached` e `git status --short`. Não adicione APKs extraídos, exportações de histórico, capturas reais, logs privados ou arquivos de credenciais. Prefira exemplos sintéticos.
