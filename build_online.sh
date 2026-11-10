#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
REPO="remote-neon-agent"

echo "==> [1/4] Git..."
git init -b main >/dev/null 2>&1 || git init >/dev/null
git config user.name "remote-neon-builder"
git config user.email "remote@neon.local"
git add -A
git commit -m "Remote Neon Agent v1" >/dev/null || true

echo "==> [2/4] Push ke GitHub..."
if ! git remote get-url origin >/dev/null 2>&1; then
  gh repo create "$REPO" --public --source=. --push || {
    REPO="$REPO-$(date +%s)"
    gh repo create "$REPO" --public --source=. --push
  }
else
  git push -u origin main
fi

echo "==> [3/4] Tunggu build APK..."
termux-wake-lock >/dev/null 2>&1 || true
sleep 20
RUN=""
for i in 1 2 3 4 5 6; do
  RUN=$(gh run list --workflow=build-apk.yml --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null || true)
  if [ -n "$RUN" ] && [ "$RUN" != "null" ]; then break; fi
  sleep 15
done
if [ -z "$RUN" ] || [ "$RUN" == "null" ]; then echo "Run tidak ditemukan."; exit 1; fi
echo "    Run ID: $RUN"
if ! gh run watch "$RUN"; then
  echo "BUILD GAGAL. Cek: gh run view $RUN --log-failed | tail -n 80"
  exit 1
fi

echo "==> [4/4] Download + pindah ke folder Download HP..."
rm -rf dist; mkdir -p dist
gh run download "$RUN" --name remote-neon-apk --dir dist

if [ ! -d ~/storage/shared ]; then
  echo "⚠️  Storage belum diizinkan. Jalanin dulu: termux-setup-storage"
  exit 1
fi

cp dist/RemoteNeonAgent.apk ~/storage/shared/Download/RemoteNeonAgent.apk
termux-wake-unlock >/dev/null 2>&1 || true
echo ""
echo "=============================================="
echo " SELESAI! APK di folder Download HP."
echo " Install: termux-open ~/storage/shared/Download/RemoteNeonAgent.apk"
echo "=============================================="
termux-open ~/storage/shared/Download/RemoteNeonAgent.apk
