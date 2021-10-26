package co.yap.modules.dashboard.yapit.sendmoney.homecountry

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import co.yap.yapuae.R
import kotlinx.android.synthetic.main.item_benefits.view.*

class SMHomeCountryBenefitsAdapter(
    private val listItems: ArrayList<String>
) : RecyclerView.Adapter<SMHomeCountryBenefitsAdapter.ViewHolder>() {
    lateinit var viewHolder: ViewHolder


    override fun onCreateViewHolder(p0: ViewGroup, p1: Int): ViewHolder {
        val v =
            LayoutInflater.from(p0.context).inflate(R.layout.item_home_country_benefits, p0, false)
        return ViewHolder(v)
    }

    override fun getItemCount(): Int {
        return listItems.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        viewHolder = holder
        holder.tvBenefit.text = listItems[position]
    }

    class ViewHolder(itemView: View) :
        RecyclerView.ViewHolder(itemView) {
        val tvBenefit: TextView = itemView.tvBenefit
    }

}