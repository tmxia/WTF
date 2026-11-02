package top.easelink.lcg.ui.main.source.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName





class ArticleAbstractResponse {
    @Expose
    @SerializedName("title")
    var title: String? = null

    @Expose
    @SerializedName("images")
    var images: List<String>? = null

    @Expose
    @SerializedName("description")
    var description: String? = null
}