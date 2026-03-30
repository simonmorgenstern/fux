# Effects API Documentation

REST API endpoints for discovering and querying available LED effects.

## Endpoints

### `GET /api/effects`

Lists all available LED effects with their metadata.

**Response:**
```json
{
  "count": 18,
  "effects": [
    {
      "id": "rain",
      "name": "Rain",
      "description": "Rain drops falling down the display",
      "algorithm": "rain",
      "type": "procedural",
      "fps": 30,
      "version": "1.0",
      "parameters": [
        {
          "name": "drop_count",
          "type": "number",
          "default": 10
        },
        {
          "name": "speed_min",
          "type": "number",
          "default": 2.0
        },
        {
          "name": "speed_max",
          "type": "number",
          "default": 5.0
        },
        {
          "name": "trail_length",
          "type": "number",
          "default": 15
        },
        {
          "name": "colors",
          "type": "array",
          "default": ["blue", "cyan", "light_blue"]
        }
      ],
      "tags": ["weather"]
    },
    ...
  ]
}
```

### `GET /api/effects/{name}`

Get detailed information about a specific effect.

**Parameters:**
- `name` (path): Effect identifier (e.g., "rain", "fire", "meteor_shower")

**Response:**
```json
{
  "id": "fire",
  "name": "Fire",
  "description": "Fire-like flickering effect",
  "algorithm": "fire",
  "type": "procedural",
  "fps": 30,
  "version": "1.0",
  "parameters": [
    {
      "name": "intensity",
      "type": "number",
      "default": 1.0
    },
    {
      "name": "cooling",
      "type": "number",
      "default": 55
    },
    {
      "name": "sparking",
      "type": "number",
      "default": 120
    }
  ],
  "tags": ["flame"]
}
```

**Error Response (404):**
```json
{
  "error": "Effect not found: nonexistent_effect",
  "status": 404
}
```

## Available Effects

The API provides metadata for all 18 built-in effects:

- **radial_wave** - Wave patterns radiating from center
- **rainbow_pulse** - Pulsing rainbow colors
- **sparkle** - Random sparkling pixels
- **fire** - Fire-like flickering effect
- **breathing** - Smooth breathing/pulsing effect
- **snake** - Snake movement patterns
- **meteor_shower** - Meteors falling across display
- **firework** - Firework explosions
- **rain** - Rain drops falling down
- **aurora** - Aurora borealis effect
- **bilateral_fill** - Bilateral fill patterns
- **motion_blur** - Motion blur effects
- **gradient** - Color gradient transitions
- **box_mirror** - Mirrored box patterns
- **eye_blink** - Eye blinking effect
- **diamond_pulse** - Diamond-shaped pulsing
- **box_wave** - Box wave patterns
- **outside_spin** - Spinning from outside

## Effect Metadata

Each effect includes:

- **id**: Unique identifier (used in API calls and queue)
- **name**: Human-readable display name
- **description**: What the effect looks like
- **algorithm**: Algorithm type (maps to effect class)
- **type**: Effect category (e.g., "procedural")
- **fps**: Target frames per second
- **version**: Effect version string
- **parameters**: Array of configurable parameters with:
  - `name`: Parameter identifier
  - `type`: Data type (number, string, boolean, array, object)
  - `default`: Default value
- **tags**: Auto-generated tags for categorization (weather, flame, particle, pulse, wave, etc.)

## Usage Examples

### List all effects
```bash
curl http://localhost:8080/api/effects
```

### Get effect details
```bash
curl http://localhost:8080/api/effects/rain
```

### Using with WebSocket
The effect `id` from this API can be used with the WebSocket control API to trigger effects:
```json
{
  "action": "add_to_queue",
  "effect": "rain"
}
```

## CORS Support

All endpoints include CORS headers to allow cross-origin requests from web frontends.

## Testing

Run the test script to verify all endpoints:
```bash
./test-effects-api.sh [port]
```

Default port is 8080, or specify alternate port as argument.
