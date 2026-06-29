#!/usr/bin/env python3
import html
import re
import sys
import textwrap
from html.parser import HTMLParser


class NotesParser(HTMLParser):
    block_tags = {"h1", "h2", "h3", "p", "li", "pre", "td", "th"}

    def __init__(self):
        super().__init__()
        self.blocks = []
        self.current_tag = None
        self.current_text = []

    def handle_starttag(self, tag, attrs):
        if tag in self.block_tags:
            self._flush()
            self.current_tag = tag
            self.current_text = []
        elif tag == "br":
            self.current_text.append("\n")

    def handle_endtag(self, tag):
        if tag == self.current_tag:
            self._flush()
        elif tag in {"tr", "table", "ol", "ul"}:
            self._flush()

    def handle_data(self, data):
        if self.current_tag:
            self.current_text.append(data)

    def _flush(self):
        if not self.current_tag:
            return

        text = html.unescape("".join(self.current_text))
        if self.current_tag != "pre":
            text = re.sub(r"\s+", " ", text).strip()
        else:
            text = text.strip("\n")

        if text:
            if self.current_tag == "li":
                text = "- " + text
            self.blocks.append((self.current_tag, text))

        self.current_tag = None
        self.current_text = []


def pdf_escape(value):
    return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")


def clean_text(value):
    return value.encode("latin-1", "replace").decode("latin-1")


def line_width(tag):
    if tag == "h1":
        return 52
    if tag == "h2":
        return 64
    if tag == "h3":
        return 72
    if tag == "pre":
        return 88
    return 92


def font_for(tag):
    if tag in {"h1", "h2", "h3", "th"}:
        return "F2"
    if tag == "pre":
        return "F3"
    return "F1"


def size_for(tag):
    if tag == "h1":
        return 20
    if tag == "h2":
        return 14
    if tag == "h3":
        return 12
    if tag == "pre":
        return 8.8
    return 10


def leading_for(tag):
    if tag == "h1":
        return 24
    if tag == "h2":
        return 18
    if tag == "h3":
        return 15
    if tag == "pre":
        return 11
    return 13


def build_pages(blocks):
    pages = []
    lines = []
    y = 790

    for tag, text in blocks:
        before = 10 if tag in {"h1", "h2"} else 5 if tag == "h3" else 2
        after = 8 if tag in {"h1", "h2"} else 4

        if y - before < 60:
            pages.append(lines)
            lines = []
            y = 790

        y -= before
        font = font_for(tag)
        size = size_for(tag)
        leading = leading_for(tag)

        raw_lines = []
        if tag == "pre":
            for source_line in text.splitlines():
                raw_lines.extend(textwrap.wrap(source_line, width=line_width(tag)) or [""])
        else:
            raw_lines = textwrap.wrap(text, width=line_width(tag)) or [""]

        for line in raw_lines:
            if y < 55:
                pages.append(lines)
                lines = []
                y = 790

            lines.append((font, size, 50, y, clean_text(line)))
            y -= leading

        y -= after

    if lines:
        pages.append(lines)

    return pages


def page_stream(lines, page_number, page_count):
    commands = []
    for font, size, x, y, text in lines:
        commands.append(f"BT /{font} {size:.1f} Tf {x} {y:.1f} Td ({pdf_escape(text)}) Tj ET")
    footer = f"Page {page_number} of {page_count}"
    commands.append(f"BT /F1 8 Tf 270 28 Td ({footer}) Tj ET")
    return "\n".join(commands).encode("latin-1")


def write_pdf(pages, output_path):
    objects = []

    def add_object(data):
        objects.append(data)
        return len(objects)

    catalog_id = add_object(b"<< /Type /Catalog /Pages 2 0 R >>")
    pages_id = add_object(b"")
    font1_id = add_object(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
    font2_id = add_object(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>")
    font3_id = add_object(b"<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>")

    page_ids = []
    for index, page in enumerate(pages, start=1):
        stream = page_stream(page, index, len(pages))
        stream_id = add_object(b"<< /Length " + str(len(stream)).encode("ascii") + b" >>\nstream\n" + stream + b"\nendstream")
        page_id = add_object(
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] "
            b"/Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R >> >> "
            b"/Contents " + str(stream_id).encode("ascii") + b" 0 R >>"
        )
        page_ids.append(page_id)

    objects[pages_id - 1] = (
        b"<< /Type /Pages /Kids ["
        + b" ".join(f"{page_id} 0 R".encode("ascii") for page_id in page_ids)
        + b"] /Count "
        + str(len(page_ids)).encode("ascii")
        + b" >>"
    )

    with open(output_path, "wb") as pdf:
        pdf.write(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
        offsets = [0]
        for number, data in enumerate(objects, start=1):
            offsets.append(pdf.tell())
            pdf.write(f"{number} 0 obj\n".encode("ascii"))
            pdf.write(data)
            pdf.write(b"\nendobj\n")

        xref = pdf.tell()
        pdf.write(f"xref\n0 {len(objects) + 1}\n".encode("ascii"))
        pdf.write(b"0000000000 65535 f \n")
        for offset in offsets[1:]:
            pdf.write(f"{offset:010d} 00000 n \n".encode("ascii"))

        pdf.write(
            b"trailer\n<< /Size "
            + str(len(objects) + 1).encode("ascii")
            + b" /Root "
            + str(catalog_id).encode("ascii")
            + b" 0 R >>\nstartxref\n"
            + str(xref).encode("ascii")
            + b"\n%%EOF\n"
        )


def main():
    if len(sys.argv) != 3:
        print("Usage: html_to_simple_pdf.py input.html output.pdf", file=sys.stderr)
        return 2

    input_path, output_path = sys.argv[1], sys.argv[2]
    parser = NotesParser()

    with open(input_path, "r", encoding="utf-8") as source:
        parser.feed(source.read())
        parser._flush()

    pages = build_pages(parser.blocks)
    write_pdf(pages, output_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
