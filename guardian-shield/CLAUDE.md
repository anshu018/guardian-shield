# Guardian Shield — Project Instructions

This file instructs any AI agent working on this project to adopt GSD planning and automated skill orchestration automatically.

---

## 1. Core Planning Framework (GSD)

This project strictly utilizes the **Get Shit Done (GSD)** project planning, roadmap management, and agentic execution framework. 

* **State Reading**: At the start of EVERY session or task, you MUST establish situational awareness by reading:
  * `.planning/STATE.md` (for active phase context, focus task, and session continuity)
  * `.planning/ROADMAP.md` (for phase goals and success criteria)
* **Status Updates**: Always use the GSD CLI utility `node C:/Users/ash74/.gemini/get-shit-done/bin/gsd-tools.cjs` instead of manually editing planning statuses or frontmatter:
  * Load state: `node C:/Users/ash74/.gemini/get-shit-done/bin/gsd-tools.cjs state load`
  * Complete a todo: `node C:/Users/ash74/.gemini/get-shit-done/bin/gsd-tools.cjs todo complete <filename>`
  * Pause session: `node C:/Users/ash74/.gemini/get-shit-done/bin/gsd-tools.cjs state record-session --stopped-at "<stopped-at>"`

---

## 2. Dynamic Skill Orchestration

To maintain clean and highly compliant code, you MUST dynamically orchestrate specialized skills from the ecosystem rather than writing raw code from scratch:

* **Trigger Orchestrator**: Consult the `@antigravity-skill-orchestrator` meta-skill at the beginning of any complex task.
* **Auto-Discovery**: Search the local skills folder (`C:\Users\ash74\.gemini\config\skills`) or fetch the master catalog (`https://raw.githubusercontent.com/sickn33/antigravity-awesome-skills/main/CATALOG.md`) for matching capabilities:
  * For architecture patterns, load `@mvvm-clean-architecture`
  * For database or real-time connectivity, load `@supabase-android`
  * For streaming or network latency handling, load `@webrtc-android`
  * For background foreground execution rules, load `@foreground-services`

---
*Follow these rules diligently to preserve roadmap alignment and code quality.*
