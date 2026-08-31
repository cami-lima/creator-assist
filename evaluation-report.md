# Evaluation report

## Tax calculation cases

| Case | Expected tax | Final solution | MEI warning | Result |
|---|---|---|---|---|
| T1 | R$ 0.00 | R$ 0.00 (OK) | n/a | PASS |
| T2 | R$ 651.83 | R$ 651.83 (OK) | n/a | PASS |
| T3 | R$ 1346.27 | R$ 1346.27 (OK) | n/a | PASS |
| T4 | n/a (ambiguous) | ambiguous=true | n/a | PASS (flagged as ambiguous) |
| T5 | R$ 1566.27 | R$ 1566.27 (OK) | expected=true, got=true | PASS |

## Contract analysis cases

| Case | Expectation | Final solution result | Result |
|---|---|---|---|
| contract_c1_clean.md | zero risk clauses | no risk clauses found | PASS |
| contract_c2_perpetual_image.md | at least one HIGH risk clause about image rights | HIGH: Cessão perpétua e irrevogável de direitos de imagem; HIGH: Sobrevivência da cessão de imagem após a rescisão | PASS |
| contract_c3_disguised_exclusivity.md | at least one risk clause flagged (hard case) | HIGH: Exclusividade disfarçada e controle unilateral | PASS |

Note: contract cases (especially the hard case, disguised exclusivity) are automatically checked only for whether the agent found something, not for exact wording. Read docs/test_cases_and_answer_key.md and the raw model output to judge quality manually, that comparison is part of the submission write-up. These prompts were also only validated against the 3 synthetic contracts in this repository, all written by the developer, so behavior on a real, unseen contract is not yet verified (see CHANGELOG.md's known limitations).
## Memory state actually persisted to the database

Not inferred from test results. This is a direct query (`memoryRepository.findAll()`) against the same H2 database the agents above just wrote to, run at the very end of this evaluation. Every row below is real, persisted data.

| creatorId | referenceMonth | taxRegime | yearToDateRevenue | taxPaidThisMonth |
|---|---|---|---|---|
| eval-T1 | 2026-06 | SELF_EMPLOYED | 3200.00 | 0.00 |
| eval-T2 | 2026-06 | SELF_EMPLOYED | 6660.00 | 651.83 |
| eval-T3 | 2026-05 | SELF_EMPLOYED | 40000.00 | 0.00 |
| eval-T3 | 2026-06 | SELF_EMPLOYED | 48200.00 | 1346.27 |
| eval-T5 | 2026-05 | MEI | 76000.00 | 0.00 |
| eval-T5 | 2026-06 | MEI | 85000.00 | 1566.27 |

## Baseline full responses

Full, untruncated baseline output for every tax case, for manual comparison against the final solution's result above. "Figures mentioned" is a best-effort regex scan for any "R$ ..." amount in the response. It is not an automatic correctness check, just a pointer for the reader's eye.

### T1

Figures mentioned: R$ 3.200,00, R$ 3.200,00, R$ 564,80, R$ 3.200,00, R$ 564,80, R$ 2.635,20

```
Olá! Claro, posso te ajudar com o cálculo do imposto e com a análise do seu contrato. 

Como você **não anexou o texto do contrato** na sua mensagem, farei primeiro a explicação sobre a tributação (Carnê-Leão) do valor recebido. Em seguida, me diga o texto do contrato para que eu possa analisar os pontos de atenção!

---

### 1. Cálculo do Carnê-Leão (R$ 3.200,00)

Para saber se você precisa ou não pagar o Carnê-Leão, o primeiro passo é identificar **quem fez o pagamento**:

#### 🏢 **Cenário A: A Marca X é Pessoa Jurídica (CNPJ)**
Se o Pix veio do CNPJ de uma empresa brasileira:
* **Você NÃO precisa pagar Carnê-Leão.**
* A responsabilidade de reter e recolher o Imposto de Renda (IRRF) é da própria empresa que te contratou.
* Você apenas deve guardar o comprovante/recibo e pedir o *Informe de Rendimentos* da empresa para incluir na sua Declaração Anual de Ajuste.

#### 👤 **Cenário B: A Marca X te pagou via Pessoa Física (CPF)**
Se o Pix veio da conta de uma pessoa física (ex: o dono da marca pagou pelo CPF dele):
* **Você É OBRIGADO a recolher o Carnê-Leão.**
* O cálculo é feito com base na **Tabela Progressiva do Imposto de Renda de 2024**:

**Passo a passo do cálculo (base 2024):**
1. **Rendimento Bruto:** R$ 3.200,00
2. **Desconto Simplificado da Receita:** R$ 564,80 *(aplicado automaticamente se for mais benéfico)*
3. **Base de Cálculo:** R$ 3.200,00 - R$ 564,80 = **R$ 2.635,20**
4. **Enquadramento na Tabela:** Faixa de 7,5% (
```

### T2

Figures mentioned: none found

```
Olá! Recebi os dados sobre os seus rendimentos do mês, porém **o texto do contrato de patrocínio não foi incluído na sua mensagem**. 

Para que eu possa analisar as cláusulas contratuais e apontar riscos ou problemas, por favor, **envie o texto do contrato em uma nova mensagem**.

Enquanto isso, fiz a **análise e o cálculo estimado do Carnê-Leão** com base nos valores informados.

---

### 1. Ent
```

### T3

Figures mentioned: R$ 8.200,00, R$ 2.259,20, R$ 2.259,21, R$ 2.826,65, R$ 169,44, R$ 2.826,66, R$ 3.751,05, R$ 351,44, R$ 3.751,06, R$ 4.664,68, R$ 632,77, R$ 4.664,68, R$ 869,36, R$ 8.200,00, R$ 8.200,00, R$ 2.255,00, R$ 869,36, R$ 1.385,64, R$ 564,80

```
Olá! Vamos analisar a sua situação tributária e o seu contrato de patrocínio.

---

### 1. Análise do Contrato de Patrocínio
> **Atenção:** Você **não anexou ou colou o texto do contrato** na sua mensagem. 
> 
> Por favor, envie o texto ou as cláusulas do contrato em uma nova resposta para que eu possa analisar pontos críticos como: cessão de direitos de imagem, exclusividade, prazos de pagamento, penalidades/multas e responsabilidade por impostos.

---

### 2. Cálculo do Carnê-Leão (Renda Mensal: R$ 8.200,00)

Para fazer este cálculo, estamos assumindo que esses pagamentos via Pix foram recebidos de **Pessoas Físicas (PF)**. 

*(Nota: Se esses pagamentos vieram de Pessoas Jurídicas (PJ) no Brasil, a empresa contratante é quem deveria ter retido o imposto na fonte (IRRF). O Carnê-Leão é obrigatório apenas para rendimentos vindos de Pessoas Físicas ou do Exterior).*

#### Tabela Progressiva Mensal do IR (Vigente):
* **Até R$ 2.259,20:** Isento
* **De R$ 2.259,21 até R$ 2.826,65:** 7,5% (Dedução: R$ 169,44)
* **De R$ 2.826,66 até R$ 3.751,05:** 15,0% (Dedução: R$ 351,44)
* **De R$ 3.751,06 até R$ 4.664,68:** 22,5% (Dedução: R$ 632,77)
* **Acima de R$ 4.664,68:** 27,5% (Dedução: R$ 869,36)

---

#### Opção A: Cálculo Padrão (Sem Dedução Simplificada)
* **Rendimento Bruto:** R$ 8.200,00
* **Faixa:** 27,5%
* **Imposto Bruto:** R$ 8.200,00 × 27,5% = R$ 2.255,00
* **Dedução da Faixa:** R$ 869,36
* **Imposto a Pagar:** **R$ 1.385,64**

---

#### Opção B: Cálculo com o Desconto Simplificado Mensal (Mais Vantajoso)
A Receita Federal permite aplicar um desconto simplificado mensal automático de **R$ 564,80
```

### T4

Figures mentioned: R$ 5.500,00, R$ 4.664,68, R$ 896,00, R$ 5.500,00, R$ 1.512,50, R$ 1.512,50, R$ 896,00, R$ 616,50, R$ 4.000, R$ 5.000

```
Olá! Vamos organizar essas informações para você não ter problemas com a Receita Federal e entender direitinho seus impostos.

---

### 1. Cálculo do Imposto (Carnê-Leão)

Como você não tem os valores exatos e disse que recebeu "uns 5 mil e pouco mais o AdSense", precisaremos fazer uma **estimativa**, mas é fundamental que você levante os valores exatos (extratos bancários e relatórios) para declarar corretamente.

#### Pontos Importantes sobre suas Fontes de Renda:
1. **Google AdSense (Renda do Exterior):** Entra obrigatoriamente no **Carnê-Leão** mês a mês, pois o pagamento vem de fora do Brasil.
2. **Marcas/Empresas (Pessoa Jurídica):** 
   * Se você recebeu de **Pessoas Físicas**, o valor deve ir para o **Carnê-Leão**.
   * Se você recebeu de **Pessoas Jurídicas (PJs)** do Brasil como Pessoa Física, a própria empresa contratante deveria ter feito a retenção do Imposto de Renda na fonte (IRRF). Se elas não retiveram ou se você prestou serviço como autônomo sem retenção, precisa apurar.

---

#### Simulação Exemplo (Pessoa Física):
Vamos supor que o seu rendimento total tributável no mês (AdSense + Marcas sem retenção) tenha sido de **R$ 5.500,00**:

* **Tabela Progressiva mensal do IRPF (2024):**
  * Faixa: Acima de R$ 4.664,68
  * Alíquota: **27,5%**
  * Parcela a deduzir da tabela: **R$ 896,00**

**Cálculo aproximado:**
1. Imposto bruto: R$ 5.500,00 × 27,5% = **R$ 1.512,50**
2. Imposto devido: R$ 1.512,50 - R$ 896,00 (dedução da faixa) = **R$ 616,50**

> 💡 **Como reduzir esse valor? (Livro Caixa):**
> Se você atua como Pessoa Física, pode deduzir despesas necessárias para a sua produção de conteúdo no **Livro Caixa** do e-CAC (ex: conta de internet, luz proporcional do espaço de trabalho, hospedagem de site, material de consumo do canal).

---

#### 🚨 Dica de Ouro: Vale a pena abrir um CNPJ?
Se você está faturando constantemente acima de R$ 4.000 / R$ 5.000 por mês, **deixar na Pessoa Física (Carnê-Leão) sai muito caro** (alíquota efetiva alta + INSS de 20%). 
Ao abrir uma empresa (por exemplo, no **Simples Nacional**), você pode pagar impostos a partir de **6%** sobre o faturamento total bruto.

---

### 2. Análise do Contrato de Patrocínio

**Atenção:** Você não anexou nem colou o texto do contrato na sua mensagem!

Por favor, **cole o texto do contrato na sua resposta** para que eu possa analisar cláusula por cláusula. 

Enquanto você envia, fique atento a estes **pontos críticos (Red Flags
```

### T5

Figures mentioned: R$ 9.000,00

```
Olá! Vou te ajudar com o cálculo do seu imposto (Carnê-Leão) e com as orientações sobre o contrato.

---

### 1. Cálculo do Carnê-Leão (Rendimentos no Brasil)

Para calcular o Carnê-Leão sobre os **R$ 9.000,00** recebidos via Pix, precisamos primeiro de uma **atenção muito importante sobre a origem desses pagamentos**:

* **Se os Pixes vieram de PESSOAS FÍSICAS (PF):** Você **deve** declarar no Carnê-Leão e pagar o imposto até o último dia útil do mês seguinte.
* **Se os Pixes vieram de PESSOAS JURÍDICAS (PJ / Empresas):** O imposto **não** deve ser pago pelo Carnê-Leão. A própria empresa contratante deveria ter retido o imposto na fonte (IRRF) ao te pagar. 

> *Considerando que a origem foi de **Pessoa Física** (ou que você não teve retenção na fonte), veja o cálculo com base na tabela progressiva mensal do Imposto de Renda (vigente em 202
```

