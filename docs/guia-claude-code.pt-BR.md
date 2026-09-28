# Guia: construindo este repositório com o Claude Code (Spec-Driven)

Este guia é para você, não para o recrutador. Ele explica como usar o Claude Code para construir o
projeto fase a fase, a partir das specs em `specs/`.

## A ideia em uma frase

Você não pede "crie um microsserviço". Você aprova **o quê** (`requirements.md`), depois **o como**
(`design.md`), depois os **passos** (`tasks.md`) — e o Claude Code implementa um passo por vez,
com teste primeiro. As specs das fases 00 a 06 já estão escritas e aprovadas.

```
requirements.md ──▶ design.md ──▶ tasks.md ──▶ /spec-implement (1 tarefa) ──▶ /spec-review
```

## 1. Pré-requisitos (Windows)

| Ferramenta | Para quê | Como conferir |
| --- | --- | --- |
| JDK 21 (Temurin) | Compilar e rodar | `java -version` |
| Docker Desktop (com WSL 2) | Infra local e Testcontainers | `docker version` |
| Git for Windows | Versionamento; o Claude Code no Windows usa o Git Bash | `git --version` |
| Claude Code | O agente | `claude --version` |
| Conta no GitHub | Repositório, board, CI | — |

Maven **não** precisa ser instalado: a fase 00 cria o Maven Wrapper. Na primeira tarefa, se o
Claude Code precisar gerar o wrapper e não houver Maven, ele pode baixar o wrapper manualmente —
deixe-o resolver e confira o resultado.

Para instalar o Claude Code, siga a documentação oficial: <https://docs.claude.com/en/docs/claude-code/setup>.

## 2. Primeiro uso

Abra o terminal na pasta do projeto:

```powershell
cd C:\Huggs\Estudos\JAVA\fuel-dispatch-platform
git init -b main
claude
```

Dentro do Claude Code, a primeira mensagem:

```
Siga o _setup/README.md para mover os comandos e o template de PR.
Depois leia o CLAUDE.md e os três arquivos de specs/steering e me diga se entendeu o fluxo.
```

Saia (`/exit`) e abra o `claude` de novo para ele carregar os comandos novos. Então rode
`/spec-status` e faça o commit inicial.

O `CLAUDE.md` é carregado automaticamente em toda sessão; ele obriga o fluxo spec-driven.

## 3. Ciclo de trabalho por tarefa

1. `/spec-status` — mostra onde você está e a próxima tarefa.
2. `/spec-implement` — implementa **uma** tarefa: teste que falha → código → build verde →
   marca o checkbox → sugere a mensagem de commit. Para aí.
3. **Leia o diff.** Você vai defender este código numa entrevista. Pergunte "por que assim?"
   sempre que não souber explicar.
4. Commit (peça ao Claude Code ou faça você): `git add . && git commit -m "<mensagem sugerida>"`.
5. `/clear` para limpar o contexto e começar a próxima tarefa com a cabeça fresca.

No fim de cada fase: `/spec-review NN`, corrija o que faltar, abra um PR para `main` e faça merge.

## 4. Quando a spec estiver errada

Vai acontecer (versões de biblioteca, detalhes que só aparecem no código). A regra do `CLAUDE.md`
é: o Claude Code para e propõe a mudança na spec. Você aprova, ele atualiza `requirements.md` /
`design.md` e só então segue. Spec e código nunca divergem — isso é o que torna o repo defensável.

Para a fase 07 (opcional), rode `/spec-design 07` e depois `/spec-tasks 07`.

## 5. Dicas de uso do Claude Code

- **Plan mode** (Shift+Tab até aparecer "plan mode") antes de tarefas grandes: ele planeja sem
  editar arquivos.
- Peça explicações em português: "explique esta classe como se eu fosse apresentá-la numa entrevista".
- Depois de cada fase, peça: "gere 5 perguntas que um entrevistador faria sobre o que construímos
  nesta fase, com respostas curtas". Isso vira seu material de estudo.
- Não aceite tarefas gigantes. Se uma tarefa gerar muitos arquivos de uma vez, peça para dividir.
- O Docker Desktop precisa estar aberto para os testes de integração (Testcontainers).

## 6. Ordem e prioridade

| Fase | Tarefas | Prioridade |
| --- | --- | --- |
| 00 Foundation | 8 | Essencial |
| 01 Dispatch domain | 8 | Essencial |
| 02 Dispatch API | 8 | Essencial |
| 03 Dispatch events | 9 | Essencial |
| 04 Tracking reactive | 9 | Essencial — **publique o repo ao terminar** |
| 05 Resiliência e observabilidade | 8 | Forte diferencial |
| 06 Entrega (CI/CD, Kubernetes) | 9 | Requisito da vaga |
| 07 AWS e Kotlin | — | Opcional |

Se a entrevista técnica chegar antes do fim, publique o que tiver: um repo com as fases 00–02
bem feitas, specs visíveis e roadmap honesto vale mais que um repo completo que você não sabe explicar.

## 7. Como isso ajuda na entrevista

A própria pasta `specs/` é um argumento: mostra que você escreve requisitos testáveis (formato
EARS), desenha antes de codar, rastreia requisito → teste → commit e documenta decisões em ADRs.
É exatamente o que se espera de um Sênior em consultoria. Frase para usar:

> "Construí o projeto com spec-driven development: cada funcionalidade começa como requisito com
> critérios de aceite, vira um design e depois tarefas pequenas, sempre com teste primeiro. Usei IA
> como par de programação, mas cada decisão está documentada e eu consigo defender cada uma."

Seja transparente sobre o uso do Claude Code se perguntarem — o diferencial é você saber explicar
o código e as decisões.
