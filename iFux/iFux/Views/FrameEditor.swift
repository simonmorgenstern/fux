//
//  FrameEditor.swift
//  iFux
//
//  Created by Simon Morgenstern on 07.04.22.
//

import SwiftUI

struct FrameEditor: View {
    @State var frame = Frame(currentColor: Color(red: 1.0, green: 1.0, blue: 0, opacity: 1.0), applePencilModus: false, brightness: 25)
    
    var body: some View {
        HStack {
            PixelFux(frame: $frame)
            FrameEditorToolbar(frame: $frame)
        }
    }
}

struct FrameEditor_Previews: PreviewProvider {
    static var previews: some View {
        FrameEditor()
    }
}
