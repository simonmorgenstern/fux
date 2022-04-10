//
//  FrameEditorToolbar.swift
//  iFux
//
//  Created by Simon Morgenstern on 07.04.22.
//

import SwiftUI


struct FrameEditorToolbar: View {

    @State private var currentColor = Color(red: 255, green: 255, blue: 0, opacity: 1.0)
    @State private var brightness = 25.0
    @State private var brightnessInput = "25"
    @State private var applePencilModus = false
    
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

struct FrameEditorToolbar_Previews: PreviewProvider {
    static var previews: some View {
        FrameEditorToolbar()
            .previewInterfaceOrientation(.landscapeLeft)
    }
}
