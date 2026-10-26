package com.example.knowledgegraph.util

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 纯 Kotlin + Jetpack Compose 原生 Markdown 解析与渲染组件
 * 支持：
 * - H1 ~ H6 标题与视觉强调条
 * - 无序列表、有序列表、待办任务列表 (- [ ] / - [x])
 * - 围栏代码块 (```language ... ```)
 * - 引用块 (> ...)
 * - 分隔线 (--- / ***)
 * - 行内粗体 (**text**)、斜体 (*text*)、删除线 (~~text~~)、行内代码 (`code`)
 * - 超链接 ([label](url)) 与 双链知识节点跳转 ([[节点标题]])
 */
sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class Quote(val text: String) : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class OrderedItem(val number: String, val text: String) : MarkdownBlock()
    data class TaskItem(val checked: Boolean, val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock()
    data object Divider : MarkdownBlock()
}

object MarkdownParser {

    fun parseBlocks(markdown: String): List<MarkdownBlock> {
        if (markdown.isBlank()) return emptyList()
        val lines = markdown.lines()
        val blocks = mutableListOf<MarkdownBlock>()
        var i = 0

        while (i < lines.size) {
            val rawLine = lines[i]
            val trimmed = rawLine.trim()

            when {
                trimmed.isEmpty() -> {
                    i++
                }

                trimmed.startsWith("```") -> {
                    val lang = trimmed.removePrefix("```").trim().ifEmpty { "code" }
                    val codeBuilder = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        if (codeBuilder.isNotEmpty()) codeBuilder.append('\n')
                        codeBuilder.append(lines[i])
                        i++
                    }
                    blocks.add(MarkdownBlock.CodeBlock(lang, codeBuilder.toString()))
                    i++ // skip closing ```
                }

                trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                    blocks.add(MarkdownBlock.Divider)
                    i++
                }

                trimmed.startsWith("#") -> {
                    val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    val content = trimmed.drop(level).trim()
                    blocks.add(MarkdownBlock.Heading(level, content))
                    i++
                }

                trimmed.startsWith(">") -> {
                    val quoteLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith(">")) {
                        quoteLines.add(lines[i].trim().removePrefix(">").trim())
                        i++
                    }
                    blocks.add(MarkdownBlock.Quote(quoteLines.joinToString("\n")))
                }

                trimmed.startsWith("- [ ] ") || trimmed.startsWith("* [ ] ") -> {
                    blocks.add(MarkdownBlock.TaskItem(false, trimmed.drop(6).trim()))
                    i++
                }

                trimmed.startsWith("- [x] ", ignoreCase = true) ||
                    trimmed.startsWith("* [x] ", ignoreCase = true) -> {
                    blocks.add(MarkdownBlock.TaskItem(true, trimmed.drop(6).trim()))
                    i++
                }

                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                    blocks.add(MarkdownBlock.BulletItem(trimmed.drop(2).trim()))
                    i++
                }

                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val dotIndex = trimmed.indexOf('.')
                    val num = trimmed.substring(0, dotIndex + 1)
                    val text = trimmed.substring(dotIndex + 1).trim()
                    blocks.add(MarkdownBlock.OrderedItem(num, text))
                    i++
                }

                else -> {
                    val paragraphLines = mutableListOf<String>()
                    while (
                        i < lines.size &&
                        lines[i].trim().isNotEmpty() &&
                        !lines[i].trim().startsWith("#") &&
                        !lines[i].trim().startsWith("```") &&
                        !lines[i].trim().startsWith(">") &&
                        !lines[i].trim().startsWith("- ") &&
                        !lines[i].trim().startsWith("* ") &&
                        lines[i].trim() != "---" &&
                        !lines[i].trim().matches(Regex("^\\d+\\.\\s+.*"))
                    ) {
                        paragraphLines.add(lines[i].trim())
                        i++
                    }
                    if (paragraphLines.isNotEmpty()) {
                        blocks.add(MarkdownBlock.Paragraph(paragraphLines.joinToString(" ")))
                    } else {
                        i++
                    }
                }
            }
        }
        return blocks
    }
}

private const val TAG_URL = "URL"
private const val TAG_WIKILINK = "WIKILINK"

@Composable
fun MarkdownPreview(
    markdown: String,
    modifier: Modifier = Modifier,
    onWikiLinkClick: ((String) -> Unit)? = null
) {
    val blocks = remember(markdown) { MarkdownParser.parseBlocks(markdown) }
    val uriHandler = LocalUriHandler.current

    if (blocks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无 Markdown 内容，切换到「编辑」开始记录知识节点…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 30.sp
                        )
                        2 -> MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            lineHeight = 26.sp
                        )
                        3 -> MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                        else -> MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (block.level == 1) 6.dp else 4.dp)
                    ) {
                        if (block.level <= 2) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(if (block.level == 1) 22.dp else 18.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        MarkdownInlineText(
                            text = block.text,
                            baseStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
                            onUrlClick = { url -> runCatching { uriHandler.openUri(url) } },
                            onWikiLinkClick = onWikiLinkClick
                        )
                    }
                }

                is MarkdownBlock.Paragraph -> {
                    MarkdownInlineText(
                        text = block.text,
                        baseStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 24.sp
                        ),
                        onUrlClick = { url -> runCatching { uriHandler.openUri(url) } },
                        onWikiLinkClick = onWikiLinkClick
                    )
                }

                is MarkdownBlock.Quote -> {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(48.dp)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                MarkdownInlineText(
                                    text = block.text,
                                    baseStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        fontStyle = FontStyle.Italic,
                                        lineHeight = 22.sp
                                    ),
                                    onUrlClick = { url -> runCatching { uriHandler.openUri(url) } },
                                    onWikiLinkClick = onWikiLinkClick
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 9.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        MarkdownInlineText(
                            text = block.text,
                            baseStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 23.sp
                            ),
                            onUrlClick = { url -> runCatching { uriHandler.openUri(url) } },
                            onWikiLinkClick = onWikiLinkClick
                        )
                    }
                }

                is MarkdownBlock.OrderedItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = block.number,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.width(26.dp)
                        )
                        MarkdownInlineText(
                            text = block.text,
                            baseStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 23.sp
                            ),
                            onUrlClick = { url -> runCatching { uriHandler.openUri(url) } },
                            onWikiLinkClick = onWikiLinkClick
                        )
                    }
                }

                is MarkdownBlock.TaskItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (block.checked) {
                                Icons.Default.CheckBox
                            } else {
                                Icons.Default.CheckBoxOutlineBlank
                            },
                            contentDescription = if (block.checked) "已完成任务" else "待办任务",
                            tint = if (block.checked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MarkdownInlineText(
                            text = block.text,
                            baseStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = if (block.checked) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                textDecoration = if (block.checked) {
                                    TextDecoration.LineThrough
                                } else {
                                    TextDecoration.None
                                }
                            ),
                            onUrlClick = { url -> runCatching { uriHandler.openUri(url) } },
                            onWikiLinkClick = onWikiLinkClick
                        )
                    }
                }

                is MarkdownBlock.CodeBlock -> {
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E293B))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Code,
                                        contentDescription = "代码块",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = block.language.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF94A3B8),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = block.code,
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        color = Color(0xFFE2E8F0)
                                    )
                                )
                            }
                        }
                    }
                }

                is MarkdownBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownInlineText(
    text: String,
    baseStyle: TextStyle,
    onUrlClick: (String) -> Unit,
    onWikiLinkClick: ((String) -> Unit)?
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val codeBgColor = MaterialTheme.colorScheme.surfaceVariant
    val codeTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val annotated = remember(text, primaryColor, tertiaryColor, codeBgColor, codeTextColor) {
        buildInlineAnnotatedString(
            raw = text,
            primaryColor = primaryColor,
            wikiColor = tertiaryColor,
            codeBgColor = codeBgColor,
            codeTextColor = codeTextColor
        )
    }

    @Suppress("DEPRECATION")
    ClickableText(
        text = annotated,
        style = baseStyle,
        onClick = { offset ->
            annotated.getStringAnnotations(tag = TAG_WIKILINK, start = offset, end = offset)
                .firstOrNull()?.let { ann ->
                    onWikiLinkClick?.invoke(ann.item)
                    return@ClickableText
                }
            annotated.getStringAnnotations(tag = TAG_URL, start = offset, end = offset)
                .firstOrNull()?.let { ann ->
                    onUrlClick(ann.item)
                }
        }
    )
}

private fun buildInlineAnnotatedString(
    raw: String,
    primaryColor: Color,
    wikiColor: Color,
    codeBgColor: Color,
    codeTextColor: Color
): AnnotatedString {
    val tokenRegex = Regex(
        "(\\[\\[([^\\]]+)]])|(\\[([^\\]]+)]\\(([^)]+)\\))|(`([^`]+)`)|(\\*\\*([^*]+)\\*\\*)|(~~([^~]+)~~)|(\\*([^*]+)\\*)"
    )

    return buildAnnotatedString {
        var lastIndex = 0
        for (match in tokenRegex.findAll(raw)) {
            if (match.range.first > lastIndex) {
                append(raw.substring(lastIndex, match.range.first))
            }

            when {
                // [[WikiLink]]
                match.groups[1] != null -> {
                    val targetTitle = match.groups[2]?.value ?: ""
                    val start = length
                    pushStyle(
                        SpanStyle(
                            color = wikiColor,
                            fontWeight = FontWeight.Bold,
                            background = wikiColor.copy(alpha = 0.14f)
                        )
                    )
                    append("⟦$targetTitle⟧")
                    pop()
                    addStringAnnotation(
                        tag = TAG_WIKILINK,
                        annotation = targetTitle,
                        start = start,
                        end = length
                    )
                }

                // [label](url)
                match.groups[3] != null -> {
                    val label = match.groups[4]?.value ?: ""
                    val url = match.groups[5]?.value ?: ""
                    val start = length
                    pushStyle(
                        SpanStyle(
                            color = primaryColor,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    append(label)
                    pop()
                    addStringAnnotation(
                        tag = TAG_URL,
                        annotation = url,
                        start = start,
                        end = length
                    )
                }

                // `inline code`
                match.groups[6] != null -> {
                    val code = match.groups[7]?.value ?: ""
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBgColor,
                            color = codeTextColor,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    append(" $code ")
                    pop()
                }

                // **bold**
                match.groups[8] != null -> {
                    val boldText = match.groups[9]?.value ?: ""
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(boldText)
                    pop()
                }

                // ~~strikethrough~~
                match.groups[10] != null -> {
                    val strikeText = match.groups[11]?.value ?: ""
                    pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                    append(strikeText)
                    pop()
                }

                // *italic*
                match.groups[12] != null -> {
                    val italicText = match.groups[13]?.value ?: ""
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(italicText)
                    pop()
                }
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < raw.length) {
            append(raw.substring(lastIndex))
        }
    }
}
