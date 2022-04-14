//
//  Frame.swift
//  iFux
//
//  Created by Simon Morgenstern on 13.04.22.
//

import Foundation
import SwiftUI

struct Frame {
    var id = UUID().uuidString
    var pixelColor = [Color](repeatElement(Color(red: 1.0, green: 1.0, blue: 1.0, opacity: 1.0), count: 268))
    var currentColor: Color
    var applePencilModus: Bool
    var brightness: Double
}
