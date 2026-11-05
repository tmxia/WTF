package top.easelink.lcg.ui.main.source.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize






@Parcelize
class Post






(
    val author: String,
    val avatar: String,
    val date: String,
    val content: String,
    val replyUrl: String?,
    val replyAddUrl: String?,
    val profileUrl: String? = null,
    val extraInfo: String? = null,
    val followInfo: Pair<String, String>? = null,
    var isLiked: Boolean = false
) : Parcelable