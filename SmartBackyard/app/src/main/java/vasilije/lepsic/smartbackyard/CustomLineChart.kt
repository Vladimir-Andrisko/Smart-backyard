package vasilije.lepsic.smartbackyard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class CustomLineChart(
    context: Context,
    private val podaciVlage: List<Float>,
    private val podaciTemp: List<Float>
) : View(context) {

    private val paintVlaga = Paint().apply {
        color = Color.parseColor("#3498DB") // Plava linija za vlagu zemlje
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val paintTemp = Paint().apply {
        color = Color.parseColor("#E67E22") // Narandžasta linija za temperaturu vazduha
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val paintMreza = Paint().apply {
        color = Color.parseColor("#E5E7EB")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val paintTekst = Paint().apply {
        color = Color.parseColor("#7F8C8D")
        textSize = 26f
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (podaciVlage.isEmpty() || podaciTemp.isEmpty()) return

        val sirina = width.toFloat()
        val visina = height.toFloat()
        val padding = 70f

        // 1. Iscrtavanje horizontalne mreže i oznaka osa
        val brojLinijaMreze = 4
        for (i in 0..brojLinijaMreze) {
            val y = padding + (visina - 2 * padding) * i / brojLinijaMreze
            canvas.drawLine(padding, y, sirina - padding, y, paintMreza)

            // Leva osa: Procenti za vlagu (0-100%)
            val procenatVlage = 100 - (i * 25)
            canvas.drawText("$procenatVlage%", 10f, y + 10f, paintTekst)

            // Desna osa: Skala za temperaturu (0-50°C)
            val vrednostTemp = 50 - (i * 12.5).toInt()
            canvas.drawText("$vrednostTemp°C", sirina - padding + 10f, y + 10f, paintTekst)
        }

        // 2. Iscrtavanje linije za vlažnost zemljišta
        iscrtajVremenskuSeriju(canvas, podaciVlage, 100f, sirina, visina, padding, paintVlaga)

        // 3. Iscrtavanje linije za temperaturu vazduha
        iscrtajVremenskuSeriju(canvas, podaciTemp, 50f, sirina, visina, padding, paintTemp)
    }

    private fun iscrtajVremenskuSeriju(
        canvas: Canvas,
        serija: List<Float>,
        maxVrednostOse: Float,
        sirina: Float,
        visina: Float,
        padding: Float,
        paintLinije: Paint
    ) {
        val korakX = (sirina - 2 * padding) / (serija.size - 1).coerceAtLeast(1)
        val putanja = Path()

        for ((indeks, vrednost) in serija.withIndex()) {
            val x = padding + (indeks * korakX)
            val y = visina - padding - ((vrednost / maxVrednostOse) * (visina - 2 * padding))

            if (indeks == 0) {
                putanja.moveTo(x, y)
            } else {
                putanja.lineTo(x, y)
            }

            // Mala tačka na svakom očitavanju
            canvas.drawCircle(x, y, 6f, Paint().apply { color = paintLinije.color; isAntiAlias = true })
        }
        canvas.drawPath(putanja, paintLinije)
    }
}