# Relatório Técnico: Refatoração do núcleo de cobrança da SaaS Billing Co.

**Disciplina:** Implantação e Entrega de Software (2026/2), INF/UFG
**Professora:** Sofia Costa Paiva
**Dupla:** Rafael Fernandes da Silva e Claudio Ferreira

## Contexto

O ponto de partida foi a classe `SubscriptionBillingService`, que concentrava num único método de 88 linhas o cálculo de preço da assinatura, a aplicação de cupons, a consulta ao banco, a cobrança no cartão, a gravação da fatura e o envio do recibo por e-mail. A meta da refatoração era pagar esse débito técnico sem alterar as regras de negócio e deixar a suíte de testes unitários executável na CI sem PostgreSQL nem servidor SMTP.

As linhas citadas abaixo se referem ao arquivo legado original (commit `ead36d4`).

## 1. Matriz de Code Smells e Violações Arquiteturais

| # | Linha / trecho | Code smell ou princípio violado | "Juros" na manutenção e na entrega contínua |
|---|---|---|---|
| 1 | L18–105, `processMonthlyBilling` inteiro | **Long Method** (88 linhas, complexidade ciclomática 18) | Qualquer ajuste de regra exige entender o método todo. A revisão de código fica lenta, os conflitos de merge se concentram num único ponto e o risco de regressão em cada deploy aumenta. |
| 2 | L12–105, classe inteira | **Violação do Single Responsibility Principle** (precificação, persistência, pagamento e notificação na mesma classe) | A classe tem pelo menos quatro motivos para mudar. Uma troca de provedor de e-mail obriga a retestar e reimplantar o cálculo de preço, e não dá para evoluir as partes de forma independente. |
| 3 | L18–20, assinatura do método | **Long Parameter List** + **Primitive Obsession** (`double basePrice`, `String signupDateStr`, `boolean isNonProfit`, `String promoCode`) | Oito parâmetros posicionais, vários do mesmo tipo: trocar dois `String` de lugar compila e quebra em produção. A data chega como texto e só falha no `parse` em tempo de execução. O `boolean` solto é um *flag argument* ilegível na chamada. |
| 4 | L29–57, cálculos com `double` | **Primitive Obsession** para valores monetários (ponto flutuante binário em dinheiro) | Valores como `84.99999999` chegam à fatura e ao e-mail. Não existe regra de arredondamento definida, então centavos divergem entre fatura, cobrança e contabilidade, o que gera retrabalho e chamados de suporte. |
| 5 | L25, `LocalDate.now()` | **Dependência oculta do relógio do sistema** (acoplamento temporal) | Regras como o cupom `BLACKFRIDAY` só podem ser exercitadas em novembro. Os testes viram não determinísticos (*flaky*) e a CI passa ou falha conforme a data de execução. |
| 6 | L59–63, L82–87, L89–95: `DriverManager` e `SimpleEmail` instanciados dentro da regra | **Dependência direta de I/O** / violação do **Dependency Inversion Principle** | Não há como testar a regra sem um PostgreSQL em `localhost:5432` e um servidor SMTP. A esteira de CI precisaria de infraestrutura externa, que é exatamente o que a restrição da manutenção proíbe. |
| 7 | L14–16, `DB_URL`, `DB_USER`, `DB_PASS` | **Credenciais e configuração hardcoded** | A senha do banco fica versionada no GitHub. Mudar de ambiente (homologação, produção) exige recompilar e gerar um novo artefato, em vez de só trocar a configuração do deploy. |
| 8 | L100–102, `catch (Exception e)` | **Error Hiding** (exceções genéricas silenciadas) | Uma queda do banco vira uma linha de log e o método retorna normalmente. O cartão pode ter sido cobrado sem a fatura ser gravada, e nem o chamador nem o monitoramento ficam sabendo. Falhas de infraestrutura passam despercebidas em produção. |
| 9 | L96 `// emailSender.send();` e L18 `planType` | **Dead Code** (código comentado e parâmetro nunca usado) | O log afirma "E-mail disparado", mas nenhum e-mail sai. O parâmetro `planType` sugere uma regra por plano que não existe. Os dois enganam quem dá manutenção. |
| 10 | L31–53, literais `24`, `12`, `0.15`, `0.10`, `0.70`, `30.0`, `0.90`, `1`, `0.02`, `0.001`, `11` | **Magic Numbers** | As regras de negócio não têm nome. Mudar a multa por atraso exige caçar literais no meio do método, e o mesmo número pode significar coisas diferentes. |
| 11 | L61–63 e L83, `PreparedStatement` e `ResultSet` sem `try-with-resources` | **Vazamento de recursos** | Sob carga, cursores e *statements* abertos se acumulam no PostgreSQL até esgotar recursos. É o tipo de problema que só aparece em produção. |

## 2. Refatorações aplicadas

### 2.1 Operações do catálogo utilizadas

| Refatoração (catálogo) | Onde foi aplicada | Atalho correspondente no IntelliJ |
|---|---|---|
| Replace Primitive with Object | `double` → `Money` (record imutável com `BigDecimal`, escala 2, `HALF_EVEN`) | *Refactor › Type Migration* (Ctrl+Shift+F6) |
| Introduce Parameter Object | 8 parâmetros → `BillingCommand` | *Refactor › Introduce Parameter Object* |
| Remove Parameter | `planType`, que não era usado | *Change Signature* (Ctrl+F6) |
| Replace Type Code with Class | `status` String → `CustomerStatus`; `"PAID"/"FAILED"` → `InvoiceStatus` | manual (*New › Enum*) + *Type Migration* |
| Extract Interface | `CustomerRepository`, `InvoiceRepository`, `PaymentGateway`, `NotificationService` | *Refactor › Extract › Interface* |
| Extract Class / Move Method | SQL → `JdbcCustomerRepository` e `JdbcInvoiceRepository`; e-mail → `SmtpNotificationService`; validação do token → `TokenValidatingPaymentGateway` | *Refactor › Extract › Delegate* / *Move* (F6) |
| Introduce Parameter (injeção de dependência) | `java.time.Clock` e portas recebidas pelo construtor | *Introduce Parameter* (Ctrl+Alt+P) / *Introduce Field* (Ctrl+Alt+F) |
| Extract Class | Regras de preço → `PricingCalculator` (domínio puro, sem I/O) | *Refactor › Extract › Delegate* |
| Extract Method | `applyLoyaltyDiscount`, `applyNonProfitDiscount`, `applyPromoCode`, `applyLateCharges`, `monthsBetween`, `bill`, `sendReceipt` | *Extract Method* (Ctrl+Alt+M) |
| Replace Magic Number with Symbolic Constant | Todas as taxas, limites e códigos promocionais | *Introduce Constant* (Ctrl+Alt+C) |
| Replace Nested Conditional with Guard Clauses | Cliente inativo e cliente inexistente no orquestrador | *Invert 'if'* (Alt+Enter) |
| Remove Dead Code | `// emailSender.send();`, `planType` e o teste `trim().isEmpty()`, que não alterava o resultado | *Safe Delete* (Alt+Delete) |

### 2.2 Arquitetura resultante

```
com.saas.billing
├── SubscriptionBillingService   fachada/orquestrador (método público com 7 linhas)
├── BillingServiceFactory        composition root: liga as implementações reais e lê config do ambiente
├── domain/                      núcleo puro, sem dependência de rede ou banco
│   ├── Money, BillingCommand, Customer, CustomerStatus, Invoice, InvoiceStatus
│   └── PricingCalculator
├── port/                        contratos que o núcleo exige do mundo externo
│   ├── CustomerRepository, InvoiceRepository, PaymentGateway, NotificationService
│   └── InfrastructureException
└── adapter/                     implementações concretas das portas
    ├── JdbcCustomerRepository, JdbcInvoiceRepository
    ├── SmtpNotificationService
    └── TokenValidatingPaymentGateway
```

O serviço depende apenas das interfaces em `port/`, e os testes injetam implementações em memória (`src/test/java/.../support`) e um `Clock.fixed(...)`. Os adaptadores capturam apenas a exceção específica da tecnologia (`SQLException`, `EmailException`) e a relançam como `InfrastructureException`, preservando a causa. Assim a falha chega ao chamador em vez de ser silenciada. A única exceção capturada no serviço é a `InfrastructureException` do envio do recibo, tratada em `sendReceipt` (ver seção 4).

A `BillingServiceFactory` exige `BILLING_DB_URL` e `BILLING_DB_USER` e falha com `IllegalStateException` se alguma estiver ausente. Não há mais URL nem usuário de banco com valor padrão no código.

## 3. Métricas antes vs. depois

A complexidade ciclomática (CCN), o NLOC e a contagem de parâmetros foram medidos com a ferramenta [lizard](https://github.com/terryyin/lizard) sobre o código de produção. As demais linhas foram contadas diretamente no código. A cobertura foi medida com o JaCoCo (`mvn verify`, relatório em `target/site/jacoco`).

| Métrica | Antes | Depois |
|---|---|---|
| Linhas do método principal (`processMonthlyBilling`) | 88 | 7 |
| NLOC do método principal | 70 | 7 |
| Complexidade ciclomática do método principal | 18 | 1 |
| Maior complexidade ciclomática do projeto | 18 | 5 (`PricingCalculator.applyPromoCode`) |
| Complexidade ciclomática média por método | 18 | 1,7 (medida antes das correções pós-auditoria, que acrescentaram `sendReceipt`, com CCN 2) |
| Parâmetros do método principal | 8 | 1 (`BillingCommand`) |
| Tipos de produção (classes, records, enums, interfaces) | 1 | 18 |
| Imports de I/O (`java.sql.*`, `commons-mail`) no serviço | 5 | 0 |
| Variáveis `double` representando dinheiro | 4 | 0 |
| Literais mágicos nas regras de preço | 11 | 0 |
| Chamadas a `LocalDate.now()` sem relógio injetado | 1 | 0 |
| Blocos `catch (Exception e)` | 1 | 0 |
| Credenciais e endereço do banco hardcoded (URL, usuário, senha) | 3 | 0 |
| Recursos JDBC sem fechamento garantido | 3 | 0 |
| Testes unitários automatizados | 0 | 42 |
| Cobertura de linhas (JaCoCo), projeto inteiro | n/a | 72,5% (111/153) |
| Cobertura de linhas (JaCoCo), domínio, serviço e portas | n/a | 100% (98/98) |
| Cobertura de ramos (JaCoCo), projeto inteiro | n/a | 100% (36/36) |
| Divergências de valor em relação ao legado (102.960 combinações) | n/a | 274, todas empates `x,xx5` |
| Divergências de status da fatura em relação ao legado | n/a | 0 |
| Infraestrutura externa exigida pelos testes | PostgreSQL + SMTP | nenhuma |

A suíte tem seis classes:

- `MoneyTest` cobre o arredondamento, a imutabilidade e as operações.
- `PricingCalculatorTest` cobre cada regra isoladamente e inclui um teste de caracterização que compara o novo cálculo com a fórmula legada em 3.840 combinações de preço, tempo de casa, cupom, atraso, ONG e mês. O valor novo precisa ser igual ao resultado legado arredondado a centavos. Só se aceita diferença de 1 centavo quando o valor legado cai exatamente na metade de um centavo (empate `x,xx5`). Há também um caso de regressão fixo: base 19,90, 25 meses e 365 dias de atraso, cujo valor legado de 23,427275 deve resultar em 23,43.
- `CustomerStatusTest` cobre o status lido do banco sem diferenciar maiúsculas de minúsculas, com `null` e valores desconhecidos tratados como inativos.
- `TokenValidatingPaymentGatewayTest` cobre a regra do prefixo `tok_`.
- `JdbcCustomerRepositoryTest` usa um `DataSource` falso (`java.lang.reflect.Proxy`, sem banco) para testar o mapeamento, o cliente inexistente, o fechamento de `ResultSet`, `PreparedStatement` e `Connection` e a tradução de `SQLException`.
- `SubscriptionBillingServiceTest` cobre o fluxo completo com dublês em memória: pagamento aprovado e recusado, cliente inativo e inexistente, relógio injetado, propagação de falha do repositório de clientes e de gravação da fatura e falha no envio do recibo.

Além da suíte, comparamos o legado (executado com um driver JDBC falso e relógio fixo) com o código refatorado em 102.960 combinações. O status da fatura nunca diverge. O valor diverge em 274 casos (0,27%), sempre em 1 centavo e sempre em empates `x,xx5`. A seção 4 explica a política de arredondamento. Os adaptadores `JdbcInvoiceRepository` e `SmtpNotificationService` e a `BillingServiceFactory` não têm testes, e por isso a cobertura de linhas do projeto inteiro é menor que a do domínio.

## 4. Diferenças de comportamento observáveis

As regras de negócio (descontos por tempo de casa, ONG, cupons, multa, juros e piso zero) foram preservadas, e o teste de caracterização garante isso. As diferenças abaixo são intencionais e decorrem dos objetivos da refatoração:

1. **Valores em centavos e política de arredondamento.** O `PricingCalculator` faz todas as etapas em `BigDecimal` sem arredondar e arredonda uma única vez, no valor final, para 2 casas com `RoundingMode.HALF_EVEN`. Uma primeira versão arredondava cada operação intermediária (desconto, multa e juros), e o total divergia do legado em 1 centavo em cerca de 13,5% das combinações. Com o arredondamento único, o resultado coincide com o valor legado arredondado, exceto nos empates `x,xx5`. Nesses casos o legado nunca definiu um arredondamento: ele gravava o `double` cru, e esse `double` muitas vezes carrega ruído binário logo acima ou abaixo da metade do centavo. Escolhemos `HALF_EVEN` (arredondamento bancário) porque ele não tem viés sistemático para cima em grandes volumes de faturas. Trocar para `HALF_UP` exige mudar só a constante em `Money`. Os logs e o e-mail passam a exibir o valor com duas casas (`R$ 100.00`), e não a representação crua do `double` (`R$ 100.0`, `R$ 84.99999999`).
2. **Falhas de infraestrutura propagam.** Antes, um erro de banco virava a linha de log `Erro fatal no processamento: ...`. Agora o método lança `InfrastructureException` com a causa original, e cabe ao chamador decidir como tratar.
3. **O recibo é enviado de fato.** No legado, `send()` estava comentado e o log mentia. O `SmtpNotificationService` realmente envia o e-mail.
4. **Falha depois da cobrança.** Como o e-mail agora é enviado, uma falha de SMTP poderia lançar exceção depois de o cartão já ter sido cobrado e a fatura gravada, e o chamador que tentasse de novo cobraria o cliente duas vezes. Por isso o recibo é tratado como não crítico: uma `InfrastructureException` no envio vira o log `Falha ao enviar recibo para: <e-mail>`, e a fatura continua registrada. Já uma falha ao gravar a fatura propaga a exceção, porque cobrar sem registrar é mais grave e precisa chegar ao chamador.
5. **E-mail nulo no cadastro.** No legado, um cliente sem e-mail gerava o log `Erro fatal no processamento: ...` depois da cobrança e da gravação da fatura. No código atual, o `SmtpNotificationService` lança `NullPointerException` (vinda do `commons-email`), que não é traduzida para `InfrastructureException` e, portanto, sai de `processMonthlyBilling` depois da cobrança e da gravação. É um caso de borda conhecido e ainda não tratado.
6. **Configuração externa.** URL, usuário e senha do banco e os dados do SMTP vêm das variáveis de ambiente `BILLING_DB_URL`, `BILLING_DB_USER`, `BILLING_DB_PASSWORD`, `BILLING_SMTP_HOST`, `BILLING_SMTP_PORT` e `BILLING_SMTP_FROM`. URL e usuário do banco são obrigatórios e a senha não tem valor padrão no código.

**Limitação do processo:** o teste de caracterização que compara o cálculo com a fórmula legada só entrou no commit 4. Os commits 2 e 3 alteraram a aritmética e o fluxo sem essa rede de segurança. A divergência de arredondamento por etapa foi identificada depois, na auditoria, e corrigida na correção pós-auditoria descrita na seção 5.

## 5. Histórico no Git

| Commit | Hash | Conteúdo |
|---|---|---|
| 1 | `ead36d4` | Adição do código legado base |
| 2 | `5c8e59e` | Estrutura Maven, Value Object `Money`, Parameter Object `BillingCommand` e remoção da Primitive Obsession |
| 3 | `c208f40` | Extração das portas de infraestrutura, adaptadores JDBC/SMTP, injeção de dependências e de `Clock`, remoção do `catch` genérico e das credenciais |
| 4 | `fa09419` | Extração do `PricingCalculator`, orquestrador como fachada e suíte completa verde |

Cada commit compila e mantém os testes existentes passando.

### Correções pós-auditoria

| Branch | Conteúdo |
|---|---|
| `fix/arredondamento-resultado-final` | Arredondamento único no resultado final do `PricingCalculator`; teste de caracterização restrito a empates e caso de regressão 19,90/25 meses/365 dias |
| `test/cobertura-regras-legado` | Testes da regra `tok_`, do `CustomerStatus.fromCode` e do `JdbcCustomerRepository` |
| `fix/falha-pos-cobranca` | Recibo tratado como não crítico depois da cobrança; testes de falha do envio e da gravação da fatura |
| `ci/github-actions-jacoco` | Workflow do GitHub Actions (`mvn -B verify`) e JaCoCo no `pom.xml` |
| `docs/readme-relatorio-final` | Factory sem valores padrão para o banco, README e este relatório atualizados |

## 6. Como executar

Requer JDK 17+ e Maven 3.8+. Os comandos, a estrutura do projeto e as variáveis de ambiente estão no [README](README.md).

```bash
mvn verify
```

Os testes não precisam de banco de dados nem de servidor de e-mail.
