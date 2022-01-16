import numpy as np
import argparse
import cv2
import json

data = []
		
# construct the argument parse and parse the arguments
ap = argparse.ArgumentParser()
ap.add_argument("-r", "--radius", type = int,
	help = "radius of Gaussian blur; must be odd")
args = vars(ap.parse_args())

for x in range(268): 
	# take webcam picture 
	video_capture = cv2.VideoCapture(0)
	# Check success
	if not video_capture.isOpened():
		raise Exception("Could not open video device")
	# Read picture. ret === True on success
	ret, frame = video_capture.read()
	# Close device

	# load the image and convert it to grayscale
	# image = cv2.imread(args["image"])
	image = frame
	orig = image.copy()
	gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

	# apply a Gaussian blur to the image then find the brightest
	# region
	gray = cv2.GaussianBlur(gray, (args["radius"], args["radius"]), 0)
	(minVal, maxVal, minLoc, maxLoc) = cv2.minMaxLoc(gray)
	coordsIndex = {
		"x": maxLoc[0],
		"y": maxLoc[1],
		"index": x
	}
	data.append(coordsIndex)
	# image = orig.copy()
	# cv2.circle(image, maxLoc, args["radius"], (255, 0, 0), 2)
	# display the results of our newly improved method
	# cv2.imshow("Robust", image)
	# cv2.waitKey(0)
	print(x)
	input('enter to continue')
video_capture.release()
with open('coords.json', 'w') as fp: 
	json.dump(data, fp, indent=4)
