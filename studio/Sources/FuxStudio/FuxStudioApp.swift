import SwiftUI

@main struct FuxStudioApp: App {
    @StateObject private var model = StudioModel()
    var body: some Scene {
        WindowGroup("Fux Studio") {
            StudioView().environmentObject(model)
                .frame(minWidth: 1050, minHeight: 720)
                .preferredColorScheme(.dark)
        }
        .defaultSize(width: 1380, height: 900)
        .commands {
            CommandGroup(replacing: .undoRedo) {
                Button("Undo") { model.undo() }.keyboardShortcut("z").disabled(!model.canUndo)
                Button("Redo") { model.redo() }.keyboardShortcut("z", modifiers: [.command, .shift]).disabled(!model.canRedo)
            }
            CommandGroup(replacing: .saveItem) {
                Button("Save to Fux") { Task { await model.save() } }.keyboardShortcut("s")
                    .disabled(!model.connected || model.show == nil || model.busy)
            }
        }
    }
}
