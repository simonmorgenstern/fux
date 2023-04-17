#include <Adafruit_NeoPixel.h>

#define PIN 5
#define NUM_PIXELS 60

Adafruit_NeoPixel strip = Adafruit_NeoPixel(NUM_PIXELS, PIN, NEO_GRBW + NEO_KHZ800);

void setup() {
  // put your setup code here, to run once:
  strip.begin();
  strip.setBrightness(20);
  strip.show(); // Initialize all pixels to 'off'
}

void loop() {
  // put your main code here, to run repeatedly:
   for(int i = 0; i < 60; i++) {
    strip.setPixelColor(i, 255, 255, 255);
   }
}