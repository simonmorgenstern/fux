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
    var pixelColor = [Color](repeatElement(Color(red: 255, green: 255, blue: 255, opacity: 1.0), count: 268))
    var currentColor: Color
    var applePencilModus: Bool
}
