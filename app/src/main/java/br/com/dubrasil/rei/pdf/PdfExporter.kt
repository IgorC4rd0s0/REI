package br.com.dubrasil.rei.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import br.com.dubrasil.rei.R
import br.com.dubrasil.rei.model.ChecklistGroup
import br.com.dubrasil.rei.model.ReportData
import br.com.dubrasil.rei.model.ReportSchema
import kotlin.math.min

object PdfExporter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    fun write(context: Context, uri: Uri, data: ReportData) {
        val document = PdfDocument()
        val writer = FormWriter(document, context, data)

        writer.section("MÓDULOS CONTRATADOS")
        writer.checklist(ReportSchema.contractedModules, columns = 3) { ReportSchema.contractedKey(it) }
        writer.space(7f)
        writer.infoTable(listOf(
            "Cliente / Projeto" to data.field("cliente"),
            "Consultor de implantação" to data.field("consultor"),
            "Usuários cadastrados no TGA" to data.field("usuariosTga"),
            "Início" to data.field("inicio"),
            "Término" to data.field("termino"),
            "Dias contratados" to data.field("diasContratados"),
            "Dias utilizados" to data.field("diasUtilizados")
        ))
        writer.supportBox()

        writer.section("PREENCHIMENTO TÉCNICO")
        writer.groups("tecnico", ReportSchema.technical)
        writer.infoTable(listOf(
            "Tipo do certificado digital" to data.field("tipoCertificado"),
            "Quantidade de usuários no Workflow" to data.field("qtdWorkflow")
        ))
        writer.paragraphBox("Observações técnicas", data.field("observacoesTecnicas"), 54f)

        writer.section("MÓDULO ESTOQUE")
        writer.groups("estoque", ReportSchema.stock)

        writer.section("MÓDULO FINANCEIRO")
        writer.groups("financeiro", ReportSchema.finance)

        writer.section("MÓDULO FISCAL E RELATÓRIOS")
        writer.groups("fiscal", ReportSchema.fiscalReports)

        writer.section("ENTREGA DA IMPLANTAÇÃO", reserveAfter = 90f)
        writer.paragraphBox("Descritivo dos serviços executados", data.field("servicosExecutados"), 74f)
        writer.statusBox(data.deliveryStatus)
        writer.paragraphBox("Pendências pós-implantação", data.field("pendencias"), 66f)
        writer.signatureBlock(
            data.field("assinaturaAnalistaImagem"),
            data.field("assinaturaClienteImagem")
        )

        if (data.attachments.isNotEmpty()) {
            writer.section("EVIDÊNCIAS E ANEXOS", reserveAfter = 70f)
            data.attachments.forEachIndexed { index, attachment ->
                if (attachment.mimeType.startsWith("image/")) {
                    writer.evidence("Evidência ${index + 1} • ${attachment.name}", Uri.parse(attachment.uri))
                } else {
                    writer.fileReference(attachment.name)
                }
            }
        }

        writer.finish()
        context.contentResolver.openOutputStream(uri)?.use(document::writeTo)
        document.close()
    }

    private class FormWriter(
        private val document: PdfDocument,
        private val context: Context,
        private val data: ReportData
    ) {
        private val left = 34f
        private val right = PAGE_WIDTH - 34f
        private val contentWidth = right - left
        private val navy = Color.rgb(38, 58, 122)
        private val navyDark = Color.rgb(24, 38, 83)
        private val green = Color.rgb(88, 173, 69)
        private val ink = Color.rgb(38, 44, 58)
        private val mutedColor = Color.rgb(105, 113, 132)
        private val borderColor = Color.rgb(207, 213, 225)
        private val softBlue = Color.rgb(237, 241, 252)
        private val softGray = Color.rgb(247, 248, 251)

        private val body = paint(9.2f, ink)
        private val bodyBold = paint(9.2f, ink, true)
        private val small = paint(7.5f, mutedColor)
        private val sectionText = paint(10.5f, Color.WHITE, true)
        private val groupText = paint(9f, navy, true)
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        private val logo = BitmapFactory.decodeResource(context.resources, R.drawable.word_logo)
        private var pageNumber = 0
        private lateinit var page: PdfDocument.Page
        private var y = 0f

        init { newPage() }

        fun section(title: String, reserveAfter: Float = 0f) {
            ensure(34f + reserveAfter)
            y += 7f
            val rect = RectF(left, y, right, y + 25f)
            page.canvas.drawRoundRect(rect, 5f, 5f, Paint().apply { color = navy })
            page.canvas.drawRect(left, y + 12f, right, y + 25f, Paint().apply { color = navy })
            page.canvas.drawText(title, left + 10f, y + 17f, sectionText)
            y += 31f
        }

        fun groups(scope: String, groups: List<ChecklistGroup>) {
            groups.forEach { group ->
                subsection(group.title)
                checklist(group.items, columns = 2) { ReportSchema.key(scope, group.title, it) }
                space(4f)
            }
        }

        fun checklist(items: List<String>, columns: Int, keyFor: (String) -> String) {
            val gap = 0f
            val cellWidth = (contentWidth - gap * (columns - 1)) / columns
            items.chunked(columns).forEachIndexed { rowIndex, row ->
                val lineSets = row.map { wrap(it, body, cellWidth - 24f) }
                val rowHeight = maxOf(22f, (lineSets.maxOfOrNull { it.size } ?: 1) * 11f + 8f)
                ensure(rowHeight)
                row.forEachIndexed { column, item ->
                    val x = left + column * (cellWidth + gap)
                    val background = if (rowIndex % 2 == 0) Color.WHITE else softGray
                    page.canvas.drawRect(x, y, x + cellWidth, y + rowHeight, Paint().apply { color = background })
                    page.canvas.drawRect(x, y, x + cellWidth, y + rowHeight, linePaint)
                    drawCheckbox(x + 7f, y + 7f, keyFor(item) in data.checks)
                    lineSets[column].forEachIndexed { lineIndex, line ->
                        page.canvas.drawText(line, x + 22f, y + 13f + lineIndex * 11f, body)
                    }
                }
                y += rowHeight
            }
        }

        fun infoTable(fields: List<Pair<String, String>>) {
            fields.chunked(2).forEachIndexed { rowIndex, row ->
                val cellWidth = contentWidth / 2f
                val valueLines = row.map { wrap(it.second.ifBlank { "—" }, bodyBold, cellWidth - 18f) }
                val height = maxOf(38f, (valueLines.maxOfOrNull { it.size } ?: 1) * 11f + 23f)
                ensure(height)
                row.forEachIndexed { column, field ->
                    val x = left + column * cellWidth
                    page.canvas.drawRect(x, y, x + cellWidth, y + height, Paint().apply { color = if (rowIndex % 2 == 0) Color.WHITE else softGray })
                    page.canvas.drawRect(x, y, x + cellWidth, y + height, linePaint)
                    page.canvas.drawText(field.first.uppercase(), x + 8f, y + 11f, small)
                    valueLines[column].forEachIndexed { index, line ->
                        page.canvas.drawText(line, x + 8f, y + 25f + index * 11f, bodyBold)
                    }
                }
                y += height
            }
            space(7f)
        }

        fun supportBox() {
            ensure(45f)
            val rect = RectF(left, y, right, y + 39f)
            page.canvas.drawRoundRect(rect, 6f, 6f, Paint().apply { color = Color.rgb(238, 247, 235) })
            page.canvas.drawRoundRect(rect, 6f, 6f, Paint().apply {
                color = Color.rgb(194, 225, 186); style = Paint.Style.STROKE; strokeWidth = 0.9f
            })
            page.canvas.drawCircle(left + 18f, y + 19.5f, 9f, Paint().apply { color = green })
            page.canvas.drawText("?", left + 15.4f, y + 23f, paint(10f, Color.WHITE, true))
            page.canvas.drawText("CONTATOS COM O SUPORTE TÉCNICO", left + 34f, y + 15f, paint(8.2f, Color.rgb(54, 90, 47), true))
            page.canvas.drawText("suportetga@dubrasilsolucoes.com.br  •  (34) 3322-8500", left + 34f, y + 29f, paint(8.5f, Color.rgb(67, 93, 62)))
            y += 45f
        }

        fun paragraphBox(label: String, value: String, minimumHeight: Float) {
            val lines = wrap(value.ifBlank { "Não informado" }, body, contentWidth - 20f)
            val height = maxOf(minimumHeight, 29f + lines.size * 11f)
            ensure(height + 7f)
            page.canvas.drawRoundRect(RectF(left, y, right, y + height), 5f, 5f, Paint().apply { color = Color.WHITE })
            page.canvas.drawRoundRect(RectF(left, y, right, y + height), 5f, 5f, linePaint)
            page.canvas.drawText(label.uppercase(), left + 9f, y + 13f, paint(7.8f, navy, true))
            lines.forEachIndexed { index, line -> page.canvas.drawText(line, left + 9f, y + 29f + index * 11f, body) }
            y += height + 7f
        }

        fun statusBox(selected: String) {
            ensure(58f)
            val options = listOf("Concluído", "Concluído, mas deseja novos serviços", "Não concluído")
            page.canvas.drawText("POSICIONAMENTO DA ENTREGA", left, y + 10f, paint(7.8f, navy, true))
            y += 17f
            val cellWidth = contentWidth / options.size
            options.forEachIndexed { index, option ->
                val x = left + index * cellWidth
                val active = selected == option
                page.canvas.drawRoundRect(RectF(x + 2f, y, x + cellWidth - 2f, y + 29f), 5f, 5f, Paint().apply {
                    color = if (active) Color.rgb(235, 246, 232) else softGray
                })
                drawCheckbox(x + 9f, y + 10f, active)
                wrap(option, paint(7.5f, if (active) Color.rgb(54, 100, 44) else ink, active), cellWidth - 30f)
                    .take(2).forEachIndexed { line, text ->
                        page.canvas.drawText(text, x + 24f, y + 12f + line * 9f, paint(7.5f, if (active) Color.rgb(54, 100, 44) else ink, active))
                    }
            }
            y += 37f
        }

        fun signatureBlock(technicianUri: String, clientUri: String) {
            ensure(205f)
            section("ASSINATURAS")
            val gap = 16f
            val width = (contentWidth - gap) / 2f
            signatureCell(left, y, width, technicianUri, "TÉCNICO DE IMPLANTAÇÃO", "DUBRASIL SOLUÇÕES")
            signatureCell(left + width + gap, y, width, clientUri, "RESPONSÁVEL PELO CLIENTE", data.field("cliente"))
            y += 151f
            val note = "Ao assinar, as partes confirmam o recebimento das informações e o posicionamento descrito neste relatório."
            page.canvas.drawText(note, left, y, small)
            y += 12f
        }

        fun evidence(label: String, uri: Uri) {
            val bitmap = loadBitmap(uri) ?: run { fileReference(label); return }
            val maxWidth = contentWidth - 12f
            var drawWidth = min(maxWidth, bitmap.width.toFloat())
            var drawHeight = drawWidth * bitmap.height / bitmap.width
            if (drawHeight > 560f) {
                drawHeight = 560f
                drawWidth = drawHeight * bitmap.width / bitmap.height
            }
            if (y + drawHeight + 42f > PAGE_HEIGHT - 45f) newPage()
            val lines = wrap(label, bodyBold, contentWidth)
            lines.forEachIndexed { index, line -> page.canvas.drawText(line, left, y + 11f + index * 11f, bodyBold) }
            y += lines.size * 11f + 7f
            val x = left + (contentWidth - drawWidth) / 2f
            page.canvas.drawRect(x - 4f, y - 4f, x + drawWidth + 4f, y + drawHeight + 4f, linePaint)
            page.canvas.drawBitmap(bitmap, null, RectF(x, y, x + drawWidth, y + drawHeight), body)
            y += drawHeight + 14f
            bitmap.recycle()
        }

        fun fileReference(name: String) {
            ensure(29f)
            page.canvas.drawRoundRect(RectF(left, y, right, y + 23f), 4f, 4f, Paint().apply { color = softGray })
            page.canvas.drawText("ARQUIVO ANEXADO", left + 8f, y + 10f, small)
            page.canvas.drawText(name, left + 92f, y + 15f, bodyBold)
            y += 29f
        }

        fun space(value: Float) { y += value }

        fun finish() { closePage() }

        private fun subsection(title: String) {
            ensure(24f)
            page.canvas.drawRect(left, y, right, y + 20f, Paint().apply { color = softBlue })
            page.canvas.drawRect(left, y, left + 4f, y + 20f, Paint().apply { color = green })
            page.canvas.drawText(title.uppercase(), left + 10f, y + 14f, groupText)
            y += 20f
        }

        private fun signatureCell(x: Float, top: Float, width: Float, uri: String, label: String, detail: String) {
            val signatureBottom = top + 100f
            page.canvas.drawRoundRect(RectF(x, top, x + width, top + 138f), 6f, 6f, Paint().apply { color = Color.WHITE })
            page.canvas.drawRoundRect(RectF(x, top, x + width, top + 138f), 6f, 6f, linePaint)
            if (uri.isNotBlank()) loadBitmap(Uri.parse(uri))?.let { bitmap ->
                val maxW = width - 24f
                val maxH = 75f
                val scale = min(maxW / bitmap.width, maxH / bitmap.height)
                val w = bitmap.width * scale
                val h = bitmap.height * scale
                page.canvas.drawBitmap(bitmap, null, RectF(x + (width - w) / 2f, top + 10f + (maxH - h) / 2f, x + (width + w) / 2f, top + 10f + (maxH + h) / 2f), body)
                bitmap.recycle()
            }
            page.canvas.drawLine(x + 16f, signatureBottom, x + width - 16f, signatureBottom, Paint().apply { color = ink; strokeWidth = 0.8f })
            val labelPaint = paint(7.2f, navy, true)
            page.canvas.drawText(label, x + (width - labelPaint.measureText(label)) / 2f, top + 114f, labelPaint)
            val detailText = detail.ifBlank { " " }
            val fitted = wrap(detailText, small, width - 18f).firstOrNull().orEmpty()
            page.canvas.drawText(fitted, x + (width - small.measureText(fitted)) / 2f, top + 128f, small)
        }

        private fun drawCheckbox(x: Float, top: Float, checked: Boolean) {
            val size = 9f
            val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (checked) green else Color.WHITE
                style = Paint.Style.FILL
            }
            page.canvas.drawRoundRect(RectF(x, top, x + size, top + size), 1.5f, 1.5f, box)
            page.canvas.drawRoundRect(RectF(x, top, x + size, top + size), 1.5f, 1.5f, Paint().apply {
                color = if (checked) green else mutedColor; style = Paint.Style.STROKE; strokeWidth = 0.8f
            })
            if (checked) {
                val check = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 1.4f; style = Paint.Style.STROKE }
                page.canvas.drawLine(x + 2f, top + 4.8f, x + 4f, top + 7f, check)
                page.canvas.drawLine(x + 4f, top + 7f, x + 7.5f, top + 2.4f, check)
            }
        }

        private fun newPage() {
            if (::page.isInitialized) closePage()
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            drawPageHeader()
            y = 91f
        }

        private fun drawPageHeader() {
            val canvas = page.canvas
            canvas.drawBitmap(logo, null, RectF(left, 24f, left + 112f, 62f), body)
            val title = "RELATÓRIO DE ENTREGA DE IMPLANTAÇÃO"
            val titlePaint = paint(12.5f, navyDark, true)
            canvas.drawText(title, right - titlePaint.measureText(title), 39f, titlePaint)
            val subtitle = "Sistema de Gestão TGA • R.E.I."
            canvas.drawText(subtitle, right - small.measureText(subtitle), 54f, small)
            canvas.drawRect(left, 72f, right, 75f, Paint().apply { color = navy })
            canvas.drawRect(left, 75f, left + 118f, 78f, Paint().apply { color = green })
        }

        private fun closePage() {
            val canvas = page.canvas
            canvas.drawLine(left, PAGE_HEIGHT - 35f, right, PAGE_HEIGHT - 35f, Paint().apply { color = borderColor; strokeWidth = 0.7f })
            canvas.drawText("DuBrasil Soluções  •  suporte: (34) 3322-8500", left, PAGE_HEIGHT - 20f, small)
            val pageText = "Página $pageNumber"
            canvas.drawText(pageText, right - small.measureText(pageText), PAGE_HEIGHT - 20f, small)
            document.finishPage(page)
        }

        private fun ensure(height: Float) {
            if (y + height > PAGE_HEIGHT - 45f) newPage()
        }

        private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
            if (text.isBlank()) return listOf("—")
            val result = mutableListOf<String>()
            text.replace('\n', ' ').split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { word ->
                val current = result.lastOrNull().orEmpty()
                val candidate = if (current.isBlank()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    if (result.isEmpty()) result.add(candidate) else result[result.lastIndex] = candidate
                } else {
                    result.add(word)
                }
            }
            return result.ifEmpty { listOf("—") }
        }

        private fun loadBitmap(uri: Uri): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }.getOrNull()

        private fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
        }
    }
}
