package nz.afhome.ledger.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn, wizarding-world-inspired icons (original artwork, 24×24 grid).
 * Each string is one shape; sub-paths inside a shape cut holes (even-odd fill).
 * Icons are tinted by Compose like any Material icon.
 */
object Magic {
    private fun icon(name: String, vararg shapes: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            shapes.forEach { addPath(addPathNodes(it), fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) }
        }.build()

    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},${cy}a$r,$r 0 1 0 ${2 * r},0a$r,$r 0 1 0 ${-2 * r},0Z"

    /** Home: a castle with three towers. */
    val Castle = icon(
        "Castle",
        "M3,21V9H2.5L5,4l2.5,5H7v4h2V7H8.5L12,1.5L15.5,7H15v6h2V9h-0.5L19,4l2.5,5H21v12Z" +
            "M10.5,21v-3.5a1.5,1.5 0 0 1 3,0V21Z M4.5,12h1v2h-1Z M18.5,12h1v2h-1Z M11.5,9h1v2h-1Z",
    )

    /** Stock: a school trunk with straps and a keyhole. */
    val Trunk = icon(
        "Trunk",
        "M3,10a3,3 0 0 1 3,-3h12a3,3 0 0 1 3,3v1H3Z",
        "M3,12h18v7a1,1 0 0 1 -1,1H4a1,1 0 0 1 -1,-1Z M6,12h0.8v8H6Z M17.2,12h0.8v8h-0.8Z M11.25,15a0.75,0.75 0 1 1 1.5,0v1.5h-1.5Z",
    )

    /** Shopping list: a parchment scroll. */
    val Scroll = icon(
        "Scroll",
        "M5.5,3h13a1.75,1.75 0 0 1 0,3.5h-13a1.75,1.75 0 0 1 0,-3.5Z",
        "M6,6.5h12v11H6Z M8,9h8v1H8Z M8,11.5h8v1H8Z M8,14h5v1H8Z",
        "M5.5,17.5h13a1.75,1.75 0 0 1 0,3.5h-13a1.75,1.75 0 0 1 0,-3.5Z",
    )

    /** Insights: a stack of Galleons and one big coin. */
    val Galleons = icon(
        "Galleons",
        "M3.2,18.6h6.6a1.2,1.2 0 0 1 0,2.4H3.2a1.2,1.2 0 0 1 0,-2.4Z",
        "M3.2,15.8h6.6a1.2,1.2 0 0 1 0,2.4H3.2a1.2,1.2 0 0 1 0,-2.4Z",
        "M3.2,13h6.6a1.2,1.2 0 0 1 0,2.4H3.2a1.2,1.2 0 0 1 0,-2.4Z",
        circle(16.5f, 10.5f, 5.5f) + circle(16.5f, 10.5f, 4.3f) + circle(16.5f, 10.5f, 3.2f),
    )

    /** Ask: an owl. */
    val Owl = icon(
        "Owl",
        "M5,3.5l3,2.8a8,8 0 0 1 8,0L19,3.5l-0.5,5.3a7.5,7.5 0 0 1 0.5,3.2c0,5 -3,9 -7,9s-7,-4 -7,-9a7.5,7.5 0 0 1 0.5,-3.2Z" +
            circle(9.2f, 11f, 2.4f) + circle(9.2f, 11f, 1f) + circle(14.8f, 11f, 2.4f) + circle(14.8f, 11f, 1f) +
            "M11.1,14h1.8L12,16Z",
    )

    /** Scan: a wand with sparkles ("Accio!"). */
    val Wand = icon(
        "Wand",
        "M4.2,18.4L15.6,7l1.4,1.4L5.6,19.8Z",
        "M2.4,18.8l1.4,-1.4l2.8,2.8l-1.4,1.4Z",
        "M19,1.5l0.7,1.8l1.8,0.7l-1.8,0.7L19,6.5l-0.7,-1.8l-1.8,-0.7l1.8,-0.7Z",
        "M21.5,9l0.4,1l1,0.4l-1,0.4l-0.4,1l-0.4,-1l-1,-0.4l1,-0.4Z",
        "M13.5,2l0.4,1l1,0.4l-1,0.4l-0.4,1l-0.4,-1l-1,-0.4l1,-0.4Z",
    )

    /** Settings: a wizard's hat. */
    val Hat = icon(
        "Hat",
        "M2.5,19.2c2.2,-1.1 5,-1.6 9.5,-1.6s7.3,0.5 9.5,1.6c-1.2,1.4 -5,2.1 -9.5,2.1s-8.3,-0.7 -9.5,-2.1Z",
        "M6.8,17.4C8,12.5 9.8,7.5 13.4,2.8c0.4,2 1.6,3.2 3.4,3.6c-1.6,1.2 -2.2,3.2 -1.6,5.8l1.8,5.2C14.5,18 9.6,18 6.8,17.4Z" +
            "M7.4,15.4c3.1,0.6 6.6,0.6 9.2,0l0.4,1.2c-3,0.6 -6.7,0.6 -9.9,0Z",
    )

    /** Backup: a vault key. */
    val VaultKey = icon(
        "VaultKey",
        circle(7f, 12f, 4.5f) + circle(7f, 12f, 2f),
        "M11.5,11h9.5v2h-9.5Z M17,13h1.6v3H17Z M19.4,13H21v2h-1.6Z",
    )

    /** Car: a car with a wing, because ours obviously flies. */
    val FlyingCar = icon(
        "FlyingCar",
        "M2.5,16v-3.2L5.3,8.5h9.2l3.2,4.1h1.8a2,2 0 0 1 2,2V16Z M6.3,12.3l1.7,-2.5h2.9v2.5Z M12.3,9.8h1.9l1.9,2.5h-3.8Z",
        circle(7f, 16.6f, 1.9f),
        circle(17f, 16.6f, 1.9f),
        "M10.5,8.4C7.5,4.6 3.5,3.4 1.2,4.3c2,0.9 3.1,2 3.6,3.3c-1.5,0 -2.6,0.4 -3.2,0.8Z",
    )

    /** Packed lunch: a bubbling cauldron. */
    val Cauldron = icon(
        "Cauldron",
        "M3,7.2h18v1.8H3Z",
        "M4.2,9.3h15.6v1.2a7.8,7.8 0 0 1 -2.9,6.1l1,2.9h-2l-0.8,-2.1A7.8,7.8 0 0 1 8.9,17.4L8.1,19.5h-2l1,-2.9A7.8,7.8 0 0 1 4.2,10.5Z",
        circle(10f, 4.8f, 1.1f), circle(13.4f, 3.2f, 0.8f), circle(15f, 5.4f, 0.6f),
    )

    /** Subscriptions: a Time-Turner, for payments that keep coming round. */
    val TimeTurner = icon(
        "TimeTurner",
        circle(12f, 13.2f, 9.3f) + circle(12f, 13.2f, 8f),
        circle(12f, 2.4f, 1.4f) + circle(12f, 2.4f, 0.6f),
        "M8.6,7.2h6.8v1.1c0,1.7 -1.2,3.2 -2.5,4.9c1.3,1.7 2.5,3.2 2.5,4.9v1.1H8.6v-1.1c0,-1.7 1.2,-3.2 2.5,-4.9c-1.3,-1.7 -2.5,-3.2 -2.5,-4.9Z" +
            "M9.9,8.4h4.2c-0.2,1.1 -1,2.2 -2.1,3.5c-1.1,-1.3 -1.9,-2.4 -2.1,-3.5Z",
    )

    // ---- The five Horcrux objects, each used once as a section mark ----

    /** Receipts: the diary. */
    val Diary = icon(
        "Diary",
        "M6.5,2.5H18a1,1 0 0 1 1,1v17a1,1 0 0 1 -1,1H6.5a2.5,2.5 0 0 1 -2.5,-2.5v-14a2.5,2.5 0 0 1 2.5,-2.5Z" +
            "M6.8,3.5h0.7v17h-0.7Z M10,7h6v1.2h-6Z",
    )

    /** Budgets: the locket. */
    val Locket = icon(
        "Locket",
        "M12,9.2L7.2,3h1.4L12,7.4L15.4,3h1.4Z",
        "M11.1,8.4h1.8v1.6h-1.8Z",
        circle(12f, 15.2f, 5.8f) + circle(12f, 15.2f, 4.6f) + circle(12f, 15.2f, 3f),
    )

    /** Gifts: the cup. */
    val Cup = icon(
        "Cup",
        "M7,3h10v5a5,5 0 0 1 -4,4.9V17h3v2H8v-2h3v-4.1A5,5 0 0 1 7,8Z",
        "M7,4.5H5a1,1 0 0 0 -1,1V7a3,3 0 0 0 3,3V8.5A1.5,1.5 0 0 1 5.5,7V6H7Z",
        "M17,4.5h2a1,1 0 0 1 1,1V7a3,3 0 0 1 -3,3V8.5A1.5,1.5 0 0 0 18.5,7V6H17Z",
        "M6,20h12v1.5H6Z",
    )

    /** "What stands out": the diadem, for wisdom. */
    val Diadem = icon(
        "Diadem",
        "M2,17L4,9l4,4l4,-8l4,8l4,-4l2,8Z" + circle(12f, 13f, 1.3f),
        "M2.5,18h19v2h-19Z",
    )

    /** "Paid from": the ring. */
    val Ring = icon(
        "Ring",
        circle(12f, 14.5f, 6.5f) + circle(12f, 14.5f, 4.8f),
        "M9.3,7.4L12,3.6l2.7,3.8L12,8.6Z",
    )
}
