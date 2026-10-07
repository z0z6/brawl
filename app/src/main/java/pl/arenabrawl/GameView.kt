package pl.arenabrawl

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private val game = Game()
    private val lock = Any()
    private var thread: Thread? = null
    @Volatile private var running = false

    private val dp = resources.displayMetrics.density
    private val stickR = 60f * dp

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val zonePath = Path()
    private val tmpRect = RectF()

    // dotyk
    private var moveId = -1
    private var moveOx = 0f; private var moveOy = 0f
    private var moveCx = 0f; private var moveCy = 0f
    private var aimId = -1
    private var aimOx = 0f; private var aimOy = 0f
    private var aimCx = 0f; private var aimCy = 0f

    init {
        holder.addCallback(this)
        isFocusable = true
    }

    // ---------- cykl życia ----------
    override fun surfaceCreated(holder: SurfaceHolder) {
        running = true
        thread = Thread(this, "game-loop").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try { thread?.join() } catch (_: InterruptedException) {}
        thread = null
    }

    override fun run() {
        var last = System.nanoTime()
        while (running) {
            val now = System.nanoTime()
            val dt = min(0.033f, (now - last) / 1_000_000_000f)
            last = now
            synchronized(lock) { game.update(dt) }
            val c: Canvas? = try { holder.lockCanvas() } catch (e: Exception) { null }
            if (c != null) {
                try {
                    synchronized(lock) { draw(c) }
                } finally {
                    holder.unlockCanvasAndPost(c)
                }
            }
            val spent = (System.nanoTime() - now) / 1_000_000L
            if (spent < 16L) {
                try { Thread.sleep(16L - spent) } catch (_: InterruptedException) {}
            }
        }
    }

    // ---------- sterowanie ----------
    private fun superX() = width - 70f * dp
    private fun superY() = height - 170f * dp

    private fun updateMove() {
        val dx = moveCx - moveOx
        val dy = moveCy - moveOy
        val len = hypot(dx, dy)
        if (len < 6f * dp) {
            game.moveX = 0f; game.moveY = 0f
        } else {
            val mag = min(1f, len / stickR)
            game.moveX = dx / len * mag
            game.moveY = dy / len * mag
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        synchronized(lock) {
            val idx = e.actionIndex
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val id = e.getPointerId(idx)
                    val x = e.getX(idx)
                    val y = e.getY(idx)
                    if (game.state != 1) {
                        game.tap()
                        return true
                    }
                    if (hypot(x - superX(), y - superY()) < 40f * dp) {
                        game.superRequested = true
                    } else if (x < width / 2f && moveId == -1) {
                        moveId = id
                        moveOx = x; moveOy = y; moveCx = x; moveCy = y
                    } else if (x >= width / 2f && aimId == -1) {
                        aimId = id
                        aimOx = x; aimOy = y; aimCx = x; aimCy = y
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    for (i in 0 until e.pointerCount) {
                        val id = e.getPointerId(i)
                        if (id == moveId) {
                            moveCx = e.getX(i); moveCy = e.getY(i)
                            updateMove()
                        } else if (id == aimId) {
                            aimCx = e.getX(i); aimCy = e.getY(i)
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    val id = e.getPointerId(idx)
                    if (id == moveId) {
                        moveId = -1
                        game.moveX = 0f; game.moveY = 0f
                    } else if (id == aimId) {
                        aimCx = e.getX(idx); aimCy = e.getY(idx)
                        val dx = aimCx - aimOx
                        val dy = aimCy - aimOy
                        game.fireRequested = true
                        if (hypot(dx, dy) > 0.3f * stickR) {
                            game.fireAngle = atan2(dy, dx)
                            game.fireAuto = false
                        } else {
                            game.fireAuto = true
                        }
                        aimId = -1
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    moveId = -1; aimId = -1
                    game.moveX = 0f; game.moveY = 0f
                }
            }
        }
        return true
    }

    // ---------- rysowanie ----------
    private fun camera(v: Float, view: Float): Float {
        val lo = -60f
        val hi = WORLD + 60f - view
        return if (hi < lo) (WORLD - view) / 2f else (v - view / 2f).coerceIn(lo, hi)
    }

    private fun draw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val g = game
        val p = g.player
        c.drawColor(Color.rgb(20, 40, 25))

        val scale = h / 720f
        val viewW = w / scale
        val camX = camera(p.x, viewW)
        val camY = camera(p.y, 720f)

        c.save()
        c.scale(scale, scale)
        c.translate(-camX, -camY)

        // podłoga
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(88, 150, 70)
        c.drawRect(0f, 0f, WORLD, WORLD, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(35, 0, 0, 0)
        var gx = 0f
        while (gx <= WORLD) {
            c.drawLine(gx, 0f, gx, WORLD, paint)
            c.drawLine(0f, gx, WORLD, gx, paint)
            gx += 100f
        }

        // strefa trucizny
        zonePath.reset()
        zonePath.fillType = Path.FillType.EVEN_ODD
        zonePath.addRect(-300f, -300f, WORLD + 300f, WORLD + 300f, Path.Direction.CW)
        zonePath.addCircle(WORLD / 2f, WORLD / 2f, g.zoneR, Path.Direction.CW)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(90, 170, 40, 200)
        c.drawPath(zonePath, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = Color.argb(200, 200, 80, 255)
        c.drawCircle(WORLD / 2f, WORLD / 2f, g.zoneR, paint)

        // kostki mocy
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(170, 90, 240)
        for (cu in g.cubes) {
            c.save()
            c.rotate(45f, cu.x, cu.y)
            c.drawRect(cu.x - 11f, cu.y - 11f, cu.x + 11f, cu.y + 11f, paint)
            c.restore()
        }

        // ściany
        for (wl in g.walls) {
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(115, 90, 70)
            c.drawRect(wl, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = Color.rgb(70, 52, 40)
            c.drawRect(wl, paint)
        }

        // linia celowania
        if (aimId != -1 && p.alive) {
            val dx = aimCx - aimOx
            val dy = aimCy - aimOy
            if (hypot(dx, dy) > 0.3f * stickR) {
                val a = atan2(dy, dx)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 8f
                paint.color = Color.argb(110, 255, 255, 255)
                c.drawLine(p.x, p.y, p.x + cos(a) * BULLET_RANGE, p.y + sin(a) * BULLET_RANGE, paint)
            }
        }

        // pociski
        paint.style = Paint.Style.FILL
        for (b in g.bullets) {
            paint.color = if (b.owner.isPlayer) Color.rgb(255, 230, 80) else Color.rgb(255, 255, 255)
            c.drawCircle(b.x, b.y, 7f, paint)
        }

        // postacie
        for (b in g.brawlers) {
            if (!b.alive) continue
            if (!b.isPlayer && b.inBush && p.alive && hypot(b.x - p.x, b.y - p.y) > 220f) continue
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 12f
            paint.color = Color.rgb(40, 40, 40)
            c.drawLine(b.x, b.y, b.x + cos(b.angle) * 40f, b.y + sin(b.angle) * 40f, paint)
            paint.style = Paint.Style.FILL
            paint.color = if (b.hitFlash > 0f) Color.WHITE else b.color
            c.drawCircle(b.x, b.y, b.radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = if (b.isPlayer) 6f else 4f
            paint.color = if (b.isPlayer) Color.WHITE else Color.rgb(30, 30, 30)
            c.drawCircle(b.x, b.y, b.radius, paint)

            val bw = 70f
            val x0 = b.x - bw / 2f
            val y0 = b.y - 54f
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(180, 0, 0, 0)
            c.drawRect(x0 - 2f, y0 - 2f, x0 + bw + 2f, y0 + 10f, paint)
            paint.color = if (b.isPlayer) Color.rgb(70, 210, 100) else Color.rgb(230, 70, 70)
            c.drawRect(x0, y0, x0 + bw * (b.hp / b.maxHp).coerceIn(0f, 1f), y0 + 8f, paint)
            if (b.cubes > 0) {
                text.textSize = 20f
                text.color = Color.rgb(220, 170, 255)
                c.drawText("\u25C6" + b.cubes, b.x, y0 - 8f, text)
                text.color = Color.WHITE
            }
        }

        // krzaki (na wierzchu - ukrywają postacie)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(225, 40, 105, 45)
        for (bs in g.bushes) c.drawRoundRect(bs, 30f, 30f, paint)

        c.restore()

        drawHud(c, w, h)
    }

    private fun drawStick(c: Canvas, ox: Float, oy: Float, cx: Float, cy: Float, color: Int) {
        var dx = cx - ox
        var dy = cy - oy
        val len = hypot(dx, dy)
        if (len > stickR) { dx = dx / len * stickR; dy = dy / len * stickR }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(60, 255, 255, 255)
        c.drawCircle(ox, oy, stickR, paint)
        paint.color = color
        c.drawCircle(ox + dx, oy + dy, stickR * 0.45f, paint)
    }

    private fun drawHud(c: Canvas, w: Float, h: Float) {
        val g = game
        val p = g.player

        if (moveId != -1) drawStick(c, moveOx, moveOy, moveCx, moveCy, Color.argb(170, 80, 160, 255))
        if (aimId != -1) drawStick(c, aimOx, aimOy, aimCx, aimCy, Color.argb(170, 255, 90, 90))

        // przycisk super
        val sx = superX()
        val sy = superY()
        val sr = 34f * dp
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(150, 30, 30, 30)
        c.drawCircle(sx, sy, sr, paint)
        tmpRect.set(sx - sr, sy - sr, sx + sr, sy + sr)
        paint.color = if (p.superCharge >= 1f) Color.rgb(255, 210, 0) else Color.rgb(150, 120, 200)
        c.drawArc(tmpRect, -90f, 360f * p.superCharge, true, paint)
        text.textSize = 12f * dp
        text.color = if (p.superCharge >= 1f) Color.BLACK else Color.WHITE
        c.drawText("SUPER", sx, sy + 4f * dp, text)
        text.color = Color.WHITE

        // amunicja
        val barW = 40f * dp
        val gap = 6f * dp
        val total = 3 * barW + 2 * gap
        val ax = (w - total) / 2f
        val ay = h - 24f * dp
        for (i in 0 until 3) {
            val x0 = ax + i * (barW + gap)
            paint.color = Color.argb(160, 0, 0, 0)
            c.drawRect(x0, ay, x0 + barW, ay + 10f * dp, paint)
            val f = (p.ammo - i).coerceIn(0f, 1f)
            paint.color = Color.rgb(255, 150, 40)
            c.drawRect(x0, ay, x0 + barW * f, ay + 10f * dp, paint)
        }

        // pasek HP i licznik
        val hw = 160f * dp
        paint.color = Color.argb(160, 0, 0, 0)
        c.drawRect(12f * dp, 12f * dp, 12f * dp + hw, 28f * dp, paint)
        paint.color = Color.rgb(70, 210, 100)
        c.drawRect(12f * dp, 12f * dp, 12f * dp + hw * (p.hp / p.maxHp).coerceIn(0f, 1f), 28f * dp, paint)

        text.textAlign = Paint.Align.LEFT
        text.textSize = 16f * dp
        c.drawText("Żywi: " + g.aliveCount() + "   Kostki: " + p.cubes, 12f * dp, 50f * dp, text)
        text.textAlign = Paint.Align.CENTER

        // nakładki stanów
        if (g.state != 1) {
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(150, 0, 0, 0)
            c.drawRect(0f, 0f, w, h, paint)
        }
        when (g.state) {
            0 -> {
                text.textSize = 44f * dp
                c.drawText("ARENA BRAWL", w / 2f, h * 0.30f, text)
                text.textSize = 16f * dp
                c.drawText("Lewy kciuk: ruch   |   Prawy kciuk: przeciągnij i puść, by strzelić", w / 2f, h * 0.45f, text)
                c.drawText("Szybkie stuknięcie = auto-celowanie   |   Żółty przycisk: SUPER", w / 2f, h * 0.52f, text)
                c.drawText("Zbieraj fioletowe kostki, unikaj fioletowej strefy. Przeżyj jako ostatni!", w / 2f, h * 0.59f, text)
                text.textSize = 24f * dp
                c.drawText("Dotknij, aby zagrać", w / 2f, h * 0.78f, text)
            }
            2 -> {
                text.textSize = 44f * dp
                c.drawText("ZWYCIĘSTWO!", w / 2f, h * 0.45f, text)
                if (g.endTimer > 1f) {
                    text.textSize = 20f * dp
                    c.drawText("Dotknij, aby zagrać ponownie", w / 2f, h * 0.62f, text)
                }
            }
            3 -> {
                text.textSize = 40f * dp
                c.drawText("Miejsce #" + g.placement + " z 7", w / 2f, h * 0.45f, text)
                if (g.endTimer > 1f) {
                    text.textSize = 20f * dp
                    c.drawText("Dotknij, aby zagrać ponownie", w / 2f, h * 0.62f, text)
                }
            }
        }
    }
}
