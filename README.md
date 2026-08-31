# Creator Assist: a tax and contract assistant for content creators

Submission for the **Frontier Engineering Challenge 2026** (micro1).

## What existed before this hackathon vs. what was built during it

Everything in this repository, the problem framing, the test cases,
the prompts, the Kotlin code, the docs, was written during the
hackathon's three-day window (August 28 to 31, 2026). Nothing here is
reused code from a prior project. What pre-dates the hackathon is only
the developer's general familiarity with the tech stack (Kotlin, Spring
Boot, PostgreSQL) from unrelated prior work, and the domain research on
Brazilian creator-economy tax rules cited in
`docs/test_cases_and_answer_key.md` (public sources, linked there).

## Important: this is not a substitute for professional advice

This tool produces a first-pass calculation and a first-pass contract
review. It is explicitly designed to flag uncertainty and defer to a
human rather than act autonomously (see `CalculationStatus.NEEDS_HUMAN_REVIEW`
in `TaxCalculatorAgent.kt`, and the MEI-limit warning in
`Orchestrator.kt`). It does not file anything with tax authorities and
does not sign or send anything on the user's behalf. A real deployment
of this idea should always be paired with a licensed accountant or
lawyer reviewing anything before the user acts on it. This project
does not replace that review, only makes it faster to get to.

## Who has the problem, and why

Early/mid-stage Brazilian content creators (about half earn up to
R$5,000/month, and 91% combine content creation with another income
source), receiving income from mixed sources (Pix from brands, AdSense
in USD, barter deals), with no dedicated accountant. Two concrete
bottlenecks:

1. Calculating the monthly "Carne-Leao" tax by hand is error-prone,
   especially with mixed income sources (domestic vs. foreign, barter).
2. Sponsorship contracts get signed without legal review, often
   containing risk clauses (indefinite exclusivity, perpetual image
   rights assignment, no late-payment penalty).

Brazilian Law No. 15.325/2026 (the "Multimedia Profession" law) and
increased data cross-referencing between banks, platforms, and the tax
authority make this risk growing, not static. Full details, market
numbers, and the evaluation answer key are in
`docs/test_cases_and_answer_key.md`.

## A note on language

All code, comments, and documentation in this repository are in
English, per the submission requirements. The **sample sponsorship
contracts** in `src/main/resources/contracts/` are a deliberate
exception: their body text is in Portuguese, because that is the real
language a Brazilian creator's contract is written in. This is
authentic domain content, not an oversight. Each contract file starts
with an English note for reviewers explaining exactly which clause is
being tested and what the expected finding is, so the evaluation can
be followed without needing to read the full contract in Portuguese.
The product's real end-user output would also be in Portuguese (the
target users are Brazilian creators); the code and this documentation
are in English purely for the submission.

## Architecture

- **Agent 1 (income classifier):** uses the model to interpret free
  text, but NEVER guesses an ambiguous value (see test case T4).
- **Agent 2 (tax calculator + verification):** **does not use the
  model at all**. The tax calculation is deterministic
  (`CarneLeaoTaxTable2026.kt`), because LLMs make arithmetic mistakes
  too easily for something with real financial consequences.
  Verification independently recalculates the result and checks sanity
  bounds before it is accepted.
- **Agent 3 (contract analyst):** uses a specialized skill
  (`prompts/contract_analyst_system.txt`) with explicit risk criteria,
  which is what allows it to catch the hard case C3 (exclusivity
  disguised as a permissive clause).
- **Memory:** an H2 database (file-based, so it persists across
  restarts) stores the tax regime and year-to-date revenue between
  runs.
- **Orchestration:** the tax and contract branches run in parallel
  (coroutines) from the same input and converge into a single report.
- **Real entry points:** beyond the evaluation harness, there are two
  genuine, user-driven ways to run this: an interactive terminal
  (`InteractiveRunner`) and a minimal browser UI (`ReportController` +
  `static/index.html`). Both call the exact same `Orchestrator` and
  `ReportFormatter` the evaluation does. See "Input scope" below.

## Input scope

**Correction:** an earlier draft of this section claimed contracts are
handled via file upload before that was actually built. It is now
accurate. There are three ways data reaches the agents in this
submission:

1. **Evaluation harness** (`EvalRunner`, `SampleReportGenerator`):
   reads bundled synthetic fixtures (`src/main/resources/testcases/`,
   `.../contracts/`) programmatically. This is what judges run to
   reproduce the 8/8 result; it never reads anything a real user typed.
2. **Interactive terminal** (`InteractiveRunner`,
   `--spring.profiles.active=interactive`): a real person types their
   income in free text and, optionally, a real file path on their own
   disk for the contract, which is read with `java.io.File(path).readText()`.
3. **Web UI** (`ReportController` + `src/main/resources/static/index.html`,
   default profile, `http://localhost:8080`): the same pipeline behind
   a browser form; the contract file is a genuine browser file upload,
   read client-side and sent to the backend.

What is **not** built: automatic ingestion from a real bank statement
or receipt photo (OCR / Open Finance). The user still types or pastes
their income description by hand in both (2) and (3). See "Roadmap"
below for why, and what a realistic next step looks like.

## How to run

See **`REPRODUCE.md`** for exact, copy-pasteable commands, prerequisites,
expected output, and runtime/cost estimates. Short version:

```bash
export GEMINI_API_KEY=your_key_here
./gradlew bootRun --args='--spring.profiles.active=eval'
```

This runs the baseline and the final solution against the same 8 test
cases and writes `evaluation-report.md`, `sample-user-report.md`
(a polished, Portuguese, end-user-facing example, see below), and
`trajectories/trajectory_eval-run.json`.

To try the real entry points instead, run `./gradlew bootRun` (no
profile) and either open `http://localhost:8080` in a browser, or run
with `--spring.profiles.active=interactive` for the terminal version.

**Note on the wrapper:** if `gradlew` / `gradlew.bat` are missing from
your copy, generate them once via IntelliJ's Gradle panel
(`Tasks > build setup > wrapper`) or `gradle wrapper` with any local
Gradle install. See `REPRODUCE.md` for details.

## Folder structure

```
src/main/kotlin/br/com/creatorassist/
  domain/       data types passed between agents + shared currency formatting
  memory/       JPA entity and repository (cross-month memory)
  llm/          LlmClient interface + GeminiClient (active) + ClaudeClient (inactive)
  trajectory/   structured log of every agent call
  agents/       Agent 1, 2, 3 + CarneLeaoTaxTable2026 + Orchestrator
  baseline/     reference solution (single prompt, no tools)
  report/       ReportFormatter, turns a result into the Portuguese
                document an actual creator would read
  eval/         EvalRunner (baseline + final solution comparison) and
                SampleReportGenerator (writes sample-user-report.md)
  cli/          InteractiveRunner, a real terminal-based entry point
  web/          ReportController, a real browser-based entry point
src/main/resources/
  prompts/      each agent's system prompt (its "skill")
  testcases/    tax test cases in JSON, with expected values
  contracts/    test contracts (C1, C2, C3)
  static/       index.html, the web UI form
docs/
  test_cases_and_answer_key.md   full answer key, problem, and sources

Generated when you run the evaluation (not checked into the repo):
  evaluation-report.md      technical comparison table, for judges
  sample-user-report.md     polished Portuguese report, for end users
  trajectories/              agent call logs
```

## Roadmap (not built in this submission)

### Automatic bank statement import (Open Finance / Open Banking)

The biggest remaining source of manual work for the user is typing
income entries by hand every month. The natural long-term fix is
connecting directly to the creator's bank via Brazil's Open Finance
framework, so recurring income could be detected automatically instead
of typed.

This was deliberately **not attempted** in this submission. It is not
a matter of writing more code. Becoming able to read a real user's bank
data in Brazil requires the product itself to become a certified Open
Finance participant with the Central Bank (Banco Central), including a
regulated consent (OAuth-based) flow and an institutional accreditation
process that takes months, not something buildable inside a hackathon.
Claiming this exists without that certification would be the same kind
of overclaim already caught and corrected earlier in this project (see
the file-upload correction in `CHANGELOG.md`), so it is listed here as
future work, not implied to be possible today.

### A realistic near-term step: CSV bank statement import

A smaller, honestly-buildable step toward the same goal: let the user
export their bank statement as a CSV (most Brazilian banks support
this) and upload that file instead of typing income by hand. This is
**not** the same as Open Finance. It would only support a documented,
fixed CSV schema (date, description, amount) defined by this project,
not the real, varying export formats of every Brazilian bank. Parsing
a fixed CSV schema is a deterministic, structured-data task, consistent
with this project's existing design principle (see `CHANGELOG.md`'s
Hot Take) of using the language model only for genuinely ambiguous
input, not for something a plain parser handles reliably. Not built in
this submission due to time, but a natural next increment on top of
the free-text path `IncomeClassifierAgent` already supports.

### A possible future warning: unusually large single-month income

While testing, a case came up where a creator reported a single month
with R$102,500 in income (all domestic Pix). The tax calculation was
correct, there is no cap on the top bracket, but it raised a fair
question: should the system flag an unusually large amount, given that
Brazil's tax authority and banks increasingly cross-reference this kind
of data (see the "Why this matters now" section above)? This was not
built, because within the hackathon's time budget we could not
confidently verify the exact regulatory threshold or rule that should
trigger such a warning. Adding a made-up number here would repeat the
same kind of overclaim already corrected earlier in this project (see
the file-upload correction in `CHANGELOG.md`), so it is listed as a
future step that needs proper legal and regulatory research first, not
implemented with a guessed threshold.

## Status

All 8 test cases pass. See `CHANGELOG.md` for the full evolution story
(baseline to final), `evaluation-report.md` / `trajectories/` for the
raw technical evidence, and `sample-user-report.md` for what an actual
end user would receive. Known limitations (not hidden, listed here on
purpose):

1. The contract-analysis skill (C1 to C3) has only been validated
   against 3 developer-written contracts, not a real, unseen one.
2. This project has only been run end-to-end on macOS. Windows/Linux
   reproduction relies on documented defensive fixes (see
   `REPRODUCE.md`), not personal verification on those platforms.
3. Income is still typed/pasted by hand (no OCR or bank-statement
   parsing) in both real entry points. Only contract *files* are
   genuinely uploaded/read from disk. See "Roadmap" above for why full
   bank-statement automation (Open Finance) was not attempted, and what
   a realistic next step looks like.
4. The submission video (up to 5 minutes) is the one remaining
   deliverable to record.

## Hot take

See `CHANGELOG.md` for the full write-up. Short version: isolating the
tax arithmetic in deterministic code with independent verification, and
keeping the LLM only for genuinely linguistic tasks, is what caught a
real numeric discrepancy during testing. It turned out the discrepancy
was in a hand-written answer key, not the agent, which is itself the
best evidence that the verification step was worth building.