# Neue Effekt-Ideen für Fux

## 1. **Rainbow Pulse** (Einfach)
**Was:** Alle LEDs pulsieren gleichzeitig durch Regenbogenfarben

**Visuell:**
- Alle 268 LEDs zeigen dieselbe Farbe
- Farbe rotiert langsam durch Regenbogen (Rot → Gelb → Grün → Cyan → Blau → Magenta → Rot)
- Helligkeit pulsiert sanft (0.2 → 1.0 → 0.2)

**Parameter:**
- `hue_rotation_speed`: Wie schnell Farben wechseln (z.B. 1.0 = 1 Grad pro Frame)
- `pulse_speed`: Wie schnell Hell/Dunkel pulsiert
- `brightness_min/max`: Pulsbereich

**Komplexität:** ⭐ (sehr einfach, ~30 Zeilen Code)

---

## 2. **Sparkle** (Einfach)
**Was:** Zufällige LEDs blinken wie Sterne

**Visuell:**
- N zufällige LEDs pro Frame leuchten hell auf
- Farbe: Weiß oder zufällige Regenbogenfarbe
- Langsames Ausblenden (2-3 Frames)

**Parameter:**
- `density`: Wie viele LEDs gleichzeitig funkeln (z.B. 10-30)
- `color_mode`: "white" | "rainbow" | "single"
- `fade_speed`: Wie schnell das Funkeln verblasst

**Komplexität:** ⭐⭐ (~40 Zeilen Code, benötigt State für Fade-Tracking)

---

## 3. **Fire** (Mittel)
**Was:** Flackerndes Feuer-Effekt (orange/rot/gelb)

**Visuell:**
- LEDs am "Boden" (hohe Y-Koordinate) sind heiß (rot/orange)
- LEDs oben sind kühler (orange/gelb)
- Zufälliges Flackern simuliert Flammen-Bewegung

**Parameter:**
- `intensity`: Wie stark das Feuer flackert
- `heat_spread`: Wie weit die Hitze nach oben wandert
- `cooling`: Wie schnell Flammen abkühlen

**Komplexität:** ⭐⭐⭐ (~80 Zeilen Code, Perlin Noise oder Cellular Automata)

---

## 4. **Breathing** (Einfach)
**Was:** Sanftes "Atmen" - alle LEDs werden langsam heller und dunkler

**Visuell:**
- Alle LEDs in einer Farbe
- Sinuskurve für Helligkeit (smooth in/out)
- Sehr beruhigend

**Parameter:**
- `color`: RGB oder HSV
- `breath_rate`: Atemzyklen pro Minute (z.B. 12 = wie echter Atem)
- `min_brightness`: Minimale Helligkeit

**Komplexität:** ⭐ (~20 Zeilen Code)

---

## 5. **Scanner** (Wie Knight Rider)
**Was:** Ein heller Balken wandert hin und her

**Visuell:**
- Heller Punkt/Balken wandert von innen nach außen
- Dann zurück von außen nach innen
- Trailing fade hinter dem Balken

**Parameter:**
- `speed`: Wandergeschwindigkeit
- `color`: Farbe des Balkens (klassisch: rot)
- `width`: Breite des Balkens (in Pixeln)

**Komplexität:** ⭐⭐ (~50 Zeilen Code)

---

## 6. **Spiral Waves** (Komplex)
**Was:** Spiralförmige Wellen statt radiale

**Visuell:**
- Wellen drehen sich um das Zentrum während sie nach außen wandern
- Regenbogenfarben entlang der Spirale
- Hypnotischer Effekt

**Parameter:**
- `rotation_speed`: Drehgeschwindigkeit
- `wave_speed`: Ausbreitungsgeschwindigkeit
- `spiral_tightness`: Wie eng die Spirale ist
- `colors`: Farbpalette

**Komplexität:** ⭐⭐⭐⭐ (~120 Zeilen Code, Polar-Koordinaten + Rotation)

---

## 7. **Meteor Shower** (Mittel-Komplex)
**Was:** Leuchtende "Meteore" fliegen über den Fuchs

**Visuell:**
- Mehrere Meteore gleichzeitig
- Jeder Meteor: Heller Kopf + verblassender Schweif
- Zufällige Start-/Endpunkte, Geschwindigkeiten

**Parameter:**
- `meteor_count`: Anzahl gleichzeitiger Meteore
- `speed_min/max`: Geschwindigkeitsbereich
- `trail_length`: Länge des Schweifs
- `colors`: Meteorfarben

**Komplexität:** ⭐⭐⭐⭐ (~150 Zeilen Code, Vektor-Mathematik, State-Tracking)

---

## 8. **Color Zones** (Mittel)
**Was:** Fuchs ist in farbige Zonen unterteilt, die sich bewegen

**Visuell:**
- 3-5 Farbzonen basierend auf Y-Koordinate
- Zonen bewegen sich langsam auf/ab
- Sanfte Farbübergänge an Grenzen

**Parameter:**
- `zone_count`: Anzahl Zonen
- `colors`: Farben der Zonen
- `scroll_speed`: Bewegungsgeschwindigkeit
- `blend_width`: Übergangsbreite

**Komplexität:** ⭐⭐⭐ (~70 Zeilen Code)

---

## 9. **Matrix Rain** (Komplex)
**Was:** Grüne Zeichen "fallen" wie in The Matrix

**Visuell:**
- Vertikale Linien von LEDs leuchten nacheinander auf
- Grüne Farbe (klassisch) oder konfigurierbar
- Zufällige Startpunkte und Geschwindigkeiten

**Parameter:**
- `drop_count`: Anzahl gleichzeitiger "Tropfen"
- `speed`: Fallgeschwindigkeit
- `color`: Farbe (klassisch: grün)
- `trail_length`: Länge des Trails

**Komplexität:** ⭐⭐⭐⭐ (~100 Zeilen Code, benötigt X/Y-Gruppierung)

---

## 10. **Heartbeat** (Einfach)
**Was:** Pulsiert wie ein schlagendes Herz

**Visuell:**
- Doppel-Puls vom Zentrum (lub-dub)
- Rote oder rosa Farbe
- Pause zwischen Schlägen

**Parameter:**
- `bpm`: Schläge pro Minute
- `color`: Herzfarbe
- `intensity`: Stärke des Pulses

**Komplexität:** ⭐⭐ (~40 Zeilen Code, Timing-basiert)

---

## Empfehlung für die nächsten Implementierungen:

**Reihenfolge nach Priorität:**
1. **Rainbow Pulse** - Super einfach, visuell ansprechend, guter Test
2. **Breathing** - Beruhigend, zeigt Timing-Kontrolle
3. **Sparkle** - Macht Spaß, zeigt Randomness
4. **Fire** - Beeindruckend, zeigt komplexere Algorithmen
5. **Meteor Shower** - Wow-Effekt, zeigt State-Management

**Quick Wins (heute noch machbar):**
- Rainbow Pulse: 15 Minuten
- Breathing: 10 Minuten
- Sparkle: 30 Minuten

**Welche interessieren dich am meisten?**
