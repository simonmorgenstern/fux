//
//  ContentView.swift
//  iFux
//
//  Created by Simon Morgenstern on 28.03.22.
//

import SwiftUI

struct ContentView: View {
    @State var x = 0
    var body: some View {
        FrameEditor()
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        ContentView()
    }
}
