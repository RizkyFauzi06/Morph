package com.morph.app.core

import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

data class EpubBookContent(
    val title: String,
    val author: String,
    val chapters: List<EpubChapter>
)

data class EpubChapter(
    val id: String,
    val href: String,
    val text: String
)

object EpubParser {

    private val dbFactory by lazy {
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            try {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            } catch (_: Exception) {}
        }
    }

    fun parseEpubToText(
        epubFile: File,
        onProgress: ((step: String, percent: Int) -> Unit)? = null
    ): String {
        val strings = AppStrings.get()
        val zip = try {
            ZipFile(epubFile)
        } catch (e: Exception) {
            throw IllegalArgumentException(strings.epubErrNotZip(e.message ?: ""))
        }

        zip.use { zipFile ->
            onProgress?.invoke(strings.epubStepMetadata, 20)

            // Step 1: Find OPF package document path from META-INF/container.xml
            val opfPath = findOpfPath(zipFile)
                ?: throw IllegalStateException(strings.epubErrNoContainer)

            val opfEntry = zipFile.getEntry(opfPath)
                ?: throw IllegalStateException(strings.epubErrNoOpf(opfPath))

            val opfBaseDir = if (opfPath.contains('/')) opfPath.substringBeforeLast('/') + "/" else ""

            // Step 2: Parse OPF Document
            onProgress?.invoke(strings.epubStepSpine, 35)
            val opfDoc = zipFile.getInputStream(opfEntry).use { stream ->
                dbFactory.newDocumentBuilder().parse(stream)
            }

            // Extract metadata (Title & Creator)
            var bookTitle = ""
            var bookAuthor = ""
            val titleNodes = opfDoc.getElementsByTagNameNS("*", "title")
            if (titleNodes.length > 0) {
                bookTitle = titleNodes.item(0).textContent?.trim() ?: ""
            }
            val creatorNodes = opfDoc.getElementsByTagNameNS("*", "creator")
            if (creatorNodes.length > 0) {
                bookAuthor = creatorNodes.item(0).textContent?.trim() ?: ""
            }

            // Step 3: Parse Manifest (id -> href)
            val manifestMap = mutableMapOf<String, String>()
            val itemNodes = opfDoc.getElementsByTagNameNS("*", "item")
            for (i in 0 until itemNodes.length) {
                val elem = itemNodes.item(i) as? Element ?: continue
                val id = elem.getAttribute("id")
                val href = elem.getAttribute("href")
                if (id.isNotEmpty() && href.isNotEmpty()) {
                    manifestMap[id] = href
                }
            }

            // Step 4: Parse Spine (reading order based on itemrefs)
            val spineOrder = mutableListOf<String>()
            val spineNodes = opfDoc.getElementsByTagNameNS("*", "itemref")
            for (i in 0 until spineNodes.length) {
                val elem = spineNodes.item(i) as? Element ?: continue
                val idref = elem.getAttribute("idref")
                if (idref.isNotEmpty()) {
                    spineOrder.add(idref)
                }
            }

            if (spineOrder.isEmpty()) {
                throw IllegalStateException(strings.epubErrEmptySpine)
            }

            // Step 5: Read XHTML chapters in exact Spine order
            val sb = StringBuilder()
            if (bookTitle.isNotEmpty()) sb.appendLine("${strings.epubBookTitlePrefix}$bookTitle")
            if (bookAuthor.isNotEmpty()) sb.appendLine("${strings.epubBookAuthorPrefix}$bookAuthor")
            if (bookTitle.isNotEmpty() || bookAuthor.isNotEmpty()) {
                sb.appendLine()
                sb.appendLine("=".repeat(60))
                sb.appendLine()
            }

            val totalItems = spineOrder.size
            var extractedCount = 0

            for ((index, idref) in spineOrder.withIndex()) {
                val rawHref = manifestMap[idref] ?: continue
                val resolvedPath = resolveZipPath(opfBaseDir, rawHref)
                val entry = zipFile.getEntry(resolvedPath)
                    ?: zipFile.getEntry(resolvedPath.replace("%20", " "))
                    ?: continue

                val percent = 40 + ((index + 1).toFloat() / totalItems * 50).toInt()
                onProgress?.invoke(strings.epubStepExtractingChapter(index + 1, totalItems), percent)

                val html = zipFile.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val text = cleanHtmlToPlainText(html)

                if (text.isNotBlank()) {
                    sb.appendLine(text)
                    sb.appendLine()
                    extractedCount++
                }
            }

            if (extractedCount == 0) {
                throw IllegalStateException(strings.epubErrNoChapterText)
            }

            return sb.toString().trim()
        }
    }

    private fun findOpfPath(zip: ZipFile): String? {
        val containerEntry = zip.getEntry("META-INF/container.xml")
            ?: zip.entries().asSequence().firstOrNull { it.name.equals("META-INF/container.xml", ignoreCase = true) }

        if (containerEntry != null) {
            try {
                val doc = zip.getInputStream(containerEntry).use { stream ->
                    dbFactory.newDocumentBuilder().parse(stream)
                }
                val rootfileNodes = doc.getElementsByTagName("rootfile")
                for (i in 0 until rootfileNodes.length) {
                    val elem = rootfileNodes.item(i) as? Element ?: continue
                    val fullPath = elem.getAttribute("full-path")
                    if (fullPath.isNotEmpty()) {
                        return fullPath.trim()
                    }
                }
            } catch (_: Exception) {}
        }

        // Fallback: search for any .opf file directly
        return zip.entries().asSequence()
            .firstOrNull { !it.isDirectory && it.name.endsWith(".opf", ignoreCase = true) }
            ?.name
    }

    private fun resolveZipPath(baseDir: String, href: String): String {
        val decodedHref = try {
            URLDecoder.decode(href, "UTF-8")
        } catch (_: Exception) {
            href
        }
        val combined = if (decodedHref.startsWith("/")) decodedHref.substring(1) else baseDir + decodedHref

        // Normalize relative path segments like ../ and ./
        val parts = combined.split('/')
        val stack = mutableListOf<String>()
        for (part in parts) {
            when (part) {
                "", "." -> {}
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                else -> stack.add(part)
            }
        }
        return stack.joinToString("/")
    }

    fun cleanHtmlToPlainText(html: String): String {
        return html
            // Strip style, script, head
            .replace(Regex("<head[^>]*>[\\s\\S]*?</head>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            // Line break and paragraph separators
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>|</div>|</h1>|</h2>|</h3>|</h4>|</h5>|</h6>|</li>|</tr>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<hr\\s*/?>", RegexOption.IGNORE_CASE), "\n---\n")
            // Remove remaining HTML tags
            .replace(Regex("<[^>]+>"), "")
            // Decode HTML entities
            .let { decodeHtmlEntities(it) }
            // Collapse whitespaces
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("(?m)^ +| +$"), "")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    fun decodeHtmlEntities(input: String): String {
        var text = input
            .replace("&nbsp;", " ", ignoreCase = true)
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&apos;", "'", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)
            .replace("&mdash;", "—", ignoreCase = true)
            .replace("&ndash;", "–", ignoreCase = true)
            .replace("&hellip;", "…", ignoreCase = true)
            .replace("&lsquo;", "‘", ignoreCase = true)
            .replace("&rsquo;", "’", ignoreCase = true)
            .replace("&ldquo;", "“", ignoreCase = true)
            .replace("&rdquo;", "”", ignoreCase = true)
            .replace("&bull;", "•", ignoreCase = true)
            .replace("&copy;", "©", ignoreCase = true)
            .replace("&reg;", "®", ignoreCase = true)

        // Numeric decimal entities: &#1234;
        text = Regex("&#(\\d+);").replace(text) { matchResult ->
            val code = matchResult.groupValues[1].toIntOrNull()
            if (code != null && code in 1..0x10FFFF) {
                Character.toChars(code).concatToString()
            } else {
                matchResult.value
            }
        }

        // Numeric hex entities: &#x1F600;
        text = Regex("&#[xX]([0-9a-fA-F]+);").replace(text) { matchResult ->
            val code = matchResult.groupValues[1].toIntOrNull(16)
            if (code != null && code in 1..0x10FFFF) {
                Character.toChars(code).concatToString()
            } else {
                matchResult.value
            }
        }

        return text
    }
}
