//
//  FrameEditor.swift
//  iFux
//
//  Created by Simon Morgenstern on 07.04.22.
//

import SwiftUI

struct FrameEditor: View {
    @State var pixelColorArray = [Color](repeatElement(Color(red: 255, green: 255, blue: 255, opacity: 1.0), count: 268))
    
    var body: some View {
        HStack {
            PixelFux(pixelColorArray: $pixelColorArray)
            FrameEditorToolbar()
        }
    }
}

struct FrameEditor_Previews: PreviewProvider {
    static var previews: some View {
        FrameEditor()
    }
}
