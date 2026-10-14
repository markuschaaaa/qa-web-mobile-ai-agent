---
name: accessibility
description: Accessibility testing for web and mobile — WCAG 2.2 AA checklist, automated scans with axe-core in Playwright, mobile checks (accessibility labels, touch target size, contrast, text scaling, dark mode, screen reader order) with adb / simctl commands, and how to report findings. Use for "a11y", accessibility, WCAG, European Accessibility Act, screen reader, TalkBack/VoiceOver, or when exploring a new screen.
---

# Accessibility

Context: the European Accessibility Act applies to many consumer products and services in the EU since June 2025 — accessibility findings can be compliance issues, not cosmetics. Ask whether the product is in scope; record the target (usually WCAG 2.2 AA) in PROJECT.md.

## 1. Web (automated + manual)

Automated scan per page/state (finds roughly a third of issues):
```ts
import AxeBuilder from '@axe-core/playwright';
const results = await new AxeBuilder({ page })
  .withTags(['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa'])
  .analyze();
expect(results.violations).toEqual([]);
```
Scan states, not just pages (open dialog, error messages shown, menus expanded). Known accepted violations → explicit `exclude`/`disableRules` with a ticket, never a blanket disable.

Manual checklist: keyboard only (tab order, visible focus, no traps, skip link), screen reader names (VoiceOver: Cmd+F5), headings structure, form labels and error announcements, zoom 200 % / reflow at 320 px, contrast (4.5:1 text, 3:1 large text & UI parts), no info by colour alone, motion/autoplay controllable, target size ≥ 24×24 px (WCAG 2.2).

## 2. Mobile

| Check | Android | iOS |
|---|---|---|
| Labels on interactive elements | page source: `content-desc` (or text) present and meaningful | page source: `name`/`label` present |
| Touch target size | ≥ 48×48 dp | ≥ 44×44 pt |
| Text scaling | `adb shell settings put system font_scale 1.3` (reset `1.0`) | `xcrun simctl ui booted content_size extra-extra-large` (reset `large`) |
| Dark mode | `adb shell cmd uimode night yes` / `no` | `xcrun simctl ui booted appearance dark` / `light` |
| Screen reader | TalkBack (manual): focus order, announcements, custom actions | VoiceOver (manual): same |
| Contrast | Accessibility Scanner app (manual) | Xcode Accessibility Inspector |
| Orientation | content works in both unless essential | same |

Automatable from page source (the `qa-mobile` MCP `rank_locators` flags missing labels): every clickable/focusable element has a label; labels are not ids like `btn_1`; no duplicate labels on one screen for different actions.
React Native: `accessibilityLabel`, `accessibilityRole`, `accessibilityHint`; `testID` is **not** an accessibility label.

## 3. Report

Per finding: screen, element, WCAG criterion (e.g. 1.4.3 Contrast), impact (who is blocked), steps, evidence, suggested fix. Severity: blocker if a core flow can't be completed with a screen reader or keyboard. Summaries go to `bugs/drafts/` (one draft per root cause, list affected screens inside).
