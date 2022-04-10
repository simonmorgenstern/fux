//
//  PixelFux.swift
//  iFux
//
//  Created by Simon Morgenstern on 07.04.22.
//

import SwiftUI

struct PixelFux: View {
    @Binding var pixelColorArray: [Color]
    @State var pixelData = [PixelData]()
    @State var scaling = 1.0
    @State var translationX: Double = 0
    @State var translationY: Double = 0

    
    var maxPixelX = 460
    var maxPixelY = 600
    
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
        translationX = (UIScreen.main.bounds.height - Double(maxPixelX) * scaling) * 0.5
        translationY = (UIScreen.main.bounds.height - Double(maxPixelY) * scaling) * 0.25
    }
    
    var body: some View {
        HStack {
            ZStack {
                if pixelData.count > 0 {
                    ForEach(0..<pixelData.count) { index in
                        Circle()
                            .stroke(Color.yellow)
                            .frame(width: 7, height: 7)
                            .position(x: CGFloat(pixelData[index].x) * scaling + translationX, y: CGFloat(pixelData[index].y) * scaling + translationY)
                    }
                }
            }.padding()
        }.onAppear {
            loadPixelData()
            scaleAndTranslate()
        }
        .frame(width: UIScreen.main.bounds.height, height: UIScreen.main.bounds.height)
    }
}
//
//struct PixelFux_Previews: PreviewProvider {
//    static var previews: some View {
//        PixelFux(pixelColorArray: nil)
//    }
//}
