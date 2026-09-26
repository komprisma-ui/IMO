# IMO Full Device Agent Architecture

## Goal

IMO is the Android-side sensor and actuator for an AI operator. The intended control loop is:

User -> AI brain -> command -> IMO -> Android -> observation -> AI brain -> verification.

## Sensors

- Accessibility tree: package, class, resource id, text, content description, clickable/editable/scrollable state and screen bounds.
- Android screenshot capture on supported Android versions.
- Active package detection.
- Conversation memory and operator session state.

## Actuators

- Open applications and URLs.
- Click by text, content description, resource id or screen coordinate.
- Long-click.
- Type and clear focused text.
- Scroll and swipe.
- Back, Home, Recents, Notifications and Quick Settings.
- Power dialog, lock screen and split-screen global actions where Android permits them.
- Volume and mute.
- Android Settings navigation.

## Agent loop

1. Observe the current UI.
2. Ask the model for exactly one next action.
3. Execute the action.
4. Wait for the UI to settle.
5. Observe again.
6. Verify against the goal.
7. Recover with a different strategy when an action fails or repeats.
8. Stop safely after the execution/time budget is exhausted.

## Important boundary

Accessibility does not grant unrestricted control of every Android subsystem. Android permissions, OEM behavior, secure settings, app protections, biometric prompts and system-level restrictions can still block an operation.

The ChatGPT conversation itself is not automatically attached to the local Android process. A future bridge must authenticate and transport structured commands between the AI service and IMO. IMO should never expose unrestricted device control over an unauthenticated network endpoint.

## Safety

Sensitive actions such as sending, deleting, purchasing, payment, transfers, OTP/PIN/password entry or permanent changes require explicit confirmation in the operator loop.

## Validation

A release is not considered physically validated until the APK is installed on the target phone and the following are tested: Accessibility, screenshot capture, voice input, TTS, app launch, UI reading, click/type/gesture execution, recovery after failure, and sensitive-action confirmation.
