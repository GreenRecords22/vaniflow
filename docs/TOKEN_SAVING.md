# Token Saving & Developer Telemetry

**Classification:** ESTIMATED / VERIFIED  

---

## 1. Estimated Tokens Metric

Token calculations are based on the industry standard heuristic:  
$$\text{Estimated Tokens} \approx \frac{\text{Character Count}}{4}$$

---

## 2. Savings Breakdown

- **Session Memory Hit:** Avoids $\approx 150 - 350$ total tokens (prompt + completion) per turn.
- **Educational Dictionary Hit:** Avoids $\approx 80 - 150$ tokens per turn.
- **Factual Knowledge Cache Hit:** Avoids $\approx 100 - 250$ tokens per turn.
- **Total Estimated Savings:** Tracked dynamically in `DailyConversationUsageTracker.getSavedTokens()`.