package io.github.nottarun7.fitt.catalogue

import io.github.nottarun7.fitt.data.ExerciseChoice
import io.github.nottarun7.fitt.data.TrackingMetric

enum class Equipment(val label: String) {
    MACHINE("Machine"), CABLE("Cable"), SMITH("Smith machine"), BODYWEIGHT("Bodyweight"),
    BAR("Pull-up / dip bar"), RINGS("Rings"), PARALLETTES("Parallettes"), BAND("Resistance band"),
    DUMBBELL("Dumbbell"), BARBELL("Barbell"), KETTLEBELL("Kettlebell"),
}

data class ExerciseDefinition(
    val id: String,
    val name: String,
    val equipment: Equipment,
    val muscles: String,
    val notes: String,
    val metric: TrackingMetric = TrackingMetric.REPS,
    val family: String? = null,
    val step: Int? = null,
    val referenceUrl: String? = null,
    val imageUrl: String? = null,
    val perSide: Boolean = false,
    val assistance: Boolean = false,
) {
    fun choice() = ExerciseChoice(id, name, metric, perSide, assistance)
}

/** Bundled, original metadata. References are external guides, opened only by an explicit tap. */
object ExerciseCatalogue {
    private const val ACE = "https://www.acefitness.org/resources/everyone/exercise-library/"
    private const val HYBRID = "https://www.hybridcalisthenics.com/"

    val all: List<ExerciseDefinition> = buildList {
        fun machine(id: String, name: String, muscles: String, link: String? = null,
                    assistance: Boolean = false, perSide: Boolean = false) {
            add(ExerciseDefinition(id, name, Equipment.MACHINE, muscles,
                "Use the setup and range adjustments for your machine model. Record the load shown on its stack or plates.",
                referenceUrl = link?.let { ACE + it }, assistance = assistance, perSide = perSide))
        }
        machine("machine-chest-press", "Machine chest press", "Chest, triceps, shoulders", "188/seated-chest-press/")
        machine("machine-incline-press", "Incline chest press machine", "Chest, triceps, shoulders")
        machine("machine-decline-press", "Decline chest press machine", "Chest, triceps")
        machine("plate-chest-press", "Plate-loaded chest press", "Chest, triceps, shoulders")
        machine("plate-incline-press", "Plate-loaded incline press", "Chest, triceps, shoulders")
        machine("pec-deck", "Pec deck fly", "Chest")
        machine("reverse-pec-deck", "Reverse pec deck", "Shoulders, upper back")
        machine("machine-shoulder-press", "Machine shoulder press", "Shoulders, triceps")
        machine("plate-shoulder-press", "Plate-loaded shoulder press", "Shoulders, triceps")
        machine("machine-lateral-raise", "Machine lateral raise", "Shoulders", "295/lateral-shoulder-raise/")
        machine("machine-rear-delt", "Rear delt fly machine", "Shoulders, upper back")
        machine("lat-pulldown-wide", "Wide-grip lat pulldown", "Back, biceps", "158/seated-lat-pulldown/")
        machine("lat-pulldown-neutral", "Neutral-grip lat pulldown", "Back, biceps")
        machine("lat-pulldown-underhand", "Underhand lat pulldown", "Back, biceps")
        machine("lat-pulldown-single", "Single-arm lat pulldown", "Back, biceps", perSide = true)
        machine("plate-pulldown", "Plate-loaded pulldown", "Back, biceps")
        machine("machine-row", "Seated row machine", "Back, biceps")
        machine("machine-high-row", "Machine high row", "Back, biceps", "169/seated-high-back-rows/")
        machine("machine-low-row", "Plate-loaded low row", "Back, biceps")
        machine("chest-supported-row", "Chest-supported row machine", "Back, biceps")
        machine("tbar-row-machine", "Chest-supported T-bar row", "Back, biceps")
        machine("machine-pullover", "Pullover machine", "Back, chest")
        machine("assisted-pullup", "Assisted pull-up machine", "Back, biceps", assistance = true)
        machine("assisted-dip", "Assisted dip machine", "Triceps, chest", assistance = true)
        machine("machine-preacher-curl", "Machine preacher curl", "Biceps", "184/seated-biceps-curl/")
        machine("machine-triceps-extension", "Triceps extension machine", "Triceps")
        machine("machine-dip-press", "Seated dip press machine", "Triceps, chest")
        machine("leg-press-45", "45-degree leg press", "Quads, glutes")
        machine("leg-press-horizontal", "Horizontal leg press", "Quads, glutes", "154/seated-leg-press/")
        machine("leg-press-single", "Single-leg press", "Quads, glutes", perSide = true)
        machine("hack-squat", "Hack squat machine", "Quads, glutes")
        machine("pendulum-squat", "Pendulum squat machine", "Quads, glutes")
        machine("belt-squat", "Belt squat machine", "Quads, glutes")
        machine("v-squat", "V-squat machine", "Quads, glutes")
        machine("leg-extension", "Leg extension", "Quads")
        machine("leg-extension-single", "Single-leg extension", "Quads", perSide = true)
        machine("seated-leg-curl", "Seated leg curl", "Hamstrings")
        machine("lying-leg-curl", "Lying leg curl", "Hamstrings", "153/lying-hamstrings-curl/")
        machine("standing-leg-curl", "Standing single-leg curl", "Hamstrings", perSide = true)
        machine("hip-abduction", "Hip abduction machine", "Glutes")
        machine("hip-adduction", "Hip adduction machine", "Adductors, legs")
        machine("glute-kickback-machine", "Glute kickback machine", "Glutes", "293/glute-press/", perSide = true)
        machine("hip-thrust-machine", "Hip thrust machine", "Glutes, hamstrings")
        machine("standing-calf-machine", "Standing calf raise machine", "Calves")
        machine("seated-calf-machine", "Seated calf raise machine", "Calves")
        machine("leg-press-calf", "Leg press calf raise", "Calves", "294/calf-raise/")
        machine("tibialis-machine", "Tibialis raise machine", "Shins, legs")
        machine("ab-crunch-machine", "Seated abdominal crunch machine", "Core", "291/seated-crunch/")
        machine("rotary-torso", "Rotary torso machine", "Core", perSide = true)
        machine("back-extension-machine", "Back extension machine", "Lower back, glutes")
        machine("reverse-hyper", "Reverse hyperextension machine", "Glutes, hamstrings, lower back")

        fun smith(id: String, name: String, muscles: String, perSide: Boolean = false) {
            add(ExerciseDefinition(id, name, Equipment.SMITH, muscles,
                "Guided-bar variation. Note whether your recorded load includes the machine's counterbalanced bar.", perSide = perSide))
        }
        smith("smith-squat", "Smith machine squat", "Quads, glutes")
        smith("smith-front-squat", "Smith machine front squat", "Quads, glutes")
        smith("smith-split-squat", "Smith machine split squat", "Quads, glutes", true)
        smith("smith-reverse-lunge", "Smith machine reverse lunge", "Quads, glutes", true)
        smith("smith-rdl", "Smith machine Romanian deadlift", "Hamstrings, glutes")
        smith("smith-hip-thrust", "Smith machine hip thrust", "Glutes, hamstrings")
        smith("smith-bench", "Smith machine bench press", "Chest, triceps")
        smith("smith-incline-bench", "Smith machine incline press", "Chest, shoulders, triceps")
        smith("smith-shoulder-press", "Smith machine shoulder press", "Shoulders, triceps")
        smith("smith-row", "Smith machine bent-over row", "Back, biceps")
        smith("smith-calf-raise", "Smith machine calf raise", "Calves")

        fun cable(id: String, name: String, muscles: String, perSide: Boolean = false) {
            add(ExerciseDefinition(id, name, Equipment.CABLE, muscles,
                "Cable-station movement. Use the same pulley setup when comparing loads between sessions.", perSide = perSide))
        }
        cable("cable-chest-press", "Standing cable chest press", "Chest, triceps")
        cable("cable-fly-mid", "Mid-height cable fly", "Chest")
        cable("cable-fly-low", "Low-to-high cable fly", "Chest, shoulders")
        cable("cable-fly-high", "High-to-low cable fly", "Chest")
        cable("cable-row", "Seated cable row", "Back, biceps")
        cable("cable-row-single", "Single-arm cable row", "Back, biceps", true)
        cable("cable-high-row", "Cable high row", "Back, shoulders")
        cable("cable-straight-pulldown", "Straight-arm cable pulldown", "Back")
        cable("cable-pulldown-kneeling", "Kneeling cable pulldown", "Back, biceps")
        cable("cable-face-pull", "Cable face pull", "Shoulders, upper back")
        cable("cable-reverse-fly", "Cable reverse fly", "Shoulders, upper back")
        cable("cable-lateral-raise", "Cable lateral raise", "Shoulders", true)
        cable("cable-front-raise", "Cable front raise", "Shoulders", true)
        cable("cable-external-rotation", "Cable external rotation", "Shoulders", true)
        cable("cable-internal-rotation", "Cable internal rotation", "Shoulders", true)
        cable("cable-curl", "Straight-bar cable curl", "Biceps")
        cable("cable-rope-curl", "Rope hammer curl", "Biceps, forearms")
        cable("cable-bayesian-curl", "Behind-body cable curl", "Biceps", true)
        cable("cable-preacher-curl", "Cable preacher curl", "Biceps")
        cable("cable-pushdown", "Bar triceps pushdown", "Triceps")
        cable("cable-rope-pushdown", "Rope triceps pushdown", "Triceps")
        cable("cable-overhead-extension", "Overhead cable triceps extension", "Triceps")
        cable("cable-single-extension", "Single-arm cable triceps extension", "Triceps", true)
        cable("cable-pullthrough", "Cable pull-through", "Glutes, hamstrings")
        cable("cable-kickback", "Cable glute kickback", "Glutes", true)
        cable("cable-hip-abduction", "Cable hip abduction", "Glutes", true)
        cable("cable-hip-adduction", "Cable hip adduction", "Adductors, legs", true)
        cable("cable-crunch", "Kneeling cable crunch", "Core")
        cable("cable-woodchop", "Cable wood chop", "Core", true)
        cable("cable-pallof", "Pallof press", "Core", true)

        fun free(id: String, name: String, equipment: Equipment, muscles: String, perSide: Boolean = false) {
            add(ExerciseDefinition(id, name, equipment, muscles,
                "Record load consistently: for paired dumbbells, use the weight of one dumbbell.", perSide = perSide))
        }
        free("barbell-squat", "Barbell back squat", Equipment.BARBELL, "Quads, glutes, core")
        free("barbell-front-squat", "Barbell front squat", Equipment.BARBELL, "Quads, glutes, core")
        free("barbell-deadlift", "Barbell deadlift", Equipment.BARBELL, "Back, glutes, hamstrings")
        free("barbell-rdl", "Barbell Romanian deadlift", Equipment.BARBELL, "Hamstrings, glutes")
        free("barbell-bench", "Barbell bench press", Equipment.BARBELL, "Chest, triceps")
        free("barbell-incline-bench", "Barbell incline bench press", Equipment.BARBELL, "Chest, shoulders, triceps")
        free("barbell-overhead-press", "Barbell overhead press", Equipment.BARBELL, "Shoulders, triceps")
        free("barbell-row", "Barbell bent-over row", Equipment.BARBELL, "Back, biceps")
        free("barbell-hip-thrust", "Barbell hip thrust", Equipment.BARBELL, "Glutes, hamstrings")
        free("barbell-curl", "Barbell curl", Equipment.BARBELL, "Biceps")
        free("dumbbell-bench", "Dumbbell bench press", Equipment.DUMBBELL, "Chest, triceps")
        free("dumbbell-incline", "Dumbbell incline press", Equipment.DUMBBELL, "Chest, shoulders, triceps")
        free("dumbbell-shoulder-press", "Dumbbell shoulder press", Equipment.DUMBBELL, "Shoulders, triceps")
        free("dumbbell-row", "Single-arm dumbbell row", Equipment.DUMBBELL, "Back, biceps", true)
        free("dumbbell-lateral", "Dumbbell lateral raise", Equipment.DUMBBELL, "Shoulders")
        free("dumbbell-rear-delt", "Dumbbell reverse fly", Equipment.DUMBBELL, "Shoulders, upper back")
        free("dumbbell-curl", "Dumbbell curl", Equipment.DUMBBELL, "Biceps")
        free("dumbbell-hammer", "Dumbbell hammer curl", Equipment.DUMBBELL, "Biceps, forearms")
        free("dumbbell-triceps", "Dumbbell overhead triceps extension", Equipment.DUMBBELL, "Triceps")
        free("dumbbell-goblet", "Goblet squat", Equipment.DUMBBELL, "Quads, glutes")
        free("dumbbell-rdl", "Dumbbell Romanian deadlift", Equipment.DUMBBELL, "Hamstrings, glutes")
        free("dumbbell-lunge", "Dumbbell reverse lunge", Equipment.DUMBBELL, "Quads, glutes", true)
        free("dumbbell-bulgarian", "Dumbbell Bulgarian split squat", Equipment.DUMBBELL, "Quads, glutes", true)
        free("kettlebell-swing", "Kettlebell swing", Equipment.KETTLEBELL, "Glutes, hamstrings, core")

        fun progression(family: String, muscles: String, link: String? = null, block: ProgressionBuilder.() -> Unit) {
            val builder = ProgressionBuilder(family, muscles, link)
            builder.block()
            addAll(builder.entries)
        }
        progression("Push-up", "Chest, triceps, shoulders", HYBRID + "pushups") {
            rep("wall-pushup", "Wall push-up")
            rep("incline-pushup", "Incline push-up")
            rep("knee-pushup", "Knee push-up")
            rep("pushup", "Push-up")
            rep("close-grip-pushup", "Close-grip push-up")
            rep("decline-pushup", "Decline push-up")
            rep("archer-pushup", "Archer push-up", perSide = true)
            rep("assisted-one-arm-pushup", "Assisted one-arm push-up", perSide = true)
            rep("one-arm-pushup", "One-arm push-up", perSide = true)
        }
        progression("Pull-up", "Back, biceps, forearms", HYBRID + "pullups") {
            hold("active-hang", "Active hang", Equipment.BAR)
            rep("scapular-pullup", "Scapular pull-up", Equipment.BAR)
            rep("inverted-row", "Inverted row", Equipment.BAR)
            rep("feet-assisted-pullup", "Feet-assisted pull-up", Equipment.BAR)
            rep("band-assisted-pullup", "Band-assisted pull-up", Equipment.BAND, assistance = true)
            rep("negative-pullup", "Negative pull-up", Equipment.BAR)
            rep("chinup", "Chin-up", Equipment.BAR)
            rep("pullup", "Pull-up", Equipment.BAR)
            rep("chest-to-bar-pullup", "Chest-to-bar pull-up", Equipment.BAR)
            rep("archer-pullup", "Archer pull-up", Equipment.BAR, perSide = true)
            rep("one-arm-pullup", "One-arm pull-up", Equipment.BAR, perSide = true)
        }
        progression("Squat", "Quads, glutes", HYBRID + "squats") {
            rep("sit-to-stand", "Sit-to-stand squat")
            rep("assisted-bodyweight-squat", "Supported bodyweight squat")
            rep("bodyweight-squat", "Bodyweight squat")
            rep("split-squat", "Bodyweight split squat", perSide = true)
            rep("reverse-lunge", "Bodyweight reverse lunge", perSide = true)
            rep("bulgarian-split-squat", "Bodyweight Bulgarian split squat", perSide = true)
            rep("assisted-pistol", "Assisted pistol squat", perSide = true)
            rep("box-pistol", "Box pistol squat", perSide = true)
            rep("pistol-squat", "Pistol squat", perSide = true)
        }
        progression("Dip", "Chest, triceps, shoulders") {
            hold("dip-support", "Parallel-bar support hold", Equipment.BAR)
            rep("feet-assisted-dip", "Feet-assisted parallel-bar dip", Equipment.BAR)
            rep("negative-dip", "Negative dip", Equipment.BAR)
            rep("parallel-dip", "Parallel-bar dip", Equipment.BAR)
            rep("ring-dip", "Ring dip", Equipment.RINGS)
            rep("weighted-dip", "Weighted dip", Equipment.BAR)
        }
        progression("Handstand", "Shoulders, core", "https://gmb.io/handstand/") {
            hold("pike-hold", "Pike support hold")
            rep("wall-walk", "Wall walk")
            hold("wall-handstand", "Chest-to-wall handstand")
            hold("handstand-wall-float", "Handstand wall float")
            hold("freestanding-handstand", "Freestanding handstand")
        }
        progression("Handstand push-up", "Shoulders, triceps") {
            rep("pike-pushup", "Pike push-up")
            rep("elevated-pike-pushup", "Feet-elevated pike push-up")
            rep("negative-wall-hspu", "Negative wall handstand push-up")
            rep("wall-hspu", "Wall handstand push-up")
            rep("freestanding-hspu", "Freestanding handstand push-up")
        }
        progression("L-sit", "Core, hip flexors, triceps", "https://gmb.io/l-sit/") {
            hold("l-sit-foot-support", "Foot-supported L-sit", Equipment.PARALLETTES)
            hold("tuck-l-sit", "Tuck L-sit", Equipment.PARALLETTES)
            hold("one-leg-l-sit", "One-leg L-sit", Equipment.PARALLETTES, perSide = true)
            hold("l-sit", "L-sit", Equipment.PARALLETTES)
            hold("v-sit", "V-sit", Equipment.PARALLETTES)
        }
        progression("Leg raise", "Core, hip flexors", HYBRID + "legraises") {
            rep("lying-knee-raise", "Lying knee raise")
            rep("lying-leg-raise", "Lying leg raise")
            rep("hanging-knee-raise", "Hanging knee raise", Equipment.BAR)
            rep("hanging-leg-raise", "Hanging leg raise", Equipment.BAR)
            rep("toes-to-bar", "Toes-to-bar", Equipment.BAR)
        }
        progression("Front lever", "Back, core", "https://gmb.io/levers/") {
            hold("tuck-front-lever", "Tuck front lever", Equipment.BAR)
            hold("advanced-tuck-front-lever", "Advanced tuck front lever", Equipment.BAR)
            hold("one-leg-front-lever", "One-leg front lever", Equipment.BAR, perSide = true)
            hold("straddle-front-lever", "Straddle front lever", Equipment.BAR)
            hold("front-lever", "Front lever", Equipment.BAR)
        }
        progression("Back lever", "Back, shoulders, core", "https://gmb.io/levers/") {
            hold("tuck-back-lever", "Tuck back lever", Equipment.RINGS)
            hold("advanced-tuck-back-lever", "Advanced tuck back lever", Equipment.RINGS)
            hold("one-leg-back-lever", "One-leg back lever", Equipment.RINGS, perSide = true)
            hold("straddle-back-lever", "Straddle back lever", Equipment.RINGS)
            hold("back-lever", "Back lever", Equipment.RINGS)
        }
        progression("Muscle-up", "Back, chest, triceps, core", "https://gmb.io/muscle-up/") {
            rep("low-ring-transition", "Low-ring muscle-up transition", Equipment.RINGS)
            rep("feet-assisted-ring-muscleup", "Feet-assisted ring muscle-up", Equipment.RINGS)
            rep("band-assisted-ring-muscleup", "Band-assisted ring muscle-up", Equipment.BAND, assistance = true)
            rep("negative-ring-muscleup", "Negative ring muscle-up", Equipment.RINGS)
            rep("strict-ring-muscleup", "Strict ring muscle-up", Equipment.RINGS)
        }
        progression("Planche", "Shoulders, chest, core", "https://gmb.io/planche/") {
            hold("planche-lean", "Planche lean")
            hold("frog-stand", "Frog stand")
            hold("tuck-planche", "Tuck planche", Equipment.PARALLETTES)
            hold("advanced-tuck-planche", "Advanced tuck planche", Equipment.PARALLETTES)
            hold("straddle-planche", "Straddle planche", Equipment.PARALLETTES)
            hold("planche", "Full planche", Equipment.PARALLETTES)
        }
        progression("Bridge", "Glutes, hamstrings, lower back", HYBRID + "bridges") {
            rep("glute-bridge", "Glute bridge")
            rep("single-leg-glute-bridge", "Single-leg glute bridge", perSide = true)
            hold("reverse-plank", "Reverse plank")
            hold("full-bridge-hold", "Full bridge hold")
        }
        add(ExerciseDefinition("plank", "Forearm plank", Equipment.BODYWEIGHT, "Core", "Static trunk support.", TrackingMetric.SECONDS))
        add(ExerciseDefinition("side-plank", "Side plank", Equipment.BODYWEIGHT, "Core", "Record hold time for each side.", TrackingMetric.SECONDS, perSide = true))
        add(ExerciseDefinition("dead-bug", "Dead bug", Equipment.BODYWEIGHT, "Core", "Alternating arm and leg movement from a supine position.", perSide = true))
        add(ExerciseDefinition("nordic-curl", "Nordic hamstring curl", Equipment.BODYWEIGHT, "Hamstrings", "Kneeling eccentric knee-flexion movement with secure ankle support."))
        add(ExerciseDefinition("bodyweight-calf-raise", "Bodyweight calf raise", Equipment.BODYWEIGHT, "Calves", "Standing ankle plantar-flexion movement."))
        add(ExerciseDefinition("bar-muscleup", "Bar muscle-up", Equipment.BAR, "Back, chest, triceps, core", "Pull and transition above a fixed bar before pressing into support."))
    }

    private val byId = all.associateBy { it.id }
    fun find(id: String?) = byId[id]
    val families: List<String> = all.mapNotNull { it.family }.distinct().sorted()
    fun progression(family: String) = all.filter { it.family == family }.sortedBy { it.step }

    fun search(query: String, category: String = "All", muscle: String = "All", family: String = "All"): List<ExerciseDefinition> {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }.map {
            when (it) { "abs" -> "core"; "lats" -> "back"; "pecs" -> "chest"; else -> it }
        }
        return all.filter { entry ->
            val categoryMatches = when (category) {
                "Machines" -> entry.equipment == Equipment.MACHINE || entry.equipment == Equipment.SMITH
                "Cables" -> entry.equipment == Equipment.CABLE
                "Calisthenics" -> entry.family != null || entry.equipment == Equipment.BODYWEIGHT
                "Free weights" -> entry.equipment in setOf(Equipment.BARBELL, Equipment.DUMBBELL, Equipment.KETTLEBELL)
                else -> true
            }
            val haystack = "${entry.name} ${entry.muscles} ${entry.equipment.label} ${entry.family.orEmpty()} ${if (entry.family != null) "calisthenics" else ""}".lowercase()
            val compact = haystack.replace(Regex("[^a-z0-9]"), "")
            categoryMatches && (muscle == "All" || entry.muscles.contains(muscle, ignoreCase = true)) &&
                (family == "All" || entry.family == family) && terms.all { term ->
                    term in haystack || term.replace(Regex("[^a-z0-9]"), "").removeSuffix("s") in compact
                }
        }
    }

    private class ProgressionBuilder(val family: String, val muscles: String, val link: String?) {
        val entries = mutableListOf<ExerciseDefinition>()
        fun rep(id: String, name: String, equipment: Equipment = Equipment.BODYWEIGHT, perSide: Boolean = false, assistance: Boolean = false) =
            add(id, name, equipment, TrackingMetric.REPS, perSide, assistance)
        fun hold(id: String, name: String, equipment: Equipment = Equipment.BODYWEIGHT, perSide: Boolean = false) =
            add(id, name, equipment, TrackingMetric.SECONDS, perSide, false)
        private fun add(id: String, name: String, equipment: Equipment, metric: TrackingMetric, perSide: Boolean, assistance: Boolean) {
            entries += ExerciseDefinition(id, name, equipment, muscles,
                "${if (metric == TrackingMetric.SECONDS) "Timed hold" else "Repetition-based movement"} in the $family family. Choose a variation you can control; progression difficulty can overlap.",
                metric, family, entries.size + 1, link, perSide = perSide, assistance = assistance)
        }
    }
}
