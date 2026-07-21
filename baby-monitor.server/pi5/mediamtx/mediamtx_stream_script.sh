#!/bin/bash
set -euo pipefail

# USBオーディオ入力デバイスのカード番号を動的に検出（hw:X,0 に固定しない）
CARD=""
for i in {1..10}; do
    CARD=$(arecord -l 2>/dev/null \
        | awk -F'[ :]' '/^card [0-9]+.*USB/ {print $2; exit}')
    [ -n "$CARD" ] && break
    sleep 1
done

if [ -z "$CARD" ]; then
    echo "No USB audio capture device found" >&2
    exit 1
fi

ALSA_DEVICE="hw:${CARD},0"
echo "Using ALSA capture device: $ALSA_DEVICE" >&2

# 内部の /video (rpiCamera H264) に検出したマイク音声を合成して /stream へ配信
exec gst-launch-1.0 \
    rtspclientsink name=s location="rtsp://tatsuya:MG4Smd2+O@127.0.0.1:${RTSP_PORT}/stream" \
    rtspsrc location="rtsp://tatsuya:MG4Smd2+O@127.0.0.1:${RTSP_PORT}/video" latency=0 ! rtph264depay ! s. \
    alsasrc device="$ALSA_DEVICE" ! audioconvert ! audioresample ! avenc_aac bitrate=128000 ! s.
