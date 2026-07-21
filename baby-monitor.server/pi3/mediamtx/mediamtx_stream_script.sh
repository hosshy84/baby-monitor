#!/bin/bash
set -euo pipefail

# PulseAudioが動作しているか確認
if ! pulseaudio --check; then
    echo "PulseAudio is not running" >&2
    exit 1
fi

# 最初のUSBオーディオ入力ソースを動的に検出（特定デバイス名に依存しない）
AUDIO_SOURCE=""
for i in {1..10}; do
    AUDIO_SOURCE=$(pactl list sources short 2>/dev/null \
        | awk '/alsa_input/ && !/\.monitor/ {print $2; exit}')
    [ -n "$AUDIO_SOURCE" ] && break
    sleep 1
done

if [ -z "$AUDIO_SOURCE" ]; then
    echo "No audio input source found" >&2
    exit 1
fi

# ソースを有効化して音量調整
pactl suspend-source "$AUDIO_SOURCE" false 2>/dev/null || true
pactl set-source-volume "$AUDIO_SOURCE" 90% 2>/dev/null || true

echo "Using audio source: $AUDIO_SOURCE" >&2

# ハードウェアエンコード + 検出したオーディオソースでストリーム配信
exec ffmpeg \
    -thread_queue_size 1024 \
    -f v4l2 -video_size 1366x768 -framerate 15 -i /dev/video0 \
    -f pulse -ac 1 -ar 44100 -i "$AUDIO_SOURCE" \
    -c:v h264_v4l2m2m -bf 0 -b:v 1.5M -maxrate 1.5M -bufsize 3M -g 30 -pix_fmt yuv420p \
    -c:a aac -b:a 128k -ar 44100 -ac 1 \
    -async 1 -vsync cfr \
    -rtsp_transport tcp \
    -f rtsp rtsp://tatsuya:MG4Smd2+O@localhost:8554/stream
