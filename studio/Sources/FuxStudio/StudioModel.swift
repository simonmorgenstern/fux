import SwiftUI
import AppKit

@MainActor final class StudioModel: ObservableObject {
    @Published var address = UserDefaults.standard.string(forKey: "studio.address") ?? "http://fux.local:8080"
    @Published var connected = false
    @Published var busy = false
    @Published var status: StudioStatus?
    @Published var show: Arrangement? { didSet { persistDraft() } }
    @Published var selectedID: String?
    @Published var dirty = false
    @Published var message: String?
    @Published var notice = "Connect to Fux to begin."
    @Published var follow = true
    @Published var inspectionTime = 0.0
    @Published var zoom = 8.0
    @Published var pixels = Array(repeating: 0, count: 268)
    @Published var points: [LEDPoint] = []
    @Published var previewMessage = "Connect to preview your effects"
    @Published var history: [Arrangement] = []
    @Published var future: [Arrangement] = []
    private var observedAt = Date()
    private var baseURL: URL?
    private let sessionID = UUID().uuidString
    private var generation = 0
    private let network: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 5
        config.timeoutIntervalForResource = 8
        return URLSession(configuration: config)
    }()
    private let persistDrafts: Bool
    private var draftURL: URL? {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first?
            .appendingPathComponent("FuxStudio").appendingPathComponent("draft.json")
    }
    private func persistDraft() {
        guard persistDrafts, let show, show.trackId != Arrangement.demo.trackId, let url = draftURL else { return }
        do {
            try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
            try JSONEncoder().encode(show).write(to: url, options: .atomic)
        } catch { notice = "Draft recovery could not be saved on this Mac" }
    }
    init(persistDrafts: Bool = true) {
        self.persistDrafts = persistDrafts
        #if SWIFT_PACKAGE
        let url = Bundle.module.url(forResource: "pixelCoordinates", withExtension: "json")
        #else
        let url = Bundle.main.url(forResource: "pixelCoordinates", withExtension: "json")
        #endif
        if let url, let data = try? Data(contentsOf: url), let layout = try? JSONDecoder().decode([LEDPoint].self, from: data) { points = layout }
        if persistDrafts, let url = draftURL, let data = try? Data(contentsOf: url),
           let restored = try? JSONDecoder().decode(Arrangement.self, from: data), let valid = try? restored.validated() {
            show = valid; dirty = true; follow = false
            notice = "Recovered your last draft · connect to save it to Fux"
        }
        if CommandLine.arguments.contains("--demo") { showDemo() }
    }
    var selected: EffectClip? { show?.clips.first { $0.id == selectedID } }
    var matchingTrack: Bool { show?.trackId != nil && show?.trackId == status?.music.trackId }
    var canUndo: Bool { !history.isEmpty }
    var canRedo: Bool { !future.isEmpty }
    func position(at now: Date = Date()) -> Double {
        guard follow, matchingTrack, let state = status else { return inspectionTime }
        let elapsed = now.timeIntervalSince(observedAt)
        let advance = state.music.playing && elapsed < 15 ? max(0, elapsed) : 0
        return min(show?.durationSeconds ?? 0, max(0, state.music.positionSeconds + advance))
    }
    func selectClip(_ id: String) { NSApp.keyWindow?.makeFirstResponder(nil); selectedID = id }
    func inspect(_ seconds: Double) {
        NSApp.keyWindow?.makeFirstResponder(nil)
        follow = false; inspectionTime = min(show?.durationSeconds ?? 0, max(0, seconds)); generation += 1
    }
    func showDemo() {
        show = .demo; dirty = false; history = []; future = []; selectedID = show?.clips.first?.id
        follow = false; inspectionTime = 8; generation += 1
        notice = "Example only · load a Spotify song to save to Fux"
    }
    private func request<T: Decodable>(_ path: String, method: String = "GET", body: Data? = nil) async throws -> T {
        guard let baseURL else { throw StudioError.message("Connect to Fux first.") }
        var request = URLRequest(url: baseURL.appendingPathComponent(path))
        request.httpMethod = method; request.httpBody = body
        if body != nil { request.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        let (data, response) = try await network.data(for: request)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200...299).contains(code) else {
            let detail = (try? JSONSerialization.jsonObject(with: data) as? [String: String])?["error"]
            throw APIError(code: code, detail: detail ?? "Fux returned HTTP \(code). Install the Studio backend update.")
        }
        do { return try JSONDecoder().decode(T.self, from: data) }
        catch { throw StudioError.message("This Fux server needs the Studio backend update.") }
    }
    struct APIError: LocalizedError { let code: Int; let detail: String; var errorDescription: String? { detail } }
    func disconnect() {
        connected = false; status = nil; baseURL = nil; follow = false; generation += 1
        pixels = Array(repeating: 0, count: 268); previewMessage = "Connect to preview your effects"
        notice = "Disconnected · your draft stays on this Mac"
        UserDefaults.standard.set(address, forKey: "studio.address")
    }
    func connect() async {
        busy = true; defer { busy = false }
        connected = false; generation += 1; status = nil
        guard let url = URL(string: address), ["http", "https"].contains(url.scheme ?? ""), url.host != nil,
              url.user == nil, url.password == nil, url.query == nil, url.fragment == nil, ["", "/"].contains(url.path)
        else { message = "Enter a server address such as http://fux.local:8080."; return }
        baseURL = url
        do {
            let state: StudioStatus = try await request("api/studio/connect", method: "POST")
            guard state.apiVersion == 1 else { throw StudioError.message("Unsupported Studio API version.") }
            let layout: [LEDPoint] = try await request("api/studio/layout")
            status = state; points = layout; observedAt = Date(); connected = true
            UserDefaults.standard.set(address, forKey: "studio.address")
            notice = state.music.authorized ? "Connected to Fux" : "Connect Spotify on Fux to select a song"
            if show == nil, state.music.trackId != nil { await loadCurrent() }
        } catch { message = error.localizedDescription; previewMessage = "Fux is unavailable" }
    }
    func poll() async {
        while !Task.isCancelled {
            if connected {
                do {
                    let state: StudioStatus = try await request("api/studio/status")
                    if matchingTrack && state.music.trackId != show?.trackId {
                        inspectionTime = position(); follow = false; generation += 1
                        notice = "Spotify changed songs. Your draft is still here."
                    }
                    status = state; observedAt = Date()
                } catch { notice = "Connection interrupted · your draft is safe" }
            }
            try? await Task.sleep(nanoseconds: 1_000_000_000)
        }
    }
    func loadCurrent() async {
        guard let state = status, let id = state.music.trackId, state.durationSeconds > 0 else {
            message = "Play a song in Spotify, then try again."; return
        }
        busy = true; defer { busy = false }
        do {
            let loaded: Arrangement
            do { loaded = try await request("api/studio/arrangements/" + id) }
            catch let error as APIError where error.code == 404 {
                loaded = Arrangement(trackId: id, title: state.music.title ?? "Untitled song", artist: state.music.artist ?? "", durationSeconds: state.durationSeconds, bpm: state.music.bpm ?? 120, offsetMs: state.music.offsetMs)
            }
            show = try loaded.validated(); dirty = false; history = []; future = []
            selectedID = show?.clips.first?.id; follow = true; generation += 1
            notice = state.music.bpm == nil ? "BPM unknown · check the 120 BPM starting grid before saving" : "Song ready to arrange"
        } catch { message = error.localizedDescription }
    }
    func save() async {
        NSApp.keyWindow?.makeFirstResponder(nil)
        await Task.yield()
        guard let show, show.trackId != Arrangement.demo.trackId else { return }
        busy = true; defer { busy = false }
        do {
            let validated = try show.validated()
            let _: Arrangement = try await request("api/studio/arrangements/" + show.trackId, method: "PUT", body: JSONEncoder().encode(validated))
            if self.show == show { dirty = false }
            notice = "Saved to Fux · Music mode can play this with your Mac closed"
        } catch { message = error.localizedDescription }
    }
    func commit(_ candidate: Arrangement) {
        do {
            let valid = try candidate.validated()
            guard let old = show, old != valid else { return }
            history.append(old); if history.count > 100 { history.removeFirst() }
            future = []; show = valid; dirty = true; generation += 1
        } catch { message = error.localizedDescription }
    }
    func undo() { guard let old = history.popLast(), let current = show else { return }; future.append(current); show = old; dirty = true; generation += 1 }
    func redo() { guard let next = future.popLast(), let current = show else { return }; history.append(current); show = next; dirty = true; generation += 1 }
    func add(_ effect: String, at seconds: Double? = nil) {
        guard var show else { return }
        let minimum = ceil(show.beat(0))
        let start = max(minimum, show.beat(seconds ?? position()).rounded())
        let next = show.clips.filter { $0.startBeat >= start }.map(\.startBeat).min() ?? show.beat(show.durationSeconds)
        let length = min(Double(show.beatsPerBar * 4), next - start)
        guard length > 0 else { message = "Drop the effect in an empty part of the timeline."; return }
        let clip = EffectClip(effect: effect, startBeat: start, lengthBeats: length)
        show.clips.append(clip); commit(show)
        if self.show?.clips.contains(where: { $0.id == clip.id }) == true { selectClip(clip.id); notice = EffectChoice.name(effect) + " added to the timeline" }
    }
    func editClip(_ id: String, start: Double? = nil, length: Double? = nil, effect: String? = nil) {
        guard var show, let index = show.clips.firstIndex(where: { $0.id == id }) else { return }
        if let start { show.clips[index].startBeat = start }
        if let length { show.clips[index].lengthBeats = length }
        if let effect { show.clips[index].effect = effect }
        commit(show)
    }
    func deleteSelected() { guard var show, let selectedID else { return }; show.clips.removeAll { $0.id == selectedID }; commit(show); self.selectedID = nil }
    func duplicateSelected() {
        guard var show, let selected else { return }
        var copy = selected; copy.id = UUID().uuidString; copy.startBeat += copy.lengthBeats
        show.clips.append(copy); commit(show)
        if self.show?.clips.contains(where: { $0.id == copy.id }) == true { selectedID = copy.id }
    }
    func previewLoop() async {
        struct Payload: Encodable { let sessionId: String; let arrangement: Arrangement; let positionSeconds: Double }
        while !Task.isCancelled {
            if connected, let show {
                if follow && !matchingTrack { previewMessage = "Play this song in Spotify, or inspect the timeline" }
                else if follow && Date().timeIntervalSince(observedAt) > 15 { previewMessage = "Waiting for Fux to reconnect" }
                else {
                    let revision = generation
                    do {
                        let body = try JSONEncoder().encode(Payload(sessionId: sessionID, arrangement: show, positionSeconds: position()))
                        let result: PreviewFrame = try await request("api/studio/preview", method: "POST", body: body)
                        if generation == revision { pixels = result.pixels; previewMessage = follow ? "Following Spotify · draft preview" : "Inspecting timeline · Spotify is unchanged" }
                    } catch { previewMessage = error.localizedDescription }
                }
            }
            try? await Task.sleep(nanoseconds: 66_000_000)
        }
    }
    func openSpotifySetup() {
        if let baseURL { NSWorkspace.shared.open(baseURL.appendingPathComponent("api/spotify/login")) }
    }
}
