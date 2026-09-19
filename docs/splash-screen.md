# Ahsan Traders Admin — Splash Screen

## 1. Purpose

The splash screen is the first thing a user sees when the app launches. It has three jobs:

1. **Brand the launch** — put the *Ahsan Traders* identity in front of the user immediately, before any data loads.
2. **Hide startup latency** — cover the time spent reading the encrypted session token and deciding where to route the user.
3. **Bridge the platform and the app** — transition from Android's native boot splash into the Jetpack Compose UI with no visible flicker or "flash of unstyled content."

The design deliberately mirrors the product's core message: **three businesses (Chicken · Broiler · LPG) run under one trusted umbrella.**

> **Design update (per UI/UX review).** The splash now uses a **single, centered composition** instead of content stretched to all four edges; it uses **two typefaces** (system sans-serif + one script accent) instead of three; the three sector circles **stagger in sequentially** with a simple fade; and the canvas color was deepened to `#0A3B22` for stronger grounding and contrast.

---

## 2. Two-layer architecture

The splash is implemented as **two stacked layers** that dissolve into each other, so the user perceives a single continuous splash.

| Layer | Technology | Responsibility |
|---|---|---|
| **Layer 1 — System splash** | `androidx.core:core-splashscreen` + Android 12+ SplashScreen API (`Theme.App.Starting`) | Renders instantly, before any Kotlin/Compose code runs. Shows only the AT mark on the deep forest-green background. |
| **Layer 2 — Compose splash** | `SplashScreen()` composable (`ui/SplashScreen.kt`) + `SplashViewModel` | Fade-in brand content: logo lockup, subtitle, three sector circles, and tagline. Also evaluates the session and triggers navigation. |

`MainActivity.onCreate` wires the two together:

```kotlin
val splashScreen = installSplashScreen()          // Layer 1
splashScreen.setKeepOnScreenCondition {            // hold until Layer 2 paints
    splashViewModel.isSystemSplashLoading.value
}
```

### Layer 1 — system splash (native)

- **Background:** `#0A3B22` (deep forest green) — set via `windowSplashScreenBackground`.
- **Icon:** `@drawable/ic_brand` (the white/gold "AT" monogram) — set via `windowSplashScreenAnimatedIcon`.
- **Theme:** `Theme.App.Starting` (parent `Theme.SplashScreen`), with `postSplashScreenTheme = Theme.Ahsan`.
- It is held on screen by `setKeepOnScreenCondition` until `SplashViewModel.onFirstFrameRendered()` sets `isSystemSplashLoading = false` — i.e. until Compose has actually drawn its first frame.
- Older devices (API < 31) get the same look through `windowBackground = #0A3B22` in `Theme.AhsanBase`.

### Layer 2 — Compose splash

This is the rich, visible splash. It fades in over ~200 ms and contains the full brand layout described in section 5.

---

## 3. Launch sequence & timing

```
App process starts
      │
      ▼
installSplashScreen()  ──►  Layer 1 (native) shows: deep green + AT monogram
      │
      ▼
SplashViewModel.init ──► evaluateSessionAndTiming()
      │                    ├─ reads JWT token from encrypted SessionStore
      │                    └─ decides target = Dashboard (token present)
      │                                      or Login  (no token)
      ▼
Compose first frame drawn
      │
      ▼
onFirstFrameRendered() ─► release Layer 1 (system splash hides)
      │
      ▼
Layer 2 sequence: hero fade-in → sector circles stagger in → footer fade-in
      │
      ▼
~1200 ms minimum visible duration elapses
      │
      ▼
navigationEvent → onNavigate(Route.Dashboard | Route.Login)
```

**Timing rules in `SplashViewModel`:**

- A **minimum visible duration of ~1200 ms** is enforced.
  ```kotlin
  val elapsed  = System.currentTimeMillis() - startTime
  val remaining = (1200L - elapsed).coerceAtLeast(0L)
  if (remaining > 0L) delay(remaining)
  ```
- **Session evaluation is instant** — the token is read from the encrypted `SessionStore` (AES/GCM via Android Keystore). No network call is made, so there is no spinner or "retry" state on the splash.
- After the delay, the ViewModel clears both loading flags and emits a single navigation event (`replay = 1`, so it survives the collector subscribing late).

**Routing decision:**

| Condition | Destination |
|---|---|
| Valid JWT token present in Keystore | `Route.Dashboard` (straight into the app) |
| No token (or cleared) | `Route.Login` |

---

## 4. Brand palette used on the splash

| Role | Value | Hex | Used for |
|---|---|---|---|
| Canvas / background | `SplashForestGreen` | `#0A3B22` | Full-screen splash background (Layer 1 + 2) |
| Primary text / icons | `BrandWhite` | `#FFFFFF` | Monogram "A", sector icons and labels, subtitle |
| Gold accent | `BrandGold` | `#E9BD55` | Monogram "T", ring stroke, footer slogan |
| Sector 1 | `ChickenRed` | `#C72D36` | Chicken Shop circle |
| Sector 2 | `LpgBlue` | `#0871C4` | LPG Business circle |
| Sector 3 | `BroilerGreen` | `#168540` | Broiler / Poultry Farm circle |

> The rest of the app keeps its original header green (`BrandGreen` `#003D2B`); `SplashForestGreen` is a splash-only canvas color.

---

## 5. Layer 2 visual layout (single centered composition)

The Compose splash is a full-screen `Box` with `contentAlignment = Center` wrapping one vertically-centered `Column`. Spacers have **fixed heights** (not weighted), so nothing is forced to the screen edges — the whole composition floats in the middle and can never collide with the status bar or gesture navigation bar.

```
┌─────────────────────────────────────────────┐
│               (status bar inset)            │
│                                             │
│        ┌──────────────────────────┐         │
│        │  LOGO LOCKUP (AT mark)   │         │
│        │  + AHSAN TRADERS wordmark│         │
│        └──────────────────────────┘         │
│             3 Businesses  |  1 Vision       │
│                                             │
│      ● Red        ● Blue       ● Green      │
│     Chicken      LPG          Poultry       │
│      Shop       Business       Farm         │
│                                             │
│        ~ Grow Together With Trust ~          │
│                                             │
│               (nav bar inset)               │
└─────────────────────────────────────────────┘
```

### Element-by-element

1. **Logo lockup** — `R.drawable.logo_lockup` (transparent-background PNG with density variants; vector fallback `ic_logo_lockup.xml`). The circular AT-disc now has a **transparent fill** so the script's deep-green canvas shows through, the white "A" carries a thin gold outline, and the gold "T" carries a thin white outline for sharper contrast. Width **60% of screen width**, height capped at 116 dp (86 dp on short screens), `ContentScale.Fit`.

2. **Subtitle** — "3 Businesses  |  1 Vision", sans-serif `BrandWhite`, 14 sp (12 sp short screens), `FontWeight.Medium`, letter-spacing 2.5 sp, centered.

3. **Sector showcase** — a row of three color-coded circular badges, `SpaceEvenly`, in product order:
   | Order | Color | Icon | Label |
   |---|---|---|---|
   | 1 | `ChickenRed` | chicken/rooster silhouette (`ic_sector_chicken`) | Chicken Shop |
   | 2 | `LpgBlue` | gas-cylinder + flame (`ic_sector_lpg`) | LPG Business |
   | 3 | `BroilerGreen` | broiler silhouette (`ic_sector_broiler`) | Poultry Farm |
   - Circle diameter: **15% of screen width**, clamped to 50–66 dp.
   - Icons are white at **50% of the circle diameter**; labels are sans-serif 10.5 sp, two lines, `maxLines = 2`.
   - Each circle **stages in** after its predecessor (`0 ms → 110 ms → 220 ms`) for a light sequential entrance.

4. **Footer slogan** — "Grow Together With Trust" in the bundled script font (`script_font.ttf`, italic), `BrandGold`, 19 sp (16 sp short screens), letter-spacing 0.5 sp, centered.

---

## 6. Typography system (two typefaces)

| Style | Family | Use |
|---|---|---|
| Header / body | System sans-serif (`FontWeight.Medium` / `Bold`) | Brand wordmark, subtitle, sector labels |
| Script accent | `R.font.script_font` (italic) | Footer slogan only |

The previous design mixed a bold serif/sans header, a clean body face and a cursive footer. The cursive footer is retained as the single decorative accent, keeping the total to **two families**.

---

## 7. Responsive scaling rules

| Measure | Rule |
|---|---|
| Logo width | `screenWidth * 0.60` |
| Sector circle size | `screenWidth * 0.15`, clamped to `50.dp…66.dp` |
| Icon inside circle | `circleSize * 0.5` |
| Short-screen mode | triggered when `screenHeight < 680.dp` — reduces logo cap (116→86 dp), font sizes, and inter-block spacing (38→26 dp) |

Insets are respected on all four edges: `statusBarsPadding()` + `navigationBarsPadding()` + `imePadding()` + 24 dp horizontal padding. Because the column uses fixed spacers and center arrangement, the footer can no longer collide with the gesture navigation bar on low-aspect-ratio phones (the analysis's "Vertical Density" concern).

---

## 8. Animation

| Element | Spec | Timing |
|---|---|---|
| Hero (logo + subtitle) | `fadeIn` | `tween 200 ms` at frame 0 |
| Sector circle 1 (Chicken) | `fadeIn` | `tween 240 ms`, +0 ms |
| Sector circle 2 (LPG) | `fadeIn` | `tween 240 ms`, +110 ms |
| Sector circle 3 (Poultry) | `fadeIn` | `tween 240 ms`, +220 ms |
| Footer slogan | `fadeIn` | `tween 240 ms` |
| System→Compose handoff | none needed: both layers share `#0A3B22` + AT monogram, so the swap is seamless |

The entire sequence completes within ~0.5 s after the first frame, comfortably inside a 1.2–2.5 s splash window with no looping motion.

---

## 9. Edge cases & behavior notes

- **No token:** navigates to `Login` after the 1.2 s hold.
- **Token present:** navigates to `Dashboard` after the 1.2 s hold. Server-side validation happens on the first real request.
- **Very short screens (< 680 dp):** compact spacing/font mode keeps the centered composition inside the safe area (see section 7).
- **RTL / Urdu:** splash text is static English and centered, so direction has no visual effect here.
- **Process death / recreation:** the ViewModel re-runs session evaluation on re-init; the splash simply replays.
- **No network dependency:** the splash never blocks on a network call, so it can't hang.

---

## 10. Source file map

| Concern | File |
|---|---|
| Layer 2 layout, animation, responsive sizing | `admin-android/app/src/main/java/com/ahsantraders/admin/ui/SplashScreen.kt` |
| Session evaluation & timing | `admin-android/app/src/main/java/com/ahsantraders/admin/ui/SplashViewModel.kt` |
| Layer 1 ↔ Layer 2 wiring | `admin-android/app/src/main/java/com/ahsantraders/admin/MainActivity.kt` |
| Brand palette + splash canvas color | `admin-android/app/src/main/java/com/ahsantraders/admin/ui/Color.kt` |
| System splash theme (bg + icon) | `admin-android/app/src/main/res/values/styles.xml` and `values-v31/styles.xml` |
| AT monogram (Layer 1 icon) | `admin-android/app/src/main/res/drawable/ic_brand.xml` |
| Logo lockup PNG + fallback vector | `drawable{,-xhdpi,-xxhdpi}/logo_lockup.png`, `drawable/ic_logo_lockup.xml` |
| Sector icons | `drawable/ic_sector_{chicken,lpg,broiler}.xml` |
| Slogan script font | `admin-android/app/src/main/res/font/script_font.ttf` |

