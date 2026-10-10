package com.alertworker.common.enums;

/**
 * Rendering/output format of a {@code message_template}.
 *
 * <ul>
 *   <li>{@link #TEXT}: rendered content is sent inline through the channel (legacy behaviour).</li>
 *   <li>{@link #HTML}: rendered content is a self-contained HTML document; the data-processing module renders it
 *       once, uploads the file to object storage and messages carry the resulting public URL (email embeds the HTML
 *       inline, DingDing sends the link). Intended for daily reports.</li>
 * </ul>
 */
public enum TemplateFormat {
    TEXT,
    HTML
}
