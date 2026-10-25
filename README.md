# IES-Refatoracao-Software

[![CI](https://github.com/claudioferreira23/IES-Refatoracao-Software/actions/workflows/ci.yml/badge.svg)](https://github.com/claudioferreira23/IES-Refatoracao-Software/actions/workflows/ci.yml)

Repositório da atividade prática de refatoração da disciplina Implantação e Entrega de Software (INF/UFG, 2026/2). O ponto de partida é o `SubscriptionBillingService` legado da plataforma SaaS Billing Co., refatorado para separar as regras de preço, a infraestrutura e a orquestração, sem alterar o comportamento de negócio.

**Dupla:** Rafael Fernandes da Silva e Claudio Ferreira

O diagnóstico do débito técnico, as refatorações aplicadas, as métricas antes e depois e as decisões de comportamento estão no [Relatório Técnico](RELATORIO.md).

## Pré-requisitos

- JDK 17 ou superior
- Maven 3.8 ou superior

Os testes não precisam de banco de dados, servidor SMTP nem acesso à rede.

## Como executar

```bash
mvn test      # compila e executa os testes unitários
mvn verify    # executa os testes e gera o relatório de cobertura
```

O relatório de cobertura do JaCoCo fica em `target/site/jacoco/index.html`. Na CI (GitHub Actions, `.github/workflows/ci.yml`), cada push e pull request executa `mvn -B verify`, e o relatório é publicado como artefato `jacoco-report`.

## Estrutura

```
src/main/java/com/saas/billing
├── SubscriptionBillingService   orquestrador: calcula o preço, cobra, grava a fatura e envia o recibo
├── BillingServiceFactory        monta o serviço com as implementações reais e lê a configuração do ambiente
├── domain/                      regras de negócio puras, sem I/O
│   ├── Money                    valor monetário imutável (BigDecimal, 2 casas, HALF_EVEN)
│   ├── BillingCommand           parâmetros de uma cobrança
│   ├── PricingCalculator        descontos, cupons, multa e juros
│   └── Customer, CustomerStatus, Invoice, InvoiceStatus
├── port/                        interfaces que o serviço exige da infraestrutura
│   └── CustomerRepository, InvoiceRepository, PaymentGateway, NotificationService, InfrastructureException
└── adapter/                     implementações concretas das portas
    └── JdbcCustomerRepository, JdbcInvoiceRepository, SmtpNotificationService, TokenValidatingPaymentGateway

src/test/java/com/saas/billing
├── support/                     dublês em memória das portas usados nos testes
└── ...                          testes do domínio, do serviço e dos adaptadores
```

## Configuração

`BillingServiceFactory.createDefault()` lê a configuração das variáveis de ambiente:

| Variável | Obrigatória | Padrão |
|---|---|---|
| `BILLING_DB_URL` | sim | (sem padrão) |
| `BILLING_DB_USER` | sim | (sem padrão) |
| `BILLING_DB_PASSWORD` | não | vazio |
| `BILLING_SMTP_HOST` | não | `smtp.empresa.com` |
| `BILLING_SMTP_PORT` | não | `587` |
| `BILLING_SMTP_FROM` | não | `billing@empresa.com` |

Se `BILLING_DB_URL` ou `BILLING_DB_USER` não estiverem definidas, a criação do serviço falha com `IllegalStateException`. Os testes não usam a factory nem essas variáveis.
