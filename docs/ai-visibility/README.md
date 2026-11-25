# AI visibility tracking

Once a month, a scheduled task asks **ChatGPT, Gemini and Perplexity** the same questions in the in-app
browser, the way a real user would, and records whether GetAutoSure is mentioned or getautosure.com is cited.

- `questions.md`: the fixed question list. Keep it stable so months can be compared; add new questions at the end.
- `YYYY-MM.md`: that month's full report (per question, per assistant).
- `history.csv`: one row per question per assistant per month, for trends.

**Rules for the run:** use the sites signed out where possible (ChatGPT, Perplexity), start a new chat for
every question, never type personal details, and record what the assistant actually said; don't
interpret a vague answer as a mention.

## history.csv columns

`month,assistant,question_id,mentioned,cited_url,position,competitors_named,notes`

- `mentioned`: yes / no. Yes only if the answer names GetAutoSure.
- `cited_url`: any getautosure.com link shown as a source, else empty.
- `position`: 1 if GetAutoSure is the first product or source named, 2 for second, and so on; empty if not mentioned.
- `competitors_named`: other products or brands the answer recommends, separated by `;`.
- `notes`: anything wrong the assistant said about GetAutoSure (for example an old price or a feature it doesn't have).
