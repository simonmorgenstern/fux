//
//  FrameEditorToolbar.swift
//  iFux
//
//  Created by Simon Morgenstern on 07.04.22.
//

import SwiftUI


struct FrameEditorToolbar: View {
    @Binding var frame: Frame
    @State var brightnessInput = ""
    
    var body: some View {
        VStack {
            Text("Frame Editor Einstellungen")
                .font(.headline)
            Divider()
            HStack {
                ColorPicker("Farbauswahl", selection: $frame.currentColor)
            }
            VStack {
                Text("aktuelle Farbe")
                Rectangle()
                    .fill(Color(frame.currentColor))
                    .frame(width: 100, height: 100)
            }
            Divider()
            HStack {
                Text("Helligkeit")
                TextField("Helligkeitswert (0 - 255)", text: $brightnessInput)
                    .onChange(of: brightnessInput) { newValue in
                        if !setBrightness(brightnessString: brightnessInput) {
                            brightnessInput = String(newValue.dropLast())
                        }
                    }
            }
            Slider(value: $frame.brightness, in: 0...255, step: 1) {
                Text("Helligkeit")
            } minimumValueLabel: {
                Text("0")
            } maximumValueLabel: {
                Text("255")
            } .onChange(of: frame.brightness) { newValue in
                brightnessInput = String(format: "%.0f", frame.brightness)
            }.accentColor(Color(frame.currentColor))
            Divider()
            HStack {
                Toggle("Apple Pencil Modus", isOn: $frame.applePencilModus)
            }
            Spacer()
        }
        .padding()
        .onAppear {
            brightnessInput = String(frame.brightness)
        }
    }
    
    func setBrightness(brightnessString: String) -> Bool {
        if let newBrightness = Double(brightnessString) {
            if newBrightness > 0 && newBrightness < 256 {
                frame.brightness = newBrightness
                return true
            }
        }
        return false
    }
}

