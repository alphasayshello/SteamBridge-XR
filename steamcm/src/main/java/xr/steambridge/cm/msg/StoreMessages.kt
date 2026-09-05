package xr.steambridge.cm.msg

/**
 * StoreBrowse.GetItems#1 — authed ServiceMethod (EMsg 151) that takes a BATCH of appids and returns
 * per-app store data as pure protobuf. We use it purely to learn which owned games are VR-capable, so
 * the library can default to a VR filter. Field numbers pinned from SteamDatabase/Protobufs
 * steammessages_storebrowse.steamclient.proto:
 *
 *   CStoreBrowse_GetItems_Request  { repeated StoreItemID ids = 1; StoreBrowseContext context = 2;
 *                                    StoreBrowseItemDataRequest data_request = 3; }
 *   StoreItemID                    { uint32 appid = 1; }
 *   StoreBrowseContext             { string language = 1; string country_code = 3; }
 *   StoreBrowseItemDataRequest     { bool include_platforms = 3; }   // turns ON the platforms/VR block
 *   CStoreBrowse_GetItems_Response { repeated StoreItem store_items = 1; }
 *   StoreItem                      { uint32 id = 2; repeated uint32 tagids = 21; Categories categories = 22;
 *                                    uint32 appid = 9; Platforms platforms = 32; }
 *   StoreItem.Categories           { repeated uint32 feature_categoryids = 3; }
 *   StoreItem.Platforms            { VRSupport vr_support = 10; }
 *   StoreItem.Platforms.VRSupport  { bool vrhmd = 1; bool vrhmd_only = 2; }
 *
 * VR rule: vrhmd == true  =>  VR-capable. Fallback (returned by default, no flag needed): the store
 * feature-category ids contain 53 ("VR Supported"), 54 ("VR Only") or 31 ("VR Support"). Note these are
 * store CATEGORY ids and only valid against Categories.feature_categoryids — NOT the tagids namespace.
 */
object GetStoreItems {
    const val METHOD = "StoreBrowse.GetItems#1"

    // Store feature-category ids that mark a title as VR (secondary signal to platforms.vr_support.vrhmd).
    private val VR_CATEGORY_IDS = intArrayOf(31, 53, 54)

    /** Steam applies a server-side cap on ids per call; chunk owned libraries at this size. */
    const val MAX_IDS_PER_CALL = 200

    fun request(appIds: List<Int>, language: String = "english", country: String = "US"): ByteArray {
        val w = ProtoWriter()
        for (id in appIds) {
            val item = ProtoWriter().varint(1, id).toByteArray()   // StoreItemID { appid = id }
            w.bytes(1, item)                                        // ids (field 1, repeated)
        }
        val ctx = ProtoWriter().string(1, language).string(3, country).toByteArray()
        w.bytes(2, ctx)                                            // context (field 2)
        val dataReq = ProtoWriter().bool(3, true).toByteArray()   // include_platforms = true
        w.bytes(3, dataReq)                                        // data_request (field 3)
        return w.toByteArray()
    }

    /** Parse the response into appId -> isVr. Only ids present in the response are returned. */
    fun parse(body: ByteArray): Map<Int, Boolean> {
        val out = HashMap<Int, Boolean>()
        val r = ProtoReader(body)
        while (r.hasNext()) {
            val f = r.nextField()
            when (f.number) {
                1 -> {                                             // store_items (repeated StoreItem)
                    val itemBytes = r.readBytes()                  // isolated copy; reader already advanced
                    try {
                        val (id, isVr) = parseStoreItem(itemBytes)
                        if (id != 0) out[id] = isVr
                    } catch (e: Exception) {
                        // Skip one malformed item rather than losing VR flags for the whole batch.
                    }
                }
                else -> r.skip(f.wireType)
            }
        }
        return out
    }

    private fun parseStoreItem(bytes: ByteArray): Pair<Int, Boolean> {
        var appId = 0
        var idFallback = 0
        var isVr = false
        val r = ProtoReader(bytes)
        while (r.hasNext()) {
            val f = r.nextField()
            when (f.number) {
                2 -> idFallback = r.readVarintValue().toInt()                       // id
                9 -> appId = r.readVarintValue().toInt()                            // appid
                22 -> if (parseCategoriesForVr(r.readBytes())) isVr = true          // categories
                32 -> if (parsePlatformsForVr(r.readBytes())) isVr = true           // platforms
                else -> r.skip(f.wireType)
            }
        }
        return Pair(if (appId != 0) appId else idFallback, isVr)
    }

    /** StoreItem.Categories.feature_categoryids (field 3, repeated uint32). */
    private fun parseCategoriesForVr(bytes: ByteArray): Boolean {
        val r = ProtoReader(bytes)
        while (r.hasNext()) {
            val f = r.nextField()
            when (f.number) {
                3 -> if (hasVrId(readRepeatedUint32(r, f.wireType))) return true
                else -> r.skip(f.wireType)
            }
        }
        return false
    }

    /** StoreItem.Platforms.vr_support (field 10, message VRSupport). */
    private fun parsePlatformsForVr(bytes: ByteArray): Boolean {
        val r = ProtoReader(bytes)
        while (r.hasNext()) {
            val f = r.nextField()
            when (f.number) {
                10 -> if (parseVrSupport(r.readBytes())) return true
                else -> r.skip(f.wireType)
            }
        }
        return false
    }

    /** VRSupport.vrhmd (field 1) or vrhmd_only (field 2) => VR-capable. */
    private fun parseVrSupport(bytes: ByteArray): Boolean {
        val r = ProtoReader(bytes)
        while (r.hasNext()) {
            val f = r.nextField()
            when (f.number) {
                1, 2 -> if (r.readVarintValue() != 0L) return true
                else -> r.skip(f.wireType)
            }
        }
        return false
    }

    private fun hasVrId(ids: List<Int>): Boolean = ids.any { it in VR_CATEGORY_IDS }

    /**
     * Read a repeated uint32 that may be packed (wire type 2 — a length-delimited run of varints) or
     * unpacked (wire type 0 — a single value per tag). proto3 defaults repeated scalars to packed, but
     * handle both to be safe.
     */
    private fun readRepeatedUint32(r: ProtoReader, wireType: Int): List<Int> {
        if (wireType != 2) return listOf(r.readVarintValue().toInt())
        val packed = ProtoReader(r.readBytes())
        val list = ArrayList<Int>()
        while (packed.hasNext()) list.add(packed.readVarintValue().toInt())
        return list
    }
}
