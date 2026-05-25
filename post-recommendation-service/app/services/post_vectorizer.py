def build_post_text(caption: str | None, hashtags: list[str] | None = None) -> str:
    chunks = [part.strip() for part in (caption,) if part and part.strip()]
    if hashtags:
        chunks.append(" ".join(hashtags))
    return "\n".join(chunks)


def prepare_post_text(content_dict: dict) -> str:
    caption = str(content_dict.get("caption", "") or "").strip()

    raw_hashtags = content_dict.get("hashtags", []) or []
    hashtags = " ".join(str(tag).replace("#", "").strip() for tag in raw_hashtags if str(tag).strip())

    location_name = str(content_dict.get("location_name", "") or "").strip()

    raw_media_types = content_dict.get("media_types", []) or []
    media_types = ", ".join(str(t).lower() for t in raw_media_types if t)

    music_title = str(content_dict.get("music_title", "") or "").strip()
    music_artist = str(content_dict.get("music_artist", "") or "").strip()

    components: list[str] = []
    if hashtags:
        components.append(f"Keywords: {hashtags}")
    if caption:
        components.append(f"Content: {caption}")
    if location_name:
        components.append(f"Location: {location_name}")
    if media_types:
        components.append(f"Media: {media_types}")
    if music_title or music_artist:
        music_str = music_title
        if music_artist:
            music_str = f"{music_title} by {music_artist}".strip(" by") if music_title else music_artist
        components.append(f"Music: {music_str}")

    return " ".join(components).strip()
