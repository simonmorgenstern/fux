# Fux Effect Format Specification

## Architektur

**Speicherort:** `/home/pi/fux-effects/` auf dem Raspberry Pi

**Ablauf:**
1. Effekte werden als JSON-Dateien auf dem Pi gespeichert
2. WebSocket-Command: `LOAD_EFFECT:radial_waves.json`
3. Java-Server lädt Effekt, berechnet Frames in Echtzeit
4. Rendert kontinuierlich ohne RAM-Overhead

## Effekt-Datei Format

```json
{
  "name": "Radial Color Waves",
  "version": "1.0",
  "type": "procedural",
  "fps": 33,
  "parameters": {
    "wave_spacing": 100,
    "wave_width": 60,
    "wave_speed": 5,
    "colors_hsv": [0, 60, 120, 180, 240, 300]
  },
  "algorithm": {
    "type": "radial_wave",
    "description": "Waves emanate from center, colored by distance"
  }
}
```

## Effekt-Typen

### Type: "procedural"
Server berechnet Frames basierend auf Algorithmus + Parametern.

**Unterstützte Algorithmen:**
- `radial_wave` - Wellen vom Zentrum nach außen
- `color_pulse` - Gleichzeitig pulsierende Farben
- `sparkle` - Zufällige blinkende LEDs
- `gradient_sweep` - Farbverlauf der sich bewegt

### Type: "pre-rendered"
Frames sind vorberechnet und komprimiert gespeichert.

```json
{
  "type": "pre-rendered",
  "frames": 120,
  "compressed": true,
  "data_file": "complex_animation.dat"
}
```

## WebSocket-Protokoll Erweiterung

### Neue Commands:

**Effekt laden & starten:**
```
EFFECT:radial_waves
```

**Effekt mit Parametern überschreiben:**
```json
{
  "command": "EFFECT",
  "name": "radial_waves",
  "override": {
    "wave_speed": 8,
    "colors_hsv": [0, 120, 240]
  }
}
```

**Effekt stoppen:**
```
STOP_EFFECT
```

**Effekte auflisten:**
```
LIST_EFFECTS
```

## Beispiel-Effekte

### 1. radial_waves.json
Kontinuierliche Farbwellen vom Zentrum

### 2. rainbow_pulse.json
Alle LEDs gleichzeitig pulsieren in Regenbogenfarben

### 3. fire.json
Flackerndes Feuer-Effekt (orange/rot/gelb)

### 4. starfield.json
Zufällige weiße LEDs blinken wie Sterne

## Implementation auf dem Pi

**Java-Klassen:**
- `EffectEngine.java` - Lädt und rendert Effekte
- `EffectLoader.java` - Parst JSON-Definitionen
- `RadialWaveEffect.java` - Implementiert radial_wave Algorithmus
- `Effect.java` - Interface für alle Effekte

**Vorteile:**
- ✓ Kein RAM-Overhead (Frames werden on-the-fly berechnet)
- ✓ Endlos-Loops perfekt (Algorithmus ist zyklisch)
- ✓ Parameter änderbar ohne Neuberechnung
- ✓ Neue Effekte = neue JSON-Datei + ggf. neue Java-Klasse
- ✓ Effekte können geteilt/versioniert werden

**Performance:**
- Moderne Pi kann ~100-200 FPS berechnen
- 268 LEDs × 33 FPS = ~9k LED-Updates/s (kein Problem)

## Migration Plan

**Phase 1:** Basis-Infrastruktur
- EffectEngine Klasse
- JSON-Loader
- WebSocket-Command-Parsing

**Phase 2:** Erste Effekte
- RadialWaveEffect (aktueller Welleneffekt)
- SolidColorEffect (einfach zum Testen)

**Phase 3:** Erweiterte Features
- Parameter-Override via WebSocket
- Effekt-Transitions (sanfter Übergang)
- Kombinations-Effekte

## Geschätzter Aufwand

- Java-Erweiterung: ~200 Zeilen für Basis + ~50-100 pro Effekt
- Python-Helper (Effekt-Generator): ~100 Zeilen
- Testing & Tuning: 1-2 Stunden

## Soll ich das umsetzen?

1. Java-Code erweitern (EffectEngine + RadialWaveEffect)
2. Beispiel-Effekt-JSON erstellen
3. Python-Tool zum Effekte-Upload erstellen
4. Testen & dokumentieren
