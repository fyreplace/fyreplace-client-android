package app.fyreplace.client.data.models

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.annotations.SerializedName

data class Area(
    val name: String,
    @SerializedName("displayname")
    val displayName: String
) : Model {
    private constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString()!!
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(displayName)
    }

    companion object CREATOR : Parcelable.Creator<Area> {
        override fun createFromParcel(parcel: Parcel) = Area(parcel)

        override fun newArray(size: Int) = arrayOfNulls<Area>(size)
    }
}

data class Reputation(
    val reputation: Int,
    val spread: Int
) : Model {
    private constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(reputation)
        parcel.writeInt(spread)
    }

    companion object CREATOR : Parcelable.Creator<Reputation> {
        override fun createFromParcel(parcel: Parcel) = Reputation(parcel)

        override fun newArray(size: Int) = arrayOfNulls<Reputation>(size)
    }
}
