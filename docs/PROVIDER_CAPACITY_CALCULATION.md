# Provider Capacity Calculation & Free-Tier Economics

**Classification:** ESTIMATED / ARCHITECTURAL SPECIFICATION  
**Date:** 2026-08-29  

---

## 1. Provider Free-Tier Limits

| Provider | Free Tier Model | Request Limit | Token Limit | Rate Limit |
|---|---|---|---|---|
| **Groq Cloud** | `llama-3.1-8b-instant` | 14,400 RPD | 500,000 TPD | 30 RPM |
| **Google Gemini** | `gemini-1.5-flash` | 1,500 RPD | 1,000,000 TPD | 15 RPM |
| **OpenRouter** | Free-tier models | 200 RPD | Variable | 20 RPM |

---

## 2. Per-User Consumption Modeling

$$\text{Tokens per Turn} \approx 60\text{ (input prompt)} + 40\text{ (output response)} = 100\text{ tokens/turn}$$
$$\text{Turns per Minute} \approx 2\text{ turns/min}$$

### A. 30-Minute Daily User
- Turns: 60 turns
- Tokens: $60 \times 100 = 6,000\text{ tokens/day}$
- **Daily Capacity on Groq (500K TPD):** $\approx 83\text{ concurrent daily users}$
- **Daily Capacity on Gemini (1M TPD):** $\approx 166\text{ concurrent daily users}$

### B. 60-Minute Daily User
- Turns: 120 turns
- Tokens: $120 \times 100 = 12,000\text{ tokens/day}$
- **Daily Capacity on Groq:** $\approx 41\text{ daily users}$
- **Daily Capacity on Gemini:** $\approx 83\text{ daily users}$

### C. 90-Minute Daily User (Fair-Use Cap)
- Turns: 180 turns
- Tokens: $180 \times 100 = 18,000\text{ tokens/day}$
- **Daily Capacity on Groq:** $\approx 27\text{ daily users}$
- **Daily Capacity on Gemini:** $\approx 55\text{ daily users}$

---

## 3. Token-Saver Multiplier Effect

With **Level 0 Knowledge Cache** ($\approx 15\%$ of queries) and **Smart Session Memory Recall** ($\approx 10\%$ of queries), **$25\%$ of total API requests are avoided entirely**, boosting capacity by **$+33.3\%$**!