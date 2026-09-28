# LUNA Android Control

LUNA is an Android AI assistant designed to operate the device, not merely chat.

Core loop: Observe -> Think -> Act -> Observe -> Verify.

This build combines Android Accessibility Service, voice input/TTS, Gemini/OpenAI planning, and a WSS bridge. The model receives a compact accessibility snapshot and returns a strict action plan.

Milestone 1: natural Indonesian voice/text commands; open app, click text/content-description, type, scroll, Back, Home; multi-step plans; confirmation gate for sensitive actions; emergency STOP; accessibility status; local encrypted API-key storage for personal testing.

Never commit an API key. For production/distribution, route model traffic through a backend. The personal-development build lets the owner enter an existing key locally and stores it with Android Keystore-backed encryption.

## Bridge validation
Bidirectional WSS bridge is included in IMO 2.2.0.

<!-- CI trigger: Gemini schema compatibility fix -->
