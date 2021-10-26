package co.yap.modules.placesautocomplete.adapter

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import co.yap.modules.placesautocomplete.PlaceAPI
import co.yap.modules.placesautocomplete.model.Place
import co.yap.yapcore.R

class PlacesAutoCompleteAdapter(mContext: Context, val placesApi: PlaceAPI) :
    ArrayAdapter<Place>(mContext, R.layout.autocomplete_list_item), Filterable {

    var resultList: ArrayList<Place>? = ArrayList()

    override fun getCount(): Int {
        return when {
            resultList.isNullOrEmpty() -> 0
            else -> resultList?.size!! - 1
        }
    }

    override fun getItem(position: Int): Place? {
        return when {
            resultList.isNullOrEmpty() -> null
            else -> resultList?.get(position)
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        val viewHolder: ViewHolder
        if (view == null) {
            viewHolder = ViewHolder()
            val inflater = LayoutInflater.from(context)
            view = inflater.inflate(R.layout.autocomplete_list_item, parent, false)
            viewHolder.description = view.findViewById(R.id.autocompleteText) as TextView
            viewHolder.lineView = view.findViewById(R.id.vDivider) as View
            view.tag = viewHolder
        } else {
            viewHolder = view.tag as ViewHolder
        }
        val place = resultList!![position]
        bindView(viewHolder, place, position)
        return view!!
    }

    private fun bindView(viewHolder: ViewHolder, place: Place, position: Int) {
        if (!resultList.isNullOrEmpty()) {
            if (position != resultList!!.size - 2) {
                viewHolder.description?.text = place.mainText
                viewHolder.description?.visibility = View.VISIBLE
                viewHolder.lineView?.visibility= View.VISIBLE
            }
            else {
                viewHolder.description?.text = place.mainText
                viewHolder.description?.visibility = View.VISIBLE
                viewHolder.lineView?.visibility= View.GONE

            }
        }
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                if (results != null && results.count > 0) {
                    notifyDataSetChanged()
                } else {
                    notifyDataSetInvalidated()
                }
            }

            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filterResults = FilterResults()
                if (constraint != null) {
                    resultList = placesApi.autocomplete(constraint.toString())
                    resultList?.add(Place("-1", "footer", "", ""))
                    filterResults.values = resultList
                    filterResults.count = resultList!!.size
                }
                return filterResults
            }
        }
    }

    internal class ViewHolder {
        var description: TextView? = null
        var lineView:View? =null
    }
}
