# Milestone 26 — Capacity & Cost Projections

**Classification:** ESTIMATED  

---

## 1. Daily Usage Modeling

- Average input prompt: $\approx 60\text{ tokens}$ (with compact context)
- Average output response: $\approx 40\text{ tokens}$
- Total per turn: $\approx 100\text{ tokens}$
- Average active conversation: $2\text{ turns/min}$

---

## 2. Scale Projections

| User Tier | Active Minutes/Day | Turns/Day | Tokens/User/Day | 10 Users | 100 Users | 1,000 Users | 10,000 Users |
|---|---|---|---|---|---|---|---|
| **Casual** | 15 min | 30 | 3,000 | 30,000 | 300,000 | 3,000,000 | 30,000,000 |
| **Standard** | 30 min | 60 | 6,000 | 60,000 | 600,000 | 6,000,000 | 60,000,000 |
| **Pro (Fair Use Cap)** | 90 min | 180 | 18,000 | 180,000 | 1,800,000 | 18,000,000 | 180,000,000 |

---

## 3. Infrastructure & Backend Gateway Recommendations

- For $>1,000\text{ users/day}$, deploy a centralized VaniFlow API Gateway (Node.js/Go/Kotlin) to manage load balancing and upstream API keys across multiple cloud provider endpoints.