package com.dopdop.bopdop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LauncherArtTest {
    @Test fun launcherPickerXmlLayersAreSupportedByComposePainterResource() {
        // The picker paints the adaptive icon's individual layers. Android shape/color
        // drawables work in the launcher but painterResource cannot load them in Settings.
        val resources = listOf(File("src/main/res/drawable"), File("app/src/main/res/drawable")).first { it.isDirectory }
        val layers = resources.listFiles()!!.filter {
            it.extension == "xml" && (it.name.startsWith("ic_launcher_bg") || it.name.startsWith("ic_launcher_fg"))
        }
        assertTrue("Launcher preview layers must be present", layers.isNotEmpty())
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        layers.forEach { layer ->
            assertEquals("${layer.name} must be a vector for the Compose icon picker", "vector", builder.parse(layer).documentElement.tagName)
        }
    }
}
