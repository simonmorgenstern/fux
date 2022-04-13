//
//  FrameEditorToolbar.swift
//  iFux
//
//  Created by Simon Morgenstern on 07.04.22.
//

import SwiftUI


struct FrameEditorToolbar: View {

    @Binding var currentColor: Color
    @Binding var brightness: Double
    @Binding var applePencilModus: Bool
    @State var brightnessInput = "25"
    
    var body: some View {
        VStack {
            Text("Frame Editor Einstellungen")
                .font(.headline)
            Divider()
            HStack {
                ColorPicker("Farbauswahl", selection: $currentColor)
            }
            VStack {
                Text("aktuelle Farbe")
                Rectangle()
                    .fill(currentColor)
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
            Slider(value: $brightness, in: 0...255, step: 1) {
                Text("Helligkeit")
            } minimumValueLabel: {
                Text("0")
            } maximumValueLabel: {
                Text("255")
            } .onChange(of: brightness) { newValue in
                brightnessInput = String(format: "%.0f", brightness)
            }.accentColor(currentColor)
            Divider()
            HStack {
                Toggle("Apple Pencil Modus", isOn: $applePencilModus)
            }
            Spacer()
        }
        .padding()
    }
    
    func setBrightness(brightnessString: String) -> Bool {
        if let newBrightness = Double(brightnessString) {
            if newBrightness > 0 && newBrightness < 256 {
                brightness = newBrightness
                return true
            }
        }
        return false
    }
}

