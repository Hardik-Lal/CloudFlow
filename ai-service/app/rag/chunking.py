"""Splits documents into overlapping chunks along line boundaries."""


def chunk_text(text: str, size: int, overlap: int) -> list[str]:
    """Chunks of at most ``size`` characters, preferring to break between lines.

    Consecutive chunks share up to ``overlap`` characters so context that spans a boundary
    (e.g. a stack trace) is retrievable from either side.
    """
    if size <= 0 or overlap < 0 or overlap >= size:
        raise ValueError("require size > 0 and 0 <= overlap < size")
    text = text.strip()
    if not text:
        return []
    chunks: list[str] = []
    start = 0
    while start < len(text):
        end = min(start + size, len(text))
        if end < len(text):
            newline = text.rfind("\n", start + size // 2, end)
            if newline != -1:
                end = newline + 1
        chunk = text[start:end].strip()
        if chunk:
            chunks.append(chunk)
        if end >= len(text):
            break
        start = max(end - overlap, start + 1)
    return chunks
