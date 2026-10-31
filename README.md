# Mayo Home

Ein eigener Startbildschirm für Android TV, gebaut für die Nokia Streaming Box 8010 und alles, was
darauf läuft: Mayo TV, TizenTube, Nuvio.

- **Start**: ein großes Poster mit den Highlights des Moments. Spiele deiner Teams stehen vorne, dann
  Sport, Filme und Serien auf deinen Sendern, mit dem Bild aus der TV-Zeitschrift. Darunter
  „Weiter schauen“, deine Sender mit der laufenden Sendung und „Zuletzt gesehen“ aus Mayo TV.
- **Apps**: alle Apps als Raster, darunter die Kanäle, die deine Apps für den Startbildschirm anbieten
  (wie in Projectivy).
- **Einstellungen**: über das Zahnrad oben rechts. Fast alles ist einstellbar.

> Prototyp. Gebaut und mit Unit-Tests geprüft, aber noch nicht auf einem Fernseher ausprobiert.

## Installieren

1. APK laden: `https://github.com/Jonathanxoxox/mayo-home/releases/download/latest/MayoHome.apk`
   (am Fernseher z. B. mit Downloader). Geht nur, wenn das Repo öffentlich ist; sonst die APK aus
   dem Actions-Lauf laden und per ADB installieren: `adb install MayoHome.apk`.
2. Mayo Home öffnen.
3. **Home-Taste auf Mayo Home legen**, eine der beiden Arten:
   - *Home-Wächter* (wie Projectivy): Einstellungen → Hauptlauncher → „Home-Wächter einschalten“ und in
     den Bedienungshilfen „Mayo Home: Home-Taste“ aktivieren. Fehlt das Menü auf der Box, per ADB:
     ```
     adb shell settings put secure enabled_accessibility_services io.github.jonathanxoxox.mayohome/.system.HomeGuardService
     adb shell settings put secure accessibility_enabled 1
     ```
   - *Standard-Startbildschirm* (sauberer, geht nicht auf jeder Box):
     ```
     adb shell cmd package set-home-activity io.github.jonathanxoxox.mayohome/.MainActivity
     ```
4. **Kanäle der Apps freigeben**: Beim ersten Start fragt Mayo Home nach den TV-Programmdaten.
   Kommt keine Abfrage, per ADB:
   ```
   adb shell pm grant io.github.jonathanxoxox.mayohome com.android.providers.tv.permission.READ_EPG_DATA
   ```
5. Optional, damit Mayo Home nach dem Einschalten sicher startet:
   ```
   adb shell appops set io.github.jonathanxoxox.mayohome SYSTEM_ALERT_WINDOW allow
   ```

## Woher die Inhalte kommen

| Bereich | Quelle |
|---|---|
| Spiele deiner Teams | Kanal „Für dich“ von Mayo TV (Android-TV-Kanäle) |
| Highlights und Sender mit Bild | Programm von MagentaTV, dieselbe Abfrage wie in [iptv-org/epg](https://github.com/iptv-org/epg). Keine offizielle Schnittstelle, funktioniert womöglich nur in Deutschland. |
| Weiter schauen | „Weiter schauen“ von Android TV, das z. B. Nuvio füllt |
| Zuletzt gesehen, Lieblingssender | Kanäle von Mayo TV |
| Kanäle der Apps | Android-TV-Kanäle aller Apps |

Ein Klick auf einen Sender öffnet ihn in Mayo TV, wenn er dort unter den Favoriten oder zuletzt
gesehenen steht; sonst öffnet sich Mayo TV.

## Einstellungen

- **Startseite**: Fußball zuerst, Lieblingsteams, welche Arten als Highlight gelten (Fußball, Sport,
  Filme, Serien, Shows, Dokus), Quellen des Posters einzeln an/aus, Wechselzeit, Anzahl, Vorschau-
  zeitraum, Reihen ein-/ausblenden und sortieren, Seite beim Öffnen.
- **Sender**: Auswahl aus über 300 Sendern, Reihenfolge, Programm neu laden.
- **Apps**: Spalten, App-Namen (immer, beim Auswählen, nie), Sortierung (eigene, alphabetisch, zuletzt
  benutzt), ausblenden, Kanäle der Apps einzeln ein/aus. Langer Druck auf eine App: verschieben,
  als Lieblings-App auf die Startseite, beim Einschalten öffnen, ausblenden, App-Info, deinstallieren.
- **Aussehen**: Akzentfarbe, Hintergrund (auch das Bild des Posters), Ecken, Vergrößern beim
  Auswählen, Animationen, Größe der Oberfläche, Uhr (24 h, Datum).
- **Hauptlauncher**: Home-Taste, Standard-Startbildschirm, beim Einschalten öffnen (Mayo Home oder
  eine App), Berechtigungen, ADB-Befehle.
- **Sichern & Schutz**: Export und Import als JSON im Download-Ordner, Zurücksetzen, PIN für die
  Einstellungen.

## Bauen

```
./gradlew testDebugUnitTest assembleDebug
```

Jeder Push auf `main` baut per GitHub Action eine signierte APK und legt sie im Release `latest` ab.
Für gleichbleibende Signatur das Secret `KEYSTORE_BASE64` setzen (dasselbe Vorgehen wie bei Mayo TV).
