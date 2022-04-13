//
//  PixelFux.swift
//  iFux
//
//  Created by Simon Morgenstern on 07.04.22.
//  Used resources:
//      - drag to move around method implementation (https://www.hackingwithswift.com/books/ios-swiftui/moving-views-with-draggesture-and-offset)

import SwiftUI
import CoreGraphics

struct PixelFux: View {
    @Binding var frame: Frame
    
    @State var pixelData = [PixelData]()
    
    @State var scaling = 1.0
    @State var lastScalingValue = 1.0
    @GestureState var magnifyBy = CGFloat(1.0)
    
    @State private var translation: CGPoint = CGPoint(x: 0, y: 0)
    @GestureState private var startLocation: CGPoint? = nil
    @State private var currentPencilLocation: CGPoint? = nil
    
    var maxPixelX = 460
    var maxPixelY = 600
    
    var magnification: some Gesture {
        MagnificationGesture()
            .updating($magnifyBy) { currentState, gestureState, transaction in
                gestureState = currentState / lastScalingValue
            }
            .onChanged { value in
                if (scaling * magnifyBy > 0.5 && scaling * magnifyBy < 2) {
                    let newXOf184 = Double(pixelData[184].x) * scaling * magnifyBy + translation.x
                    let newYOf184 = Double(pixelData[184].y) * scaling * magnifyBy + translation.y
                    if newXOf184 > 0 && newYOf184 > 0 {
                        scaling *= magnifyBy
                        lastScalingValue = value
                    }
                }
            }
            .onEnded { value in
                lastScalingValue = 1.0
            }
    }
    
    var drag: some Gesture {
        DragGesture()
            .onChanged { value in
                if frame.applePencilModus {
                    currentPencilLocation = value.location
                    // lock fox, if pencil over pixel -> fill with current color
                    let applePencilRect = CGRect(x: value.location.x - 9, y: value.location.y - 9, width: 18.0, height: 18.0)
                    for i in 0..<268 {
                        let pixelRect = CGRect(x: Double(pixelData[i].x) * scaling + translation.x, y: Double(pixelData[i].y) * scaling + translation.y, width: 12.0, height: 12.0)
                        if applePencilRect.intersects(pixelRect) {
                            frame.pixelColor[i] = frame.currentColor
                        }
                    }
                } else {
                    // drag fox around
                    let foxWidth = Double(maxPixelX) * scaling
                    let foxHeight = Double(maxPixelY) * scaling
                    var newLocation = startLocation ?? translation
                    
                    let foxIsInFrameLeft = newLocation.x + value.translation.width > foxWidth * -0.5
                    let foxIsInFrameRight = newLocation.x + value.translation.width < UIScreen.main.bounds.height - foxWidth * 0.5
                    
                    if foxIsInFrameLeft && foxIsInFrameRight {
                        newLocation.x += value.translation.width
                    } else if !foxIsInFrameLeft{
                        newLocation.x = foxWidth * -0.5
                    } else if !foxIsInFrameRight {
                        newLocation.x = UIScreen.main.bounds.height - foxWidth / 2
                    }
                    
                    let foxIsInFrameUp = newLocation.y + value.translation.height > foxHeight * -0.5
                    let foxIsInFrameDown = newLocation.y + value.translation.height < UIScreen.main.bounds.height - foxHeight * 0.5
                    
                    if foxIsInFrameUp && foxIsInFrameDown {
                        newLocation.y += value.translation.height
                    } else if !foxIsInFrameUp {
                        newLocation.y = foxHeight * -0.5
                    } else if !foxIsInFrameDown {
                        newLocation.y = UIScreen.main.bounds.height - foxHeight * 0.5
                    }
                    
                    self.translation = newLocation
                }

            }.updating($startLocation) { (value, startLocation, transaction) in
                startLocation = startLocation ?? translation
            }
            .onEnded { _ in
                currentPencilLocation = nil
            }
        
    }
    
    func loadPixelData() {
        if let url = Bundle.main.url(forResource: "coords", withExtension: "json") {
            do {
                let jsonData = try! Data(contentsOf: url)
                pixelData = try! JSONDecoder().decode([PixelData].self, from: jsonData)
            }
        }
    }
    
    func scaleAndTranslate() {
        while (Double(maxPixelX) * (scaling + 0.1) < UIScreen.main.bounds.height && Double(maxPixelY) * (scaling + 0.1) < UIScreen.main.bounds.height) {
            scaling += 0.1
        }
        translation.x = (UIScreen.main.bounds.height - Double(maxPixelX) * scaling) * 0.5
        translation.y = (UIScreen.main.bounds.height - Double(maxPixelY) * scaling) * 0.25
    }
    
    var body: some View {
        ZStack {
            ZStack {
                if pixelData.count > 0 {
                    ForEach(0..<pixelData.count) { index in
                        Circle()
                            .fill(frame.pixelColor[index])
                            .frame(width: 12, height: 12)
                            .position(x: CGFloat(pixelData[index].x) * scaling, y: CGFloat(pixelData[index].y) * scaling)
                            .onTapGesture {
                                frame.pixelColor[index] = frame.currentColor
                            }
                    }
                }
                if let location = currentPencilLocation, frame.applePencilModus{
                    Circle()
                        .fill(Color.red)
                        .frame(width: 18, height: 18)
                        .position(x: location.x - translation.x - 18, y: location.y - translation.y - 18)
                }
            }
            .padding()
            .offset(x: translation.x, y: translation.y)
            .onAppear {
                loadPixelData()
                scaleAndTranslate()
            }
            .frame(width: UIScreen.main.bounds.height, height: UIScreen.main.bounds.height)
            .background(Color.black)
            .gesture(drag)
            .simultaneousGesture(magnification)

        }
    }
}
