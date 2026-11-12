package com.morph.app

import com.morph.app.core.EpubParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testEpubSpineOrderExtractionNotAlphabetical() {
        val epubFile = tempFolder.newFile("test_book.epub")

        val containerXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/package.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
        """.trimIndent()

        val opfContent = """
            <?xml version="1.0" encoding="utf-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>Test Spine Title</dc:title>
                    <dc:creator>Test Author</dc:creator>
                </metadata>
                <manifest>
                    <item id="item_z" href="text/z_first_chapter.xhtml" media-type="application/xhtml+xml"/>
                    <item id="item_a" href="text/a_second_chapter.xhtml" media-type="application/xhtml+xml"/>
                    <item id="item_m" href="text/m_third_chapter.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine>
                    <itemref idref="item_z"/>
                    <itemref idref="item_a"/>
                    <itemref idref="item_m"/>
                </spine>
            </package>
        """.trimIndent()

        val chapterZ = """
            <?xml version="1.0" encoding="utf-8"?>
            <html>
                <body>
                    <h1>BAB 1: Pembukaan</h1>
                    <p>Ini adalah bab pertama di urutan spine &mdash; walaupun namanya z_first.</p>
                </body>
            </html>
        """.trimIndent()

        val chapterA = """
            <?xml version="1.0" encoding="utf-8"?>
            <html>
                <body>
                    <h1>BAB 2: Pertengahan</h1>
                    <p>&ldquo;Ini adalah bab kedua,&rdquo; kata pengarang &amp; pembaca.</p>
                </body>
            </html>
        """.trimIndent()

        val chapterM = """
            <?xml version="1.0" encoding="utf-8"?>
            <html>
                <body>
                    <h1>BAB 3: Penutup</h1>
                    <p>Ini adalah bab ketiga dan terakhir. Kode Unicode &#8212; selesai.</p>
                </body>
            </html>
        """.trimIndent()

        // Create EPUB ZIP container
        ZipOutputStream(FileOutputStream(epubFile)).use { zos ->
            // Mimetype
            zos.putNextEntry(ZipEntry("mimetype"))
            zos.write("application/epub+zip".toByteArray(Charsets.US_ASCII))
            zos.closeEntry()

            // container.xml
            zos.putNextEntry(ZipEntry("META-INF/container.xml"))
            zos.write(containerXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // package.opf
            zos.putNextEntry(ZipEntry("OEBPS/package.opf"))
            zos.write(opfContent.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Chapters in reverse alphabetical entry order to prove extraction follows Spine
            zos.putNextEntry(ZipEntry("OEBPS/text/z_first_chapter.xhtml"))
            zos.write(chapterZ.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/text/m_third_chapter.xhtml"))
            zos.write(chapterM.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("OEBPS/text/a_second_chapter.xhtml"))
            zos.write(chapterA.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val extractedText = EpubParser.parseEpubToText(epubFile)

        assertNotNull(extractedText)
        assertTrue(extractedText.contains("JUDUL: Test Spine Title"))
        assertTrue(extractedText.contains("PENULIS: Test Author"))

        // Check Spine sequence: BAB 1 must appear before BAB 2, and BAB 2 before BAB 3
        val idx1 = extractedText.indexOf("BAB 1: Pembukaan")
        val idx2 = extractedText.indexOf("BAB 2: Pertengahan")
        val idx3 = extractedText.indexOf("BAB 3: Penutup")

        assertTrue("Bab 1 found", idx1 != -1)
        assertTrue("Bab 2 found", idx2 != -1)
        assertTrue("Bab 3 found", idx3 != -1)

        assertTrue("Bab 1 is before Bab 2", idx1 < idx2)
        assertTrue("Bab 2 is before Bab 3", idx2 < idx3)

        // Check HTML entities decoded
        assertTrue("Em-dash entity decoded", extractedText.contains("—"))
        assertTrue("Quotes decoded", extractedText.contains("“Ini adalah bab kedua,”"))
        assertTrue("Ampersand decoded", extractedText.contains("& pembaca"))
    }

    @Test(expected = Exception::class)
    fun testCorruptEpubThrowsException() {
        val corruptFile = tempFolder.newFile("corrupt.epub")
        corruptFile.writeText("Bukan zip file")
        EpubParser.parseEpubToText(corruptFile)
    }

    @Test
    fun testHtmlCleaningAndEntityDecoding() {
        val html = "<p>Halo &amp; selamat datang!&nbsp;&ldquo;Morph&rdquo;&mdash;&#8212;</p>"
        val plain = EpubParser.cleanHtmlToPlainText(html)
        assertEquals("Halo & selamat datang! “Morph”——", plain)
    }
}
