package android.net

class TestUri(private val uriString: String = "content://dummy/file") : Uri() {
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
    override fun getScheme(): String = "content"
    override fun getSchemeSpecificPart(): String = "//dummy/file"
    override fun getEncodedSchemeSpecificPart(): String = "//dummy/file"
    override fun getAuthority(): String = "dummy"
    override fun getEncodedAuthority(): String = "dummy"
    override fun getUserInfo(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getHost(): String = "dummy"
    override fun getPort(): Int = -1
    override fun getPath(): String = "/file"
    override fun getEncodedPath(): String = "/file"
    override fun getQuery(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getFragment(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getPathSegments(): List<String> = emptyList()
    override fun getLastPathSegment(): String = "file"
    override fun buildUpon(): Builder? = null
    override fun writeToParcel(dest: android.os.Parcel, flags: Int) {}
    override fun describeContents(): Int = 0
    override fun toString(): String = uriString
    override fun compareTo(other: Uri?): Int = 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Uri) return false
        return this.toString() == other.toString()
    }

    override fun hashCode(): Int = uriString.hashCode()
}
