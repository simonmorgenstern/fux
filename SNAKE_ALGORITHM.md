# Snake-Effekt: Nachbarschafts-Algorithmus

## Problem
Die LEDs haben X/Y-Koordinaten, aber keine explizite Information darüber, welche LEDs physisch "verbunden" sind (als Pfad).

## Lösung: Distanz-basierte Nachbarschaftserkennung

### Algorithmus

**Für jede LED:**
1. Berechne euklidische Distanz zu allen anderen LEDs
2. Finde die N nächsten LEDs (z.B. N=3-4)
3. Akzeptiere nur LEDs innerhalb eines Distanz-Schwellwerts (z.B. 15-35 Pixel)
4. Speichere als Nachbarn

**Mathematik:**
```
distance = sqrt((x1 - x2)² + (y1 - y2)²)

if distance < MAX_NEIGHBOR_DISTANCE and distance > MIN_NEIGHBOR_DISTANCE:
    → Nachbar!
```

### Parameter
- **MIN_DISTANCE:** 10 Pixel (verhindert "sich selbst" als Nachbar)
- **MAX_DISTANCE:** 30 Pixel (typischer Abstand zwischen aufeinanderfolgenden LEDs)
- **MAX_NEIGHBORS:** 4 (jede LED hat maximal 4 Nachbarn: oben, unten, links, rechts)

### Java-Implementation

**Klasse: `LEDNeighborGraph`**
```java
public class LEDNeighborGraph {
    private Map<Integer, List<Integer>> neighbors;
    
    public void build(PixelCoordinates coords) {
        // Für jede LED
        for (int i = 0; i < coords.getCount(); i++) {
            PixelCoordinate led = coords.get(i);
            List<Integer> ledNeighbors = new ArrayList<>();
            
            // Finde nächste LEDs
            List<LEDDistance> distances = new ArrayList<>();
            for (int j = 0; j < coords.getCount(); j++) {
                if (i == j) continue;
                
                PixelCoordinate other = coords.get(j);
                double dist = distance(led, other);
                
                if (dist >= 10 && dist <= 30) {
                    distances.add(new LEDDistance(j, dist));
                }
            }
            
            // Sortiere nach Distanz, nimm die 4 nächsten
            distances.sort(Comparator.comparingDouble(d -> d.distance));
            for (int k = 0; k < Math.min(4, distances.size()); k++) {
                ledNeighbors.add(distances.get(k).ledIndex);
            }
            
            neighbors.put(i, ledNeighbors);
        }
    }
    
    public List<Integer> getNeighbors(int ledIndex) {
        return neighbors.getOrDefault(ledIndex, new ArrayList<>());
    }
}
```

### Snake-Bewegung

**Algorithmus:**
1. Snake startet bei zufälliger LED
2. Jeder Frame: Wähle zufälligen Nachbarn der aktuellen Kopf-Position
3. Bewege Kopf dorthin
4. Körper folgt (Queue: neue Position am Kopf, alte am Schwanz entfernen)
5. Trail-Effekt: Körper verblasst langsam

**Pseudo-Code:**
```java
// Snake state
Queue<Integer> snakeBody; // LED indices
int snakeLength = 10;

// Move
int currentHead = snakeBody.peekLast();
List<Integer> possibleMoves = graph.getNeighbors(currentHead);
int nextLED = possibleMoves.get(random.nextInt(possibleMoves.size()));

snakeBody.add(nextLED);
if (snakeBody.size() > snakeLength) {
    snakeBody.remove(); // Remove tail
}
```

### Visualisierung

```
Frame 1:  Snake: [148, 147, 181]  (Kopf=181, grün)
Frame 2:  Snake: [147, 181, 149]  (bewegt sich zu 149)
Frame 3:  Snake: [181, 149, 182]  (weiter...)

Rendering:
- Kopf: Helle Farbe
- Körper: Gradient zum Schwanz (verblassend)
- Schwanz: Dunkel/transparent
```

### Erweiterungen

**Mögliche Features:**
1. **Mehrere Schlangen** gleichzeitig
2. **Kollisionserkennung** (Snake vermeidet sich selbst)
3. **"Wachstum"** - Snake wird länger über Zeit
4. **Farbwechsel** - Kopf wechselt Farbe während Bewegung
5. **Geschwindigkeit** - Parameter für Frames-pro-Bewegung

## Soll ich das implementieren?

Ich würde:
1. `LEDNeighborGraph.java` erstellen
2. `SnakeEffect.java` implementieren
3. Graph beim Start einmalig berechnen (cachen)
4. Snake animieren

**Geschätzte Zeit:** 30-40 Minuten
**Komplexität:** ⭐⭐⭐⭐

Bereit?
