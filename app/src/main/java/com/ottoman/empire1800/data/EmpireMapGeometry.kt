package com.ottoman.empire1800.data

data class MapPoint(val x: Float, val y: Float)

data class SubProvinceMapGeometry(
    val key: String,
    val shortLabel: String,
    val polygon: List<MapPoint>,
    val labelPos: MapPoint,
)

val subProvinceMapGeometries: List<SubProvinceMapGeometry> = listOf(
    // الثغور الغربية: الجزائر وتونس
    SubProvinceMapGeometry(
        key = "algeria_algiers",
        shortLabel = "الجزائر",
        polygon = listOf(MapPoint(0.04f, 0.40f), MapPoint(0.15f, 0.39f), MapPoint(0.15f, 0.51f), MapPoint(0.05f, 0.52f)),
        labelPos = MapPoint(0.095f, 0.45f),
    ),
    SubProvinceMapGeometry(
        key = "algeria_constantine",
        shortLabel = "قسنطينة",
        polygon = listOf(MapPoint(0.15f, 0.39f), MapPoint(0.24f, 0.40f), MapPoint(0.24f, 0.53f), MapPoint(0.15f, 0.51f)),
        labelPos = MapPoint(0.195f, 0.46f),
    ),
    SubProvinceMapGeometry(
        key = "tunisia_tunis",
        shortLabel = "تونس",
        polygon = listOf(MapPoint(0.24f, 0.40f), MapPoint(0.32f, 0.41f), MapPoint(0.31f, 0.50f), MapPoint(0.24f, 0.50f)),
        labelPos = MapPoint(0.28f, 0.45f),
    ),
    SubProvinceMapGeometry(
        key = "tunisia_sfax",
        shortLabel = "صفاقس",
        polygon = listOf(MapPoint(0.24f, 0.50f), MapPoint(0.32f, 0.50f), MapPoint(0.33f, 0.61f), MapPoint(0.24f, 0.60f)),
        labelPos = MapPoint(0.285f, 0.555f),
    ),
    // الروملي والبلقان
    SubProvinceMapGeometry(
        key = "rumelia_bosnia",
        shortLabel = "البوسنة",
        polygon = listOf(MapPoint(0.25f, 0.10f), MapPoint(0.34f, 0.11f), MapPoint(0.34f, 0.23f), MapPoint(0.26f, 0.23f)),
        labelPos = MapPoint(0.295f, 0.165f),
    ),
    SubProvinceMapGeometry(
        key = "rumelia_sofia",
        shortLabel = "صوفيا",
        polygon = listOf(MapPoint(0.34f, 0.11f), MapPoint(0.44f, 0.12f), MapPoint(0.43f, 0.24f), MapPoint(0.34f, 0.23f)),
        labelPos = MapPoint(0.388f, 0.175f),
    ),
    SubProvinceMapGeometry(
        key = "rumelia_salonica",
        shortLabel = "سلانيك",
        polygon = listOf(MapPoint(0.31f, 0.24f), MapPoint(0.43f, 0.24f), MapPoint(0.42f, 0.36f), MapPoint(0.32f, 0.36f)),
        labelPos = MapPoint(0.37f, 0.30f),
    ),
    // كريت (ثغر بحري)
    SubProvinceMapGeometry(
        key = "crete_heraklion",
        shortLabel = "كريت",
        polygon = listOf(MapPoint(0.34f, 0.415f), MapPoint(0.44f, 0.415f), MapPoint(0.44f, 0.495f), MapPoint(0.34f, 0.495f)),
        labelPos = MapPoint(0.39f, 0.455f),
    ),
    // الأناضول
    SubProvinceMapGeometry(
        key = "anatolia_istanbul",
        shortLabel = "إسطنبول",
        polygon = listOf(MapPoint(0.44f, 0.19f), MapPoint(0.55f, 0.18f), MapPoint(0.55f, 0.30f), MapPoint(0.45f, 0.30f)),
        labelPos = MapPoint(0.498f, 0.24f),
    ),
    SubProvinceMapGeometry(
        key = "anatolia_izmir",
        shortLabel = "إزمير",
        polygon = listOf(MapPoint(0.45f, 0.30f), MapPoint(0.57f, 0.30f), MapPoint(0.56f, 0.41f), MapPoint(0.46f, 0.41f)),
        labelPos = MapPoint(0.51f, 0.355f),
    ),
    SubProvinceMapGeometry(
        key = "anatolia_trabzon",
        shortLabel = "طرابزون",
        polygon = listOf(MapPoint(0.55f, 0.18f), MapPoint(0.70f, 0.17f), MapPoint(0.69f, 0.31f), MapPoint(0.55f, 0.30f)),
        labelPos = MapPoint(0.625f, 0.24f),
    ),
    // القوقاز (ثغر شرقي)
    SubProvinceMapGeometry(
        key = "caucasus_kars",
        shortLabel = "قارص",
        polygon = listOf(MapPoint(0.70f, 0.14f), MapPoint(0.81f, 0.13f), MapPoint(0.81f, 0.27f), MapPoint(0.70f, 0.27f)),
        labelPos = MapPoint(0.755f, 0.20f),
    ),
    SubProvinceMapGeometry(
        key = "caucasus_baku",
        shortLabel = "باكو",
        polygon = listOf(MapPoint(0.81f, 0.13f), MapPoint(0.94f, 0.13f), MapPoint(0.94f, 0.27f), MapPoint(0.81f, 0.27f)),
        labelPos = MapPoint(0.875f, 0.20f),
    ),
    // الشام
    SubProvinceMapGeometry(
        key = "syria_aleppo",
        shortLabel = "حلب",
        polygon = listOf(MapPoint(0.59f, 0.31f), MapPoint(0.71f, 0.31f), MapPoint(0.71f, 0.42f), MapPoint(0.59f, 0.42f)),
        labelPos = MapPoint(0.65f, 0.365f),
    ),
    SubProvinceMapGeometry(
        key = "syria_beirut",
        shortLabel = "بيروت",
        polygon = listOf(MapPoint(0.57f, 0.42f), MapPoint(0.64f, 0.42f), MapPoint(0.64f, 0.55f), MapPoint(0.57f, 0.54f)),
        labelPos = MapPoint(0.605f, 0.485f),
    ),
    SubProvinceMapGeometry(
        key = "syria_damascus",
        shortLabel = "دمشق",
        polygon = listOf(MapPoint(0.64f, 0.42f), MapPoint(0.73f, 0.42f), MapPoint(0.73f, 0.56f), MapPoint(0.64f, 0.55f)),
        labelPos = MapPoint(0.685f, 0.49f),
    ),
    // العراق
    SubProvinceMapGeometry(
        key = "iraq_mosul",
        shortLabel = "الموصل",
        polygon = listOf(MapPoint(0.71f, 0.28f), MapPoint(0.84f, 0.28f), MapPoint(0.84f, 0.40f), MapPoint(0.71f, 0.40f)),
        labelPos = MapPoint(0.775f, 0.34f),
    ),
    SubProvinceMapGeometry(
        key = "iraq_baghdad",
        shortLabel = "بغداد",
        polygon = listOf(MapPoint(0.73f, 0.40f), MapPoint(0.86f, 0.40f), MapPoint(0.86f, 0.53f), MapPoint(0.73f, 0.53f)),
        labelPos = MapPoint(0.795f, 0.465f),
    ),
    SubProvinceMapGeometry(
        key = "iraq_basra",
        shortLabel = "البصرة",
        polygon = listOf(MapPoint(0.79f, 0.53f), MapPoint(0.92f, 0.53f), MapPoint(0.91f, 0.65f), MapPoint(0.79f, 0.64f)),
        labelPos = MapPoint(0.855f, 0.585f),
    ),
    // مصر
    SubProvinceMapGeometry(
        key = "egypt_alexandria",
        shortLabel = "الإسكندرية",
        polygon = listOf(MapPoint(0.39f, 0.54f), MapPoint(0.49f, 0.54f), MapPoint(0.49f, 0.65f), MapPoint(0.40f, 0.65f)),
        labelPos = MapPoint(0.442f, 0.595f),
    ),
    SubProvinceMapGeometry(
        key = "egypt_cairo",
        shortLabel = "القاهرة",
        polygon = listOf(MapPoint(0.49f, 0.54f), MapPoint(0.57f, 0.55f), MapPoint(0.56f, 0.66f), MapPoint(0.49f, 0.65f)),
        labelPos = MapPoint(0.528f, 0.60f),
    ),
    SubProvinceMapGeometry(
        key = "egypt_upper",
        shortLabel = "الصعيد",
        polygon = listOf(MapPoint(0.43f, 0.65f), MapPoint(0.56f, 0.66f), MapPoint(0.55f, 0.83f), MapPoint(0.43f, 0.82f)),
        labelPos = MapPoint(0.495f, 0.74f),
    ),
    // الحجاز واليمن
    SubProvinceMapGeometry(
        key = "hijaz_mecca",
        shortLabel = "مكة والمدينة",
        polygon = listOf(MapPoint(0.61f, 0.58f), MapPoint(0.73f, 0.58f), MapPoint(0.73f, 0.72f), MapPoint(0.61f, 0.72f)),
        labelPos = MapPoint(0.67f, 0.65f),
    ),
    SubProvinceMapGeometry(
        key = "hijaz_jeddah",
        shortLabel = "جدة",
        polygon = listOf(MapPoint(0.59f, 0.72f), MapPoint(0.69f, 0.72f), MapPoint(0.69f, 0.82f), MapPoint(0.60f, 0.82f)),
        labelPos = MapPoint(0.642f, 0.77f),
    ),
    SubProvinceMapGeometry(
        key = "hijaz_yemen",
        shortLabel = "اليمن",
        polygon = listOf(MapPoint(0.63f, 0.82f), MapPoint(0.77f, 0.82f), MapPoint(0.76f, 0.95f), MapPoint(0.64f, 0.94f)),
        labelPos = MapPoint(0.70f, 0.885f),
    ),
)

fun pointInPolygon(x: Float, y: Float, polygon: List<MapPoint>): Boolean {
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val xi = polygon[i].x
        val yi = polygon[i].y
        val xj = polygon[j].x
        val yj = polygon[j].y
        val intersect = (yi > y) != (yj > y) &&
            x < ((xj - xi) * (y - yi)) / (yj - yi + 1e-6f) + xi
        if (intersect) inside = !inside
        j = i
    }
    return inside
}

fun findSubProvinceAtPoint(normX: Float, normY: Float): String? =
    subProvinceMapGeometries.firstOrNull { pointInPolygon(normX, normY, it.polygon) }?.key
