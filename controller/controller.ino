#include <Adafruit_NeoPixel.h>
#include <Arduino.h>

#define PIN 5
#define NUM_PIXELS 268

Adafruit_NeoPixel strip = Adafruit_NeoPixel(NUM_PIXELS, PIN, NEO_GRB + NEO_KHZ800);

void setup() {
    strip.begin();
    strip.setBrightness(50);
    strip.show(); // Initialize all pixels to 'off'
}

void loop() {

}