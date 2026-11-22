package world.lunlun.furnmigrate.converter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import world.lunlun.furnmigrate.common.Facing
import world.lunlun.furnmigrate.common.IVec3
import world.lunlun.furnmigrate.common.Vec3

class RigAndModelTest {
    private val rig = Rig()
    private val block = ResolvedModel("test:block", emptyMap(), emptyList(), emptyMap(), false)

    private fun stand(offset: Vec3 = Vec3.ZERO, yaw: Double = 0.0, small: Boolean = false, pose: Map<BodyPart, Vec3> = emptyMap()) =
        ModelEntity("0", "armor_stand", offset, yaw, small, true, true, null, pose, emptyMap())

    private fun near(a: Vec3, b: Vec3) = assertTrue(a.approxEquals(b, 1e-6), "expected $b but was $a")

    @Test
    fun `block on a normal stand head spans 0_625 blocks centred above the head pivot`() {
        val t = ArmorStandRig.headItem(stand(), block, rig)
        near(t.apply(Vec3(8.0, 8.0, 8.0)), Vec3(0.0, rig.headPivotY + 0.25, 0.0))
        val lo = t.apply(Vec3(0.0, 0.0, 0.0)); val hi = t.apply(Vec3(16.0, 16.0, 16.0))
        assertEquals(0.625, Math.abs(hi.y - lo.y), 1e-9)
    }

    @Test
    fun `small stand halves height and size`() {
        val t = ArmorStandRig.headItem(stand(small = true), block, rig)
        near(t.apply(Vec3(8.0, 8.0, 8.0)), Vec3(0.0, (rig.headPivotY + 0.25) * 0.5, 0.0))
    }

    @Test
    fun `stand yaw rotates the item the same way as Minecraft yaw (clockwise from above)`() {
        // Model north face (z = 0) points forward. A stand with yaw 0 faces south (+z) …
        near(ArmorStandRig.headItem(stand(yaw = 0.0), block, rig).apply(Vec3(8.0, 8.0, 0.0)) - Vec3(0.0, rig.headPivotY + 0.25, 0.0), Vec3(0.0, 0.0, 0.3125))
        // … and yaw 90 faces west (-x).
        near(ArmorStandRig.headItem(stand(yaw = 90.0), block, rig).apply(Vec3(8.0, 8.0, 0.0)) - Vec3(0.0, rig.headPivotY + 0.25, 0.0), Vec3(-0.3125, 0.0, 0.0))
    }

    @Test
    fun `positive head pose X tilts the face down`() {
        val t = ArmorStandRig.headItem(stand(pose = mapOf(BodyPart.HEAD to Vec3(Math.PI / 2, 0.0, 0.0))), block, rig)
        val front = t.apply(Vec3(8.0, 8.0, 0.0)); val centre = t.apply(Vec3(8.0, 8.0, 8.0))
        assertTrue(front.y < centre.y - 0.3, "face should point down: front=$front centre=$centre")
    }

    @Test
    fun `stand offset is applied in the canonical frame`() {
        val t = ArmorStandRig.headItem(stand(offset = Vec3(1.0, -1.0, 2.0)), block, rig)
        near(t.apply(Vec3(8.0, 8.0, 8.0)), Vec3(1.0, rig.headPivotY + 0.25 - 1.0, 2.0))
    }

    @Test
    fun `placed block fills its cell relative to the floor centre`() {
        val t = ArmorStandRig.placedBlock(Vec3(1.0, 2.0, -1.0), 0, 0)
        near(t.apply(Vec3(0.0, 0.0, 0.0)), Vec3(0.5, 2.0, -1.5))
        near(t.apply(Vec3(16.0, 16.0, 16.0)), Vec3(1.5, 3.0, -0.5))
    }

    @Test
    fun `canonical floor centre is output pixel (8, 0, 8) and the model is turned 180 degrees`() {
        val t = ArmorStandRig.canonicalToOutput(rig)
        near(t.apply(Vec3.ZERO), Vec3(8.0, 0.0, 8.0))
        near(t.apply(Vec3(0.5, 0.0, 0.0)), Vec3(0.0, 0.0, 8.0))
    }

    @Test
    fun `barrier blocks become Oraxen barriers with z mirrored`() {
        val m = DModel("T", "T", "T", "T", "TOP", emptyList(),
            listOf(ModelBlockEntry("0", Vec3(1.0, 0.0, 2.0), "minecraft:barrier"), ModelBlockEntry("1", Vec3(0.0, 1.0, 0.0), "minecraft:barrier")), false)
        val assets = VanillaAssets.open(kotlin.io.path.createTempDirectory().toFile())
        val r = StyleConverter(assets, rig).convert(m, "dice_t")
        assertEquals(listOf(IVec3(1, 0, -2), IVec3(0, 1, 0)), r.barriers)
        assertEquals(null, r.hitbox)
        // and they land where FurnitureLib had them, for every facing
        for (yaw in listOf(0.0, 90.0, 180.0, 270.0)) {
            val fl = Facing.flWorldOffset(Vec3(1.0, 0.0, 2.0), yaw)
            val ox = Facing.oraxenGroundRotate(1, 0, -2, Facing.oraxenYaw(yaw))
            assertEquals(IVec3(Math.round(fl.x).toInt(), 0, Math.round(fl.z).toInt()), ox)
        }
    }

    @Test
    fun `sprite extrusion makes one box per opaque run`() {
        // 4x4 sprite: row 0 = ##.#, row 3 = .##.
        val opaque = setOf(0 to 0, 1 to 0, 3 to 0, 1 to 3, 2 to 3)
        val els = SpriteExtruder.extrude(4, 4) { x, y -> (x to y) in opaque }
        assertEquals(3, els.size)
        assertEquals(Vec3(0.0, 12.0, 7.5), els[0].from)
        assertEquals(Vec3(8.0, 16.0, 8.5), els[0].to)
        assertEquals(listOf(0.0, 0.0, 8.0, 4.0), els[0].faces[FaceDir.SOUTH]!!.uv)
    }
}
