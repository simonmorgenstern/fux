#!/usr/bin/env python3
import json
import math
import matplotlib.pyplot as plt
import sys

with open('assets/pixelCoordinates.json', 'r') as f:
    coords = json.load(f)

# Sort by index
sorted_coords = sorted(coords, key=lambda c: c['index'])
xs = [c['x'] for c in sorted_coords]
ys = [c['y'] for c in sorted_coords]

print(f"Path length {len(xs)}")
print("First 20 points:")
for i in range(min(20, len(xs))):
    print(f"  {i}: ({xs[i]}, {ys[i]})")

# Compute direction vectors
angles = []
for i in range(len(xs)-1):
    dx = xs[i+1] - xs[i]
    dy = ys[i+1] - ys[i]
    angle = math.atan2(dy, dx) if abs(dx) > 0.1 or abs(dy) > 0.1 else 0
    angles.append(angle)

# Look for sharp turns (change > 45 degrees)
sharp_turns = []
for i in range(len(angles)-1):
    delta = abs(angles[i+1] - angles[i])
    if delta > math.pi:
        delta = 2*math.pi - delta
    if delta > math.pi/4:  # 45 degrees
        sharp_turns.append(i+1)

print(f"\nSharp turns at indices: {sharp_turns[:10]}")
if sharp_turns:
    # Show coordinates around turns
    for t in sharp_turns[:5]:
        print(f"  Turn at index {t}: ({xs[t]}, {ys[t]})")

# Try to detect rectangles: look for sequences of four turns approx 90 degrees
rectangles = []
for i in range(len(sharp_turns)-3):
    # check if the four turns are roughly sequential
    idx0 = sharp_turns[i]
    idx1 = sharp_turns[i+1]
    idx2 = sharp_turns[i+2]
    idx3 = sharp_turns[i+3]
    # distances between turns should be similar? Not sure
    # just print for now
    print(f"Possible rectangle corners at indices {idx0},{idx1},{idx2},{idx3}")

# If matplotlib is available, plot
try:
    plt.figure(figsize=(10,8))
    plt.plot(xs, ys, 'b-', linewidth=0.5, alpha=0.7)
    plt.scatter(xs, ys, c=range(len(xs)), cmap='viridis', s=10)
    plt.colorbar(label='LED index')
    plt.title('LED layout (index order)')
    plt.xlabel('X')
    plt.ylabel('Y')
    plt.gca().invert_yaxis()  # because y=0 at top? maybe not
    plt.savefig('led_layout.png', dpi=150)
    print("\nSaved plot to led_layout.png")
except Exception as e:
    print(f"Could not plot: {e}")