package vasilije.lepsic.smartbackyard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class CircularMoistureView(context: Context, var procenat: Int) : View(context) {

    private val paintPozadina = Paint().apply {
        color = Color.parseColor("#E5E7EB") // Svetlo sivi krug
        style = Paint.Style.STROKE
        strokeWidth = 12f
        isAntiAlias = true
    }

    private val paintProgres = Paint().apply {
        color = Color.parseColor("#3498DB") // Plavi luk za vlagu
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND // Zaobljeni krajevi luka
        isAntiAlias = true
    }

    private val rectF = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Definišemo kvadrat unutar kojeg crtamo krug
        val velicina = width.toFloat()
        val padding = 10f
        rectF.set(padding, padding, velicina - padding, velicina - padding)

        // 1. Crta pun sivi krug u pozadini
        canvas.drawOval(rectF, paintPozadina)

        // 2. Crta plavi luk na osnovu procenta vlage (0% do 100%)
        // -90 stepeni znači da crtanje počinje od vrha (12 sati)
        val ugaoZahvata = (procenat / 100f) * 360f
        canvas.drawArc(rectF, -90f, ugaoZahvata, false, paintProgres)
    }
}