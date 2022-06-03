# fux
## Controller
### Getting started
1. run `rsync websocket.py pi@fux.local:~/` locally to move websocket code to raspberry pi
2. run `sudy python3 websocket.py` on raspberry pi to start tornado server

### Endpoint
The websocket is available at the adress of the raspberry pi. \
In my case `ws://192.168.178.82`

### Change Instructions
For each Frame the fux expects a array filled with what has changed in comparison to the previous frame. 
The array contains Color LED pairs. 
Message is formatted as a json string. 

Colors can either be a 
- "r, g, b" string 
- "r" (for one random color) 
- "ra" (for a seperate random color) 
- "c" (clear / turn LED off)

LEDs can be described as 
- "{start-end}" (a range, with start and end integer)
- "[1, 2, 3]" (a list of integers)
- "42" (a single number)

```
[
  ["255, 255, 255", "1"],
  ["r", "{22-44}"],
  ["c", "[4, 6, 8, 10, 12, 14, 16]"]
]
```