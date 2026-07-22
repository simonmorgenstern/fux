# LED Effects Audit Report
## Date: 2026-03-31

### Summary
- **Total Effects:** 18
- **Working:** ~14
- **Broken:** 4 (LED group loading issue)
- **Needs Tuning:** 1 (Fire)

---

## CRITICAL ISSUES FOUND

### 1. LED Group Loading Path Bug (CRITICAL)
**Affected Effects:**
- EyeBlinkEffect
- DiamondPulseEffect
- OutsideSpinEffect
- BoxWaveEffect

**Problem:**
All these effects use `../led-namer/led-groups.json` with a relative path that won't resolve correctly from Java runtime directory.

**Impact:** Effects fail to load LED groups, resulting in empty/no rendering.

**Solution:** Create a centralized LED group loader utility that tries multiple paths:
1. Absolute path: `/home/simon/.openclaw/workspace/fux/led-namer/led-groups.json`
2. Classpath resource
3. Relative to working directory

---

### 2. Fire Effect - Parameter Tuning Needed
**Status:** Implemented but may not work well

**Issues:**
- Very high sparking value (120) might create too much heat too fast
- Cooling value (55) might be too aggressive
- Y-coordinate dependency might not match actual LED layout

**Solution:** 
- Reduce sparking to 0.05-0.15 range
- Adjust cooling to 0.8-1.2 range
- Test and iterate on parameters
- Consider adding more color variety (blues at base, yellows/whites at tips)

---

## EFFECT STATUS TABLE

| # | Effect | Status | Issues | Notes |
|---|--------|--------|--------|-------|
| 1 | radial_wave | ✅ WORKING | None | Uses PixelCoordinates correctly |
| 2 | rainbow_pulse | ✅ WORKING | None | Simple, reliable |
| 3 | sparkle | ✅ WORKING | None | Good fade implementation |
| 4 | fire | ⚠️ NEEDS TUNING | Parameters too extreme | Reduce sparking, adjust cooling |
| 5 | breathing | ✅ WORKING | None | Smooth sine wave |
| 6 | snake | ✅ WORKING | Requires neighbor graph | Complex but functional |
| 7 | meteor_shower | ✅ WORKING | None | Good particle system |
| 8 | firework | ✅ WORKING | None | Nice explosion effect |
| 9 | rain | ✅ WORKING | None | Good droplet simulation |
| 10 | aurora | ✅ WORKING | None | Complex wave calculations |
| 11 | bilateral_fill | ✅ WORKING | None | Interesting fill patterns |
| 13 | gradient | ✅ WORKING | None | Multiple gradient modes |
| 14 | box_mirror | ✅ WORKING | Hardcoded box mappings | Works but not dynamic |
| 15 | eye_blink | ❌ BROKEN | LED group path issue | Can't load eyes group |
| 16 | diamond_pulse | ❌ BROKEN | LED group path issue | Can't load diamond group |
| 17 | box_wave | ❌ BROKEN | LED group path issue | Can't load box groups |
| 18 | outside_spin | ❌ BROKEN | LED group path issue | Can't load outside group |

---

## IMPROVEMENT OPPORTUNITIES

### Performance
- Most effects run at 30 FPS - consider optimizing for 60 FPS on simple effects
- Reduce logging frequency (currently logs every 300 frames)

### Color Variety
- Many effects use single colors or limited palettes
- Add more color modes and customization options

### Configurability
- Add more runtime parameters (speed, intensity, color) to JSON definitions
- Allow dynamic parameter updates without reloading effect

### Code Organization
- Create shared utilities for:
  - LED group loading
  - Color palette management
  - Common animation patterns (fade, pulse, etc.)

---

## RECOMMENDED FIXES (Priority Order)

1. **HIGH:** Create LEDGroupLoader utility class
2. **HIGH:** Fix all 4 broken effects to use new loader
3. **MEDIUM:** Tune Fire effect parameters
4. **MEDIUM:** Add parameter validation and fallbacks
5. **LOW:** Optimize performance for 60 FPS effects
6. **LOW:** Add more color variety to existing effects
