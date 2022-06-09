import tornado.httpserver
import tornado.websocket
import tornado.ioloop
import tornado.web

import json
import re
import random

from rpi_ws281x import PixelStrip, Color

# WebSocket config
PORT = 80

# NeoPixel config
LED_COUNT = 268
LED_PIN = 18
LED_FREQ_HZ = 800000
LED_DMA = 10
LED_BRIGHTNESS = 255
LED_INVERT = False
LED_CHANNEL = 0


class WSHandler(tornado.websocket.WebSocketHandler):
  def open(self):
    print('[WS] Connection was opened.')

  def on_message(self, message):
    print('[WS] Incoming message:')
    print(message)
    obj = json.loads(message)
    print(obj)

    randomColor = None
    currentColor = (0, 0, 0)

    for colorLedPair in obj:
      color = colorLedPair[0]
      leds = colorLedPair[1]
      # parse color
      match = re.search("(\d+),(\d+),(\d+)", color)
      if match is not None:
        currentColor = (int(match.group(1)), int(match.group(2)), int(match.group(3)))
        print(currentColor)
      elif color == "r":
        if randomColor is None:
          randomColor = (random.randint(0, 255), random.randint(0, 255), random.randint(0, 255))
          currentColor = randomColor
        else: 
          currentColor = randomColor
      elif color == "c":
        currentColor = (0, 0, 0)
        
      # parse led(s) and set color
      matchRange = re.search("{(\d+)-(\d+)}", leds)
      if matchRange is not None: 
        start = int(matchRange.group(1))
        end = int(matchRange.group(2))
        for i in range(start, end + 1): 
          if color == "ra":
            currentColor = (random.randint(0, 255), random.randint(0, 255), random.randint(0,255))
          strip.setPixelColor(i, Color(currentColor[0], currentColor[1], currentColor[2]))        
          print("Set color of " + str(i) + " to " + str(currentColor))
      
      matchList = re.search("\[.+\]", leds)
      if matchList != None: 
        array = json.loads(matchList.group(0))
        print(array)
        for i in array: 
          if color == "ra":
            currentColor = (random.randint(0, 255), random.randint(0, 255), random.randint(0,255))
          strip.setPixelColor(i, Color(currentColor[0], currentColor[1], currentColor[2]))        
          print("Set color of " + str(i) + " to " + str(currentColor))
      
      matchSingleNumber = re.search("^\d+", leds)
      if matchSingleNumber is not None:
        i = int(matchSingleNumber.group(0))
        if color == "ra":
          currentColor = (random.randint(0, 255), random.randint(0, 255), random.randint(0,255))
        strip.setPixelColor(i, Color(currentColor[0], currentColor[1], currentColor[2]))        
        print("Set color of " + str(i) + " to " + str(currentColor))       
    strip.show()
    randomColor = None

  def on_close(self):
    print ('[WS] Connection was closed.')


application = tornado.web.Application([
  (r'/', WSHandler),
  ])


if __name__ == "__main__":
    try:
        strip = PixelStrip(LED_COUNT, LED_PIN, LED_FREQ_HZ, LED_DMA, LED_INVERT, LED_BRIGHTNESS, LED_CHANNEL)
        # Intialize the library (must be called once before other functions).
        strip.begin()
        http_server = tornado.httpserver.HTTPServer(application)
        http_server.listen(PORT)
        main_loop = tornado.ioloop.IOLoop.instance()
        print ("Tornado Server started")
        main_loop.start()

    except:
        print ("Exception triggered - Tornado Server stopped.")
