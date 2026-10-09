package nl.vdzon.hkhworkflow.ux.api

import nl.vdzon.hkhworkflow.ux.DemoData
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

/** De schermen uit het UX-ontwerp, met voorbeeldgegevens. */
@Controller
class UxController {

    @GetMapping("/")
    fun toProcess(model: Model): String {
        model.addAttribute("active", "verwerken")
        model.addAttribute("folders", DemoData.folders)
        model.addAttribute("total", DemoData.allFiles.size)
        model.addAttribute("collections", DemoData.collections)
        model.addAttribute("preselected", DemoData.folders.first().files.map { it.id })
        return "verwerken"
    }

    @GetMapping("/batch/controleren")
    fun checkBatch(
        @RequestParam("files", required = false) files: List<String>?,
        @RequestParam("collectie", defaultValue = "archief") collection: String,
        model: Model,
    ): String {
        val selected = DemoData.allFiles.filter { (_, file) -> files.orEmpty().contains(file.id) }
        val first = 1711
        model.addAttribute("active", "verwerken")
        model.addAttribute("rows", selected.mapIndexed { i, (folder, file) ->
            mapOf("path" to "${folder.person}\\${folder.path.substringAfterLast('\\')}\\", "name" to file.name, "record" to (first + i).toString())
        })
        model.addAttribute("collectionLabel", DemoData.collections.firstOrNull { it.first == collection }?.second ?: collection)
        model.addAttribute("range", if (selected.isEmpty()) "–" else "$first t/m ${first + selected.size - 1}")
        model.addAttribute("count", selected.size)
        return "batch-controleren"
    }

    @GetMapping("/batches")
    fun batches(model: Model): String {
        model.addAttribute("active", "batches")
        model.addAttribute("batches", DemoData.batches)
        model.addAttribute("collections", DemoData.collections)
        return "batches"
    }

    @GetMapping("/batches/demo-bezig")
    fun busy(model: Model): String {
        model.addAttribute("active", "batches")
        return "batch-bezig"
    }

    @GetMapping("/batches/demo-klaar")
    fun done(model: Model): String {
        model.addAttribute("active", "batches")
        model.addAttribute("results", DemoData.results)
        model.addAttribute("zcbsBase", DemoData.ZCBS_BASE)
        return "batch-klaar"
    }
}
