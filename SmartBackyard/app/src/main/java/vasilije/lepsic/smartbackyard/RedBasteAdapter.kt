package vasilije.lepsic.smartbackyard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RedBasteAdapter(
    private val lista: List<RedBasteEntity>,
    private val mapaKultura: Map<Int, KulturaEntity>,
    private val onDelete: (RedBasteEntity) -> Unit
) : RecyclerView.Adapter<RedBasteAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNaziv: TextView = view.findViewById(R.id.tvNazivReda)
        val btnDelete: Button = view.findViewById(R.id.btnDeleteRed)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_red_baste, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = lista[position]
        val nazivKulture = mapaKultura[item.kulturaIdRef]?.naziv ?: "—"
        holder.tvNaziv.text = "${item.nazivReda} ($nazivKulture)"

        holder.btnDelete.setOnClickListener {
            onDelete(item)
        }
    }

    override fun getItemCount() = lista.size
}