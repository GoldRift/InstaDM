---
name: mobile-android-design
description: Material Design 3 and Jetpack Compose design patterns for building new Android screens, Material 3 theming/expressive surfaces, adaptive layouts, or Navigation Compose architecture. Do not load for minor UI tweaks or bug fixes.
---

# Android Mobile Design

Reference for designing new Material Design 3 screens, theming systems, adaptive layouts, and Navigation Compose flows.

## Core principles

- **Material Theme**: read colors, typography, and shapes from `MaterialTheme` (`colorScheme`, `typography`, `shapes`) so dark mode and dynamic color (Android 12+) work automatically.
- **Adaptive layouts**: use `WindowSizeClass` for phones, tablets, and foldables.
- **Accessibility**: minimum 48dp touch targets; set `contentDescription` on interactive icons/images (`null` for purely decorative ones).
- **State hoisting & previews**: hoist state out of reusable components; use `rememberSaveable` for state that survives configuration changes; add `@Preview` for key states/themes.

## On-demand references

Read only the specific file needed for the task:

- `references/material3-theming.md`: Color system, typography, shape system, elevation/shadows, responsive design.
- `references/compose-components.md`: Lists/collections, forms/input, dialogs/bottom sheets, loading states, animations.
- `references/android-navigation.md`: Navigation Compose basics, bottom nav, drawer, rail, deep linking, nested graphs, state, transitions.
- `references/details.md`: Core Material 3 & Compose architecture concepts.
