package app.chalkroom.feature.classroom

import app.chalkroom.shared.StudentId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class ScenePickerTest {
    @Test
    fun `a visit opens with an entry scene, not again on a quick return`() {
        val picker = ScenePicker(random = Random(3))
        assertEquals(SceneKind.ENTRY, picker.visitStarted(0)?.kind)
        picker.visitEnded(5_000)
        assertNull(picker.visitStarted(5_000 + 10_000))
        picker.visitEnded(20_000)
        assertNull(picker.visitStarted(20_000 + ScenePicker.ENTRY_AGAIN_MS - 1))
        picker.visitEnded(50_000)
        assertNotNull(picker.visitStarted(50_000 + ScenePicker.ENTRY_AGAIN_MS))
    }

    @Test
    fun `never the same scene twice in a row`() {
        val picker = ScenePicker(random = Random(11))
        var last: String? = null
        var now = 0L
        repeat(2_000) { i ->
            val scene = if (i % 5 == 0) {
                picker.visitEnded(now)
                now += 30_000
                picker.visitStarted(now)!!
            } else {
                picker.nextAmbient(if (i % 2 == 0) setOf(StudentId.PIP) else emptySet())
            }
            assertNotEquals(last, scene.id)
            last = scene.id
        }
    }

    @Test
    fun `every unplayed scene comes before any repeat`() {
        val picker = ScenePicker(random = Random(5))
        val ambient = Scenes.all.filter { it.kind == SceneKind.AMBIENT }.map { it.id }.toSet()
        val first = List(ambient.size) { picker.nextAmbient(emptySet()).id }
        assertEquals(ambient, first.toSet())

        val entries = Scenes.all.filter { it.kind == SceneKind.ENTRY }.map { it.id }.toSet()
        var now = 0L
        val firstEntries = List(entries.size) {
            now += 60_000
            picker.visitEnded(now)
            picker.visitStarted(now + 30_000)!!.id.also { now += 30_000 }
        }
        assertEquals(entries, firstEntries.toSet())
    }

    @Test
    fun `paused scenes only while their student has a paused lesson`() {
        val picker = ScenePicker(random = Random(9))
        repeat(500) {
            val scene = picker.nextAmbient(setOf(StudentId.MIRA))
            assertTrue(scene.kind == SceneKind.AMBIENT || scene.needsPaused == StudentId.MIRA, scene.id)
        }
        val ids = List(30) { picker.nextAmbient(setOf(StudentId.PIP, StudentId.JUNO)).id }
        assertTrue("P1" in ids && "P3" in ids, ids.toString())
        assertFalse("P2" in ids)
    }

    @Test
    fun `the script is well formed`() {
        val byKind = Scenes.all.groupBy { it.kind }
        assertEquals(5, byKind.getValue(SceneKind.ENTRY).size)
        assertEquals(10, byKind.getValue(SceneKind.AMBIENT).size)
        assertEquals(3, byKind.getValue(SceneKind.PAUSED).size)
        assertEquals(Scenes.all.size, Scenes.all.map { it.id }.toSet().size)
        Scenes.all.forEach { scene ->
            assertTrue(scene.beats.isNotEmpty(), scene.id)
            assertFalse(scene.beats.last().cutOff, scene.id)
            assertTrue(scene.beats.last().text != null, scene.id)
            assertEquals(scene.kind == SceneKind.PAUSED, scene.needsPaused != null, scene.id)
            scene.beats.forEach { beat ->
                assertNotEquals(Gaze.At(beat.speaker), beat.gaze, scene.id)
                beat.text?.let { assertTrue(it.isNotBlank(), scene.id) }
            }
        }
    }
}
