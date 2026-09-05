import Foundation

struct EffectChoice: Identifiable {
    let id: String
    let name: String
    let detail: String
    let symbol: String
    static let all: [EffectChoice] = [
        .init(id: "beat_pulse", name: "Beat Pulse", detail: "A whole-fox pulse on every beat", symbol: "waveform.path"),
        .init(id: "beat_sweep", name: "Beat Sweep", detail: "A soft band travels through the fox", symbol: "line.3.horizontal.decrease"),
        .init(id: "beat_sparkle", name: "Beat Sparkle", detail: "Mirrored sparks over a gentle wash", symbol: "sparkles"),
        .init(id: "firework", name: "Firework", detail: "Rockets and bursts on the beat", symbol: "party.popper"),
        .init(id: "heartbeat", name: "Heartbeat", detail: "A rhythmic double pulse", symbol: "heart"),
        .init(id: "strobe", name: "Strobe", detail: "Sharp flashes synchronized to tempo", symbol: "bolt"),
        .init(id: "center_pulse", name: "Center Pulse", detail: "Waves radiate from the centre", symbol: "dot.radiowaves.left.and.right"),
        .init(id: "center_heartbeat", name: "Center Heartbeat", detail: "A heartbeat spreading outwards", symbol: "heart.circle")
    ]
    static func name(_ id: String) -> String { all.first { $0.id == id }?.name ?? id }
}
struct EffectClip: Codable, Identifiable, Equatable {
    var id = UUID().uuidString
    var effect: String
    var startBeat: Double
    var lengthBeats: Double
}
struct Arrangement: Codable, Equatable {
    var version = 1
    var trackId: String
    var title: String
    var artist: String
    var durationSeconds: Double
    var bpm: Double
    var offsetMs: Double
    var beatsPerBar: Int = 4
    var clips: [EffectClip] = []
    func seconds(_ beat: Double) -> Double { offsetMs / 1000 + beat * 60 / bpm }
    func beat(_ seconds: Double) -> Double { (seconds - offsetMs / 1000) * bpm / 60 }
    func active(at seconds: Double) -> EffectClip? {
        guard seconds >= 0, seconds < durationSeconds else { return nil }
        return clips.first { beat(seconds) >= $0.startBeat && beat(seconds) < $0.startBeat + $0.lengthBeats }
    }
    func validated() throws -> Arrangement {
        guard version == 1, trackId.range(of: "^[a-zA-Z0-9]{22}$", options: .regularExpression) != nil,
              !title.isEmpty, title.count <= 1000, artist.count <= 1000,
              durationSeconds.isFinite, durationSeconds > 0, durationSeconds <= 86400,
              bpm.isFinite, (20...400).contains(bpm), offsetMs.isFinite,
              abs(offsetMs) <= durationSeconds * 1000, (1...16).contains(beatsPerBar), clips.count <= 2000
        else { throw StudioError.message("Check the song details, BPM (20–400) and beat alignment.") }
        var copy = self
        copy.clips.sort { $0.startBeat < $1.startBeat }
        var end = -Double.greatestFiniteMagnitude
        var ids = Set<String>()
        for clip in copy.clips {
            guard ids.insert(clip.id).inserted, EffectChoice.all.contains(where: { $0.id == clip.effect }),
                  clip.startBeat.isFinite, clip.lengthBeats.isFinite, clip.lengthBeats > 0,
                  seconds(clip.startBeat) >= -0.000001,
                  seconds(clip.startBeat + clip.lengthBeats) <= durationSeconds + 0.000001
            else { throw StudioError.message("Keep each effect inside the song with a positive length.") }
            guard clip.startBeat >= end - 0.0000001 else { throw StudioError.message("Effects cannot overlap. Move this clip into an empty space.") }
            end = clip.startBeat + clip.lengthBeats
        }
        return copy
    }
    static let demo = Arrangement(trackId: "0000000000000000000000", title: "Your next light show", artist: "Example arrangement · connect to edit a Spotify song", durationSeconds: 192, bpm: 120, offsetMs: 0,
        clips: [.init(effect: "beat_pulse", startBeat: 0, lengthBeats: 32), .init(effect: "beat_sweep", startBeat: 32, lengthBeats: 32), .init(effect: "beat_sparkle", startBeat: 64, lengthBeats: 64), .init(effect: "center_heartbeat", startBeat: 160, lengthBeats: 64)])
}
struct LEDPoint: Codable { var index: Int; var x: Double; var y: Double }
struct MusicState: Codable {
    var configured: Bool; var authorized: Bool; var polling: Bool; var playing: Bool
    var trackId: String?; var title: String?; var artist: String?; var bpm: Double?
    var offsetMs: Double; var positionSeconds: Double; var error: String?
}
struct StudioStatus: Codable { var apiVersion: Int; var music: MusicState; var durationSeconds: Double; var mode: String }
struct PreviewFrame: Codable { var pixels: [Int]; var positionSeconds: Double }
enum StudioError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let text) = self { return text }; return nil }
}
