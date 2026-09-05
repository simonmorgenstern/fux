import SwiftUI
import UniformTypeIdentifiers

struct ArrangementTimeline: View {
    @EnvironmentObject var model: StudioModel
    let show: Arrangement
    @Binding var contentFrame: CGRect
    @Binding var viewportFrame: CGRect
    var body: some View {
        GeometryReader { geometry in
            let width = max(geometry.size.width - 40, show.durationSeconds * model.zoom)
            let scale = width / show.durationSeconds
            ScrollView(.horizontal) {
                ZStack(alignment: .topLeading) {
                    Canvas { context, size in
                        let spacing = 60 / show.bpm * scale
                        let strideBeats = spacing < 6 ? show.beatsPerBar * 4 : (spacing < 18 ? show.beatsPerBar : 1)
                        let minBeat = Int(ceil(show.beat(0)))
                        let maxBeat = Int(floor(show.beat(show.durationSeconds)))
                        if maxBeat >= minBeat {
                            for beat in stride(from: minBeat, through: maxBeat, by: strideBeats) {
                                let x = show.seconds(Double(beat)) * scale
                                let bar = beat % show.beatsPerBar == 0
                                var line = Path(); line.move(to: CGPoint(x: x, y: 26)); line.addLine(to: CGPoint(x: x, y: size.height))
                                context.stroke(line, with: .color(.white.opacity(bar ? 0.14 : 0.05)), lineWidth: 1)
                                if bar {
                                    context.draw(Text("\(Int(floor(Double(beat) / Double(show.beatsPerBar))) + 1)").font(.system(size: 10, design: .monospaced)).foregroundColor(.secondary), at: CGPoint(x: x + 4, y: 14), anchor: .leading)
                                }
                            }
                        }
                        let secondsStride = scale < 5 ? 30 : 10
                        for second in stride(from: 0, through: Int(show.durationSeconds), by: secondsStride) {
                            context.draw(Text(timeLabel(Double(second))).font(.system(size: 9, design: .monospaced)).foregroundColor(.secondary), at: CGPoint(x: Double(second) * scale + 4, y: size.height - 14), anchor: .leading)
                        }
                    }.frame(width: width, height: max(130, geometry.size.height - 10))
                        .contentShape(Rectangle())
                        .gesture(DragGesture(minimumDistance: 0).onChanged { model.inspect($0.location.x / scale) })
                    ForEach(show.clips) { clip in
                        TimelineClip(clip: clip, show: show, scale: scale)
                            .offset(x: show.seconds(clip.startBeat) * scale, y: 46)
                    }
                    TimelineView(.periodic(from: .now, by: 1 / 30)) { context in
                        let x = model.position(at: context.date) * scale
                        VStack(spacing: 0) {
                            Image(systemName: "arrowtriangle.down.fill").font(.system(size: 10))
                            Rectangle().frame(width: 1)
                        }.foregroundStyle(studioOrange).frame(width: 10, height: max(130, geometry.size.height - 10))
                            .offset(x: x - 5)
                    }.allowsHitTesting(false)
                }.frame(width: width, height: max(130, geometry.size.height - 10))
                    .background(GeometryReader { proxy in
                        Color.clear.onAppear { contentFrame = proxy.frame(in: .named("studio")) }
                            .onChange(of: proxy.frame(in: .named("studio"))) { contentFrame = $0 }
                    })
                    .dropDestination(for: String.self) { items, point in
                        guard let effect = items.first, EffectChoice.all.contains(where: { $0.id == effect }) else { return false }
                        model.add(effect, at: point.x / scale)
                        return true
                    }
                    .padding(.horizontal, 20)
            }.background(Color.clear.onAppear { viewportFrame = geometry.frame(in: .named("studio")) }
                .onChange(of: geometry.frame(in: .named("studio"))) { viewportFrame = $0 })
        }
    }
}
struct TimelineClip: View {
    @EnvironmentObject var model: StudioModel
    let clip: EffectClip
    let show: Arrangement
    let scale: Double
    @GestureState private var translation = 0.0
    @GestureState private var resize = 0.0
    var body: some View {
        let color = effectColor(clip.effect)
        let width = max(6, clip.lengthBeats * 60 / show.bpm * scale + resize)
        HStack(spacing: 4) {
            VStack(alignment: .leading, spacing: 7) {
                Text(EffectChoice.name(clip.effect)).font(.system(size: 11, weight: .semibold)).lineLimit(1)
                Text("\(clip.lengthBeats, specifier: "%.0f") beats").font(.system(size: 9, design: .monospaced)).opacity(0.7).lineLimit(1)
            }.padding(.leading, 10)
            Spacer(minLength: 0)
            RoundedRectangle(cornerRadius: 2).fill(color.opacity(0.6)).frame(width: 4, height: 28).padding(.horizontal, 4)
                .contentShape(Rectangle())
                .gesture(DragGesture().updating($resize) { value, state, _ in state = value.translation.width }
                    .onEnded { value in
                        let length = max(1, (clip.lengthBeats + value.translation.width / scale * show.bpm / 60).rounded())
                        model.editClip(clip.id, length: length)
                    })
                .help("Drag to resize")
        }
        .foregroundStyle(color).frame(width: width, height: 64)
        .background(color.opacity(0.16), in: RoundedRectangle(cornerRadius: 7))
        .overlay(RoundedRectangle(cornerRadius: 7).stroke(color.opacity(model.selectedID == clip.id ? 1 : 0.45), lineWidth: model.selectedID == clip.id ? 2 : 1))
        .offset(x: translation).onTapGesture { model.selectClip(clip.id) }
        .gesture(DragGesture(minimumDistance: 4).updating($translation) { value, state, _ in state = value.translation.width }
            .onEnded { value in
                model.selectClip(clip.id)
                let start = max(ceil(show.beat(0)), (clip.startBeat + value.translation.width / scale * show.bpm / 60).rounded())
                model.editClip(clip.id, start: start)
            })
        .contextMenu {
            Button("Duplicate") { model.selectClip(clip.id); model.duplicateSelected() }
            Button("Delete", role: .destructive) { model.selectClip(clip.id); model.deleteSelected() }
        }
        .accessibilityLabel("\(EffectChoice.name(clip.effect)), beat \(Int(clip.startBeat)), \(Int(clip.lengthBeats)) beats")
    }
}
