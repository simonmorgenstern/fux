# LED Effect Fix Summary

## Task
Fix four broken LED effects in Fux after sideStrip removal and diozero migration.

## Effects Investigated
1. **Eye Blink** (EyeBlinkEffect.java)
2. **Diamond Pulse** (DiamondPulseEffect.java)
3. **Box Wave** (BoxWaveEffect.java)
4. **Outside Spin** (OutsideSpinEffect.java)

## Root Cause
After the migration that removed sideStrip (commit 6381361), the `led-namer/led-groups.json` file only contained the 8 box groups (boxes1-boxes8). However, three of the four effects were trying to load LED groups that were never defined:

- **EyeBlinkEffect**: Attempted to load "eyes" group → returned empty set (0 LEDs)
- **DiamondPulseEffect**: Attempted to load "diamond" group → returned empty set (0 LEDs)
- **OutsideSpinEffect**: Attempted to load "outside" group → returned empty set (0 LEDs)
- **BoxWaveEffect**: Used "boxes1" through "boxes8" → **already worked** (groups existed)

When these effects rendered frames with empty LED sets, they would produce no visible output, making them appear "broken".

## What Was NOT Broken
- ✅ The migration to diozero API was correct (`setPixelColourRGB` used properly in EffectRenderer)
- ✅ No null pointer errors from sideStrip removal (effects don't reference it)
- ✅ Effect rendering pipeline works correctly
- ✅ All effects compile and build successfully
- ✅ BoxWaveEffect was already functional (used existing groups)

## The Fix
Added three missing LED group definitions to `led-namer/led-groups.json`:

### 1. Eyes Group
```json
{
  "name": "eyes",
  "ranges": [
    { "start": 34, "end": 67 },    // box 2
    { "start": 204, "end": 237 }   // box 7
  ]
}
```
- **68 LEDs total** (34 per "eye")
- Uses boxes 2 and 7 as the two eyes
- EyeBlinkEffect now blinks these LEDs with cycling colors

### 2. Diamond Group
```json
{
  "name": "diamond",
  "ranges": [
    { "start": 68, "end": 101 },    // box 3
    { "start": 102, "end": 135 },   // box 4
    { "start": 136, "end": 169 },   // box 5
    { "start": 170, "end": 203 }    // box 6
  ]
}
```
- **136 LEDs total** (center diamond pattern)
- Uses boxes 3-6 (middle four boxes)
- DiamondPulseEffect now pulses these LEDs with breathing pattern

### 3. Outside Group
```json
{
  "name": "outside",
  "ranges": [
    { "start": 0, "end": 33 },      // box 1
    { "start": 34, "end": 67 },     // box 2
    { "start": 204, "end": 237 },   // box 7
    { "start": 238, "end": 267 }    // box 8
  ]
}
```
- **136 LEDs total** (outer ring)
- Uses boxes 1, 2, 7, 8 (perimeter boxes)
- OutsideSpinEffect now creates spinning rainbow on these LEDs

## LED Layout (Total: 268 LEDs, 0-267)
```
Box Structure:
- Box 1: LEDs 0-33    (34 LEDs) 
- Box 2: LEDs 34-67   (34 LEDs)  } Eyes (also in Outside)
- Box 3: LEDs 68-101  (34 LEDs)  }
- Box 4: LEDs 102-135 (34 LEDs)  } Diamond (center)
- Box 5: LEDs 136-169 (34 LEDs)  }
- Box 6: LEDs 170-203 (34 LEDs)  }
- Box 7: LEDs 204-237 (34 LEDs)  } Eyes (also in Outside)
- Box 8: LEDs 238-267 (30 LEDs)  } Outside (perimeter)
```

## Testing
- ✅ Build: `mvn clean package` - **SUCCESS**
- ✅ All effects load LED groups successfully (no longer return empty sets)
- ✅ No compilation errors
- ✅ Effects can render frames without crashing
- ✅ Committed and pushed to `feature/effect-engine` branch

## Commit Details
- **Commit**: 2a0427c (after rebase with a6e77da)
- **Branch**: feature/effect-engine
- **Files Changed**: led-namer/led-groups.json (+25 lines)

## Notes
The LED group mappings (eyes=boxes 2+7, diamond=boxes 3-6, outside=boxes 1+2+7+8) are reasonable defaults based on the 8-box structure. If the physical installation has a different layout, these can be adjusted by editing `led-namer/led-groups.json`.

The visual LED editor (`led-namer/`) can be used to refine these groups if needed, but these defaults should make all four effects functional immediately.
