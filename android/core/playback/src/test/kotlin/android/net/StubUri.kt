package android.net

import android.os.Parcel

class StubUri : Uri() {
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
    override fun getScheme(): String = "https"
    override fun getSchemeSpecificPart(): String = "//example.com/audio"
    override fun getEncodedSchemeSpecificPart(): String = "//example.com/audio"
    override fun getAuthority(): String = "example.com"
    override fun getEncodedAuthority(): String = "example.com"
    override fun getUserInfo(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getHost(): String = "example.com"
    override fun getPort(): Int = -1
    override fun getPath(): String = "/audio"
    override fun getEncodedPath(): String = "/audio"
    override fun getQuery(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getFragment(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getPathSegments(): List<String> = listOf("audio")
    override fun getLastPathSegment(): String = "audio"
    override fun toString(): String = "https://example.com/audio"
    override fun buildUpon(): Builder = throw UnsupportedOperationException()
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) = Unit

    companion object {
        fun create(): Uri {
            val unsafeClass = Class.forName("sun.misc.Unsafe")
            val field = unsafeClass.getDeclaredField("theUnsafe")
            field.isAccessible = true
            val unsafe = field.get(null)
            val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
            return allocate.invoke(unsafe, StubUri::class.java) as Uri
        }
    }
}
