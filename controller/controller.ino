#include <Adafruit_NeoPixel.h>
#include <Arduino.h>
#include <SoftwareSerial.h>

#define RX 10
#define TX 11
#define PIN 5
#define NUM_PIXELS 268

SoftwareSerial mySerial(RX, TX);
Adafruit_NeoPixel strip = Adafruit_NeoPixel(NUM_PIXELS, PIN, NEO_GRB + NEO_KHZ800);
int currentLED = 0;
String btData; 

void setup() {
    strip.begin();
    strip.setBrightness(50);
    strip.setPixelColor(currentLED, 255, 255, 255);
    strip.show(); // Initialize all pixels to 'off'
    mySerial.begin(9600);
}

void loop() {
    
}