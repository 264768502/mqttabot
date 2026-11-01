#!/usr/bin/env bash
# Builds the self-hosted F-Droid repository from the signed GitHub release APKs.
#
#   fdroid/repo/build-repo.sh <work-dir> <site-dir>
#
# Needs: fdroidserver, gh (GH_TOKEN), Android SDK build-tools (ANDROID_HOME),
# FDROID_KEYSTORE (path to the repo signing key) and FDROID_KEYSTORE_PASSWORD.
# Only <site-dir> is meant to be published; it never contains the key or config.
set -euo pipefail

work=${1:?usage: build-repo.sh <work-dir> <site-dir>}
site=${2:?usage: build-repo.sh <work-dir> <site-dir>}
for d in "$work" "$site"; do
    case "$(realpath -m "$d")" in /|"$HOME"|"$(pwd)") echo "refusing to use $d" >&2; exit 1;; esac
done
src=$(cd "$(dirname "$0")/../.." && pwd)
repo_slug=${GITHUB_REPOSITORY:-264768502/mqttabot}
owner=${repo_slug%%/*}
name=${repo_slug#*/}
repo_url=${FDROID_REPO_URL:-https://$owner.github.io/$name/repo}
: "${FDROID_KEYSTORE:?path to the repo signing keystore}"
: "${FDROID_KEYSTORE_PASSWORD:?repo keystore password}"
: "${ANDROID_HOME:?Android SDK location}"

rm -rf "$work" "$site"
mkdir -p "$work/repo" "$work/metadata" "$site"

# Every published (non-pre-release) version, signed with the release key.
gh release list --repo "$repo_slug" --exclude-pre-releases --limit 100 --json tagName --jq '.[].tagName' |
    while read -r tag; do
        gh release download "$tag" --repo "$repo_slug" --pattern '*-release.apk' --dir "$work/repo"
    done

# App metadata; store texts and images come from the fastlane listing.
cp "$src/fdroid/repo/metadata/com.mqttabot.yml" "$work/metadata/"
cp -r "$src/fastlane/metadata/android" "$work/metadata/com.mqttabot"
cp "$src/fastlane/metadata/android/en-US/images/icon.png" "$work/repo-icon.png"

cat > "$work/config.yml" <<CONFIG
sdk_path: $ANDROID_HOME
repo_url: $repo_url
repo_name: MqttABot F-Droid repository
repo_icon: repo-icon.png
repo_description: >-
  Official builds of MqttABot, an MQTT client and Tasker plugin, from
  https://github.com/$repo_slug. APKs are the signed GitHub release builds.
archive_older: 0
keystore: $FDROID_KEYSTORE
repo_keyalias: mqttabot-repo
keystorepass: $FDROID_KEYSTORE_PASSWORD
keypass: $FDROID_KEYSTORE_PASSWORD
CONFIG
chmod 600 "$work/config.yml"

(cd "$work" && fdroid update --use-date-from-apk)

fingerprint=$(keytool -list -v -keystore "$FDROID_KEYSTORE" -storepass "$FDROID_KEYSTORE_PASSWORD" -alias mqttabot-repo |
    sed -nE 's/^\s*SHA256: ([0-9A-F:]+)$/\1/p' | tr -d ':')
cp -r "$work/repo" "$site/repo"
cp "$src/fdroid/repo/qr.png" "$site/qr.png"
sed -e "s|@REPO_URL@|$repo_url|g" -e "s|@FINGERPRINT@|$fingerprint|g" \
    -e "s|@ADD_URL@|fdroidrepos://${repo_url#https://}?fingerprint=$fingerprint|g" \
    "$src/fdroid/repo/index.html" > "$site/index.html"
echo "Repository: $repo_url"
echo "Fingerprint: $fingerprint"
