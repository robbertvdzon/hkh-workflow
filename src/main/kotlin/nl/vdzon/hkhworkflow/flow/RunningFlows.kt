package nl.vdzon.hkhworkflow.flow

/** Wie er nu bezig is met nieuwe bestanden. Zo'n stap overleeft een herstart niet, dus de app werkt zich dan niet bij. */
fun interface RunningFlows {
    /** Een regel per doorloop die nu bezig is, bijvoorbeeld "Carla in archief". */
    fun running(): List<String>
}
