#!/usr/bin/env bash
# Task 14, run once by the owner in a real terminal (passwords are typed here, never logged or echoed).
# Creates ~/.android-keys/recon-release.jks (PKCS12: one password for store and key), writes the git-ignored
# keystore.properties (mode 600) and prints the certificate SHA-256 for D18 and the README.
set -euo pipefail
cd "$(dirname "$0")/.."
ks=$HOME/.android-keys/recon-release.jks
[[ -e $ks ]] && { echo "Refusing: $ks already exists (changing the key breaks updates)."; exit 1; }
[[ -e keystore.properties ]] && { echo "Refusing: keystore.properties already exists."; exit 1; }
mkdir -p "$(dirname "$ks")" && chmod 700 "$(dirname "$ks")"
read -rsp "Keystore password (from your password manager, 12+ chars): " pw; echo
read -rsp "Again: " pw2; echo
[[ $pw == "$pw2" && ${#pw} -ge 12 ]] || { echo "Passwords differ or are too short."; exit 1; }
# Password goes through the environment, not the command line, so it never shows in ps or shell history.
RECON_KS_PW=$pw keytool -genkeypair -keystore "$ks" -storetype PKCS12 -alias recon -keyalg RSA -keysize 4096 \
  -validity 10950 -dname "CN=Recon, O=wolzard" -storepass:env RECON_KS_PW -keypass:env RECON_KS_PW
chmod 600 "$ks"
umask 077
printf 'storeFile=%s\nstorePassword=%s\nkeyAlias=recon\nkeyPassword=%s\n' "$ks" "$pw" "$pw" > keystore.properties
echo "Certificate SHA-256 (put in D18 and README):"
RECON_KS_PW=$pw keytool -list -v -keystore "$ks" -alias recon -storepass:env RECON_KS_PW 2>/dev/null | grep 'SHA256:' || true
unset pw pw2
git check-ignore -q keystore.properties || { echo "WARNING: keystore.properties is NOT git-ignored"; exit 1; }
echo "Now back up $ks twice offline (encrypted USB + password-manager attachment). No cloud plaintext copy."
