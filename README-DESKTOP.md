# Arena — Windows Desktop App (JavaFX)

A Windows desktop wrapper for **https://arena.ai/** built with JavaFX:

1. **Discord-style splash screen** on launch — borderless window, logo, rotating
   tips, live loading status and progress bar.
2. The website **preloads behind the splash**, then the splash fades out and the
   main window appears **already on arena.ai** (no second load).
3. The main window is a slim desktop browser: back / forward / reload / home,
   address pill, thin load-progress bar, offline/error overlay, sign-in popups
   kept in-app, other pop-ups opened in your system browser.

> Note: the original `HelloApplication` / `hello-view.fxml` sample files are
> still in the repo but are no longer used. The entry point is now
> `Launcher` → `ArenaApplication`.

## Run it (Windows, dev mode)

Prerequisites: **JDK 21+** on `PATH` (Maven itself is not required —
the repo ships the `mvnw` wrapper).

```powershell
.\mvnw.cmd javafx:run
```

## Project map

| File | Role |
|---|---|
| `src/main/java/cm/odigital/arenaai/Launcher.java` | `main()` entry — sets up logging, launches `ArenaApplication` (also the `jpackage` entry) |
| `.../ArenaApplication.java` | App orchestration: preload site → splash → fade to main window; remembers window size |
| `.../ArenaConfig.java` | One place for the URL, timings, user agent, window defaults, auth-popup hosts |
| `.../SplashController.java` | Splash logic: tips rotation, status text, progress binding, fade in/out |
| `.../BrowserController.java` | Main window: toolbar, history, address pill, error overlay, shortcuts, JS dialogs, pop-up handling, log-out, logs |
| `.../WebViewFactory.java` | Builds the pre-configured `WebView` (JS on, desktop Chrome-on-Windows user agent) |
| `.../CookiePersistence.java` | Installs the file-backed cookie handler; `clearAll()` for log-out |
| `.../PersistentCookieStore.java` | `CookieStore` that saves cookies to disk on every change and reloads them on launch |
| `.../AppPaths.java` | Resolves the per-user data dir (`%APPDATA%\Arena`) |
| `.../AppLog.java` | Central logging: console + rotating files under `%APPDATA%\Arena\logs` |
| `.../JsConsoleBridge.java` | Captures page JS console output + uncaught JS errors into the log |
| `.../AuthPopupDialog.java` | In-app sign-in window for login popups (shares the app's cookies) |
| `src/main/resources/cm/odigital/arenaai/splash-view.fxml` | Splash layout (borderless card) |
| `src/main/resources/cm/odigital/arenaai/browser-view.fxml` | Main window layout (toolbar + `WebView` container + error overlay) |
| `.../css/splash.css`, `.../css/browser.css` | Discord-ish dark themes |
| `.../images/arena-logo.png` | App logo (splash + window icon). Replace with your own art anytime |
| `package-windows.ps1` | One-command Windows packaging (portable folder or installer) |

## Change the website / splash timing

Everything lives in
`src/main/java/cm/odigital/arenaai/ArenaConfig.java`:

```java
public static final String HOME_URL = "https://arena.ai/";
public static final long SPLASH_MIN_VISIBLE_MS = 3200;  // min splash time
public static final long SPLASH_MAX_WAIT_MS = 20_000;   // never stuck longer
```

The splash closes when **both** are true: the minimum time elapsed **and**
the page finished loading (or failed) — the max timeout guarantees the app
never gets stuck on the splash, even offline.

## Build a Windows installer / portable app

```powershell
# Portable folder (no extra tools needed) -> target\dist\Arena\Arena.exe
.\package-windows.ps1

# Real installers (require WiX Toolset v3 on PATH: https://wixtoolset.org)
.\package-windows.ps1 -Type msi
.\package-windows.ps1 -Type exe -AppVersion 1.0.1
```

What the script does: `mvnw clean package` → `mvnw javafx:jlink`
(trimmed runtime in `target\app`) → `jpackage` with Start-Menu entry,
desktop shortcut and install-dir chooser.

## App icon for the installer

`jpackage` on Windows needs **`.ico`** format. To use the bundled logo:

1. Convert `src/main/resources/cm/odigital/arenaai/images/arena-logo.png`
   to `.ico` (e.g. with GIMP, IrfanView, or an online PNG→ICO converter).
2. Save it as `src/main/resources/arena-icon.ico`.
3. Re-run `.\package-windows.ps1` — it picks the icon up automatically.

(The in-app splash + window icon already use the PNG directly.)

## Shortcuts

| Keys | Action |
|---|---|
| `F5` / `Ctrl+R` | Reload |
| `Alt+Left` / `Alt+Right` | Back / forward |
| `Alt+Home` | Go to arena.ai home |
| Click address pill | Copy current page URL |

## Browsing data & privacy

- **Cookies persist** in `%APPDATA%\Arena\cookies.dat` (on other OSes:
  `~/.arena/cookies.dat`), so you **stay logged in** to arena.ai between
  launches. `Max-Age` cookies are aged by the time spent away; expired ones
  are dropped on load.
- The cookie file is a plain binary file, **not encrypted** — anyone with
  access to your Windows user account could read your session cookies.
- The **Log out** toolbar button (with confirmation) clears all cookies +
  the current site's local/session storage and returns to the home page.
- Cache, history and `localStorage` are otherwise in-memory and vanish on exit.

## Logs & diagnosing errors (e.g. Google sign-in)

- Click **Logs** in the toolbar to open the current log file directly.
  Files live at `%APPDATA%\Arena\logs\arena-0.log` (`arena-1.log`… are older
  rotations, 1 MB × 5). Errors are also printed to the console when running
  via `.\mvnw.cmd javafx:run`.
- What gets logged: app lifecycle, every navigation, page-load failures with
  stack traces, WebView engine errors, **page JavaScript console output and
  uncaught JS errors** (tagged `[JS …]`), popup decisions, sign-in window
  events, uncaught exceptions.
- To diagnose the Google-login error: reproduce the sign-in, open **Logs**,
  and look for `[JS error]`, `Page load failed` and `Popup (sign-in)` lines —
  they show what the page itself reported.
- Sign-in popups (Google, GitHub, Microsoft, … — see `AUTH_POPUP_HOSTS` in
  `ArenaConfig`) now open in an **in-app sign-in window** sharing the app's
  cookies, instead of the system browser where the login couldn't complete.
  Set `OPEN_AUTH_POPUPS_IN_APP = false` to restore the old behavior.

## Good to know / limitations

- The embedded browser is JavaFX `WebView` (WebKit-based), **not** full
  Chrome: most of arena.ai works fine, but some cutting-edge web features,
  DRM video or exotic CSS may render differently than in Chrome/Edge.
  If a page misbehaves, the `↗` button opens it in the system browser.
- `target="_blank"` links and `window.open(...)` pop-ups open in the system
  browser, **except sign-in flows**, which stay in-app (see above).
- JavaScript `alert` / `confirm` / `prompt` are mapped to native dialogs.
- Downloads: `WebView` has no download manager — file downloads triggered
  by the site may not work inside the app; use the system browser for those.
- No build was run in this environment (Java/Maven excluded) — first run
  `.\mvnw.cmd javafx:run` on a machine with JDK 21 to verify.
