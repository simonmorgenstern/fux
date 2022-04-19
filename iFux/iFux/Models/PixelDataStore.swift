//
//  PixelDataStore.swift
//  iFux
//

import Foundation
import UIKit

final class PixelDataStore: ObservableObject {
    @Published var pixelData: [PixelData]?
    var maxPixelX: Double = 0
    var maxPixelY: Double = 0

    init() {
        loadData()
    }

    func loadData() {
        if let url = Bundle.main.url(forResource: "coords", withExtension: "json") {
            do {
                let jsonData = try! Data(contentsOf: url)
                pixelData = try! JSONDecoder().decode([PixelData].self, from: jsonData)
                if let data = pixelData {
                    for index in 0..<data.count {
                        if data[index].x > maxPixelX {
                            maxPixelX = data[index].x
                        }
                        if data[index].y > maxPixelY {
                            maxPixelY = data[index].y
                        }
                    }
                }
            }
        }
    }
}
