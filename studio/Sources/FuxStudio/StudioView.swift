import SwiftUI
import UniformTypeIdentifiers

let studioOrange = Color(red: 1, green: 0.48, blue: 0.25)
let studioBackground = Color(red: 0.055, green: 0.065, blue: 0.08)
func effectColor(_ id: String) -> Color {
    let colors: [Color] = [studioOrange, .cyan, .purple, .pink, .mint, .yellow, .blue, .teal]
    return colors[EffectChoice.all.firstIndex { $0.id == id } ?? 0]
}
func timeLabel(_ seconds: Double) -> String { String(format: "%02d:%02d", Int(max(0, seconds)) / 60, Int(max(0, seconds)) % 60) }

struct StudioView: View {
    @EnvironmentObject var model: StudioModel
    @State private var replaceDraft = false
    @State private var dragging: String?
    @State private var dragPoint = CGPoint.zero
    @State private var timelineContent = CGRect.zero
    @State private var timelineViewport = CGRect.zero
    var body: some View {
        VStack(spacing: 0) {
            connectionBar
            Divider()
            HStack(spacing: 0) {
                library.frame(width: 235)
                Divider()
                VSplitView {
                    HStack(spacing: 0) {
                        preview.frame(maxWidth: .infinity, maxHeight: .infinity)
                        Divider()
                        inspector.frame(width: 235)
                    }.frame(minHeight: 265)
                    timelinePanel.frame(minHeight: 240, idealHeight: 310)
                }
            }
            Divider()
            HStack {
                Circle().fill(model.connected ? .green : .gray).frame(width: 6, height: 6)
                Text(model.notice).lineLimit(1)
                Spacer()
                Text(model.dirty ? "Unsaved changes" : "FUX STUDIO / 01").foregroundStyle(model.dirty ? studioOrange : .secondary)
            }.font(.system(size: 11)).foregroundStyle(.secondary).padding(.horizontal, 18).padding(.vertical, 9)
        }
        .background(studioBackground).tint(studioOrange)
        .coordinateSpace(name: "studio")
        .overlay(alignment: .topLeading) {
            if let dragging {
                Text(EffectChoice.name(dragging)).font(.system(size: 12, weight: .semibold))
                    .padding(10).background(effectColor(dragging).opacity(0.9), in: RoundedRectangle(cornerRadius: 8))
                    .offset(x: dragPoint.x + 10, y: dragPoint.y + 10).allowsHitTesting(false)
            }
        }
        .task { await model.poll() }
        .task { await model.previewLoop() }
        .alert("Fux Studio", isPresented: Binding(get: { model.message != nil }, set: { if !$0 { model.message = nil } })) {
            Button("OK") { model.message = nil }
        } message: { Text(model.message ?? "") }
        .confirmationDialog("Replace this unsaved draft with the current Spotify song?", isPresented: $replaceDraft) {
            Button("Replace draft", role: .destructive) { Task { await model.loadCurrent() } }
        }
    }
    private var connectionBar: some View {
        HStack(spacing: 14) {
            Image(systemName: "waveform.path").font(.title2).foregroundStyle(studioOrange)
            VStack(alignment: .leading, spacing: 2) {
                Text("FUX STUDIO").font(.system(size: 14, weight: .bold, design: .rounded)).tracking(2)
                Text("MUSIC ARRANGEMENTS").font(.system(size: 9, weight: .medium)).tracking(1.5).foregroundStyle(.secondary)
            }
            Spacer()
            TextField("http://fux.local:8080", text: $model.address).textFieldStyle(.roundedBorder).frame(width: 220)
                .onSubmit { Task { await model.connect() } }
            Button(model.connected ? "Disconnect" : "Connect") {
                if model.connected { model.disconnect() } else { Task { await model.connect() } }
            }.disabled(model.busy)
            Button { if model.dirty { replaceDraft = true } else { Task { await model.loadCurrent() } } } label: {
                Label("Current song", systemImage: "music.note")
            }.disabled(!model.connected || model.busy)
            Button { Task { await model.save() } } label: {
                Label("Save to Fux", systemImage: "arrow.up.circle.fill")
            }.buttonStyle(.borderedProminent)
                .disabled(!model.connected || model.show == nil || model.show?.trackId == Arrangement.demo.trackId || model.busy)
        }.padding(18)
    }
    private var library: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack { Text("Effects").font(.title3.bold()); Spacer(); Text("08").foregroundStyle(.secondary).monospacedDigit() }.padding(20)
            Text("Drag onto the timeline, or + to add at the playhead.")
                .font(.system(size: 11)).foregroundStyle(.secondary).padding(.horizontal, 20).padding(.bottom, 18)
            ScrollView {
                VStack(spacing: 5) {
                    ForEach(EffectChoice.all) { effect in
                        HStack(spacing: 10) {
                            Image(systemName: effect.symbol).font(.system(size: 17)).foregroundStyle(effectColor(effect.id)).frame(width: 28)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(effect.name).font(.system(size: 12, weight: .semibold))
                                Text(effect.detail).font(.system(size: 10)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                            }
                            Spacer(minLength: 0)
                            Button { model.add(effect.id) } label: { Image(systemName: "plus") }.buttonStyle(.plain).disabled(model.show == nil)
                        }.padding(12).background(.white.opacity(0.035), in: RoundedRectangle(cornerRadius: 9))
                            .contentShape(Rectangle())
                            .highPriorityGesture(DragGesture(minimumDistance: 5, coordinateSpace: .named("studio"))
                                .onChanged { value in dragging = effect.id; dragPoint = value.location; model.notice = "Release over the timeline to add " + effect.name }
                                .onEnded { value in
                                    defer { dragging = nil }
                                    guard let show = model.show, timelineViewport.contains(value.location), timelineContent.width > 0 else { model.notice = "Drop the effect inside the timeline"; return }
                                    let seconds = (value.location.x - timelineContent.minX) / timelineContent.width * show.durationSeconds
                                    model.add(effect.id, at: seconds)
                                })
                    }
                }.padding(.horizontal, 10)
            }
            Spacer(minLength: 15)
            VStack(alignment: .leading, spacing: 7) {
                Label("One effect. Every beat.", systemImage: "square.stack").font(.system(size: 11, weight: .medium))
                Text("Saved songs play automatically in Music mode, even with your Mac closed.").font(.system(size: 11)).foregroundStyle(.secondary)
            }.padding(18).background(studioOrange.opacity(0.065))
        }.background(.white.opacity(0.015))
    }
    private var preview: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(model.show?.title ?? "Compose with the music.").font(.system(size: 24, weight: .semibold))
                    Text(model.show?.artist ?? "Choose the Spotify song playing on your Fux.").font(.system(size: 12)).foregroundStyle(.secondary)
                }
                Spacer()
                Text("DRAFT PREVIEW").font(.system(size: 9, weight: .semibold)).tracking(1.2).foregroundStyle(.secondary)
            }.padding(24)
            ZStack {
                FoxCanvas(points: model.points, pixels: model.pixels).padding(.horizontal, 45).padding(.bottom, 20)
                if model.show == nil {
                    VStack(spacing: 14) {
                        Text("Your fox. Your arrangement.").font(.title2.bold())
                        Text("Connect to the Pi, play a song in Spotify,\nand start placing effects on its beat grid.")
                            .font(.system(size: 13)).multilineTextAlignment(.center).foregroundStyle(.secondary)
                        Button("Explore the editor") { model.showDemo() }.buttonStyle(.bordered)
                    }.padding(28).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 15))
                }
            }.frame(maxWidth: .infinity, maxHeight: .infinity)
            HStack {
                Circle().fill(model.connected ? studioOrange : .gray).frame(width: 5, height: 5)
                Text(model.previewMessage).font(.system(size: 10)).foregroundStyle(.secondary).lineLimit(2)
                Spacer()
                if model.connected && model.status?.music.authorized == false {
                    Button("Connect Spotify") { model.openSpotifySetup() }.font(.system(size: 11))
                }
            }.padding(.horizontal, 24).padding(.bottom, 16)
        }
    }
    private var inspector: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("Inspector").font(.system(size: 13, weight: .semibold))
                if let show = model.show {
                    VStack(alignment: .leading, spacing: 12) {
                        sectionLabel("SONG GRID")
                        NumericField(title: "Tempo / BPM", value: show.bpm) { value in var copy = show; copy.bpm = value; model.commit(copy) }
                        NumericField(title: "First beat / ms", value: show.offsetMs) { value in var copy = show; copy.offsetMs = value; model.commit(copy) }
                        Stepper("\(show.beatsPerBar) beats per bar", value: Binding(get: { show.beatsPerBar }, set: { value in var copy = show; copy.beatsPerBar = value; model.commit(copy) }), in: 1...16).font(.system(size: 11))
                    }
                    Divider()
                    if let clip = model.selected {
                        VStack(alignment: .leading, spacing: 12) {
                            sectionLabel("SELECTED CLIP")
                            Picker("Effect", selection: Binding(get: { clip.effect }, set: { model.editClip(clip.id, effect: $0) })) {
                                ForEach(EffectChoice.all) { Text($0.name).tag($0.id) }
                            }.labelsHidden()
                            NumericField(title: "Start / beat (from 0)", value: clip.startBeat) { model.editClip(clip.id, start: $0.rounded()) }
                            NumericField(title: "Length / beats", value: clip.lengthBeats) { model.editClip(clip.id, length: $0) }
                            Text("\(timeLabel(show.seconds(clip.startBeat))) – \(timeLabel(show.seconds(clip.startBeat + clip.lengthBeats)))")
                                .font(.system(size: 11, design: .monospaced)).foregroundStyle(.secondary)
                            HStack {
                                Button { model.duplicateSelected() } label: { Label("Duplicate", systemImage: "plus.square.on.square") }
                                Button(role: .destructive) { model.deleteSelected() } label: { Image(systemName: "trash") }
                            }.controlSize(.small)
                        }
                    } else {
                        Text("Select a clip to adjust its effect and timing.").font(.system(size: 12)).foregroundStyle(.secondary)
                    }
                    Divider()
                    Text("Empty sections are dark. Each clip follows the song’s beat grid.").font(.system(size: 11)).foregroundStyle(.secondary)
                } else { Text("Song and clip settings appear here.").font(.system(size: 12)).foregroundStyle(.secondary) }
            }.padding(20)
        }.background(.white.opacity(0.02))
    }
    private var timelinePanel: some View {
        VStack(spacing: 0) {
            HStack(spacing: 15) {
                Text("Arrangement").font(.system(size: 13, weight: .semibold))
                TimelineView(.periodic(from: .now, by: 0.1)) { context in
                    Text(timeLabel(model.position(at: context.date))).font(.system(size: 17, weight: .medium, design: .monospaced)).foregroundStyle(studioOrange)
                }
                Button { model.follow = true } label: { Label("Follow Spotify", systemImage: model.follow ? "dot.radiowaves.left.and.right" : "play.circle") }
                    .disabled(!model.matchingTrack).tint(model.follow ? studioOrange : .gray)
                Spacer()
                Label("Snap: 1 beat", systemImage: "square.grid.3x3").font(.system(size: 10)).foregroundStyle(.secondary)
                Image(systemName: "minus.magnifyingglass").foregroundStyle(.secondary)
                Slider(value: $model.zoom, in: 2...30).frame(width: 100)
                Image(systemName: "plus.magnifyingglass").foregroundStyle(.secondary)
            }.padding(.horizontal, 20).padding(.vertical, 13)
            Divider()
            if let show = model.show { ArrangementTimeline(show: show, contentFrame: $timelineContent, viewportFrame: $timelineViewport) }
            else { Spacer(); Text("Your song timeline will appear here").foregroundStyle(.secondary); Spacer() }
            HStack {
                Text("Drag to move · drag a clip’s right edge to resize · click the ruler to inspect")
                Spacer()
                Text(model.show.map { "\($0.clips.count) clips · \(timeLabel($0.durationSeconds))" } ?? "")
            }.font(.system(size: 10)).foregroundStyle(.secondary).padding(14)
        }.background(.white.opacity(0.025))
    }
    private func sectionLabel(_ text: String) -> some View { Text(text).font(.system(size: 9, weight: .semibold)).tracking(1.4).foregroundStyle(.secondary) }
}
struct NumericField: View {
    let title: String
    let value: Double
    let commit: (Double) -> Void
    @State private var text = ""
    @FocusState private var focused: Bool
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.system(size: 11)).foregroundStyle(.secondary)
            TextField(title, text: $text).textFieldStyle(.roundedBorder).focused($focused)
                .onSubmit { apply() }
                .onChange(of: focused) { if !$0 { apply() } }
        }.onAppear { text = formatted(value) }.onChange(of: value) { text = formatted($0) }
    }
    private func apply() {
        if let number = Double(text), number.isFinite, number != value { commit(number) }
        text = formatted(value)
    }
    private func formatted(_ n: Double) -> String { String(format: n == n.rounded() ? "%.0f" : "%.2f", n) }
}
struct FoxCanvas: View {
    let points: [LEDPoint]
    let pixels: [Int]
    var body: some View {
        Canvas { context, size in
            guard let minX = points.map(\.x).min(), let maxX = points.map(\.x).max(), let minY = points.map(\.y).min(), let maxY = points.map(\.y).max() else { return }
            let scale = min(size.width / max(1, maxX - minX + 30), size.height / max(1, maxY - minY + 30))
            let ox = (size.width - (maxX - minX) * scale) / 2
            let oy = (size.height - (maxY - minY) * scale) / 2
            for point in points {
                let rgb = pixels.indices.contains(point.index) ? pixels[point.index] : 0
                let lit = rgb != 0
                let color = lit ? Color(red: Double((rgb >> 16) & 255) / 255, green: Double((rgb >> 8) & 255) / 255, blue: Double(rgb & 255) / 255) : Color.white.opacity(0.13)
                let radius = max(1.5, min(4, scale * 3.4))
                let x = ox + (point.x - minX) * scale, y = oy + (point.y - minY) * scale
                if lit { context.fill(Path(ellipseIn: CGRect(x: x - radius * 2.5, y: y - radius * 2.5, width: radius * 5, height: radius * 5)), with: .color(color.opacity(0.12))) }
                context.fill(Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2)), with: .color(color))
            }
        }.accessibilityLabel("Preview of 268 LEDs in the fox layout")
    }
}
