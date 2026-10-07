package pl.arenabrawl

import android.graphics.Color
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

const val WORLD = 1800f
const val BULLET_SPEED = 900f
const val BULLET_RANGE = 460f
const val MOVE_SPEED = 270f
val TWO_PI = (2.0 * PI).toFloat()

class Brawler(var x: Float, var y: Float, val isPlayer: Boolean, val color: Int) {
    val radius = 26f
    var maxHp = 3600f
    var hp = 3600f
    var angle = 0f
    var ammo = 3f
    var superCharge = 0f
    var cubes = 0
    var alive = true
    var aiTimer = 0f
    var tx = x
    var ty = y
    var strafe = 1f
    var inBush = false
    var hitFlash = 0f
    val dmgMul: Float get() = 1f + 0.1f * cubes
}

class Bullet(
    var x: Float, var y: Float,
    val vx: Float, val vy: Float,
    val owner: Brawler, val dmg: Float, var life: Float
)

class Cube(val x: Float, val y: Float)

class Game {
    val walls = ArrayList<RectF>()
    val bushes = ArrayList<RectF>()
    val brawlers = ArrayList<Brawler>()
    val bullets = ArrayList<Bullet>()
    val cubes = ArrayList<Cube>()
    var player: Brawler = Brawler(0f, 0f, true, Color.BLUE)

    // 0 = menu, 1 = gra, 2 = wygrana, 3 = przegrana
    var state = 0
    var time = 0f
    var zoneR = 1300f
    var placement = 0
    var endTimer = 0f

    // wejście (ustawiane przez GameView)
    var moveX = 0f
    var moveY = 0f
    var fireRequested = false
    var fireAngle = 0f
    var fireAuto = false
    var superRequested = false

    private val rnd = Random(System.nanoTime())

    init {
        start()
        state = 0
    }

    private fun distToRect(r: RectF, px: Float, py: Float): Float {
        val cx = px.coerceIn(r.left, r.right)
        val cy = py.coerceIn(r.top, r.bottom)
        return hypot(px - cx, py - cy)
    }

    fun start() {
        walls.clear(); bushes.clear(); brawlers.clear(); bullets.clear(); cubes.clear()
        time = 0f; zoneR = 1300f; endTimer = 0f; placement = 0
        moveX = 0f; moveY = 0f; fireRequested = false; superRequested = false

        val t = 80f
        walls.add(RectF(-t, -t, WORLD + t, 0f))
        walls.add(RectF(-t, WORLD, WORLD + t, WORLD + t))
        walls.add(RectF(-t, 0f, 0f, WORLD))
        walls.add(RectF(WORLD, 0f, WORLD + t, WORLD))

        val spawns = ArrayList<FloatArray>()
        for (i in 0 until 7) {
            val a = i * TWO_PI / 7f
            spawns.add(floatArrayOf(WORLD / 2f + cos(a) * 700f, WORLD / 2f + sin(a) * 700f))
        }

        var placed = 0
        var tries = 0
        while (placed < 28 && tries < 500) {
            tries++
            val w = 70f + rnd.nextFloat() * 190f
            val h = 70f + rnd.nextFloat() * 140f
            val x = 60f + rnd.nextFloat() * (WORLD - w - 120f)
            val y = 60f + rnd.nextFloat() * (WORLD - h - 120f)
            val r = RectF(x, y, x + w, y + h)
            if (spawns.any { distToRect(r, it[0], it[1]) < 150f }) continue
            val padded = RectF(r.left - 50f, r.top - 50f, r.right + 50f, r.bottom + 50f)
            var clash = false
            for (i in 4 until walls.size) {
                if (RectF.intersects(walls[i], padded)) { clash = true; break }
            }
            if (clash) continue
            walls.add(r)
            placed++
        }

        placed = 0
        tries = 0
        while (placed < 14 && tries < 300) {
            tries++
            val w = 140f + rnd.nextFloat() * 110f
            val h = 100f + rnd.nextFloat() * 90f
            val x = 60f + rnd.nextFloat() * (WORLD - w - 120f)
            val y = 60f + rnd.nextFloat() * (WORLD - h - 120f)
            val r = RectF(x, y, x + w, y + h)
            if (walls.any { RectF.intersects(it, r) }) continue
            bushes.add(r)
            placed++
        }

        val colors = intArrayOf(
            Color.rgb(60, 120, 255), Color.rgb(230, 70, 70), Color.rgb(240, 160, 40),
            Color.rgb(170, 80, 210), Color.rgb(40, 190, 190), Color.rgb(230, 100, 160),
            Color.rgb(120, 120, 120)
        )
        for (i in 0 until 7) {
            val b = Brawler(spawns[i][0], spawns[i][1], i == 0, colors[i])
            b.angle = atan2(WORLD / 2f - b.y, WORLD / 2f - b.x)
            b.tx = b.x; b.ty = b.y
            brawlers.add(b)
        }
        player = brawlers[0]
        state = 1
    }

    fun tap() {
        if (state == 0) state = 1
        else if ((state == 2 || state == 3) && endTimer > 1f) start()
    }

    fun aliveCount(): Int = brawlers.count { it.alive }

    fun update(dt: Float) {
        if (state == 0) return
        if (state == 2 || state == 3) endTimer += dt
        if (state == 2) return

        time += dt
        zoneR = max(260f, 1300f - max(0f, time - 15f) * 11f)

        for (b in brawlers) {
            b.inBush = false
            for (br in bushes) if (br.contains(b.x, b.y)) { b.inBush = true; break }
        }

        val p = player
        if (p.alive) {
            val ml = hypot(moveX, moveY)
            if (ml > 0.01f) {
                moveBrawler(p, moveX, moveY, dt)
                if (!fireRequested) p.angle = atan2(moveY, moveX)
            }
            if (fireRequested) {
                var ang = fireAngle
                if (fireAuto) {
                    val tg = nearestVisible(p)
                    ang = if (tg != null) atan2(tg.y - p.y, tg.x - p.x) else p.angle
                }
                shoot(p, ang)
            }
            if (superRequested) useSuper(p)
        }
        fireRequested = false
        superRequested = false

        for (b in brawlers) {
            if (!b.alive) continue
            b.ammo = min(3f, b.ammo + dt / 1.1f)
            b.hitFlash = max(0f, b.hitFlash - dt)
            if (!b.isPlayer) botThink(b, dt)
        }

        // pociski
        val it = bullets.iterator()
        while (it.hasNext()) {
            val bl = it.next()
            bl.x += bl.vx * dt
            bl.y += bl.vy * dt
            bl.life -= dt
            var dead = bl.life <= 0f
            if (!dead) {
                for (w in walls) if (w.contains(bl.x, bl.y)) { dead = true; break }
            }
            if (!dead) {
                for (tg in brawlers) {
                    if (!tg.alive || tg === bl.owner) continue
                    if (hypot(tg.x - bl.x, tg.y - bl.y) < tg.radius + 6f) {
                        damage(tg, bl.dmg, bl.owner)
                        dead = true
                        break
                    }
                }
            }
            if (dead) it.remove()
        }

        // kostki mocy
        val ci = cubes.iterator()
        while (ci.hasNext()) {
            val c = ci.next()
            for (b in brawlers) {
                if (!b.alive) continue
                if (hypot(b.x - c.x, b.y - c.y) < b.radius + 20f) {
                    b.cubes++
                    b.maxHp += 360f
                    b.hp = min(b.maxHp, b.hp + 360f)
                    ci.remove()
                    break
                }
            }
        }

        // strefa trucizny
        for (b in brawlers) {
            if (b.alive && hypot(b.x - WORLD / 2f, b.y - WORLD / 2f) > zoneR) {
                b.hp -= 260f * dt
                b.hitFlash = 0.05f
                if (b.hp <= 0f) kill(b)
            }
        }

        if (state == 1 && player.alive && aliveCount() == 1) state = 2
    }

    private fun damage(t: Brawler, d: Float, src: Brawler) {
        t.hp -= d
        t.hitFlash = 0.12f
        src.superCharge = min(1f, src.superCharge + d / 4000f)
        if (t.hp <= 0f) kill(t)
    }

    private fun kill(t: Brawler) {
        if (!t.alive) return
        t.alive = false
        repeat(1 + t.cubes) {
            cubes.add(Cube(t.x + rnd.nextFloat() * 60f - 30f, t.y + rnd.nextFloat() * 60f - 30f))
        }
        if (t === player) {
            placement = aliveCount() + 1
            state = 3
            endTimer = 0f
        }
    }

    private fun shoot(b: Brawler, ang: Float): Boolean {
        if (b.ammo < 1f) return false
        b.ammo -= 1f
        b.angle = ang
        for (i in -1..1) {
            val a = ang + i * 0.14f
            bullets.add(
                Bullet(
                    b.x + cos(a) * b.radius, b.y + sin(a) * b.radius,
                    cos(a) * BULLET_SPEED, sin(a) * BULLET_SPEED,
                    b, 380f * b.dmgMul, BULLET_RANGE / BULLET_SPEED
                )
            )
        }
        return true
    }

    private fun useSuper(b: Brawler) {
        if (b.superCharge < 1f) return
        b.superCharge = 0f
        val n = 16
        for (i in 0 until n) {
            val a = i * TWO_PI / n
            bullets.add(
                Bullet(
                    b.x + cos(a) * b.radius, b.y + sin(a) * b.radius,
                    cos(a) * BULLET_SPEED * 0.8f, sin(a) * BULLET_SPEED * 0.8f,
                    b, 520f * b.dmgMul, 0.6f
                )
            )
        }
    }

    private fun moveBrawler(b: Brawler, dx: Float, dy: Float, dt: Float) {
        b.x += dx * MOVE_SPEED * dt
        b.y += dy * MOVE_SPEED * dt
        resolveWalls(b)
    }

    private fun resolveWalls(b: Brawler) {
        for (w in walls) {
            val cx = b.x.coerceIn(w.left, w.right)
            val cy = b.y.coerceIn(w.top, w.bottom)
            val dx = b.x - cx
            val dy = b.y - cy
            val d2 = dx * dx + dy * dy
            if (d2 < b.radius * b.radius) {
                if (d2 > 0.0001f) {
                    val d = sqrt(d2)
                    val push = b.radius - d
                    b.x += dx / d * push
                    b.y += dy / d * push
                } else {
                    val dl = b.x - w.left
                    val dr = w.right - b.x
                    val dtp = b.y - w.top
                    val dbt = w.bottom - b.y
                    val m = min(min(dl, dr), min(dtp, dbt))
                    when (m) {
                        dl -> b.x = w.left - b.radius
                        dr -> b.x = w.right + b.radius
                        dtp -> b.y = w.top - b.radius
                        else -> b.y = w.bottom + b.radius
                    }
                }
            }
        }
    }

    private fun nearestVisible(b: Brawler): Brawler? {
        var best: Brawler? = null
        var bd = Float.MAX_VALUE
        for (o in brawlers) {
            if (o === b || !o.alive) continue
            val d = hypot(o.x - b.x, o.y - b.y)
            if (o.inBush && d > 220f) continue
            if (d < bd) { bd = d; best = o }
        }
        return best
    }

    private fun botThink(b: Brawler, dt: Float) {
        b.aiTimer -= dt
        var dirx = 0f
        var diry = 0f
        val cx = WORLD / 2f
        val cy = WORLD / 2f
        val dc = hypot(b.x - cx, b.y - cy)
        val target = nearestVisible(b)
        val td = if (target != null) hypot(target.x - b.x, target.y - b.y) else Float.MAX_VALUE

        if (dc > zoneR - 120f) {
            dirx = cx - b.x
            diry = cy - b.y
        } else if (target != null && td < 650f) {
            val ang = atan2(target.y - b.y, target.x - b.x)
            if (b.aiTimer <= 0f) {
                b.strafe = if (rnd.nextBoolean()) 1f else -1f
                b.aiTimer = 1f + rnd.nextFloat() * 1.5f
            }
            if (td > 330f) {
                dirx = cos(ang); diry = sin(ang)
            } else if (td < 200f) {
                dirx = -cos(ang); diry = -sin(ang)
            } else {
                dirx = -sin(ang) * b.strafe; diry = cos(ang) * b.strafe
            }
            b.angle = ang
            if (td < 480f && b.ammo >= 1f && rnd.nextFloat() < dt * 2.5f) {
                shoot(b, ang + (rnd.nextFloat() - 0.5f) * 0.3f)
            }
            if (b.superCharge >= 1f && td < 400f) useSuper(b)
        } else {
            if (b.aiTimer <= 0f) {
                var bestC: Cube? = null
                var bd = 700f
                for (c in cubes) {
                    val d = hypot(c.x - b.x, c.y - b.y)
                    if (d < bd) { bd = d; bestC = c }
                }
                if (bestC != null) {
                    b.tx = bestC.x; b.ty = bestC.y
                } else {
                    val a = rnd.nextFloat() * TWO_PI
                    val r = rnd.nextFloat() * zoneR * 0.8f
                    b.tx = cx + cos(a) * r; b.ty = cy + sin(a) * r
                }
                b.aiTimer = 1.5f + rnd.nextFloat() * 2f
            }
            dirx = b.tx - b.x
            diry = b.ty - b.y
            if (hypot(dirx, diry) < 25f) { dirx = 0f; diry = 0f }
            if (dirx != 0f || diry != 0f) b.angle = atan2(diry, dirx)
        }

        val len = hypot(dirx, diry)
        if (len > 0.001f) moveBrawler(b, dirx / len * 0.85f, diry / len * 0.85f, dt)
    }
}
