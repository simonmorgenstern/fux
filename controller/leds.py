import json 
import re
import random 

msg = "[[\"253,254,255\",\"1\"],[\"r\",\"{22-44}\"],[\"c\",\"[4,6,8,10,12,14,16]\"]]"
obj = json.loads(msg)

randomColor = None
currentColor = (0, 0, 0)

for colorLedPair in obj: 
    color = colorLedPair[0]
    leds = colorLedPair[1]
    # parse color 
    match = re.search("(\d+),(\d+),(\d+)", color)
    if match is not None: 
        currentColor = (int(match.group(1)), int(match.group(2)), int(match.group(3)))
    elif color == "r":
        if randomColor is None:
            randomColor = (random.randint(0, 255), random.randint(0, 255), random.randint(0,255))
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
            #! strip.setPixelColor(i, Color(currentColor))        
            print("Set color of " + str(i) + " to " + str(currentColor))
    matchList = re.search("\[.+\]", leds)
    if matchList != None: 
        array = json.loads(matchList.group(0))
        for i in array: 
            if color == "ra":
                currentColor = (random.randint(0, 255), random.randint(0, 255), random.randint(0,255))
            #! strip.setPixelColor(i, Color(currentColor))        
            print("Set color of " + str(i) + " to " + str(currentColor))
    matchSingleNumber = re.search("^\d+", leds)
    if matchSingleNumber is not None:
        i = int(matchSingleNumber.group(0))
        if color == "ra":
            currentColor = (random.randint(0, 255), random.randint(0, 255), random.randint(0,255))
            #! strip.setPixelColor(i, Color(currentColor))        
        print("Set color of " + str(i) + " to " + str(currentColor))
        
# strip.show()
randomColor = None