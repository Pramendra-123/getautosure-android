package com.enginesense.app.ui.components

import com.enginesense.app.R

/**
 * Car pictures for the Home card, one per make and model (source: the "car images final" Drive
 * folder, kept in brand/car-images-final). Pictures show the maker's own colour, not the user's;
 * the RTO colour is shown as text. A car with no picture here keeps the plain car icon.
 *
 * Names are matched loosely (case, spaces and punctuation ignored), so "Scorpio-N", "Scorpio N"
 * and an RTO brand like "MARUTI SUZUKI INDIA LTD" all find their picture.
 */
object CarImages {

    /** Short brand keys; a brand name matches when its normalised form starts with one of these. */
    private val brandKeys = listOf(
        "maruti", "hyundai", "tata", "mahindra", "kia", "honda", "toyota", "volkswagen", "skoda", "renault",
        "nissan", "mg", "jeep", "citroen", "byd", "mercedes", "bmw", "audi", "volvo", "tesla", "vinfast"
    )

    /** Picker and older model names that are the same car as a differently named picture. */
    private val aliases = mapOf(
        "maruti|alto" to "alto800", "maruti|maruti800" to "800",
        "mahindra|thar" to "tharog", "mahindra|scorpio" to "scorpioclassic",
        "mahindra|xuv300" to "xuv3xo", "mahindra|xuv700" to "xuv7xo", "mahindra|xuv3xoev" to "3xoev", "mahindra|be6" to "be6sporteq",
        "bmw|3serieslwb" to "3lwb",
        "citroen|c3" to "c3x", "citroen|ec3" to "ec3x", "citroen|c3aircross" to "aircrossx",
        "skoda|octavia" to "octaviars"
    )

    private val images: Map<String, Int> = mapOf(
        // Audi
        "audi|a4" to R.drawable.model_audi_a4,
        "audi|a6" to R.drawable.model_audi_a6,
        "audi|q3sportback" to R.drawable.model_audi_q3_sportback,
        "audi|q3" to R.drawable.model_audi_q3,
        "audi|q5" to R.drawable.model_audi_q5,
        "audi|q7" to R.drawable.model_audi_q7,
        "audi|q8" to R.drawable.model_audi_q8,
        "audi|rsq8" to R.drawable.model_audi_rs_q8,
        "audi|sq8" to R.drawable.model_audi_sq8,
        // BMW
        "bmw|2seriesgrancoupe" to R.drawable.model_bmw_2_series_gran_coupe,
        "bmw|3lwb" to R.drawable.model_bmw_3_lwb,
        "bmw|5series" to R.drawable.model_bmw_5_series,
        "bmw|7series" to R.drawable.model_bmw_7_series,
        "bmw|i5" to R.drawable.model_bmw_i5,
        "bmw|m2" to R.drawable.model_bmw_m2,
        "bmw|m340i" to R.drawable.model_bmw_m340i,
        "bmw|m4" to R.drawable.model_bmw_m4,
        "bmw|m5" to R.drawable.model_bmw_m5,
        "bmw|x1lwb" to R.drawable.model_bmw_x1_lwb,
        "bmw|x1" to R.drawable.model_bmw_x1,
        "bmw|x3" to R.drawable.model_bmw_x3,
        "bmw|x5" to R.drawable.model_bmw_x5,
        "bmw|x6" to R.drawable.model_bmw_x6,
        "bmw|x7" to R.drawable.model_bmw_x7,
        "bmw|xm" to R.drawable.model_bmw_xm,
        "bmw|i5lwb" to R.drawable.model_bmw_i5_lwb,
        "bmw|i7" to R.drawable.model_bmw_i7,
        "bmw|ix" to R.drawable.model_bmw_ix,
        "bmw|ix1lwb" to R.drawable.model_bmw_ix1_lwb,
        // BYD
        "byd|atto3" to R.drawable.model_byd_atto_3,
        "byd|seal" to R.drawable.model_byd_seal,
        "byd|sealion7" to R.drawable.model_byd_sealion_7,
        "byd|emax7" to R.drawable.model_byd_emax_7,
        // Citroen
        "citroen|aircrossx" to R.drawable.model_citroen_aircross_x,
        "citroen|basalt" to R.drawable.model_citroen_basalt,
        "citroen|c3x" to R.drawable.model_citroen_c3x,
        "citroen|c5aircross" to R.drawable.model_citroen_c5_aircross,
        "citroen|ec3x" to R.drawable.model_citroen_e_c3_x,
        // Honda
        "honda|amaze" to R.drawable.model_honda_amaze,
        "honda|cityehev" to R.drawable.model_honda_city_ehev,
        "honda|city" to R.drawable.model_honda_city,
        "honda|elevate" to R.drawable.model_honda_elevate,
        "honda|zrv" to R.drawable.model_honda_zr_v,
        // Hyundai
        "hyundai|alcazar" to R.drawable.model_hyundai_alcazar,
        "hyundai|aura" to R.drawable.model_hyundai_aura,
        "hyundai|bayon" to R.drawable.model_hyundai_bayon,
        "hyundai|cretaelectric" to R.drawable.model_hyundai_creta_electric,
        "hyundai|cretanline" to R.drawable.model_hyundai_creta_n_line,
        "hyundai|creta" to R.drawable.model_hyundai_creta,
        "hyundai|elantra" to R.drawable.model_hyundai_elantra,
        "hyundai|eon" to R.drawable.model_hyundai_eon,
        "hyundai|exter" to R.drawable.model_hyundai_exter,
        "hyundai|grandi10nios" to R.drawable.model_hyundai_grand_i10_nios,
        "hyundai|ioniq5" to R.drawable.model_hyundai_ioniq_5,
        "hyundai|konaelectric" to R.drawable.model_hyundai_kona_electric,
        "hyundai|santafe" to R.drawable.model_hyundai_santa_fe,
        "hyundai|santro" to R.drawable.model_hyundai_santro,
        "hyundai|sonata" to R.drawable.model_hyundai_sonata,
        "hyundai|tucson" to R.drawable.model_hyundai_tucson,
        "hyundai|venuenline" to R.drawable.model_hyundai_venue_n_line,
        "hyundai|venue" to R.drawable.model_hyundai_venue,
        "hyundai|verna" to R.drawable.model_hyundai_verna,
        "hyundai|xcent" to R.drawable.model_hyundai_xcent,
        "hyundai|i20nline" to R.drawable.model_hyundai_i20_n_line,
        "hyundai|i20" to R.drawable.model_hyundai_i20,
        // Jeep
        "jeep|compass" to R.drawable.model_jeep_compass,
        "jeep|grandcherokee" to R.drawable.model_jeep_grand_cherokee,
        "jeep|meridian" to R.drawable.model_jeep_meridian,
        "jeep|wrangler" to R.drawable.model_jeep_wrangler,
        // Kia
        "kia|carensclavisev" to R.drawable.model_kia_carens_clavis_ev,
        "kia|carensclavis" to R.drawable.model_kia_carens_clavis,
        "kia|carens" to R.drawable.model_kia_carens,
        "kia|carnival" to R.drawable.model_kia_carnival,
        "kia|ev6" to R.drawable.model_kia_ev6,
        "kia|ev9" to R.drawable.model_kia_ev9,
        "kia|seltos" to R.drawable.model_kia_seltos,
        "kia|sonet" to R.drawable.model_kia_sonet,
        "kia|sorento" to R.drawable.model_kia_sorento,
        "kia|syrosev" to R.drawable.model_kia_syros_ev,
        "kia|syros" to R.drawable.model_kia_syros,
        // MG
        "mg|astor" to R.drawable.model_mg_astor,
        "mg|cometev" to R.drawable.model_mg_comet_ev,
        "mg|cyberster" to R.drawable.model_mg_cyberster,
        "mg|hectortomahawkev" to R.drawable.model_mg_hector_tomahawk_ev,
        "mg|hectortomahawkphev" to R.drawable.model_mg_hector_tomahawk_phev,
        "mg|hector" to R.drawable.model_mg_hector,
        "mg|m9" to R.drawable.model_mg_m9,
        "mg|majestor" to R.drawable.model_mg_majestor,
        "mg|windsorev" to R.drawable.model_mg_windsor_ev,
        "mg|zsev" to R.drawable.model_mg_zs_ev,
        // Mahindra
        "mahindra|3xoev" to R.drawable.model_mahindra_3xo_ev,
        "mahindra|be6sporteq" to R.drawable.model_mahindra_be_6_sporteq,
        "mahindra|boleroneoplus" to R.drawable.model_mahindra_bolero_neo_plus,
        "mahindra|boleroneo" to R.drawable.model_mahindra_bolero_neo,
        "mahindra|bolero" to R.drawable.model_mahindra_bolero,
        "mahindra|scorpioclassic" to R.drawable.model_mahindra_scorpio_classic,
        "mahindra|scorpion" to R.drawable.model_mahindra_scorpio_n,
        "mahindra|tharog" to R.drawable.model_mahindra_thar_og,
        "mahindra|tharroxx" to R.drawable.model_mahindra_thar_roxx,
        "mahindra|xev9s" to R.drawable.model_mahindra_xev_9s,
        "mahindra|xev9e" to R.drawable.model_mahindra_xev_9e,
        "mahindra|xuv3xo" to R.drawable.model_mahindra_xuv_3xo,
        "mahindra|xuv7xo" to R.drawable.model_mahindra_xuv_7xo,
        "mahindra|xuv400" to R.drawable.model_mahindra_xuv400,
        // Maruti
        "maruti|800" to R.drawable.model_maruti_800,
        "maruti|altok10" to R.drawable.model_maruti_alto_k10,
        "maruti|alto800" to R.drawable.model_maruti_alto800,
        "maruti|baleno" to R.drawable.model_maruti_baleno,
        "maruti|brezza" to R.drawable.model_maruti_brezza,
        "maruti|celerio" to R.drawable.model_maruti_celerio,
        "maruti|ciaz" to R.drawable.model_maruti_ciaz,
        "maruti|dzire" to R.drawable.model_maruti_dzire,
        "maruti|eeco" to R.drawable.model_maruti_eeco,
        "maruti|ertiga" to R.drawable.model_maruti_ertiga,
        "maruti|fronx" to R.drawable.model_maruti_fronx,
        "maruti|grandvitara" to R.drawable.model_maruti_grand_vitara,
        "maruti|gypsy" to R.drawable.model_maruti_gypsy,
        "maruti|ignis" to R.drawable.model_maruti_ignis,
        "maruti|invicto" to R.drawable.model_maruti_invicto,
        "maruti|jimny" to R.drawable.model_maruti_jimny,
        "maruti|omni" to R.drawable.model_maruti_omni,
        "maruti|ritz" to R.drawable.model_maruti_ritz,
        "maruti|scross" to R.drawable.model_maruti_s_cross,
        "maruti|spresso" to R.drawable.model_maruti_s_presso,
        "maruti|sx4" to R.drawable.model_maruti_sx4,
        "maruti|swift" to R.drawable.model_maruti_swift,
        "maruti|victoris" to R.drawable.model_maruti_victoris,
        "maruti|wagonr" to R.drawable.model_maruti_wagon_r,
        "maruti|xl6" to R.drawable.model_maruti_xl6,
        "maruti|evitara" to R.drawable.model_maruti_evitara,
        // Mercedes
        "mercedes|aclass" to R.drawable.model_mercedes_a_class,
        "mercedes|cclass" to R.drawable.model_mercedes_c_class,
        "mercedes|cla" to R.drawable.model_mercedes_cla,
        "mercedes|cle" to R.drawable.model_mercedes_cle,
        "mercedes|eclass" to R.drawable.model_mercedes_e_class,
        "mercedes|eqs" to R.drawable.model_mercedes_eqs,
        "mercedes|gclass" to R.drawable.model_mercedes_g_class,
        "mercedes|gla" to R.drawable.model_mercedes_gla,
        "mercedes|glccoupe" to R.drawable.model_mercedes_glc_coupe,
        "mercedes|glc" to R.drawable.model_mercedes_glc,
        "mercedes|glecoupe" to R.drawable.model_mercedes_gle_coupe,
        "mercedes|gle" to R.drawable.model_mercedes_gle,
        "mercedes|gls" to R.drawable.model_mercedes_gls,
        "mercedes|maybachsclass" to R.drawable.model_mercedes_maybach_s_class,
        "mercedes|sclass" to R.drawable.model_mercedes_s_class,
        // Nissan
        "nissan|gravite" to R.drawable.model_nissan_gravite,
        "nissan|magnite" to R.drawable.model_nissan_magnite,
        "nissan|tekton" to R.drawable.model_nissan_tekton,
        "nissan|xtrail" to R.drawable.model_nissan_x_trail,
        // Renault
        "renault|duster" to R.drawable.model_renault_duster,
        "renault|kiger" to R.drawable.model_renault_kiger,
        "renault|kwid" to R.drawable.model_renault_kwid,
        "renault|triber" to R.drawable.model_renault_triber,
        // Skoda
        "skoda|kodiaq" to R.drawable.model_skoda_kodiaq,
        "skoda|kushaq" to R.drawable.model_skoda_kushaq,
        "skoda|kylaq" to R.drawable.model_skoda_kylaq,
        "skoda|octaviars" to R.drawable.model_skoda_octavia_rs,
        "skoda|slavia" to R.drawable.model_skoda_slavia,
        // Tata
        "tata|aeris" to R.drawable.model_tata_aeris,
        "tata|altroz" to R.drawable.model_tata_altroz,
        "tata|curvvev" to R.drawable.model_tata_curvv_ev,
        "tata|curvv" to R.drawable.model_tata_curvv,
        "tata|harrierev" to R.drawable.model_tata_harrier_ev,
        "tata|harrier" to R.drawable.model_tata_harrier,
        "tata|nexonev" to R.drawable.model_tata_nexon_ev,
        "tata|nexon" to R.drawable.model_tata_nexon,
        "tata|punchev" to R.drawable.model_tata_punch_ev,
        "tata|punch" to R.drawable.model_tata_punch,
        "tata|safari" to R.drawable.model_tata_safari,
        "tata|sierraev" to R.drawable.model_tata_sierra_ev,
        "tata|sierra" to R.drawable.model_tata_sierra,
        "tata|tiagoev" to R.drawable.model_tata_tiago_ev,
        "tata|tiago" to R.drawable.model_tata_tiago,
        "tata|tigorev" to R.drawable.model_tata_tigor_ev,
        // Tesla
        "tesla|modely" to R.drawable.model_tesla_model_y,
        // Toyota
        "toyota|camry" to R.drawable.model_toyota_camry,
        "toyota|fortuner" to R.drawable.model_toyota_fortuner,
        "toyota|glanza" to R.drawable.model_toyota_glanza,
        "toyota|hilux" to R.drawable.model_toyota_hilux,
        "toyota|innovacrysta" to R.drawable.model_toyota_innova_crysta,
        "toyota|innovahycross" to R.drawable.model_toyota_innova_hycross,
        "toyota|landcruiser300" to R.drawable.model_toyota_land_cruiser_300,
        "toyota|rumion" to R.drawable.model_toyota_rumion,
        "toyota|urbancruiserebella" to R.drawable.model_toyota_urban_cruiser_ebella,
        "toyota|urbancruiserhyryder" to R.drawable.model_toyota_urban_cruiser_hyryder,
        "toyota|urbancruisertaisor" to R.drawable.model_toyota_urban_cruiser_taisor,
        "toyota|vellfire" to R.drawable.model_toyota_vellfire,
        // Vinfast
        "vinfast|vf6" to R.drawable.model_vinfast_vf_6,
        "vinfast|vf7" to R.drawable.model_vinfast_vf_7,
        "vinfast|vfmpv7" to R.drawable.model_vinfast_vf_mpv_7,
        // Volkswagen
        "volkswagen|golfgti" to R.drawable.model_volkswagen_golf_gti,
        "volkswagen|taigun" to R.drawable.model_volkswagen_taigun,
        "volkswagen|tayronrline" to R.drawable.model_volkswagen_tayron_r_line,
        "volkswagen|tiguan" to R.drawable.model_volkswagen_tiguan,
        "volkswagen|virtus" to R.drawable.model_volkswagen_virtus,
        // Volvo
        "volvo|ec40" to R.drawable.model_volvo_ec40,
        "volvo|ex30" to R.drawable.model_volvo_ex30,
        "volvo|ex90" to R.drawable.model_volvo_ex90,
        "volvo|xc60" to R.drawable.model_volvo_xc60,
        "volvo|xc90" to R.drawable.model_volvo_xc90
    )

    /** The picture for this car, or null to fall back to the icon. An electric car uses its EV picture when there is one. */
    fun forCar(brand: String?, model: String?, fuelType: String?): Int? {
        if (brand.isNullOrBlank() || model.isNullOrBlank()) return null
        val brandKey = normalise(brand).let { b -> brandKeys.firstOrNull { b.startsWith(it) } } ?: return null
        val m = normalise(model).let { aliases["$brandKey|$it"] ?: it }
        val tries = if (fuelType == "Electric") listOf(m + "ev", m + "electric", m) else listOf(m)
        return tries.firstNotNullOfOrNull { images["$brandKey|$it"] }
            // An RTO model often carries the variant ("NEXON XZ PLUS"): fall back to the longest model name it starts with.
            ?: images.entries
                .filter { (key, _) -> key.startsWith("$brandKey|") && key.substringAfter('|').let { it.length >= 3 && m.startsWith(it) } }
                .maxByOrNull { it.key.length }?.value
    }

    internal fun all(): Set<Int> = images.values.toSet()

    private fun normalise(s: String) = s.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }
}
