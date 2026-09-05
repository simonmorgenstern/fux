// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "FuxStudio", platforms: [.macOS(.v13)], products: [
    .executable(name: "FuxStudio", targets: ["FuxStudio"])
], targets: [
    .executableTarget(name: "FuxStudio", resources: [.copy("pixelCoordinates.json")]),
    .testTarget(name: "FuxStudioTests", dependencies: ["FuxStudio"])
])
