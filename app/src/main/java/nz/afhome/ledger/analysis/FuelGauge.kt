package nz.afhome.ledger.analysis

import nz.afhome.ledger.data.FuelLog
import nz.afhome.ledger.data.Vehicle

/** Turns the dashboard's fuel bars into litres, driving range and the cost to fill up. */
object FuelGauge {

    data class Estimate(
        val bars: Int,
        val litresLeft: Double,
        val rangeKm: Int,
        val fillLitres: Double,
        val fillCost: Double,
        val pricePerL: Double,
        val lp100: Double,
        /** True when fuel use is measured from your own fill-ups rather than a typical figure. */
        val measured: Boolean,
    ) {
        val low: Boolean get() = rangeKm < 80
    }

    /** Typical real-world use when no fill-ups with odometer readings exist yet. */
    fun typicalLp100(v: Vehicle): Double = when {
        v.name.contains("civic", true) -> 7.6   // 2008 Civic 1.8L: Honda-rated 6.9, owners report ~7.6
        v.fuelType.contains("hybrid", true) -> 4.5
        else -> 8.5
    }

    /** L/100km from consecutive fill-ups with odometer readings (full-tank method), or null. */
    fun measuredLp100(v: Vehicle, fuel: List<FuelLog>): Double? {
        val fills = fuel.filter { (it.vehicleId == null || it.vehicleId == v.id) && it.odometer != null }.sortedBy { it.odometer }
        if (fills.size < 2) return null
        val km = (fills.last().odometer!! - fills.first().odometer!!).toDouble()
        return if (km > 50) fills.drop(1).sumOf { it.litres } / km * 100 else null
    }

    fun estimate(v: Vehicle, fuel: List<FuelLog>): Estimate? {
        val bars = v.fuelBars ?: return null
        val full = v.gaugeBars.coerceAtLeast(1)
        val litres = v.tankLitres * bars.coerceIn(0, full) / full
        val measured = measuredLp100(v, fuel)
        val lp100 = measured ?: typicalLp100(v)
        val price = fuel.filter { it.pricePerL > 0 }.maxByOrNull { it.date }?.pricePerL ?: Auckland.PETROL_TYPICAL
        val fill = v.tankLitres - litres
        return Estimate(bars, litres, (litres / lp100 * 100).toInt(), fill, fill * price, price, lp100, measured != null)
    }
}
