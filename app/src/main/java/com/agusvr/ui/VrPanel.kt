package com.agusvr.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.agusvr.interaction.PanelElement
import com.agusvr.interaction.PanelTarget
import com.agusvr.runtime.gl.CanvasTexture
import com.agusvr.runtime.gl.MeshSet
import com.agusvr.runtime.gl.SceneObject

/**
 * Agus UI — painel espacial flutuante.
 *
 * Cada painel é um quad texturizado com um Bitmap desenhado via Canvas
 * (texto nativo do Android, nítido e barato). O ray interage com ele via
 * [PanelTarget]; o hover dos subelementos é repintado sob demanda — nada
 * de repintar por frame sem necessidade.
 *
 * Coordenadas locais: -0.5..0.5 em X (esquerda→direita) e Y (topo→base).
 */
class VrPanel(
    val id: String,
    val widthM: Float,
    val heightM: Float,
    meshes: MeshSet,
    val pixelW: Int = 1024
) {
    companion object {
        val BG = 0xEF0B1220.toInt()
        val BORDER = 0x386EE7FF
        val CARD = 0xFF0D1526.toInt()
        val CARD_BORDER = 0x66233452
        val CARD_HOVER = 0xFF1A3D52.toInt()
        val CARD_HOVER_BORDER = 0xFF9DF0FF.toInt()
        val ACCENT = 0xFF6EE7FF.toInt()
        val ACCENT_FILL = 0xFF12293C.toInt()
        val TEXT = 0xFFE8F1F8.toInt()
        val TEXT_DIM = 0xFF8CA0B3.toInt()
        val OK = 0xFF7DFFB2.toInt()
    }

    val pixelH = (pixelW * heightM / widthM).toInt()
    val bitmap: Bitmap = Bitmap.createBitmap(pixelW, pixelH, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    val texture = CanvasTexture()

    val item = SceneObject(meshes.quad, SceneObject.PROG_TEX).apply {
        scale[0] = widthM; scale[1] = heightM; scale[2] = 1f
        depthWrite = false
        sortBias = 5f
    }

    val target = PanelTarget(id, item, widthM, heightM) { u, v -> elementAt(u, v) }

    val elements = ArrayList<PanelElement>()
    var title = ""
    var subtitle = ""
    var customDraw: ((Canvas, Int, Int) -> Unit)? = null

    var hovered: PanelElement? = null
    var hoverProgress = 0f
    var flashMs = 0f

    private var dirty = true

    init {
        texture.setBitmap(bitmap)
        repaint()
    }

    fun place(x: Float, y: Float, z: Float, yaw: Float) {
        item.pos[0] = x; item.pos[1] = y; item.pos[2] = z
        item.yaw = yaw
    }

    // ------------------------------------------------------------- conteúdo
    fun clearElements() {
        elements.clear()
    }

    fun addButton(
        id: String,
        label: String,
        x: Float, y: Float, w: Float, h: Float,
        accent: Boolean = false,
        meta: String = "",
        onSelect: ((PanelElement) -> Unit)? = null
    ): PanelElement {
        val el = PanelElement(id, label, x, y, w, h)
        el.accent = accent
        el.meta = meta
        el.onSelect = onSelect
        elements.add(el)
        return el
    }

    fun elementAt(u: Float, v: Float): PanelElement? {
        for (i in elements.indices.reversed()) {
            val el = elements[i]
            if (el.enabled && el.contains(u, v)) return el
        }
        return null
    }

    // -------------------------------------------------------------- repaint
    fun repaint() {
        synchronized(bitmap) {
            drawPanel()
        }
        texture.dirty = true
    }

    fun setHovered(el: PanelElement?) {
        if (hovered !== el) {
            hovered = el
            repaint()
        }
    }

    /**
     * Chamado pelo engine todo frame. O hoverProgress vem do engine
     * (progresso real da seleção por aproximação); aqui só animamos o
     * flash de seleção, repintando apenas quando necessário.
     */
    fun update(dtMs: Float) {
        if (flashMs > 0f) {
            flashMs -= dtMs
            if (flashMs <= 0f) { flashMs = 0f; repaint() }
        }
    }

    private fun drawPanel() {
        val W = pixelW; val H = pixelH
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

        val r = W * 0.035f
        val bgRect = RectF(W * 0.005f, H * 0.005f, W * 0.995f, H * 0.995f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // Fundo
        p.color = BG
        canvas.drawRoundRect(bgRect, r, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = W * 0.004f
        p.color = BORDER
        canvas.drawRoundRect(bgRect, r, r, p)
        p.style = Paint.Style.FILL

        // Flash de seleção
        if (flashMs > 0f) {
            p.color = Color.argb((90f * (flashMs / 320f)).toInt().coerceIn(0, 90), 0x9D, 0xFF, 0xB2)
            canvas.drawRoundRect(bgRect, r, r, p)
        }

        // Título
        if (title.isNotEmpty()) {
            val titleSize = H * 0.105f
            p.color = ACCENT
            canvas.drawRect(W * 0.045f, H * 0.05f, W * 0.052f, H * 0.05f + titleSize * 0.9f, p)
            p.color = TEXT
            p.textSize = titleSize
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(title, W * 0.075f, H * 0.05f + titleSize * 0.82f, p)
            if (subtitle.isNotEmpty()) {
                p.color = TEXT_DIM
                p.textSize = H * 0.048f
                p.typeface = Typeface.DEFAULT
                canvas.drawText(subtitle, W * 0.077f, H * 0.05f + titleSize * 0.82f + H * 0.062f, p)
            }
        }

        // Conteúdo customizado (listas, valores, gráficos simples)
        customDraw?.invoke(canvas, W, H)

        // Elementos (botões / cards)
        for (el in elements) {
            val x = (el.x + 0.5f) * W
            val y = (el.y + 0.5f) * H
            val w = el.w * W
            val h = el.h * H
            val rect = RectF(x, y, x + w, y + h)
            val rr = h * 0.28f

            val isHover = hovered === el
            if (!el.enabled) {
                p.color = 0xFF080D17.toInt()
            } else if (isHover) {
                p.color = CARD_HOVER
            } else if (el.accent) {
                p.color = ACCENT_FILL
            } else {
                p.color = CARD
            }
            canvas.drawRoundRect(rect, rr, rr, p)
            p.style = Paint.Style.STROKE
            p.strokeWidth = W * 0.0025f
            p.color = when {
                isHover -> CARD_HOVER_BORDER
                el.accent -> ACCENT
                else -> CARD_BORDER
            }
            canvas.drawRoundRect(rect, rr, rr, p)
            p.style = Paint.Style.FILL

            // Texto centralizado com auto-shrink
            val label = el.label
            var ts = h * 0.46f
            p.typeface = if (el.accent) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            p.color = if (el.enabled) TEXT else TEXT_DIM
            while (ts > h * 0.18f) {
                p.textSize = ts
                if (p.measureText(label) < w * 0.88f) break
                ts *= 0.9f
            }
            val tx = x + w / 2f - p.measureText(label) / 2f
            val ty = y + h / 2f + ts * 0.34f
            canvas.drawText(label, tx, ty, p)

            // Meta (linha auxiliar pequena)
            if (el.meta.isNotEmpty()) {
                p.textSize = h * 0.22f
                p.color = TEXT_DIM
                val mx = x + w / 2f - p.measureText(el.meta) / 2f
                canvas.drawText(el.meta, mx, y + h * 0.86f, p)
            }

            // Barra de progresso de seleção por aproximação
            if (isHover && hoverProgress > 0.02f) {
                p.color = OK
                canvas.drawRoundRect(
                    RectF(x + w * 0.1f, y + h * 0.94f, x + w * (0.1f + 0.8f * hoverProgress), y + h * 0.99f),
                    h * 0.04f, h * 0.04f, p
                )
            }
        }
    }

    /** Registra o painel nas estruturas do engine. */
    fun registerTo(transparent: ArrayList<SceneObject>, textures: ArrayList<CanvasTexture>) {
        transparent.add(item)
        textures.add(texture)
    }

    /** Sincroniza o id de textura após o primeiro upload (thread GL). */
    fun syncTexture() {
        item.textureId = texture.texId
    }
}
