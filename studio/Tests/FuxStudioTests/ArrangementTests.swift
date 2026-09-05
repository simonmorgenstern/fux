import XCTest
@testable import FuxStudio

final class ArrangementTests: XCTestCase {
    func testBeatGridAndHalfOpenBoundaries() throws {
        var show = Arrangement.demo
        show.offsetMs = 1000
        show.clips = [.init(effect: "beat_pulse", startBeat: 0, lengthBeats: 4), .init(effect: "beat_sweep", startBeat: 4, lengthBeats: 4)]
        show = try show.validated()
        XCTAssertNil(show.active(at: 0.999))
        XCTAssertEqual(show.active(at: 1)?.effect, "beat_pulse")
        XCTAssertEqual(show.active(at: 3)?.effect, "beat_sweep")
        XCTAssertNil(show.active(at: 5))
        XCTAssertEqual(show.beat(show.seconds(17)), 17, accuracy: 0.000001)
    }
    func testRejectsOverlapWithoutMutatingOriginal() throws {
        let original = Arrangement.demo
        var candidate = original
        candidate.clips[1].startBeat = 10
        XCTAssertThrowsError(try candidate.validated())
        XCTAssertNoThrow(try original.validated())
    }
    func testRejectsOutOfBoundsInvalidTempoAndDuplicateIDs() {
        var show = Arrangement.demo
        show.bpm = .nan; XCTAssertThrowsError(try show.validated())
        show = .demo; show.clips[0].startBeat = -1; XCTAssertThrowsError(try show.validated())
        show = .demo; show.clips[0].lengthBeats = 10000; XCTAssertThrowsError(try show.validated())
        show = .demo; show.clips[1].id = show.clips[0].id; XCTAssertThrowsError(try show.validated())
    }
    func testPortableJSONRoundTrip() throws {
        let data = try JSONEncoder().encode(Arrangement.demo)
        XCTAssertEqual(try JSONDecoder().decode(Arrangement.self, from: data), Arrangement.demo)
    }
    @MainActor func testEditingUndoRedoAndOverlapRejection() {
        let model = StudioModel(persistDrafts: false); model.showDemo()
        let original = model.show
        let id = model.show!.clips[0].id
        model.editClip(id, length: 16)
        XCTAssertEqual(model.show?.clips[0].lengthBeats, 16)
        model.undo(); XCTAssertEqual(model.show, original)
        model.redo(); XCTAssertEqual(model.show?.clips[0].lengthBeats, 16)
        model.editClip(id, length: 60)
        XCTAssertNotNil(model.message)
        XCTAssertEqual(model.show?.clips[0].lengthBeats, 16)
        model.selectedID = id; model.duplicateSelected()
        XCTAssertEqual(model.show?.clips.count, 5)
    }
}
