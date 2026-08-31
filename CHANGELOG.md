# Improvement Changelog

This document tells the story of how the solution evolved, from a plain
single-prompt baseline to the final multi-agent pipeline, using the same
8 test cases throughout (`docs/test_cases_and_answer_key.md`). Full raw
output for every case is in `evaluation-report.md`; agent-by-agent
trajectories are in `trajectories/`.

## Stage-by-stage

| Stage | What we tried and why | Evidence | Decision / learning |
|---|---|---|---|
| **Baseline** | A single generic prompt (`prompts/baseline_system.txt`) with no tools, no memory, no verification, and no specialized skill. Given the same income description and contract text as the final solution, asked in one shot to calculate the tax and flag contract problems. | Produces fluent, plausible-sounding text for every case (see the "Baseline full responses" section in `evaluation-report.md`), but the output is unstructured prose. There is no explicit signal for whether it flagged T4 as ambiguous rather than guessing a number, no cross-checked arithmetic, and no structured, severity-tagged list of contract clauses. It cannot be scored automatically the way the final solution can. | Established as the fixed comparison point. Every later change was tested against the exact same 8 cases. |
| **Iteration 1: specialized skill for contract analysis** | Gave Agent 3 an explicit skill (`prompts/contract_analyst_system.txt`) listing concrete risk criteria, including instructions to judge a clause by its *practical effect* rather than the literal presence of words like "exclusivity". Motivation: a generic prompt tends to do a surface-level, keyword-style reading, which would miss a clause that looks permissive but functions as exclusivity (case C3). | With the skill in place: C1 (clean contract) found 0 risk clauses (no false positive); C2 (perpetual image rights) found 2 HIGH-severity clauses ("Perpetual image rights assignment without additional compensation surviving termination"; "Image rights survival post-termination"); C3 (disguised exclusivity, the hard case) found 1 clause, described by the agent as **"Exclusividade disfarçada e controle unilateral"** ("disguised exclusivity and unilateral control"). That matches the answer key's own characterization almost verbatim, without the phrase ever appearing in the prompt. | Kept. This is the single change that most directly targets the hardest test case in the set, and the C3 result is the strongest single piece of evidence that the skill is doing real interpretive work, not keyword matching. |
| **Iteration 2: deterministic tax calculation plus independent verification** | Moved the Carnê-Leão calculation out of the language model entirely and into plain, deterministic Kotlin code (`CarneLeaoTaxTable2026.kt`), with a second recalculation used purely to cross-check the first result before it is accepted. Motivation: LLMs are unreliable at exact arithmetic, and this has real financial consequences for the user. | T1 and T3 passed immediately. T2 initially showed as **FAIL** against our own hand-written answer key (which said R$652.03). Investigating the discrepancy, a full-precision recheck (see below) showed the agent's answer, R$651.83, was actually correct. Our hand-calculated gold answer had a rounding mistake, not the code. | Kept the deterministic design. Fixed the answer key instead of the code. Documented in `docs/test_cases_and_answer_key.md`. This is arguably the most important finding in this project, see Hot Take below. |
| **Iteration 2b: made verification genuinely independent** | Initial self-review found a real weakness. The "verification" step re-typed the *same* factored formula a second time, so a coding mistake in that formula would reproduce itself identically in both places. It was not really independent. Rewrote `verify()` to instead recompute the phase-out band via an algebraically-expanded quadratic form (`a·x² + b·x + c`), derived by hand from the original formula but evaluated through entirely different arithmetic operations. | Confirmed by hand and by an independent Python script that the two forms agree at several sample points (R$651.83 at base R$6,660, R$128.46 at R$5,500, R$864.91 at R$7,000, R$1,112.51 at R$7,349.99). | Kept. This is a stronger, more honest claim to "independent verification" than the original version. See the full derivation in the code comment in `CarneLeaoTaxTable2026.kt`. |
| **Iteration 3: cross-month memory plus parallel orchestration** | Added an H2-backed memory table (`CreatorMonthState`) so the agent does not need to re-ask for a tax-regime decision every month, and split the pipeline into two independent branches (tax and contract) running in parallel from the same input. Motivation: repeated, unnecessary questions are the fastest way to make a "real person" stop using a tool, and the two branches have nothing in common (different tools, different success criteria), so forcing one agent to do both tends to dilute quality. | T3 (continuity across months) passed. Given a simulated prior month with a stored tax regime and accumulated year-to-date revenue, the agent used that stored state directly and did not ask again. | Kept. |
| **Iteration 3b: added a second, distinct memory scenario (T5)** | Self-review found that memory was only exercised by one scenario (T3: "don't re-ask a stored decision"). Added T5, a creator on the MEI regime whose accumulated revenue crosses the legal cap once this month's income is added. This requires memory to be *used*, not just *recalled*, to detect a business-rule threshold being crossed. | T5 passed. The orchestrator correctly summed the stored year-to-date revenue (R$76,000) with this month's base (R$9,000) to R$85,000, exceeding the R$81,000 MEI cap, and surfaced the warning. | Kept. |
| **Iteration 4: explicit ambiguity handling** | Instructed Agent 1's skill to return `"ambiguous": true` with a clarifying question instead of guessing a number when the income description is too vague to be confident about (case T4: *"I got like 5 thousand and change... didn't keep track properly"*). Motivation: a naive prompt might be tempted to produce a plausible-looking number here, which is worse than admitting uncertainty. | T4 passed. The classifier returned `ambiguous=true` and a clarifying message instead of a fabricated tax figure. **Honest finding:** the baseline, on this run, also asked for clarification instead of guessing. A reasonably-prompted single call can behave sensibly on an obviously ambiguous case too. The real, durable difference is not "baseline guesses, ours doesn't" (that turned out not to hold here). It is that our output is a **structured, machine-checkable** `ambiguous: true` flag a calling system can branch on automatically, versus the baseline's free-text paragraph, which a human (or another LLM call) has to read and interpret every single time. | Kept. The value is in automatability, not in preventing a failure mode this particular baseline happened to avoid on its own. |
| **Tried and removed: OCR / bank statement parsing** | Considered building ingestion from photographed Pix receipts or real bank-statement PDFs/CSVs, so the user would never type anything manually. | Explicitly descoped given the hackathon's time budget. This is a computer-vision/parsing problem largely orthogonal to the agentic reasoning being evaluated here, and building it would have consumed time better spent on the calculation/verification/skill work above. | Not built. Documented as a known limitation and explicit roadmap item in `README.md`, rather than silently left out. |
| **Changed mid-project: LLM provider** | Started wired to the Anthropic Claude API. | No API budget was available (Anthropic requires paid credits beyond a small initial trial); Google's Gemini API has a genuinely free tier. | Swapped providers behind a shared `LlmClient` interface (`ClaudeClient` and `GeminiClient` both implement it), so the change touched only one file's wiring, not any agent's logic or prompts. |
| **Final** | All kept changes combined: specialized contract skill, deterministic calculation with a genuinely independent verification path, cross-month memory exercised by two distinct scenarios, parallel orchestration, explicit ambiguity handling. | **8 / 8 test cases passed** (T1 to T5, C1 to C3), after the T2 answer-key correction above. Full technical output (baseline shown in full, no truncation) in `evaluation-report.md`; per-agent trajectories in `trajectories/trajectory_eval-run.json`; a polished, Portuguese, end-user-facing example in `sample-user-report.md`. | This is the submitted solution. Known limitations: the contract skill (C1 to C3) has only been tested against 3 developer-written contracts, not a real unseen one; the solution has only been run end-to-end on macOS (Windows/Linux reproduction relies on documented, but not personally verified, fixes, see `REPRODUCE.md`). |

## Baseline vs. final comparison

| Metric | Baseline | Final solution | Change |
|---|---|---|---|
| Test cases correctly resolved | Not automatically scored on exact figures. Full raw text captured in `evaluation-report.md`'s "Baseline full responses" section for manual comparison, plus a best-effort regex scan for mentioned currency amounts. | 8 / 8 | Qualitative to verifiable, structured, automatically gradable output |
| Arithmetic verified before being shown to the user | No, a single LLM pass, unchecked | Yes, independent recalculation plus sanity bounds on every tax case | New capability |
| Ambiguous input handled without guessing | Also handled reasonably on this run (asked for clarification). See honest note in Iteration 4. | Guaranteed by design, and returned as a structured `ambiguous: true` flag a system can act on automatically (T4) | Same qualitative outcome, but only one of the two produces a machine-checkable signal |
| Repeated questions across months | Not applicable, baseline has no memory | None. Regime and revenue carried over automatically (T3). | New capability |
| Contract clause detection on the hard case (C3) | Unstructured, unscored | Correctly flagged, with the practical-effect reasoning made explicit | Improved |
| Cost per evaluation run | $0 (free-tier API) | $0 (free-tier API; tax calculations make zero LLM calls at all, see Hot Take) | No change, but see note |

**Note on baseline capture:** an earlier version of this project truncated each baseline response to about 200 characters, which cut off before the final computed figure and made a real numeric comparison impossible. `evaluation-report.md` now includes each baseline response in full (see "Baseline full responses"), plus a best-effort regex scan for any "R$ ..." amount mentioned, so a human reader can actually check the baseline's arithmetic. It is still not automatically scored, since the baseline's free-form prose has no reliable place to parse a "final answer" from, but at least nothing is hidden from the comparison anymore.

## Main failure mode

The most time-consuming failure mode in building this project was not
about agent reasoning. It was about reliably reaching an external LLM
provider at all. Over the course of development we hit, in order: a
corporate network's TLS-inspecting proxy that Java's default trust
store didn't recognize, a Spring WebClient quirk that silently
mis-encoded the literal `:` Google's API requires in its endpoint path,
a changed authentication scheme (newer API keys require the
`x-goog-api-key` header, not the legacy `?key=` query parameter), a
model that had just been deprecated for new API keys, and a low
free-tier request quota that needed retry-with-backoff to survive a
full evaluation run. None of these are about whether the *agent* is
reliable. But building anything that calls a hosted LLM has to treat
"the call itself might fail for reasons that have nothing to do with
your prompt" as a first-class concern, not an edge case.

## Hot take

LLMs are excellent at exactly the tasks that resist verification
(interpreting messy language, judging the practical effect of a
contract clause) and unreliable at exactly the tasks that are trivial
to verify formally (arithmetic). The right pattern is not "ask the
model to double-check itself." It is to physically separate what the
model is used for from what deterministic code is used for, and add an
independent verification step only where a wrong answer has a real
cost. In this project, isolating the tax math into plain, testable
Kotlin with a second, independently-written recalculation is what
caught a real discrepancy on case T2, except the discrepancy turned
out to be in *our own hand-written answer key*, not the agent's output.
That is the actual payoff of building verification in from the start.
It does not just catch the agent's mistakes. It catches yours too.