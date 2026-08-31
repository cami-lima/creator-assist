# Test Cases and Answer Key: Tax and Contract Assistant for Content Creators

## 0. Problem definition

**Who has the problem:** early/mid-stage Brazilian content creators (about half earn up to R$5,000/month, and 91% combine content creation with another income source, per the Wake Creators Census 2025 / Brunch+YOUPIX Creators & Business report), receiving income from mixed sources (Pix from brands, AdSense in USD, barter deals), with no dedicated accountant.

**Bottleneck:**
1. Calculating the monthly "Carne-Leao" (Brazil's monthly withholding tax for self-employed income) by hand is error-prone, especially with mixed sources (domestic vs. foreign, barter).
2. Sponsorship contracts get signed without legal review, containing risk clauses (indefinite exclusivity, perpetual image rights assignment, no late-payment penalty).

**Why this matters now:** Brazilian Law No. 15.325/2026 ("Multimedia Profession" law) formally recognized the profession in January 2026, and data cross-referencing between banks, payment platforms, and the Brazilian tax authority (Receita Federal) has become more sophisticated, generating automatic alerts. The risk is growing, not static.

**Source of truth for the calculation:** the Carne-Leao table changes by law (Law 15.270/2025, for instance, changed the brackets effective 2026). In a production system this should be fetched via a search tool, not hardcoded permanently. For this test set we fix the table currently in force in 2026, documented and citable, so the calculation is reproducible.

### Reference tax table used in these tests (in force in 2026)

| Monthly taxable income bracket | Treatment |
|---|---|
| Up to R$5,000.00 | Tax-exempt |
| R$5,000.01 to R$7,350.00 | Phase-out band: tax decreases linearly to zero at R$7,350.00 |
| Above R$7,350.00 | Regular bracket: Tax = 27.5% × taxable base − R$908.73 |

Simplified formula used for the phase-out band (documented approximation for test purposes):
`Tax = (base − 5000) / (7350 − 5000) × (27.5% × base − 908.73)`

Barter (goods/services received instead of cash) is taxable at the market value of the item/service received and must be included in that month's taxable base.
Foreign income (AdSense, Patreon) enters the calculation converted at the exchange rate on the date it was received.

**On regional variation:** Carnê-Leão is a federal tax (IRPF), uniform across all of Brazil. It does not vary by state. Confirmed directly on Receita Federal's official site (gov.br/pt-br/servicos/apurar-carne-leao): "Não, exceto no caso do Simples Nacional, regime único de arrecadação dos impostos e contribuições comum da União, dos Estados, do Distrito Federal e dos Municípios." Simples Nacional (used by MEI) is the one exception, bundling state/municipal taxes into one payment. This project only detects the MEI revenue cap being crossed and recommends professional guidance; it never calculates a MEI/Simples Nacional tax amount, so this exception does not affect the math implemented here.

### Real-world input format

The user does **not** need to type income entries or contract text into the evaluation harness (`EvalRunner`), which reads bundled fixtures programmatically for reproducibility. Outside the harness, this submission has two real, human-driven entry points:

- **Interactive terminal** (`InteractiveRunner`): a real person types their income in free text and, optionally, a real file path on disk for the contract.
- **Web UI** (`ReportController` + `static/index.html`): the same pipeline behind a browser form, with a genuine browser file upload for the contract.

What is still not built: automatic ingestion from a real bank statement or receipt photo (OCR / Open Finance). Income is still typed by hand in both real entry points. See `README.md`'s Roadmap section for why, and what a realistic near-term step (CSV import) would look like.

Memory further reduces re-entry: tax-regime decisions and year-to-date totals do not need to be re-stated every month.

## 1. Test cases: tax calculation (synthetic creator profiles)

The test cases below (T1-T5) represent data that has **already been extracted and structured**. The goal is to test classification and calculation, not the extraction step itself.

### Case T1: Beginner creator, domestic Pix only
- Income: R$3,200.00 (Pix from a Brazilian company)
- Memory: no previous month (first use)
- **Answer:** below R$5,000, so tax-exempt. No DARF (tax payment slip) due. Still must be logged in Carne-Leao Web for the annual return.

### Case T2: Foreign income (AdSense) + domestic, phase-out band
- Income: US$900 (AdSense, exchange rate on receipt day: R$5.40) + R$1,800.00 (domestic Pix)
- **Answer:** AdSense converted = 900 × 5.40 = R$4,860.00. Total base = 4,860 + 1,800 = R$6,660.00, which falls in the phase-out band.
  Tax = (6,660 − 5,000) / 2,350 × (27.5% × 6,660 − 908.73) = (1,660/2,350) × 922.77 = **R$651.83**, computed with full precision. An earlier hand-rounded draft of this answer key briefly said R$652.03, which was corrected after the implementation's independent verification step and a full-precision recheck both agreed on R$651.83. This is a useful data point for the changelog: the gold answer had the arithmetic slip, not the code.
- **What this tests:** correct currency conversion and applying the phase-out band, not the full-exemption or full-bracket formulas.

### Case T3: Continuity across months (tests memory)
- Previous month (stored in memory): creator already chose to file as self-employed (not MEI). This decision must be remembered.
- This month's income: R$8,200.00, all domestic
- **Answer:** Tax = 27.5% × 8,200 − 908.73 = 2,255 − 908.73 = **R$1,346.27**. The agent must not ask again whether the creator is self-employed or MEI; it must use the already-stored decision.
- **What this tests:** whether memory is actually consulted before repeating an already-answered question.

### Case T4 (hard case): Ambiguous / incomplete data
- Free text input: "I got like 5 thousand and change from brands and a bit of AdSense, I didn't keep track properly"
- **Expected answer:** the agent must **not invent a number**. It should flag the ambiguity, ask for exact figures or the source of each entry before calculating, and never present a tax figure with false precision.
- **What this reveals:** whether the agent resists the temptation to guess a plausible number. This is the case that most separates a naive solution from one with proper verification and confidence checking before answering.

### Case T5: Near/over the MEI revenue cap (a second, distinct memory scenario)
- Previous month (stored in memory): creator is on the MEI regime, year-to-date revenue already at R$76,000.00.
- This month's income: R$9,000.00, all domestic.
- **Answer:** this month's tax = 27.5% × 9,000 − 908.73 = **R$1,566.27** (same top-bracket formula as T3, independent of the MEI question). Separately, accumulated year-to-date revenue becomes 76,000 + 9,000 = R$85,000.00, which exceeds the MEI cap (R$81,000.00). The system must surface a warning recommending the creator seek accounting guidance about switching regimes.
- **What this tests:** T3 only checks that a stored decision isn't re-asked. T5 checks that accumulated state across months is used to detect a business-rule threshold being crossed, not just remembered, but acted on. Added after the initial submission to close a known gap: memory had only been exercised by one scenario (T3) before this.

## 2. Test cases: sponsorship contract analysis

Full contract text is in `src/main/resources/contracts/` (English notes for reviewers are prepended to each file; the contract body itself is in Portuguese, the real language a Brazilian creator's contract would be written in).

### Case C1: Clean contract (negative control)
File: `contract_c1_clean.md`. Terms: 3-month term, image use limited to the campaign and 6 months after airing, payment within 30 days, 2%/month late penalty.
**Answer:** no high-risk clauses. The agent must not produce false positives.

### Case C2: Perpetual image rights assignment
File: `contract_c2_perpetual_image.md`. Clause: the brand may use the creator's image, voice, and name in any materials, indefinitely, with no need for new authorization or additional payment, surviving termination.
**Answer:** **high risk**, a perpetual, unrestricted image assignment with no additional compensation. Must be flagged with the source clause quoted.

### Case C3 (hard case): Disguised exclusivity
File: `contract_c3_disguised_exclusivity.md`. Clause: the creator "may accept other partnerships... provided they are not considered conflicting with the contracting party's interests, at the contracting party's sole discretion", and violating this is grounds for termination.
**Answer:** this is exclusivity **disguised as flexibility**. In practice it gives the brand unrestricted, subjective veto power, without defining what counts as "conflicting". The agent should recognize that the clause looks permissive but functions as soft exclusivity, and explain why, pointing to the exact excerpt, rather than simply classifying it as "fine" because it never uses the word "exclusivity".
**What this reveals:** this is the single most important test in the set. It separates surface-level analysis (keyword search for "exclusivity") from real analysis of contractual meaning and practical effect. **Known limitation:** this skill has only been validated against the 3 synthetic contracts in this repository, all written by the developer. It has not yet been tested against a real, unseen contract, so some risk of the prompt being overfit to these specific examples remains (see `CHANGELOG.md`).

## 3. How this is used

- **Baseline (direct prompt):** runs the same T1-T5 and C1-C3 cases with a single prompt, no tools, memory, or verification. Full raw output is captured (see `evaluation-report.md`'s "Baseline full responses" section) for manual comparison. Its exact numeric answers are not automatically extracted or scored, only spot-checked via a best-effort regex scan for mentioned currency figures.
- **Final solution:** runs the same cases through the full pipeline (classifier plus calculator with independent verification plus memory plus contract analyst with specialized skill). Automatically scored against this answer key.
- **Primary metric:** percentage of cases with the correct tax amount (R$0.01 tolerance), correct MEI-warning detection where applicable, and percentage of risk clauses correctly identified (with no false positive on C1).
- **Mandatory hard cases for the report:** T4 (tax ambiguity) and C3 (disguised exclusivity). Both require the agent to avoid answering with false confidence, which is exactly where a direct prompt tends to fail.