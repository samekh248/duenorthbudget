#!/usr/bin/env bash
# Keeps Gradle's dependency cache across Claude cloud sessions.
#
# Every cloud session starts from a fresh container, so without this each build
# re-downloads the whole dependency set from Maven Central. Central rate-limits
# per IP, and cloud sessions share egress IPs, so those downloads often come back
# 429 Too Many Requests. This hook stores the cache in the project's shared
# folder (/mnt/project-files), which outlives containers, and restores it at
# session start, so a build only reaches Central for dependencies that changed.
#
#   restore  (SessionStart) unpack the shared cache and pick a Central mirror
#   save     (Stop)         repack the shared cache when this session added to it
#
# Outside a cloud session, or without the shared folder, it does nothing.
set -u

[ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || exit 0
[ -d /mnt/project-files/ ] || exit 0

SHARED=/mnt/project-files/.cache/gradle-deps
ARCHIVE="$SHARED/deps.tar"
COUNT="$SHARED/deps.count"
# What gets cached: Gradle's resolved modules, plus the android-all jars that
# Robolectric fetches into ~/.m2 on its own at test time.
PATHS=(.gradle/caches/modules-2 .m2/repository/org/robolectric)
MIRROR=https://maven-central.storage-download.googleapis.com/maven2

file_count() {
  (cd "$HOME" && find "${PATHS[@]}" -type f ! -name '*.lock' ! -name gc.properties 2>/dev/null | wc -l)
}

restore() {
  if [ -f "$ARCHIVE" ]; then
    mkdir -p "$HOME/.gradle/caches"
    if tar -xf "$ARCHIVE" -C "$HOME" 2>/dev/null; then
      echo "Restored Gradle dependency cache ($(file_count) files) from the project's shared folder."
    else
      echo "Could not unpack the shared Gradle dependency cache; builds will download dependencies."
    fi
  fi

  # Google's Maven Central mirror has no shared rate limit, but the session's
  # network policy has to allow it. Use it only when it answers.
  if [ -n "${CLAUDE_ENV_FILE:-}" ] &&
    [ "$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 \
      "$MIRROR/junit/junit/4.13.2/junit-4.13.2.pom")" = "200" ]; then
    echo "export MAVEN_CENTRAL_MIRROR=$MIRROR" >>"$CLAUDE_ENV_FILE"
    echo "Using Google's Maven Central mirror."
  fi
}

save() {
  # Skip while a build is running: its cache files may be mid-write.
  pgrep -f GradleWrapperMain >/dev/null && return
  local have shared lock="$HOME/.gradle/dep-cache-save.lock"
  have=$(file_count)
  shared=$(cat "$COUNT" 2>/dev/null || echo 0)
  [ "$have" -gt "$shared" ] || return
  [ -e "$lock" ] && return

  # Pack in the background so the turn isn't held up. Write to a temp file and
  # move it into place, so a session restoring meanwhile never reads half an
  # archive.
  local existing=() p
  for p in "${PATHS[@]}"; do [ -d "$HOME/$p" ] && existing+=("$p"); done

  touch "$lock"
  (
    mkdir -p "$SHARED"
    tmp="$ARCHIVE.$$.tmp"
    if (cd "$HOME" && tar -cf "$tmp" --exclude='*.lock' --exclude=gc.properties \
      "${existing[@]}" 2>/dev/null); then
      mv -f "$tmp" "$ARCHIVE" && echo "$have" >"$COUNT"
    else
      rm -f "$tmp"
    fi
    rm -f "$lock"
  ) </dev/null >/dev/null 2>&1 &
  disown
}

case "${1:-}" in
  restore) restore ;;
  save) save ;;
  *) echo "usage: $0 restore|save" >&2; exit 2 ;;
esac
exit 0
