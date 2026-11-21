package com.enginesense.app.vehicle.pricing

/** Approximate new-car price in India, base to top variant, in rupees. */
data class ExShowroomRange(val baseInr: Double, val topInr: Double) {
    val midInr: Double get() = (baseInr + topInr) / 2
}

/** Which variant of the car the buyer is looking at — picks a point in [ExShowroomRange]. */
enum class Variant(val label: String) { BASE("Base"), MID("Mid"), TOP("Top") }

fun ExShowroomRange.priceFor(variant: Variant): Double = when (variant) {
    Variant.BASE -> baseInr
    Variant.MID -> midInr
    Variant.TOP -> topInr
}

/**
 * Tentative ex-showroom prices (Delhi, base to top variant, last listed price for discontinued
 * models), so the Used Car Check can estimate a price without the buyer looking it up. Like
 * [com.enginesense.app.vehicle.ai.RepairCostReference], these are approximate figures from
 * general knowledge, added at the user's explicit request, and every screen that uses them must
 * say so. They drift with every price revision; refresh them from the makers' sites a few times
 * a year, alongside the dealer pricing data the user is collecting.
 */
object ExShowroomPrices {

    private fun lakh(base: Double, top: Double) = ExShowroomRange(base * 100_000, top * 100_000)

    private val prices: Map<String, Map<String, ExShowroomRange>> = mapOf(
        "Maruti Suzuki" to mapOf(
            "Swift" to lakh(6.5, 9.6), "Baleno" to lakh(6.7, 9.9), "Dzire" to lakh(6.8, 10.2), "WagonR" to lakh(5.6, 7.5),
            "Alto" to lakh(4.2, 6.1), "Celerio" to lakh(5.6, 7.4), "Ertiga" to lakh(8.8, 13.0), "Brezza" to lakh(8.7, 14.1),
            "Ignis" to lakh(5.9, 8.3), "S-Presso" to lakh(4.3, 6.1), "XL6" to lakh(11.6, 14.8), "Fronx" to lakh(7.5, 13.0),
            "Jimny" to lakh(12.8, 15.0), "Grand Vitara" to lakh(11.4, 20.1), "Invicto" to lakh(25.5, 29.2),
            "Omni" to lakh(2.8, 3.0), "Eeco" to lakh(5.3, 6.6), "Gypsy" to lakh(6.2, 6.5), "Zen Estilo" to lakh(3.4, 4.0),
            "A-Star" to lakh(3.9, 4.6), "Ritz" to lakh(4.5, 6.8), "SX4" to lakh(7.5, 10.0), "Ciaz" to lakh(9.4, 12.3),
            "S-Cross" to lakh(8.9, 12.9), "Esteem" to lakh(4.8, 5.5), "Maruti 800" to lakh(2.1, 2.4),
            "Alto K10" to lakh(4.0, 6.0), "Victoris" to lakh(10.5, 19.5), "e Vitara" to lakh(17.5, 25.0)
        ),
        "Hyundai" to mapOf(
            "Grand i10 Nios" to lakh(5.9, 8.6), "i20" to lakh(7.0, 11.2), "Venue" to lakh(7.9, 13.5), "Creta" to lakh(11.1, 20.5),
            "Verna" to lakh(11.1, 17.5), "Alcazar" to lakh(15.0, 21.7), "Exter" to lakh(6.0, 10.5), "Aura" to lakh(6.5, 9.1),
            "Tucson" to lakh(29.0, 36.0), "Ioniq 5" to lakh(46.0, 46.0),
            "Santro" to lakh(4.2, 5.8), "Grand i10" to lakh(5.0, 7.0), "i10" to lakh(4.3, 5.9), "Eon" to lakh(3.3, 4.1),
            "Xcent" to lakh(5.8, 8.1), "Getz" to lakh(4.0, 5.2), "Accent" to lakh(5.5, 7.0), "Elantra" to lakh(17.9, 21.3), "Santa Fe" to lakh(26.0, 30.0),
            "Creta N Line" to lakh(16.9, 20.6), "Venue N Line" to lakh(12.2, 14.0), "i20 N Line" to lakh(10.0, 12.6), "Kona Electric" to lakh(23.8, 24.0), "Sonata" to lakh(19.0, 26.0), "Bayon" to lakh(9.0, 12.0)
        ),
        "Tata" to mapOf(
            "Nexon" to lakh(8.0, 15.6), "Punch" to lakh(6.0, 10.3), "Tiago" to lakh(5.0, 8.5), "Tigor" to lakh(6.0, 9.5),
            "Altroz" to lakh(6.9, 11.3), "Harrier" to lakh(15.0, 26.5), "Safari" to lakh(15.5, 27.0), "Curvv" to lakh(10.0, 19.5),
            "Indica" to lakh(3.5, 5.0), "Indigo" to lakh(4.8, 6.0), "Nano" to lakh(2.4, 3.2), "Zest" to lakh(5.6, 9.3),
            "Bolt" to lakh(5.0, 7.8), "Hexa" to lakh(13.7, 19.3), "Sumo" to lakh(7.4, 8.6),
            "Sierra" to lakh(11.5, 21.0), "Aeris" to lakh(8.0, 12.0)
        ),
        "Mahindra" to mapOf(
            "XUV300" to lakh(7.9, 14.8), "XUV700" to lakh(13.9, 25.9), "Scorpio Classic" to lakh(13.6, 17.4),
            "Scorpio-N" to lakh(13.9, 24.9), "Thar" to lakh(11.5, 17.6), "Bolero" to lakh(9.8, 10.9),
            "XUV400" to lakh(15.5, 17.7), "Marazzo" to lakh(14.4, 16.8),
            "XUV 3XO" to lakh(7.5, 15.5), "Scorpio" to lakh(10.0, 16.5), "XUV500" to lakh(13.0, 17.7), "KUV100" to lakh(5.9, 7.6),
            "TUV300" to lakh(8.5, 10.6), "Xylo" to lakh(9.0, 12.0), "Quanto" to lakh(6.9, 8.0),
            "Thar Roxx" to lakh(12.9, 23.1), "Bolero Neo" to lakh(9.9, 12.2), "Bolero Neo Plus" to lakh(11.4, 12.5), "XUV 3XO EV" to lakh(14.0, 17.0), "BE 6" to lakh(18.9, 26.9), "XEV 9e" to lakh(21.9, 30.5), "XEV 9S" to lakh(19.9, 29.5)
        ),
        "Kia" to mapOf(
            "Seltos" to lakh(11.0, 20.5), "Sonet" to lakh(8.0, 15.7), "Carens" to lakh(10.6, 19.7),
            "Carnival" to lakh(63.9, 63.9), "EV6" to lakh(61.0, 66.0),
            "Syros" to lakh(9.0, 17.8), "Carens Clavis" to lakh(11.1, 21.5), "EV9" to lakh(130.0, 130.0), "Sorento" to lakh(40.0, 50.0)
        ),
        "Honda" to mapOf(
            "City" to lakh(11.9, 16.1), "Amaze" to lakh(8.0, 11.2), "Elevate" to lakh(11.9, 16.7), "WR-V" to lakh(8.5, 11.0), "Jazz" to lakh(7.6, 9.9),
            "Brio" to lakh(4.7, 6.8), "Mobilio" to lakh(7.6, 11.5), "BR-V" to lakh(9.5, 13.0), "Civic" to lakh(17.9, 22.4),
            "Accord" to lakh(39.0, 43.0), "CR-V" to lakh(28.0, 33.0),
            "City e:HEV" to lakh(19.0, 20.6), "ZR-V" to lakh(35.0, 45.0)
        ),
        "Toyota" to mapOf(
            "Innova Crysta" to lakh(19.9, 26.8), "Fortuner" to lakh(33.4, 51.9), "Glanza" to lakh(6.9, 10.0),
            "Urban Cruiser Hyryder" to lakh(11.1, 20.0), "Camry" to lakh(48.0, 48.0), "Rumion" to lakh(10.4, 13.7), "Hilux" to lakh(30.4, 37.9),
            "Innova" to lakh(13.0, 21.0), "Innova Hycross" to lakh(19.8, 31.3), "Etios" to lakh(6.5, 9.0), "Etios Liva" to lakh(5.4, 7.6),
            "Corolla Altis" to lakh(16.5, 20.5),
            "Urban Cruiser Taisor" to lakh(7.7, 13.0), "Urban Cruiser Ebella" to lakh(18.0, 25.0), "Vellfire" to lakh(120.0, 130.0), "Land Cruiser 300" to lakh(210.0, 220.0)
        ),
        "Volkswagen" to mapOf(
            "Polo" to lakh(6.5, 10.0), "Vento" to lakh(9.0, 14.0), "Taigun" to lakh(11.7, 19.8), "Virtus" to lakh(11.6, 19.4), "Tiguan" to lakh(35.2, 38.2),
            "Ameo" to lakh(5.9, 9.9), "Jetta" to lakh(15.0, 21.0), "Passat" to lakh(30.0, 32.0),
            "Golf GTI" to lakh(53.0, 53.0), "Tayron R-Line" to lakh(49.0, 49.0)
        ),
        "Skoda" to mapOf(
            "Slavia" to lakh(10.7, 18.7), "Kushaq" to lakh(10.9, 19.0), "Octavia" to lakh(27.4, 30.5), "Superb" to lakh(54.0, 54.0), "Kodiaq" to lakh(40.0, 45.0),
            "Rapid" to lakh(8.0, 13.7), "Fabia" to lakh(5.5, 8.0), "Laura" to lakh(13.0, 17.0),
            "Kylaq" to lakh(7.9, 14.4)
        ),
        "Renault" to mapOf(
            "Kwid" to lakh(4.7, 6.5), "Triber" to lakh(6.1, 8.9), "Kiger" to lakh(6.1, 11.2),
            "Duster" to lakh(8.5, 14.0), "Pulse" to lakh(4.5, 6.3), "Scala" to lakh(7.0, 9.5), "Lodgy" to lakh(8.6, 12.0)
        ),
        "Nissan" to mapOf(
            "Magnite" to lakh(6.1, 11.8), "Kicks" to lakh(9.5, 14.9), "Micra" to lakh(5.0, 7.5), "Sunny" to lakh(7.0, 10.0), "Terrano" to lakh(10.0, 14.6),
            "X-Trail" to lakh(49.9, 49.9), "Gravite" to lakh(6.5, 9.5), "Tekton" to lakh(10.0, 16.0)
        ),
        "Ford" to mapOf(
            "EcoSport" to lakh(8.2, 11.7), "Figo" to lakh(5.8, 8.4), "Endeavour" to lakh(33.8, 36.3), "Aspire" to lakh(6.4, 8.4),
            "Fiesta" to lakh(8.0, 10.8), "Freestyle" to lakh(5.9, 8.3), "Ikon" to lakh(4.5, 6.5)
        ),
        "Chevrolet" to mapOf(
            "Beat" to lakh(4.2, 5.5), "Spark" to lakh(3.0, 3.8), "Sail" to lakh(5.2, 7.5), "Cruze" to lakh(14.0, 17.0),
            "Enjoy" to lakh(5.9, 7.9), "Tavera" to lakh(8.0, 10.0), "Aveo" to lakh(5.0, 7.0)
        ),
        "MG Motor" to mapOf(
            "Hector" to lakh(14.0, 22.9), "Astor" to lakh(10.0, 18.4), "ZS EV" to lakh(18.9, 26.6), "Comet" to lakh(7.0, 9.8), "Gloster" to lakh(38.8, 43.9),
            "Windsor EV" to lakh(14.0, 18.4), "Hector Tomahawk EV" to lakh(20.0, 28.0), "Hector Tomahawk PHEV" to lakh(22.0, 30.0), "Cyberster" to lakh(75.0, 75.0), "M9" to lakh(70.0, 70.0), "Majestor" to lakh(40.0, 46.0)
        ),
        "Jeep" to mapOf("Compass" to lakh(19.0, 32.4), "Meridian" to lakh(24.9, 38.5), "Wrangler" to lakh(67.7, 71.7), "Grand Cherokee" to lakh(67.5, 69.0)),
        "Citroen" to mapOf("C3" to lakh(6.2, 10.2), "C3 Aircross" to lakh(8.5, 14.6), "eC3" to lakh(12.8, 13.4), "Basalt" to lakh(8.3, 14.1), "C5 Aircross" to lakh(36.9, 39.9)),
        "BYD" to mapOf("Atto 3" to lakh(25.0, 34.0), "Seal" to lakh(41.0, 53.0), "e6" to lakh(29.2, 29.2), "Sealion 7" to lakh(48.9, 54.9), "eMAX 7" to lakh(26.9, 29.9)),
        "Mercedes-Benz" to mapOf(
            "C-Class" to lakh(59.4, 66.0), "E-Class" to lakh(78.5, 92.5), "GLA" to lakh(50.5, 56.9), "GLC" to lakh(76.8, 77.8), "S-Class" to lakh(179.0, 190.0),
            "A-Class" to lakh(45.0, 47.0), "CLA" to lakh(50.0, 55.0), "CLE" to lakh(105.0, 110.0), "GLC Coupe" to lakh(80.0, 85.0), "GLE" to lakh(99.0, 120.0), "GLE Coupe" to lakh(115.0, 120.0), "GLS" to lakh(132.0, 137.0), "G-Class" to lakh(256.0, 280.0), "EQS" to lakh(130.0, 165.0), "Maybach S-Class" to lakh(270.0, 370.0)
        ),
        "BMW" to mapOf("3 Series" to lakh(60.0, 75.0), "5 Series" to lakh(72.9, 72.9), "X1" to lakh(50.6, 52.5), "X3" to lakh(75.8, 77.8), "X5" to lakh(97.0, 110.0), "2 Series Gran Coupe" to lakh(43.9, 46.9), "3 Series LWB" to lakh(62.6, 62.6), "7 Series" to lakh(182.0, 185.0), "X1 LWB" to lakh(50.0, 53.0), "iX1 LWB" to lakh(49.0, 49.0), "X6" to lakh(120.0, 130.0), "X7" to lakh(125.0, 135.0), "XM" to lakh(260.0, 260.0), "M2" to lakh(103.0, 103.0), "M340i" to lakh(74.9, 74.9), "M4" to lakh(150.0, 160.0), "M5" to lakh(199.0, 199.0), "i5" to lakh(115.0, 120.0), "i5 LWB" to lakh(120.0, 125.0), "i7" to lakh(205.0, 260.0), "iX" to lakh(120.0, 140.0)),
        "Audi" to mapOf("A4" to lakh(46.0, 55.0), "A6" to lakh(65.0, 72.0), "Q3" to lakh(44.0, 55.0), "Q5" to lakh(66.0, 73.0), "Q7" to lakh(88.0, 97.0), "Q3 Sportback" to lakh(54.0, 56.0), "Q8" to lakh(117.0, 117.0), "SQ8" to lakh(180.0, 180.0), "RS Q8" to lakh(230.0, 230.0)),
        "Volvo" to mapOf("XC40" to lakh(56.1, 56.1), "XC60" to lakh(68.9, 70.0), "XC90" to lakh(101.0, 101.0), "S90" to lakh(68.3, 68.3), "EX30" to lakh(40.0, 42.0), "EC40" to lakh(59.0, 61.0), "EX90" to lakh(100.0, 110.0)),
        "Tesla" to mapOf("Model Y" to lakh(59.9, 67.9)),
        "VinFast" to mapOf("VF 6" to lakh(16.5, 24.0), "VF 7" to lakh(20.9, 38.0), "VF MPV 7" to lakh(22.0, 27.0)),
        "Land Rover" to mapOf(
            "Range Rover" to lakh(240.0, 400.0), "Range Rover Evoque" to lakh(67.9, 69.5), "Discovery Sport" to lakh(67.9, 72.0), "Defender" to lakh(98.0, 150.0)
        ),
        "Jaguar" to mapOf("XF" to lakh(72.9, 76.0), "F-Pace" to lakh(80.0, 83.0)),
        "Lexus" to mapOf("ES" to lakh(64.0, 69.7), "NX" to lakh(67.4, 72.2), "RX" to lakh(99.0, 118.0)),
        "Isuzu" to mapOf("D-Max" to lakh(10.6, 11.6), "MU-X" to lakh(37.0, 40.7), "V-Cross" to lakh(21.0, 31.5)),
        "Fiat" to mapOf("Punto" to lakh(5.5, 8.5), "Linea" to lakh(8.0, 11.0), "Palio" to lakh(4.0, 5.0)),
        "Mitsubishi" to mapOf("Pajero Sport" to lakh(28.0, 31.5), "Lancer" to lakh(7.0, 9.0), "Outlander" to lakh(25.0, 31.0)),
        "Datsun" to mapOf("GO" to lakh(4.0, 6.5), "GO+" to lakh(4.3, 7.0), "redi-GO" to lakh(2.9, 4.9))
    )

    /** Null for a make or model not in the table — the screen then asks for the price instead. */
    fun lookup(make: String?, model: String?): ExShowroomRange? = prices[make]?.get(model)
}
