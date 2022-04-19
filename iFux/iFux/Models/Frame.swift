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
    var pixelColor = [CGColor](repeatElement(CGColor(red: 1.0, green: 1.0, blue: 1.0, alpha: 1.0), count: 268))
    var currentColor: CGColor
    var applePencilModus: Bool
    var brightness: Double
}
