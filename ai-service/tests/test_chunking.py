import pytest

from app.rag.chunking import chunk_text


def test_short_text_is_one_chunk() -> None:
    assert chunk_text("hello\nworld", size=100, overlap=10) == ["hello\nworld"]


def test_chunks_break_on_lines_and_overlap() -> None:
    text = "\n".join(f"line {i:03d} " + "x" * 40 for i in range(50))
    chunks = chunk_text(text, size=500, overlap=100)

    assert len(chunks) > 1
    assert all(len(chunk) <= 500 for chunk in chunks)
    assert all(chunk.startswith("line") for chunk in chunks)
    # Overlap: each chunk starts with content from the end of the previous one.
    assert chunks[1].splitlines()[0] in chunks[0]


def test_blank_text_has_no_chunks() -> None:
    assert chunk_text("  \n ", size=100, overlap=10) == []


def test_rejects_invalid_sizes() -> None:
    with pytest.raises(ValueError):
        chunk_text("x", size=10, overlap=10)
