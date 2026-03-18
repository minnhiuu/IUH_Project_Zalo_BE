def build_post_text(title: str | None, caption: str | None, description: str | None) -> str:
    chunks = [part.strip() for part in (title, caption, description) if part and part.strip()]
    return "\n".join(chunks)


def prepare_post_text(content_dict: dict) -> str:
    title = str(content_dict.get("title", "")).strip()
    caption = str(content_dict.get("caption", "")).strip()
    description = str(content_dict.get("description", "")).strip()

    raw_hashtags = content_dict.get("hashtags", [])
    hashtags = " ".join(str(tag).replace("#", "").strip() for tag in raw_hashtags if str(tag).strip())

    components: list[str] = []
    if title:
        components.append(f"Title: {title}")
    if hashtags:
        components.append(f"Keywords: {hashtags}")
    if caption or description:
        body = f"{caption} {description}".strip()
        components.append(f"Content: {body}")

    return " ".join(components).strip()
