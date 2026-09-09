# IMO — JARVIS Core Roadmap

## Vision
IMO (Intelligent Mobile Operator) is designed as a phone-native assistant that can understand natural language, reason over tasks, observe the Android UI, execute actions, remember useful context, and stop safely when confidence or authorization is insufficient.

## Core layers
1. **Voice identity** — local speaker enrollment and verification.
2. **Speech** — local multilingual ASR and Indonesian TTS.
3. **Understanding** — deterministic intent normalization and confidence estimation.
4. **Safety** — explicit risk assessment and confirmation for external, financial, destructive, or privacy-sensitive actions.
5. **Planning** — multi-step action planning through IMOPlanner.
6. **Execution** — Android Accessibility actions with bounded recovery.
7. **Observation** — UI snapshots/read-screen verification after actions.
8. **Memory** — local task outcomes and useful context.
9. **Future agent bus** — specialized agents coordinated by a single IMO core.

## Principles
- No fake AI claims: capabilities must be backed by executable code.
- Offline-first for voice identity and core phone control.
- Least privilege and explicit confirmation for consequential actions.
- Bounded retries; never loop forever when an action fails.
- Prefer observation + verification over blind action sequences.
- Keep sensitive voice data local.

## Next milestones
- Agent orchestration bus with typed messages.
- Better semantic intent matching and parameter extraction.
- Persistent task/session context.
- Wake-word service with battery-aware lifecycle.
- Rich Android UI state understanding.
- Automated instrumentation tests on representative Android flows.
- Release signing and production packaging after device validation.
